package moe.nikableh.sift

import android.app.Application
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.BitSet
import java.util.concurrent.atomic.AtomicBoolean

sealed interface Screen {
    data object Home : Screen
    data object Sort : Screen
    data object Setup : Screen
    data object Selections : Screen
    data object Skipped : Screen
    data class Picker(val forSource: Boolean) : Screen
}

sealed interface Dialog {
    data class Progress(val title: String, val total: Int, val cancellable: Boolean) : Dialog
    data class MoveDone(val result: Mover.Result, val batch: Batch?) : Dialog
    data class ConfirmUndoBatch(val batch: Batch) : Dialog
    data class UndoFailed(val restored: Int, val failed: List<Pair<String, String>>) : Dialog
    data object ConfirmClear : Dialog
    data object SelectionsLocked : Dialog
    data class ConfirmUnhide(val count: Int) : Dialog
    data class ConfirmDiscardPicks(val count: Int) : Dialog
    data class ConfirmRemoveDest(val dest: Dest, val queued: Int) : Dialog
    data class RenameDest(val dest: Dest) : Dialog
    data class NewFolder(val parent: File, val forSource: Boolean, val album: Boolean = false) : Dialog
    data class GoToPhoto(val current: Int, val total: Int) : Dialog
}

enum class Gate { Permission, Setup, Reading, Empty, End, None }

class Album(val path: String, val name: String, val count: Int, val cover: String)

class Folder(val dir: File, val title: String, val subtitle: String)

class Flash(val id: Int, val label: String, val colorIndex: Int)

/** One photo waiting for Move. A null [dest] is a skip, which Move hides. */
class Selection(val file: File, val dest: String?)

/** The Selections screen's rows for one folder, or for the skips when [dest] is null. */
class SelectionGroup(val dest: String?, val rows: List<Selection>)

/** One Move's journal: the photos it moved and the skips it hid. */
data class Batch(val journal: File, val moved: Int, val skips: Int)

