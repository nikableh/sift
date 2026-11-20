package moe.nikableh.sift

import android.Manifest
import android.app.Application
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
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
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.BitSet

sealed interface Screen {
    data object Home : Screen
    data object Sort : Screen
    data object Setup : Screen
    data object Selections : Screen
    data object About : Screen
    data object Licenses : Screen
    data class Picker(val forSource: Boolean) : Screen
}

sealed interface Dialog {
    /** While Android is asked for write access, before anything moves. */
    data class Preparing(val title: String) : Dialog
    data class MoveDone(val result: Mover.Result, val batch: Batch?) : Dialog

    /** At the first launch after the process died under a Move. [left] photos are still queued. */
    data class MoveInterrupted(val moved: Int, val left: Int) : Dialog
    data class UndoInterrupted(val batch: Batch) : Dialog
    data class ConfirmUndoBatch(val batch: Batch) : Dialog
    data class UndoFailed(val restored: Int, val failed: List<Pair<String, String>>) : Dialog
    data object ConfirmClear : Dialog
    data object SelectionsLocked : Dialog
    data class ConfirmDiscardPicks(val count: Int, val unticked: Int) : Dialog
    data class ConfirmRemoveDest(val dest: Dest, val queued: Int) : Dialog

    /**
     * [queued] selections wait in the albums, [elsewhere] in the other albums. With [picker] set,
     * confirming saves the album picker, which may add albums too
     */
    data class ConfirmRemoveSource(
        val paths: List<String>,
        val queued: Int,
        val elsewhere: Int,
        val picker: Boolean = false,
    ) : Dialog
    data class RenameDest(val dest: Dest) : Dialog
    data class NewFolder(val parent: File, val forSource: Boolean, val album: Boolean = false) : Dialog
    data class GoToPhoto(val current: Int, val total: Int) : Dialog
}

enum class Gate { Permission, Setup, Reading, Empty, End, None }

/** How far Android lets Sift go. Only [Ready] shows the app itself. */
enum class Access { AskPhotos, PhotosDenied, PhotosLimited, AskManage, Ready }

/** [videos] is the part of [count] that is videos. */
class Album(val path: String, val name: String, val count: Int, val videos: Int, val cover: String)

/**
 * One album being sorted. [videos] is the part of [total] that is videos, and [cover] is its first
 * photo or video in sorting order
 */
class AlbumStat(val path: String, val name: String, val total: Int, val videos: Int, val queued: Int, val cover: File?)

