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
    data class Picker(val forSource: Boolean) : Screen
}

sealed interface Dialog {
    data class Progress(val title: String, val total: Int, val cancellable: Boolean) : Dialog
    data class MoveDone(val result: Mover.Result, val batch: Batch?) : Dialog
    data class ConfirmUndoBatch(val batch: Batch) : Dialog
    data class UndoFailed(val restored: Int, val failed: List<Pair<String, String>>) : Dialog
    data object ConfirmClear : Dialog
    data object SelectionsLocked : Dialog
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

/** One photo waiting for Move. */
class Selection(val file: File, val dest: String)

/** The Selections screen's rows for one folder. */
class SelectionGroup(val dest: String, val rows: List<Selection>)

/** One Move's journal. */
data class Batch(val journal: File, val moved: Int)

/** Screen state in front of [Store], which Compose can't observe on its own. Main thread only. */
class SiftViewModel(app: Application) : AndroidViewModel(app) {
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
    var photos by mutableStateOf<List<Photo>>(emptyList())
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

    private var indexOf: Map<String, Int> = emptyMap()

    /** Positions in [photos] that aren't queued, so finding the next one skips 64 at a time. */
    private var unsorted = BitSet()
    private var scannedVersion = -1
    private var lastAssignAt = 0L
    private var flashId = 0
    private var cancel: AtomicBoolean? = null
    private var browseGeneration = 0
    private var batchKey: String? = null
    private var batchMoved = 0

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

    val leftHere: Int get() = photos.size - queuedHere

    /** What the Selections screen lists, in button order. */
    var selectionGroups by mutableStateOf<List<SelectionGroup>>(emptyList())
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
                val queue = withContext(Dispatchers.IO) {
                    // A big batch's journal has 100,000 lines, so its count is read here too
                    lastBatch()
                    Store.readQueue()
                }
                Store.useQueue(queue)
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
        if (target is Screen.Picker) {
            browsing = false
            picked.clear()
            loadAlbums()
        }
    }