/** Screen state in front of [Store], which Compose can't observe on its own. Main thread only. */
class SiftViewModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        /** Sits next to Store's SKIP_ values, which [stateOf] passes through for photos that aren't queued */
        const val QUEUED = 3
    }

    /** One reversible step. A photo is queued for a folder, marked skipped, or neither. */
    private class UndoEntry(
        val path: String,
        val before: String?,
        val after: String?,
        val skipBefore: Int = Store.SKIP_NONE,
        val skipAfter: Int = Store.SKIP_NONE,
    )

    private val ctx get() = getApplication<Application>()

    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    var source by mutableStateOf<String?>(null)
        private set
    var recursive by mutableStateOf(false)
        private set
    var order by mutableIntStateOf(Store.ORDER_NEWEST)
        private set

    // Dest.label is mutable, so equal-looking lists still have to trigger recomposition
    var dests by mutableStateOf<List<Dest>>(emptyList(), neverEqualPolicy())
        private set

    var allowed by mutableStateOf(false)
        private set
    /** The photos to sort: the album without its hidden skips. */
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set

    /** What the Skipped screen shows: this album's hidden skips, in album order. */
    var hiddenPhotos by mutableStateOf<List<Photo>>(emptyList())
        private set

    /** Equal to photos.size on the end card. */
    var index by mutableIntStateOf(0)
        private set
    var scanning by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var queued by mutableIntStateOf(0)
        private set
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** Destination folder queued for the photo on screen. */
    var assigned by mutableStateOf<String?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var bitmapPath by mutableStateOf<String?>(null)
        private set

    /** Decoded photos on either side of the one on screen, for the viewer to slide in. Null until decoded. */
    var prevBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var nextBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var decodeFailed by mutableStateOf(false)
        private set
    var flash by mutableStateOf<Flash?>(null)
        private set
    var dialog by mutableStateOf<Dialog?>(null)
    var progress by mutableIntStateOf(0)
        private set

    /** Null while loading. */
    var albums by mutableStateOf<List<Album>?>(null)
        private set
    var browsing by mutableStateOf(false)
        private set

    /** Folders ticked in the destination picker. Tap order, because it becomes button order. */
    val picked = mutableStateListOf<String>()

    /** Null at the list of storage volumes. */
    var browseDir by mutableStateOf<File?>(null)
        private set
    var folders by mutableStateOf<List<Folder>>(emptyList())
        private set
    var imagesHere by mutableIntStateOf(0)
        private set

    /** The whole scan, hidden skips included. */
    private var all: List<Photo> = emptyList()
    private var inAll: Set<String> = emptySet()
    private var indexOf: Map<String, Int> = emptyMap()

    /** Positions in [photos] that are neither queued nor skipped, so finding the next one skips 64 at a time. */
    private var unsorted = BitSet()

    /** Set when a skip was undone, which leaves [photos] one photo short until the Skipped screen closes. */
    private var albumStale = false
    private val undo = ArrayDeque<UndoEntry>()
    private var scannedVersion = -1
    private var lastAssignAt = 0L
    private var flashId = 0
    private var cancel: AtomicBoolean? = null
    private var browseGeneration = 0
    private var batchKey: String? = null
    private var batchMoved = 0
    private var batchSkips = 0

    val current: Photo? get() = photos.getOrNull(index)

    val gate: Gate
        get() = when {
            !allowed -> Gate.Permission
            source == null || dests.isEmpty() -> Gate.Setup
            scanning -> Gate.Reading
            photos.isEmpty() -> Gate.Empty
            index >= photos.size -> Gate.End
            else -> Gate.None
        }

    /** Queued photos of the album on screen. The queue itself can also hold other albums. */
    var queuedHere by mutableIntStateOf(0)
        private set

    /** Photos of this album skipped since the last Move. They stay in the list until the next one. */
    var skippedHere by mutableIntStateOf(0)
        private set

    /** Skipped photos of this album that a Move took out of the list. */
    var hiddenHere by mutableIntStateOf(0)
        private set

    /** Size of the album without its hidden skips. */
    var total by mutableIntStateOf(0)
        private set

    /** Photos of this album that are neither queued nor skipped. */
    var leftHere by mutableIntStateOf(0)
        private set

    /** Folder choices and skips no Move has committed yet. The album can't change while there are any. */
    var selections by mutableIntStateOf(0)
        private set

    /** What the Selections screen lists: folder choices in button order, then skips. */
    var selectionGroups by mutableStateOf<List<SelectionGroup>>(emptyList())
        private set

    /** True when the photo on screen carries a skip mark. */
    var isSkipped by mutableStateOf(false)
        private set

    /** The last applied batch, for the undo entries. */
    var lastBatchInfo by mutableStateOf<Batch?>(null)
        private set

    init {
        Store.init(app)
        syncSettings()
        if (!Store.ready) {
            // Reads the queue like a folder: everything that would change it waits until it is in
            scanning = true
            viewModelScope.launch {
                val queues = withContext(Dispatchers.IO) {
                    // A big batch's journal has 100,000 lines, so its count is read here too
                    lastBatch()
                    Store.readQueues()
                }
                Store.useQueues(queues)
                scanning = false
                refresh()
            }
        }
    }

    // region navigation

    fun open(target: Screen) {
        if (busy) return
        keepPosition()
        backStack.add(target)
        if (target == Screen.Sort) refresh()
        if (target == Screen.Selections) loadSelections()
        if (target == Screen.Skipped) loadHidden()
        if (target is Screen.Picker) {
            browsing = false
            picked.clear()
            loadAlbums()
        }
    }

    fun back() {
        keepPosition()
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        // Lands on the first unsorted photo, which a restored skip can be, anywhere in the album
        if (albumStale) showAlbum(null)
        if (screen == Screen.Sort || screen == Screen.Home) refresh()
    }

    /** Remembers the photo on screen, so a rescan while on another screen comes back to it. */
    private fun keepPosition() {
        if (screen == Screen.Sort && scannedVersion == Store.scanVersion && !scanning) {
            Store.lastPath = current?.path
        }
    }

    // endregion

    // region sorting

    /** Called on resume and whenever the sorting screen comes back. */
    fun refresh() {
        allowed = Environment.isExternalStorageManager()
        syncSettings()
        // Before the queue is in, the start-up read above owns the journal count
        if (Store.ready) lastBatchInfo = lastBatch()
        if (!allowed || source == null || dests.isEmpty()) {
            showImage(null)
            return
        }
        if (scanning) return
        // Every change to the queue already updated the counts, so coming back only needs the position
        if (scannedVersion != Store.scanVersion) rescan() else syncPosition()
    }

    fun persist() {
        if (scannedVersion == Store.scanVersion) Store.lastPath = current?.path
        Store.saveConfig()
    }

    private fun rescan() {
        val root = Store.source ?: return
        val version = Store.scanVersion
        val recursive = Store.recursive
        val order = Store.order
        val exclude = Store.dests.map { it.path }.toSet()
        scanning = true
        showImage(null)
        viewModelScope.launch {
            // Indexing and counting a big album would freeze the screen on the main thread. Nothing
            // changes the queue while scanning is set, so reading it from here is safe
            val result = withContext(Dispatchers.IO) { prepare(Scanner.scan(File(root), recursive, exclude, order)) }
            scanning = false
            all = result.all
            inAll = result.inAll
            albumStale = false
            scannedVersion = version
            show(result.visible, result.indexed)
            land(Store.lastPath)
            useTally(result.tally)
            counts = result.counts
            if (screen == Screen.Selections) loadSelections()
            if (screen == Screen.Skipped) loadHidden()
            refresh()
        }
    }

    /** What a folder read hands to the main thread, worked out on the read's own thread. */
    private class ScanResult(
        val all: List<Photo>,
        val inAll: Set<String>,
        val visible: List<Photo>,
        val indexed: Indexed,
        val tally: Tally,
        val counts: Map<String, Int>,
    )

    private fun prepare(list: List<Photo>): ScanResult {
        val visible = list.filter { it.path !in Store.hidden }
        return ScanResult(
            list, list.mapTo(HashSet(list.size * 2)) { it.path }, visible, index(visible), tally(list), Store.counts(),
        )
    }

    private class Indexed(val indexOf: Map<String, Int>, val unsorted: BitSet)

    private fun index(list: List<Photo>): Indexed {
        val indexOf = HashMap<String, Int>(list.size * 2)
        val unsorted = BitSet(list.size)
        list.forEachIndexed { i, p ->
            indexOf[p.path] = i
            if (!reviewed(p.path)) unsorted.set(i)
        }
        return Indexed(indexOf, unsorted)
    }

    private fun show(list: List<Photo>, indexed: Indexed = index(list)) {
        photos = list
        indexOf = indexed.indexOf
        unsorted = indexed.unsorted
    }

    /** Lands on [at], or on the first unsorted photo. */
    private fun land(at: String?) {
        index = at?.let { indexOf[it] } ?: firstUnsorted()
    }

    /** Lists the album without its hidden skips and lands on [at]. */
    private fun showAlbum(at: String?) {
        albumStale = false
        show(all.filter { it.path !in Store.hidden })
        land(at)
    }

    private fun loadHidden() {
        hiddenPhotos = all.filter { it.path in Store.hidden }
    }

    /** Puts one hidden skip back into the album as not reviewed. */
    fun unhide(photo: Photo) {
        if (busy || scanning || Store.skipState(photo.path) != Store.SKIP_HIDDEN) return
        // The next photo takes this one's place, so a bounced tap would undo a skip nobody looked at
        val now = SystemClock.uptimeMillis()
        if (now - lastAssignAt < 120) return
        lastAssignAt = now
        changePhoto(photo.path) { Store.setSkip(photo.path, Store.SKIP_NONE) }
        hiddenPhotos = hiddenPhotos.filter { it !== photo }
        // Listing and indexing a 200,000-photo album again on every tap would stutter, so it waits for Back
        albumStale = true
        syncPosition()
        if (hiddenPhotos.isEmpty()) back()
    }

    /** Puts every hidden skip of this album back into the list as not reviewed. */
    fun unhideAll() {
        if (busy || scanning) return
        var changed = false
        for (photo in all) if (Store.hidden.remove(photo.path)) changed = true
        if (changed) Store.compactSkipped()
        hiddenPhotos = emptyList()
        showAlbum(current?.path)
        sync()
        if (screen == Screen.Skipped) back()
    }

    private fun reviewed(path: String) = stateOf(path) != Store.SKIP_NONE

    /** First photo from [from] on that is neither queued nor skipped. */
    private fun firstUnsorted(from: Int = 0): Int = unsorted.nextSetBit(from).takeIf { it >= 0 } ?: photos.size

    private fun showImage(path: String?) {
        if (path == null) {
            ImageLoader.wanted = emptySet()
            prevBitmap = null
            nextBitmap = null
        }
        bitmapPath = path
        bitmap = null
        decodeFailed = false
    }

    /** Pushes Store and position into observable state, and keeps the next photos decoded. */
    private fun sync() {
        recount()
        if (screen == Screen.Selections) loadSelections()
        syncPosition()
    }

    /** Counts the whole album and queue again. Anything that changes more than one photo goes through here. */
    private fun recount() {
        counts = Store.counts()
        useTally(tally(all))
    }

    private class Tally(var queued: Int = 0, var skipped: Int = 0, var hidden: Int = 0)

    /** The one full count, shared by a folder read and [recount], so the per-tap differences have a single source to match. */
    private fun tally(list: List<Photo>): Tally {
        val t = Tally()
        for (photo in list) {
            when (stateOf(photo.path)) {
                QUEUED -> t.queued++
                Store.SKIP_FRESH -> t.skipped++
                Store.SKIP_HIDDEN -> t.hidden++
            }
        }
        return t
    }

    private fun useTally(t: Tally) {
        queuedHere = t.queued
        skippedHere = t.skipped
        hiddenHere = t.hidden
    }

    /** Queued wins over a skip mark, as in [assign], which drops the mark. */
    private fun stateOf(path: String) = if (Store.pending.containsKey(path)) QUEUED else Store.skipState(path)

    /**
     * Runs a change to one photo and moves the counts by the difference, where a [recount] of a
     * 200,000-photo album takes a noticeable part of a second.
     */
    private inline fun changePhoto(path: String, change: () -> Unit) {
        val before = stateOf(path)
        val destBefore = Store.pending[path]
        change()
        val after = stateOf(path)
        val destAfter = Store.pending[path]
        if (before != after && path in inAll) {
            bump(before, -1)
            bump(after, 1)
        }
        indexOf[path]?.let { unsorted.set(it, !reviewed(path)) }
        if (destBefore != destAfter) {
            // A new map, so the folder badges see the change
            counts = HashMap(counts).also { m ->
                destBefore?.let { m[it] = (m[it] ?: 1) - 1 }
                destAfter?.let { m[it] = (m[it] ?: 0) + 1 }
            }
        }
    }

    private fun bump(state: Int, by: Int) {
        when (state) {
            QUEUED -> queuedHere += by
            Store.SKIP_FRESH -> skippedHere += by
            Store.SKIP_HIDDEN -> hiddenHere += by
        }
    }

    /** The cheap part of [sync]: totals from the counts, and the photo on screen. */
    private fun syncPosition() {
        queued = Store.pending.size
        canUndo = undo.isNotEmpty()
        selections = Store.pending.size + Store.skipped.size
        total = all.size - hiddenHere
        leftHere = total - queuedHere - skippedHere
        // The viewer there decides which photos get decoded, and this would take that over
        if (screen == Screen.Skipped) return
        val p = current
        assigned = p?.let { Store.pending[it.path] }
        isSkipped = p != null && assigned == null && Store.skipState(p.path) != Store.SKIP_NONE
        if (p == null) {
            showImage(null)
            // The end card can still be swiped back to the last photo
            prevBitmap = photos.getOrNull(index - 1)?.let { ImageLoader.cached(it.file) }
            return
        }
        val want = HashSet<String>()
        for (d in intArrayOf(0, 1, 2, 3, -1)) photos.getOrNull(index + d)?.let { want.add(it.path) }
        ImageLoader.wanted = want
        if (bitmapPath != p.path) {
            showImage(p.path)
            bitmap = ImageLoader.cached(p.file)
            if (bitmap == null) {
                ImageLoader.load(p.file) { bmp ->
                    if (bitmapPath != p.path) return@load
                    if (bmp != null) bitmap = bmp else decodeFailed = true
                }
            }
        }
        neighbour(1) { nextBitmap = it }
        neighbour(-1) { prevBitmap = it }
        for (d in intArrayOf(2, 3)) photos.getOrNull(index + d)?.let { ImageLoader.prefetch(it.file) }
    }

    private fun neighbour(delta: Int, set: (Bitmap?) -> Unit) {
        val at = index
        val photo = photos.getOrNull(at + delta)
        set(photo?.let { ImageLoader.cached(it.file) })
        if (photo == null) return
        ImageLoader.load(photo.file) { bmp -> if (index == at && photos.getOrNull(at + delta) === photo) set(bmp) }
    }

    /** The viewer asks for a sharper bitmap the first time the user zooms into a photo. */
    fun loadSharper() {
        val p = current ?: return
        ImageLoader.loadFull(p.file) { bmp -> if (bmp != null && bitmapPath == p.path) bitmap = bmp }
    }

    fun go(delta: Int) {
        if (busy || scanning) return
        val next = (index + delta).coerceIn(0, photos.size)
        if (next == index) return
        index = next
        flash = null
        syncPosition()
    }

    /**
     * The Skip button: marks the photo as looked at and left in place, then shows the next one.
     * A swipe only navigates and leaves no mark.
     */
    fun skip() {
        if (busy || scanning) return
        val p = current ?: return
        // Same guard as assign, or a bounced tap would mark a photo nobody looked at
        val now = SystemClock.uptimeMillis()
        if (now - lastAssignAt < 120) return
        lastAssignAt = now
        if (!reviewed(p.path)) {
            changePhoto(p.path) { Store.setSkip(p.path, Store.SKIP_FRESH) }
            pushUndo(UndoEntry(p.path, null, null, skipAfter = Store.SKIP_FRESH))
        }
        flash = null
        index = (index + 1).coerceAtMost(photos.size)
        syncPosition()
    }

    fun clearSkip() {
        if (busy || scanning) return
        val p = current ?: return
        val before = Store.skipState(p.path)
        if (before == Store.SKIP_NONE) return
        changePhoto(p.path) { Store.setSkip(p.path, Store.SKIP_NONE) }
        pushUndo(UndoEntry(p.path, null, null, skipBefore = before))
        syncPosition()
    }

    fun jumpTo(target: Int) {
        if (busy || scanning) return
        index = target.coerceIn(0, photos.size)
        syncPosition()
    }

    fun assign(dest: Dest) {
        val p = current ?: return
        if (busy || scanning) return
        // Swallows the second half of an accidental double tap, which would file the next photo unseen
        val now = SystemClock.uptimeMillis()
        if (now - lastAssignAt < 120) return
        lastAssignAt = now

        val before = Store.pending[p.path]
        if (before != dest.path) {
            // Queued and skipped exclude each other, so filing a skipped photo drops its mark
            val skip = Store.skipState(p.path)
            changePhoto(p.path) {
                if (skip != Store.SKIP_NONE) Store.setSkip(p.path, Store.SKIP_NONE)
                Store.setPending(p.path, dest.path)
            }
            pushUndo(UndoEntry(p.path, before, dest.path, skipBefore = skip))
        }
        flash = Flash(++flashId, dest.label, Store.dests.indexOf(dest))
        // Jumps past photos that already have a folder, so fixing an old choice returns to where you left off
        index = firstUnsorted(index + 1)
        syncPosition()
    }

    fun flashShown(id: Int) {
        if (flash?.id == id) flash = null
    }

    fun clearAssignment() {
        if (busy || scanning) return
        val p = current ?: return
        val before = Store.pending[p.path] ?: return
        changePhoto(p.path) { Store.setPending(p.path, null) }
        pushUndo(UndoEntry(p.path, before, null))
        syncPosition()
    }

    private fun pushUndo(e: UndoEntry) {
        undo.addLast(e)
        if (undo.size > 1000) undo.removeFirst()
    }

    fun undoLast() {
        if (busy || scanning) return
        val e = undo.removeLastOrNull() ?: return
        changePhoto(e.path) {
            if (e.before != e.after) Store.setPending(e.path, e.before)
            if (e.skipBefore != e.skipAfter) Store.setSkip(e.path, e.skipBefore)
        }
        indexOf[e.path]?.let { index = it }
        syncPosition()
    }

    fun reload() {
        if (busy || scanning) return
        ImageLoader.clear()
        Store.lastPath = current?.path
        Store.scanVersion++
        refresh()
    }

    private fun loadSelections() {
        val buttons = Store.dests.map { it.path }
        val byDest = Store.pending.entries.groupBy({ it.value }, { Selection(File(it.key), it.value) })
        val out = ArrayList<SelectionGroup>()
        for (dest in buttons + byDest.keys.filter { it !in buttons }) byDest[dest]?.let { out.add(SelectionGroup(dest, it)) }
        if (Store.skipped.isNotEmpty()) out.add(SelectionGroup(null, Store.skipped.map { Selection(File(it), null) }))
        selectionGroups = out
    }

    /** Takes one row out of the selections, from the Selections screen. */
    fun dropSelection(row: Selection) {
        if (busy || scanning) return
        val path = row.file.path
        val dest = Store.pending[path]
        changePhoto(path) {
            if (dest != null) Store.setPending(path, null) else Store.setSkip(path, Store.SKIP_NONE)
        }
        undo.clear()
        // Only the row's own group changes, and the row is found by identity, so one removal costs
        // a copy of one group instead of regrouping 100,000 queued photos
        selectionGroups = selectionGroups.mapNotNull { g ->
            val at = if (g.dest == dest) g.rows.indexOf(row) else -1
            when {
                at < 0 -> g
                g.rows.size == 1 -> null
                else -> SelectionGroup(dest, ArrayList(g.rows).apply { removeAt(at) })
            }
        }
        syncPosition()
    }

    /** Drops everything no Move has committed: folder choices and the skips made since the last one. */
    fun clearSelections() {
        if (busy || scanning) return
        Store.pending.clear()
        Store.compactPending()
        Store.skipped.clear()
        Store.compactSkipped()
        undo.clear()
        sync()
    }

    private fun lastBatch(): Batch? = try {
        // One line per moved file or hidden skip. A big batch has 100,000 of them, so the counts are kept until the file changes
        Store.journals().firstOrNull()?.let { file ->
            val key = "${file.name}:${file.length()}"
            if (key != batchKey) {
                var moved = 0
                var skips = 0
                file.useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        if (Mover.isMoveLine(line)) moved++ else skips++
                    }
                }
                batchMoved = moved
                batchSkips = skips
                batchKey = key
            }
            Batch(file, batchMoved, batchSkips)
        }
    } catch (e: Exception) {
        null
    }

    /** Opens the Selections screen, the only place with a button that moves files. */
    fun reviewSelections() {
        if (busy || scanning || (Store.pending.isEmpty() && Store.skipped.isEmpty())) return
        dialog = null
        if (screen != Screen.Selections) open(Screen.Selections)
    }

    private fun leaveSelections() {
        if (screen == Screen.Selections) backStack.removeAt(backStack.lastIndex)
    }

    fun move() {
        if (busy || scanning || (Store.pending.isEmpty() && Store.skipped.isEmpty())) return
        val entries = Store.pending.map { it.key to it.value }
        val skips = Store.skipped.toList()
        // Where to land afterwards: the first photo from here on that is neither moved nor hidden as skipped
        val landing = photos.getOrNull(firstUnsorted(index))?.path
        val journal = Store.newJournal()
        if (entries.isEmpty()) {
            // Skips alone: nothing moves on disk, so the list is rebuilt without a scan
            busy = true
            viewModelScope.launch {
                val logged = withContext(Dispatchers.IO) {
                    try {
                        Mover.logSkips(journal, skips)
                        true
                    } catch (e: Exception) {
                        journal.delete()
                        false
                    }
                }
                busy = false
                lastBatchInfo = if (logged) Batch(journal, 0, skips.size) else lastBatch()
                Store.hideSkipped()
                undo.clear()
                flash = null
                dialog = null
                leaveSelections()
                showAlbum(landing)
                sync()
            }
            return
        }
        val stop = AtomicBoolean(false)
        cancel = stop
        progress = 0
        dialog = Dialog.Progress("Moving ${entries.size} photos", entries.size, cancellable = true)
        busy = true
        Store.lastPath = landing
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val r = Mover.move(ctx, entries, journal, stop) { done -> if (done % 8 == 0) progress = done }
                // The skips get hidden only when something moved, so only then do they belong to the batch
                if (r.moved > 0) {
                    try {
                        Mover.logSkips(journal, skips)
                    } catch (e: Exception) {
                    }
                }
                r
            }
            for (path in result.settled) Store.pending.remove(path)
            Store.compactPending()
            if (result.moved > 0) Store.hideSkipped()
            undo.clear()
            ImageLoader.clear()
            busy = false
            Store.scanVersion++
            dialog = Dialog.MoveDone(result, lastBatch().takeIf { result.moved > 0 })
            // What failed or was stopped is still queued, and then the list stays up to show it
            if (Store.pending.isEmpty()) leaveSelections()
            refresh()
        }
    }

    fun stopMove() {
        cancel?.set(true)
    }

    fun undoBatch(batch: Batch) {
        // A folder read counts against the queue from another thread, so the queue holds still until it ends
        if (busy || scanning) return
        progress = 0
        val title = if (batch.moved > 0) "Moving ${batch.moved} photos back" else "Bringing back ${batch.skips} skipped photos"
        dialog = Dialog.Progress(title, batch.moved + batch.skips, cancellable = false)
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                Mover.undo(ctx, batch.journal) { done -> if (done % 8 == 0) progress = done }
            }
            for ((path, dest) in result.restored) Store.pending[path] = dest
            Store.compactPending()
            Store.reopenSkips(result.unskipped)
            undo.clear()
            ImageLoader.clear()
            busy = false
            Store.lastPath = result.restored.lastOrNull()?.first ?: result.unskipped.lastOrNull()
            if (result.restored.isNotEmpty() || result.failed.isNotEmpty()) {
                Store.scanVersion++
            } else if (scannedVersion == Store.scanVersion) {
                // Skips alone, as in move: nothing moved on disk, so the list is rebuilt without a scan
                showAlbum(Store.lastPath)
                sync()
            }
            dialog = if (result.failed.isEmpty()) null else Dialog.UndoFailed(result.restored.size, result.failed)
            val hasSelections = Store.pending.isNotEmpty() || Store.skipped.isNotEmpty()
            if (hasSelections && screen != Screen.Selections) open(Screen.Selections)
            refresh()
        }
    }

    // endregion

    // region settings

    private fun syncSettings() {
        source = Store.source
        recursive = Store.recursive
        order = Store.order
        dests = Store.dests.toList()
        queued = Store.pending.size
        selections = Store.pending.size + Store.skipped.size
    }

    private fun changed(rescan: Boolean) {
        if (rescan) Store.scanVersion++
        Store.saveConfig()
        syncSettings()
    }

    /**
     * The album and its subfolder setting stay fixed while selections wait for Move. Changing
     * either would leave choices queued for photos that are no longer on screen.
     */
    private fun locked(): Boolean {
        // An empty queue that hasn't been read yet would look unlocked
        if (!Store.ready) return true
        if (Store.pending.isEmpty() && Store.skipped.isEmpty()) return false
        dialog = Dialog.SelectionsLocked
        return true
    }

    fun askPickSource() {
        if (Store.source != null && locked()) return
        open(Screen.Picker(forSource = true))
    }

    fun changeRecursive(on: Boolean) {
        if (!on && locked()) return
        Store.recursive = on
        changed(rescan = true)
    }

    fun changeOrder(value: Int) {
        if (value == Store.order) return
        Store.order = value
        changed(rescan = true)
    }

    fun pickSource(path: String) {
        if (Store.dests.any { it.path == path }) {
            messages.tryEmit("That folder is already a destination")
            return
        }
        Store.source = path
        Store.lastPath = null
        Store.lastPickerDir = File(path).parent
        changed(rescan = true)
        back()
    }

    /** True for the folder being sorted and for folders that already are destinations. */
    fun isTaken(path: String): Boolean = path == source || dests.any { it.path == path }

    fun togglePicked(path: String) {
        if (picked.remove(path)) return
        if (path == Store.source) {
            messages.tryEmit("That's the folder being sorted")
            return
        }
        if (Store.dests.any { it.path == path }) {
            messages.tryEmit("Already a destination")
            return
        }
        picked.add(path)
    }

    /** Leaves the picker, asking first when that would drop ticked folders. */
    fun leavePicker() {
        if (picked.isEmpty()) back() else dialog = Dialog.ConfirmDiscardPicks(picked.size)
    }

    /** Adds every ticked folder as a destination in one go and leaves the picker. */
    fun addPicked() {
        if (picked.isEmpty()) return
        for (path in picked) Store.dests.add(Dest(path, File(path).name))
        Store.lastPickerDir = File(picked.last()).parent
        picked.clear()
        changed(rescan = true)
        back()
    }

    fun moveDest(from: Int, to: Int) {
        if (to !in Store.dests.indices) return
        val t = Store.dests[from]
        Store.dests[from] = Store.dests[to]
        Store.dests[to] = t
        changed(rescan = false)
    }

    fun renameDest(dest: Dest, label: String) {
        dest.label = label.trim().ifEmpty { File(dest.path).name }
        changed(rescan = false)
    }

    fun askRemoveDest(dest: Dest) {
        if (scanning) {
            messages.tryEmit("Wait until the folder has been read")
            return
        }
        val n = counts[dest.path] ?: 0
        if (n == 0) removeDest(dest) else dialog = Dialog.ConfirmRemoveDest(dest, n)
    }

    fun removeDest(dest: Dest) {
        if (scanning) return
        Store.dests.remove(dest)
        if (Store.pending.values.removeAll { it == dest.path }) Store.compactPending()
        changed(rescan = true)
    }

    // endregion

    // region picker

    /**
     * Albums as a gallery shows them: every folder MediaStore has indexed images in, newest first.
     * All-files access is enough to read that index. If the index comes back empty, a walk over
     * the disk stands in for it.
     */
    private fun loadAlbums() {
        albums = null
        viewModelScope.launch {
            albums = withContext(Dispatchers.IO) { queryAlbums().ifEmpty { walkAlbums() } }
        }
    }

    private fun queryAlbums(): List<Album> {
        class Acc(var count: Int, var cover: String, var coverTime: Long)

        val found = HashMap<String, Acc>()
        try {
            @Suppress("DEPRECATION")
            val columns = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_MODIFIED)
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    val parent = File(path).parent ?: continue
                    val time = c.getLong(1)
                    val acc = found[parent]
                    if (acc == null) {
                        found[parent] = Acc(1, path, time)
                    } else {
                        acc.count++
                        if (time > acc.coverTime) {
                            acc.cover = path
                            acc.coverTime = time
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // The disk walk covers for a failed query
        }
        return found.entries
            .sortedByDescending { it.value.coverTime }
            .map { Album(it.key, File(it.key).name, it.value.count, it.value.cover) }
    }

    private fun walkAlbums(): List<Album> =
        Scanner.albums(volumes().map { it.dir })
            .sortedByDescending { it.count }
            .map { Album(it.path, File(it.path).name, it.count, it.cover) }

    fun browse(dir: File?) {
        browsing = true
        browseDir = dir
        folders = emptyList()
        imagesHere = 0
        val generation = ++browseGeneration
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { if (dir == null) volumes() else children(dir) }
            if (generation != browseGeneration) return@launch
            folders = list
            if (dir != null) {
                val here = withContext(Dispatchers.IO) { Scanner.countImages(dir) }
                if (generation == browseGeneration) imagesHere = here
            }
        }
    }

    fun browseUp() {
        val dir = browseDir
        if (dir == null) {
            browsing = false
            return
        }
        val parent = dir.parentFile
        browse(if (parent != null && parent.canRead()) parent else null)
    }

    private fun volumes(): List<Folder> {
        val out = ArrayList<Folder>()
        val primary = Environment.getExternalStorageDirectory()
        out.add(Folder(primary, "Internal storage", primary.path))
        for (v in ctx.getSystemService(StorageManager::class.java).storageVolumes) {
            val d = v.directory ?: continue
            if (!v.isPrimary) out.add(Folder(d, v.getDescription(ctx), d.path))
        }
        return out
    }

    private fun children(dir: File): List<Folder> =
        (dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray())
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map {
                val n = Scanner.countImages(it)
                Folder(it, it.name, if (n == 1) "1 image" else "$n images")
            }

    /** New albums go under Pictures, where gallery apps expect them. */
    fun picturesDir(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    fun createFolder(parent: File, name: String, forSource: Boolean) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) return
        val made = File(parent, clean)
        if (made.isDirectory || made.mkdirs()) {
            if (forSource) {
                pickSource(made.path)
            } else {
                // Stays in the picker, so more folders can be ticked before adding them all
                if (made.path !in picked) togglePicked(made.path)
                if (browsing) browse(browseDir)
            }
        } else {
            messages.tryEmit("Can't create a folder there")
        }
    }

    // endregion
}