/** [count] is what the folder holds directly. A storage volume has none and shows its path instead. */
class Folder(val dir: File, val title: String, val count: Scanner.Count?)

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

    /** Albums to sort, in the order they were added. */
    var sources by mutableStateOf<List<String>>(emptyList())
        private set

    /** The albums of [sources] that a Move emptied. They are read like the others and come back when they hold photos again. */
    var sortedAlbums by mutableStateOf<List<String>>(emptyList())
        private set

    val activeAlbums: List<String> get() = sources.filter { it !in sortedAlbums }

    val allSorted: Boolean get() = sources.isNotEmpty() && sortedAlbums.size == sources.size

    /** What Home and the sorting screen name: the albums still to sort, or every album once all are sorted. */
    val shownAlbums: List<String> get() = if (allSorted) sources else activeAlbums

    /**
     * The albums of the last folder read, in the order they had then. An album added since has no
     * entry yet, and one removed since keeps its entry until the next read, so look them up by path
     */
    var albumStats by mutableStateOf<List<AlbumStat>>(emptyList())
        private set
    var recursive by mutableStateOf(false)
        private set
    var order by mutableIntStateOf(Store.ORDER_NEWEST)
        private set

    // Dest.label is mutable, so equal-looking lists still have to trigger recomposition
    var dests by mutableStateOf<List<Dest>>(emptyList(), neverEqualPolicy())
        private set

    var access by mutableStateOf(Access.AskPhotos)
        private set
    val allowed: Boolean get() = access == Access.Ready

    /**
     * Set while photos are allowed and videos are not, which is how an install from before video
     * sorting starts. The screen then asks for them, and Android answers that without a dialog
     */
    var askVideo by mutableStateOf(false)
        private set
    private var videoAllowed = false
    private var videoAsked = false

    /** False while Android would ask for confirmation on every Move. Always true before Android 12, which has no switch */
    var manageMedia by mutableStateOf(true)
        private set

    /** A write request for the activity to launch. Android answers it without a dialog while [manageMedia] is on */
    var writeRequest by mutableStateOf<IntentSender?>(null)
        private set
    private var writeAnswer: CompletableDeferred<Boolean>? = null
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set

    /** Equal to photos.size on the end card. */
    var index by mutableIntStateOf(0)
        private set
    var scanning by mutableStateOf(false)
        private set
    /** Asking Android for write access, before a run starts. */
    var preparing by mutableStateOf(false)
        private set

    /** A Move or an undo is being prepared or is running, started here or by a view model before this one. */
    val busy: Boolean get() = preparing || MoveRunner.run != null
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

    /** The moving picture of the GIF on screen, which [bitmap] is the first frame of. */
    var animation by mutableStateOf<AnimatedImageDrawable?>(null)
        private set

    /** Decoded photos on either side of the one on screen, for the viewer to slide in. Null until decoded. */
    var prevBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var nextBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var decodeFailed by mutableStateOf(false)
        private set

    var videoMuted by mutableStateOf(true)
        private set

    /** Length of the video on screen in milliseconds. Null for a photo, and until the video's still frame is in */
    var duration by mutableStateOf<Long?>(null)
        private set
    var flash by mutableStateOf<Flash?>(null)
        private set
    var dialog by mutableStateOf<Dialog?>(null)

    /** Null while loading. */
    var albums by mutableStateOf<List<Album>?>(null)
        private set
    var browsing by mutableStateOf(false)
        private set

    /** Folders ticked in the picker. Tap order, because for destinations it becomes button order. */
    val picked = mutableStateListOf<String>()

    /** Albums being sorted that were unticked in the picker. They stay in [sources] until it is saved. */
    val unticked = mutableStateListOf<String>()

    /** Null at the list of storage volumes. */
    var browseDir by mutableStateOf<File?>(null)
        private set
    var folders by mutableStateOf<List<Folder>>(emptyList())
        private set
    var mediaHere by mutableStateOf(Scanner.Count(0, 0))
        private set

    private var indexOf: Map<String, Int> = emptyMap()

    /** What [Photo.album] points into: the albums as they were when [photos] was read. */
    private var scannedSources: List<String> = emptyList()
    private var albumTotals = IntArray(0)
    private var albumVideos = IntArray(0)
    private var albumQueued = IntArray(0)
    private var albumCovers = arrayOfNulls<File>(0)

    /** Positions in [photos] that aren't queued, so finding the next one skips 64 at a time. */
    private var unsorted = BitSet()
    private var scannedVersion = -1

    /** The order [photos] was read in, or -1 before the first read. */
    private var scannedOrder = -1
    private var lastAssignAt = 0L
    private var flashId = 0
    private var browseGeneration = 0

    /** Held back until the first folder read ends, because Move and undo do nothing during one. */
    private var interrupted: Dialog? = null
    private var batchKey: String? = null
    private var batchMoved = 0

    val current: Photo? get() = photos.getOrNull(index)

    val gate: Gate
        get() = when {
            !allowed -> Gate.Permission
            sources.isEmpty() || dests.isEmpty() -> Gate.Setup
            scanning -> Gate.Reading
            photos.isEmpty() -> Gate.Empty
            index >= photos.size -> Gate.End
            else -> Gate.None
        }

    /** Queued photos of the albums on screen. The queue itself can also hold photos from elsewhere. */
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
        readAccess()
        if (!Store.ready) {
            // Reads the queue like a folder: everything that would change it waits until it is in
            scanning = true
            viewModelScope.launch {
                var movedBefore = emptyList<String>()
                val (queue, cut) = withContext(Dispatchers.IO) {
                    val queue = Store.readQueue()
                    val cut = Store.recover(ctx, queue)
                    // A big batch's journal has 100,000 lines, so its count is read here too
                    lastBatch()
                    // Neither an earlier version nor a Move cut short noted the albums it took from
                    val unnoted = when {
                        Store.sortedUnknown -> Store.journals()
                        cut != null && !cut.undo -> listOf(cut.journal)
                        else -> emptyList()
                    }
                    movedBefore = unnoted.flatMap { journal -> Mover.readJournal(journal).map { it.first } }
                    queue to cut
                }
                Store.useQueue(queue)
                Store.noteMovedFrom(movedBefore)
                interrupted = when {
                    cut == null -> null
                    cut.undo -> Dialog.UndoInterrupted(Batch(cut.journal, cut.moved)).takeIf { cut.moved > 0 }
                    else -> Dialog.MoveInterrupted(cut.moved, queue.size).takeIf { cut.moved > 0 || queue.isNotEmpty() }
                }
                scanning = false
                refresh()
            }
        }
        viewModelScope.launch {
            // A run can end while no view model is around, and then its outcome waits for this one
            snapshotFlow { MoveRunner.outcome }.collect { if (it != null) finished(it) }
        }
    }

    private fun finished(outcome: MoveRunner.Outcome) {
        MoveRunner.outcome = null
        when (outcome) {
            is MoveRunner.Outcome.Moved -> {
                val result = outcome.result
                dialog = Dialog.MoveDone(result, lastBatch().takeIf { result.moved > 0 })
                // What failed or was stopped is still queued, and then the list stays up to show it
                if (Store.pending.isEmpty()) leaveSelections()
            }
            is MoveRunner.Outcome.Undone -> {
                val result = outcome.result
                dialog = if (result.failed.isEmpty()) null else Dialog.UndoFailed(result.restored.size, result.failed)
                if (Store.pending.isNotEmpty() && screen != Screen.Selections) open(Screen.Selections)
            }
        }
        refresh()
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
            clearPicks()
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
        val wasAllowed = allowed
        val hadVideo = videoAllowed
        readAccess()
        // A read made without full access found nothing or only part of the albums
        if ((allowed && !wasAllowed) || (videoAllowed && !hadVideo)) Store.scanVersion++
        syncSettings()
        // Before the queue is in, the start-up read above owns the journal count
        if (Store.ready) lastBatchInfo = lastBatch()
        if (!allowed || sources.isEmpty() || dests.isEmpty()) {
            showImage(null)
            announceInterrupted()
            return
        }
        // A folder read counts against the queue from another thread, and a run settles the queue when it ends
        if (scanning || MoveRunner.run != null) return
        // Every change to the queue already updated the counts, so coming back only needs the position
        if (scannedVersion != Store.scanVersion) {
            rescan()
        } else {
            syncPosition()
            announceInterrupted()
        }
    }

    fun resumed() {
        refresh()
        lookIntoSorted()
    }

    /**
     * Nothing else brings a sorted album back while the app stays open, because coming back to the
     * front reads nothing. Looking into albums that were empty costs next to nothing, and a full read
     * follows only when one of them holds photos again
     */
    private fun lookIntoSorted() {
        val albums = sortedAlbums
        if (albums.isEmpty() || scanning || busy || scannedVersion != Store.scanVersion) return
        val version = Store.scanVersion
        val recursive = Store.recursive
        // An album inside a sorted one is read on its own, as in the full read
        val exclude = Store.dests.mapTo(HashSet()) { it.path } + Store.sources
        viewModelScope.launch {
            val refilled = withContext(Dispatchers.IO) {
                Scanner.scan(albums.map { File(it) }, recursive, exclude, Store.ORDER_NAME).isNotEmpty()
            }
            if (refilled && version == Store.scanVersion) reload()
        }
    }

    private fun announceInterrupted() {
        dialog = interrupted ?: return
        interrupted = null
    }

    fun persist() {
        if (scannedVersion == Store.scanVersion) Store.lastPath = current?.path
        // Until the first read ends there is no list here, and the number saved by the last run still counts
        if (scannedOrder >= 0) {
            Store.lastIndex = index
            Store.lastOrder = scannedOrder
        }
        Store.saveConfig()
    }

    private fun rescan() {
        val roots = Store.sources.toList()
        if (roots.isEmpty()) return
        val version = Store.scanVersion
        val recursive = Store.recursive
        val order = Store.order
        val exclude = Store.dests.map { it.path }.toSet()
        scanning = true
        showImage(null)
        viewModelScope.launch {
            // Indexing and counting a big album would freeze the screen on the main thread. Nothing
            // changes the queue while scanning is set, so reading it from here is safe
            val result = withContext(Dispatchers.IO) {
                prepare(Scanner.scan(roots.map { File(it) }, recursive, exclude, order), roots.size)
            }
            scanning = false
            scannedVersion = version
            // Android can end the app between a new order and its read, so the first read compares with the last run
            if (scannedOrder < 0) {
                scannedOrder = Store.lastOrder
                index = Store.lastIndex
            }
            // A new order keeps the number on screen, and another photo comes to it
            val number = if (scannedOrder >= 0 && scannedOrder != order) sameNumber(result.photos.size) else null
            scannedOrder = order
            photos = result.photos
            indexOf = result.indexOf
            unsorted = result.unsorted
            index = number ?: Store.lastPath?.let { indexOf[it] } ?: firstUnsorted()
            // A read that starts away from the sorting screen would go back to the old photo
            if (number != null) Store.lastPath = current?.path
            queuedHere = result.queued
            counts = result.counts
            scannedSources = roots
            albumTotals = result.albumTotals
            albumVideos = result.albumVideos
            albumQueued = result.albumQueued
            albumCovers = result.albumCovers
            publishAlbums()
            Store.settleSorted(roots, result.albumTotals)
            if (screen == Screen.Selections) loadSelections()
            refresh()
        }
    }

    /** The current position in a list of [size]. The end card stays the end card, and a shorter list stops at its last photo. */
    private fun sameNumber(size: Int): Int =
        if (photos.isNotEmpty() && index == photos.size) size else index.coerceIn(0, maxOf(size - 1, 0))

    /** What a folder read hands to the main thread, worked out on the read's own thread. */
    private class ScanResult(
        val photos: List<Photo>,
        val indexOf: Map<String, Int>,
        val unsorted: BitSet,
        val queued: Int,
        val counts: Map<String, Int>,
        val albumTotals: IntArray,
        val albumVideos: IntArray,
        val albumQueued: IntArray,
        val albumCovers: Array<File?>,
    )

    private fun prepare(list: List<Photo>, albums: Int): ScanResult {
        val indexOf = HashMap<String, Int>(list.size * 2)
        val unsorted = BitSet(list.size)
        val totals = IntArray(albums)
        val videos = IntArray(albums)
        val queuedIn = IntArray(albums)
        val covers = arrayOfNulls<File>(albums)
        list.forEachIndexed { i, p ->
            indexOf[p.path] = i
            totals[p.album]++
            if (p.video) videos[p.album]++
            if (covers[p.album] == null) covers[p.album] = p.file
            if (p.path in Store.pending) queuedIn[p.album]++ else unsorted.set(i)
        }
        return ScanResult(list, indexOf, unsorted, list.size - unsorted.cardinality(), Store.counts(), totals, videos, queuedIn, covers)
    }

    /** Pushes the per-album counts into observable state. An album list is short, so a tap can afford it. */
    private fun publishAlbums() {
        albumStats = scannedSources.mapIndexed { i, path ->
            AlbumStat(path, File(path).name, albumTotals[i], albumVideos[i], albumQueued[i], albumCovers[i])
        }
    }

    /** Counts of one album, or null when it hasn't been read since it was added. */
    fun statOf(path: String): AlbumStat? = albumStats.firstOrNull { it.path == path }

    /** "Camera" for one album, "3 albums" for more. */
    val albumsLabel: String
        get() = shownAlbums.let { albums -> albums.singleOrNull()?.let { File(it).name } ?: "${albums.size} albums" }

    /** The album a photo of the list was read from, with the folders under it: "Camera" or "Camera/Trip". */
    fun titleOf(photo: Photo): String {
        val root = scannedSources.getOrNull(photo.album) ?: return photo.file.parentFile?.name ?: ""
        return File(root).name + (photo.file.parent ?: root).removePrefix(root)
    }

    /**
     * Where a queued photo sits, for the Selections rows. With several albums that is the album's
     * name and the folders under it. With one album only the folders under it are worth a line.
     */
    fun placeOf(file: File): String? {
        val parent = file.parent ?: return null
        val at = Store.albumOf(file.path)
        if (at < 0) return parent
        val root = Store.sources[at]
        val under = parent.removePrefix(root).trimStart('/')
        return when {
            Store.sources.size == 1 -> under.ifEmpty { null }
            under.isEmpty() -> File(root).name
            else -> "${File(root).name}/$under"
        }
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
        animation = null
        decodeFailed = false
        duration = null
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
        albumQueued.fill(0)
        for (i in photos.indices) {
            val queued = photos[i].path in Store.pending
            unsorted.set(i, !queued)
            if (queued) albumQueued[photos[i].album]++
        }
        queuedHere = albumQueued.sum()
        publishAlbums()
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
            val by = if (before == null) 1 else if (dest == null) -1 else 0
            if (by != 0) {
                queuedHere += by
                albumQueued[photos[i].album] += by
                publishAlbums()
            }
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
                    if (p.video) duration = ImageLoader.durationOf(p.file)
                }
            } else if (p.video) {
                duration = ImageLoader.durationOf(p.file)
            }
            if (Scanner.isGif(p.file.name)) {
                ImageLoader.loadAnimation(p.file) { if (bitmapPath == p.path) animation = it }
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
        val p = current?.takeIf { !it.video && animation == null } ?: return
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

    /** Returns whether the photo got the folder, so a buzz only comes with a visible change. */
    fun assign(dest: Dest): Boolean {
        val p = current ?: return false
        if (busy || scanning) return false
        // Swallows the second half of an accidental double tap, which would file the next photo unseen
        val now = SystemClock.uptimeMillis()
        if (now - lastAssignAt < 120) return false
        lastAssignAt = now

        setPending(p.path, dest.path)
        flash = Flash(++flashId, dest.label, Store.dests.indexOf(dest))
        // Jumps past photos that already have a folder, so fixing an old choice returns to where you left off
        index = firstUnsorted(index + 1)
        syncPosition()
        return true
    }

    fun changeMuted(muted: Boolean) {
        Store.videoMuted = muted
        Store.saveConfig()
        videoMuted = muted
    }

    fun flashShown(id: Int) {
        if (flash?.id == id) flash = null
    }

    fun clearAssignment(): Boolean {
        if (busy || scanning) return false
        val p = current ?: return false
        setPending(p.path, null)
        syncPosition()
        return true
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
        preparing = true
        viewModelScope.launch {
            // Refused in Android's own dialog, so the untouched list says enough
            val granted = grantWrite(entries.map { it.first })
            if (granted) {
                Store.lastPath = landing
                MoveRunner.move(ctx, entries)
            }
            preparing = false
        }
    }

    fun stopMove() {
        MoveRunner.stop()
    }

    fun undoBatch(batch: Batch) {
        // A folder read counts against the queue from another thread, so the queue holds still until it ends
        if (busy || scanning) return
        preparing = true
        // Every way in is a dialog button
        dialog = null
        viewModelScope.launch {
            val moved = withContext(Dispatchers.IO) { Mover.readJournal(batch.journal).map { it.second } }
            val granted = grantWrite(moved)
            if (granted) MoveRunner.undo(ctx, batch)
            preparing = false
        }
    }

    // endregion

    // region access

    private fun granted(permission: String) = ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun readAccess() {
        val full = granted(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)
        // Before Android 13 one permission covers both
        videoAllowed = full && (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.READ_MEDIA_VIDEO))
        // Asked once per run, so a refusal leaves Sift sorting photos instead of asking in a loop
        askVideo = full && !videoAllowed && !videoAsked
        val limited = !full && Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        manageMedia = Build.VERSION.SDK_INT < 31 || MediaStore.canManageMedia(ctx)
        if (full && manageMedia && !Store.accessSetUp) {
            Store.accessSetUp = true
            Store.saveConfig()
        }
        access = when {
            full && (manageMedia || Store.accessSetUp) -> Access.Ready
            full -> Access.AskManage
            limited -> Access.PhotosLimited
            Store.askedPhotos -> Access.PhotosDenied
            else -> Access.AskPhotos
        }
    }

    /** What the photo permission dialog asks for. The location one keeps Media management from asking on Move */
    fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        Build.VERSION.SDK_INT == 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    fun photosAsked() {
        if (Store.askedPhotos) return
        Store.askedPhotos = true
        Store.saveConfig()
    }

    fun videoAnswered() {
        videoAsked = true
        refresh()
    }

    fun skipManage() {
        Store.accessSetUp = true
        Store.saveConfig()
        refresh()
    }

    fun writeAnswered(ok: Boolean) {
        val answer = writeAnswer
        writeRequest = null
        writeAnswer = null
        // Last: it runs grantWrite on, which sets the next chunk's request and answer before it returns
        answer?.complete(ok)
    }

    /**
     * Gets write access to every photo in [paths] that still exists, in requests Android accepts. With
     * Media management on, Android grants it without a dialog. The grant lasts while the process does
     */
    private suspend fun grantWrite(paths: List<String>): Boolean {
        val uris = withContext(Dispatchers.IO) { MediaAccess.urisFor(ctx, paths) }
        for (chunk in uris.chunked(MediaAccess.MAX_REQUEST)) {
            val answer = CompletableDeferred<Boolean>()
            writeAnswer = answer
            writeRequest = MediaStore.createWriteRequest(ctx.contentResolver, chunk).intentSender
            if (!answer.await()) return false
        }
        return true
    }

    // endregion

    // region settings

    private fun syncSettings() {
        sources = Store.sources.toList()
        sortedAlbums = Store.sources.filter { it in Store.sorted }
        recursive = Store.recursive
        order = Store.order
        dests = Store.dests.toList()
        queued = Store.pending.size
        videoMuted = Store.videoMuted
    }

    private fun changed(rescan: Boolean) {
        if (rescan) Store.scanVersion++
        Store.forgetRemoved()
        Store.saveConfig()
        syncSettings()
    }

    /**
     * The subfolder setting stays fixed while selections wait for Move. Turning it off would leave
     * choices queued for photos that are no longer on screen.
     */
    private fun locked(): Boolean {
        // An empty queue that hasn't been read yet would look unlocked
        if (!Store.ready) return true
        if (Store.pending.isEmpty()) return false
        dialog = Dialog.SelectionsLocked
        return true
    }

    /** Opens the picker to add and remove albums. Nothing changes until it is saved. */
    fun pickAlbums() {
        open(Screen.Picker(forSource = true))
    }

    fun askRemoveSource(path: String) {
        // A folder read counts against the queue from another thread, so the queue holds still until it ends
        if (!Store.ready || scanning) {
            messages.tryEmit("Wait until the albums have been read")
            return
        }
        val at = Store.sources.indexOf(path)
        if (at < 0) return
        val here = Store.pending.keys.count { Store.albumOf(it) == at }
        if (here == 0) removeSource(path) else dialog = Dialog.ConfirmRemoveSource(listOf(path), here, Store.pending.size - here)
    }

    /** Takes an album out of sorting and drops the selections made in it. Its photos stay where they are. */
    fun removeSource(path: String) {
        if (busy || scanning || !Store.ready) return
        val at = Store.sources.indexOf(path)
        if (at < 0) return
        if (Store.pending.keys.removeAll { Store.albumOf(it) == at }) {
            Store.compactPending()
            counts = Store.counts()
        }
        Store.sources.removeAt(at)
        changed(rescan = true)
    }

    /** Takes every sorted album off the list. They are empty, so no confirmation is needed. */
    fun clearSorted() {
        if (busy || scanning || !Store.ready) {
            messages.tryEmit("Wait until the albums have been read")
            return
        }
        for (path in sortedAlbums) removeSource(path)
    }

    fun changeRecursive(on: Boolean) {
        if (!on && locked()) return
        Store.recursive = on
        changed(rescan = true)
        // The Folders screen shows what each album holds, and that just changed
        refresh()
    }

    fun changeOrder(value: Int) {
        if (value == Store.order) return
        Store.order = value
        changed(rescan = true)
    }

    fun isSource(path: String): Boolean = path in sources

    fun isDest(path: String): Boolean = dests.any { it.path == path }

    /**
     * A folder can't be sorted into itself, so a destination can't become an album or the reverse.
     * The album picker can still untick the albums being sorted
     */
    fun canTick(path: String, forSource: Boolean): Boolean = !isDest(path) && (forSource || !isSource(path))

    fun isTicked(path: String, forSource: Boolean): Boolean =
        path in picked || (forSource && isSource(path) && path !in unticked)

    fun tick(path: String, forSource: Boolean) {
        if (!forSource || !isSource(path)) togglePicked(path, forSource)
        else if (!unticked.remove(path)) unticked.add(path)
    }

    fun clearPicks() {
        picked.clear()
        unticked.clear()
    }

    fun togglePicked(path: String, forSource: Boolean) {
        if (picked.remove(path)) return
        if (path in Store.sources) {
            messages.tryEmit(if (forSource) "Already being sorted" else "That's a folder being sorted")
            return
        }
        if (Store.dests.any { it.path == path }) {
            messages.tryEmit(if (forSource) "That folder is a destination" else "Already a destination")
            return
        }
        picked.add(path)
    }

    /** Leaves the picker, asking first when that would drop changes made in it. */
    fun leavePicker() {
        if (picked.isEmpty() && unticked.isEmpty()) back()
        else dialog = Dialog.ConfirmDiscardPicks(picked.size, unticked.size)
    }

    /**
     * Takes out the unticked albums, adds every ticked folder, as albums to sort or as destinations,
     * and leaves the picker. Asks first when that drops selections, unless [confirmed]
     */
    fun savePicked(forSource: Boolean, confirmed: Boolean = false) {
        if (picked.isEmpty() && unticked.isEmpty()) return
        if (unticked.isNotEmpty() && !removeUnticked(confirmed)) return
        if (forSource) Store.sources.addAll(picked) else for (path in picked) Store.dests.add(Dest(path, File(path).name))
        picked.lastOrNull()?.let { Store.lastPickerDir = File(it).parent }
        clearPicks()
        changed(rescan = true)
        back()
        // The Folders screen shows what each album holds, so new albums are read without waiting for Home
        if (forSource) refresh()
    }

    /** False when the albums stay for now, because a folder read is on or the user has to confirm. */
    private fun removeUnticked(confirmed: Boolean): Boolean {
        // A folder read counts against the queue from another thread, so the queue holds still until it ends
        if (busy || scanning || !Store.ready) {
            messages.tryEmit("Wait until the albums have been read")
            return false
        }
        val gone = unticked.toSet()
        // Counts the albums being added too: a photo in one of them belongs to it and stays queued
        val albums = Store.sources + picked
        val dropped = Store.pending.keys.filterTo(HashSet()) { albums.getOrNull(Store.albumOf(it, albums)) in gone }
        if (dropped.isNotEmpty()) {
            if (!confirmed) {
                dialog = Dialog.ConfirmRemoveSource(unticked.toList(), dropped.size, Store.pending.size - dropped.size, picker = true)
                return false
            }
            Store.pending.keys.removeAll(dropped)
            Store.compactPending()
            counts = Store.counts()
        }
        Store.sources.removeAll(gone)
        return true
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
     * Albums as a gallery shows them: every folder MediaStore has indexed photos or videos in,
     * newest first. If the index comes back empty, a walk over the disk stands in for it.
     */
    private fun loadAlbums() {
        albums = null
        viewModelScope.launch {
            albums = withContext(Dispatchers.IO) { queryAlbums().ifEmpty { walkAlbums() } }
        }
    }

    private fun queryAlbums(): List<Album> {
        class Acc(var count: Int, var videos: Int, var cover: String, var coverTime: Long)

        val found = HashMap<String, Acc>()
        try {
            val type = MediaStore.Files.FileColumns.MEDIA_TYPE
            val image = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            val video = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            @Suppress("DEPRECATION")
            val columns = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED, type)
            // The one collection that lists photos and videos together
            val all = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            ctx.contentResolver.query(all, columns, "$type IN ($image, $video)", null, null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    val isVideo = c.getInt(2) == video
                    // A video in a container the player can't read isn't sorted, so it isn't counted
                    if (isVideo && !Scanner.isVideo(path)) continue
                    val parent = File(path).parent ?: continue
                    val time = c.getLong(1)
                    val acc = found[parent] ?: Acc(0, 0, path, time).also { found[parent] = it }
                    acc.count++
                    if (isVideo) acc.videos++
                    if (time > acc.coverTime) {
                        acc.cover = path
                        acc.coverTime = time
                    }
                }
            }
        } catch (e: Exception) {
            // The disk walk covers for a failed query
        }
        return found.entries
            .sortedByDescending { it.value.coverTime }
            .map { Album(it.key, File(it.key).name, it.value.count, it.value.videos, it.value.cover) }
    }

    private fun walkAlbums(): List<Album> =
        Scanner.albums(volumes().map { it.dir })
            .sortedByDescending { it.count }
            .map { Album(it.path, File(it.path).name, it.count, it.videos, it.cover) }

    fun browse(dir: File?) {
        browsing = true
        browseDir = dir
        folders = emptyList()
        mediaHere = Scanner.Count(0, 0)
        val generation = ++browseGeneration
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { if (dir == null) volumes() else children(dir) }
            if (generation != browseGeneration) return@launch
            folders = list
            if (dir != null) {
                val here = withContext(Dispatchers.IO) { Scanner.count(dir) }
                if (generation == browseGeneration) mediaHere = here
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
        out.add(Folder(primary, "Internal storage", null))
        for (v in ctx.getSystemService(StorageManager::class.java).storageVolumes) {
            val d = v.directory ?: continue
            if (!v.isPrimary) out.add(Folder(d, v.getDescription(ctx), null))
        }
        return out
    }

    private fun children(dir: File): List<Folder> =
        (dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray())
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { Folder(it, it.name, Scanner.count(it)) }

    /** New albums go under Pictures, where gallery apps expect them. */
    fun picturesDir(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    fun createFolder(parent: File, name: String, forSource: Boolean) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) return
        val made = File(parent, clean)
        if (made.isDirectory || made.mkdirs()) {
            // Stays in the picker, so more folders can be ticked before adding them all
            if (made.path !in picked) togglePicked(made.path, forSource)
            if (browsing) browse(browseDir)
        } else {
            messages.tryEmit("Can't create a folder there")
        }
    }

    // endregion
}