    fun back() {
        keepPosition()
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
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
            scannedVersion = version
            photos = result.photos
            indexOf = result.indexOf
            unsorted = result.unsorted
            index = Store.lastPath?.let { indexOf[it] } ?: firstUnsorted()
            queuedHere = result.queued
            counts = result.counts
            if (screen == Screen.Selections) loadSelections()
            refresh()
        }
    }

    /** What a folder read hands to the main thread, worked out on the read's own thread. */
    private class ScanResult(
        val photos: List<Photo>,
        val indexOf: Map<String, Int>,
        val unsorted: BitSet,
        val queued: Int,
        val counts: Map<String, Int>,
    )

    private fun prepare(list: List<Photo>): ScanResult {
        val indexOf = HashMap<String, Int>(list.size * 2)
        val unsorted = BitSet(list.size)
        list.forEachIndexed { i, p ->
            indexOf[p.path] = i
            if (p.path !in Store.pending) unsorted.set(i)
        }
        return ScanResult(list, indexOf, unsorted, list.size - unsorted.cardinality(), Store.counts())
    }

    /** First unqueued photo from [from] on. */
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
        queuedHere = photos.count { it.path in Store.pending }
        for (i in photos.indices) unsorted.set(i, photos[i].path !in Store.pending)
    }

    /**
     * Sets or clears one photo's folder and moves the counts by the difference, where a [recount]
     * of a 200,000-photo album takes a noticeable part of a second.
     */
    private fun setPending(path: String, dest: String?) {
        val before = Store.pending[path]
        if (before == dest) return
        Store.setPending(path, dest)
        indexOf[path]?.let { i ->
            unsorted.set(i, dest == null)
            if (before == null) queuedHere++ else if (dest == null) queuedHere--
        }
        // A new map, so the folder badges see the change
        counts = HashMap(counts).also { m ->
            before?.let { m[it] = (m[it] ?: 1) - 1 }
            dest?.let { m[it] = (m[it] ?: 0) + 1 }
        }
    }

    /** The cheap part of [sync]: totals from the counts, and the photo on screen. */
    private fun syncPosition() {
        queued = Store.pending.size
        val p = current
        assigned = p?.let { Store.pending[it.path] }
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

        setPending(p.path, dest.path)
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
        setPending(p.path, null)
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
        selectionGroups = out
    }

    /** Takes one row out of the selections, from the Selections screen. */
    fun dropSelection(row: Selection) {
        if (busy || scanning) return
        val path = row.file.path
        val dest = Store.pending[path] ?: return
        setPending(path, null)
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

    /** Drops every folder choice no Move has committed. */
    fun clearSelections() {
        if (busy || scanning) return
        Store.pending.clear()
        Store.compactPending()
        sync()
    }

    private fun lastBatch(): Batch? = try {
        // One line per moved file. A big batch has 100,000 of them, so the count is kept until the file changes
        Store.journals().firstNotNullOfOrNull { file ->
            val key = "${file.name}:${file.length()}"
            if (key != batchKey) {
                batchMoved = file.useLines { lines -> lines.count { Mover.isMoveLine(it) } }
                batchKey = key
            }
            if (batchMoved > 0) {
                Batch(file, batchMoved)
            } else {
                // Hid skips and moved nothing, so there is nothing left to undo. A running Move's
                // journal is empty until its first file lands, so that one stays
                if (!busy) file.delete()
                null
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Opens the Selections screen, the only place with a button that moves files. */
    fun reviewSelections() {
        if (busy || scanning || Store.pending.isEmpty()) return
        dialog = null
        if (screen != Screen.Selections) open(Screen.Selections)
    }

    private fun leaveSelections() {
        if (screen == Screen.Selections) backStack.removeAt(backStack.lastIndex)
    }

    fun move() {
        if (busy || scanning || Store.pending.isEmpty()) return
        val entries = Store.pending.map { it.key to it.value }
        // Where to land afterwards: the first photo from here on that isn't moved
        val landing = photos.getOrNull(firstUnsorted(index))?.path
        val journal = Store.newJournal()
        val stop = AtomicBoolean(false)
        cancel = stop
        progress = 0
        dialog = Dialog.Progress("Moving ${entries.size} photos", entries.size, cancellable = true)
        busy = true
        Store.lastPath = landing
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                Mover.move(ctx, entries, journal, stop) { done -> if (done % 8 == 0) progress = done }
            }
            for (path in result.settled) Store.pending.remove(path)
            Store.compactPending()
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
        dialog = Dialog.Progress("Moving ${batch.moved} photos back", batch.moved, cancellable = false)
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                Mover.undo(ctx, batch.journal) { done -> if (done % 8 == 0) progress = done }
            }
            for ((path, dest) in result.restored) Store.pending[path] = dest
            Store.compactPending()
            ImageLoader.clear()
            busy = false
            Store.lastPath = result.restored.lastOrNull()?.first
            Store.scanVersion++
            dialog = if (result.failed.isEmpty()) null else Dialog.UndoFailed(result.restored.size, result.failed)
            if (Store.pending.isNotEmpty() && screen != Screen.Selections) open(Screen.Selections)
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
        if (Store.pending.isEmpty()) return false
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
        // A folder can't be sorted into itself
        Store.dests.firstOrNull { it.path == path }?.let { dest ->
            Store.dests.remove(dest)
            if (Store.pending.values.removeAll { it == path }) Store.compactPending()
            messages.tryEmit("Removed the ${dest.label} button")
        }
        Store.source = path
        Store.lastPath = null
        Store.lastPickerDir = File(path).parent
        changed(rescan = true)
        back()
    }

    /** True for the folder being sorted, and when adding destinations, for folders that already are ones. */
    fun isTaken(path: String, forSource: Boolean): Boolean =
        path == source || (!forSource && dests.any { it.path == path })

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
