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
import java.util.concurrent.atomic.AtomicBoolean

sealed interface Screen {
    data object Home : Screen
    data object Sort : Screen
    data object Setup : Screen
    data class Picker(val forSource: Boolean) : Screen
}

sealed interface Dialog {
    data object ConfirmApply : Dialog
    data class Progress(val title: String, val total: Int, val cancellable: Boolean) : Dialog
    data class ApplyDone(val result: Mover.Result, val journal: File?) : Dialog
    data class ConfirmUndoBatch(val journal: File, val count: Int) : Dialog
    data class UndoFailed(val restored: Int, val failed: List<Pair<String, String>>) : Dialog
    data object ConfirmClear : Dialog
    data class ConfirmResetSkipped(val count: Int) : Dialog
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

/** Screen state in front of [Store], which Compose can't observe on its own. Main thread only. */
class SiftViewModel(app: Application) : AndroidViewModel(app) {
    /** One reversible step. A photo is queued for a folder, marked skipped, or neither. */
    private class UndoEntry(
        val path: String,
        val before: String?,
        val after: String?,
        val skippedBefore: Boolean = false,
        val skippedAfter: Boolean = false,
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

    private var indexOf: Map<String, Int> = emptyMap()
    private val undo = ArrayDeque<UndoEntry>()
    private var scannedVersion = -1
    private var lastAssignAt = 0L
    private var flashId = 0
    private var cancel: AtomicBoolean? = null
    private var browseGeneration = 0

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

    /** Photos of this album the user looked at and left in place with Skip. */
    var skippedHere by mutableIntStateOf(0)
        private set

    /** Photos of this album that are neither queued nor skipped. */
    val leftHere: Int get() = photos.size - queuedHere - skippedHere

    /** True when the photo on screen carries a skip mark. */
    var isSkipped by mutableStateOf(false)
        private set

    /** Journal and size of the last applied batch, for the undo entries. */
    var lastBatchInfo by mutableStateOf<Pair<File, Int>?>(null)
        private set

    init {
        Store.init(app)
        syncSettings()
    }

    // region navigation

    fun open(target: Screen) {
        if (busy) return
        keepPosition()
        backStack.add(target)
        if (target == Screen.Sort) refresh()
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
        lastBatchInfo = lastBatch()
        if (!allowed || source == null || dests.isEmpty()) {
            showImage(null)
            return
        }
        if (scanning) return
        if (scannedVersion != Store.scanVersion) rescan() else sync()
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
            val list = withContext(Dispatchers.IO) { Scanner.scan(File(root), recursive, exclude, order) }
            scanning = false
            photos = list
            indexOf = HashMap<String, Int>(list.size * 2).also { m -> list.forEachIndexed { i, p -> m[p.path] = i } }
            scannedVersion = version
            index = Store.lastPath?.let { indexOf[it] } ?: firstUnsorted()
            refresh()
        }
    }

    private fun reviewed(path: String) = Store.pending.containsKey(path) || path in Store.skipped

    /** First photo from [from] on that is neither queued nor skipped. */
    private fun firstUnsorted(from: Int = 0): Int {
        var i = from
        while (i < photos.size && reviewed(photos[i].path)) i++
        return i
    }

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
        queued = Store.pending.size
        counts = Store.counts()
        canUndo = undo.isNotEmpty()
        var q = 0
        var s = 0
        for (photo in photos) {
            if (Store.pending.containsKey(photo.path)) q++ else if (photo.path in Store.skipped) s++
        }
        queuedHere = q
        skippedHere = s
        val p = current
        assigned = p?.let { Store.pending[it.path] }
        isSkipped = p != null && assigned == null && p.path in Store.skipped
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
        sync()
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
            Store.setSkipped(p.path, true)
            pushUndo(UndoEntry(p.path, null, null, skippedBefore = false, skippedAfter = true))
        }
        flash = null
        index = (index + 1).coerceAtMost(photos.size)
        sync()
    }

    fun clearSkip() {
        if (busy || scanning) return
        val p = current ?: return
        if (p.path !in Store.skipped) return
        Store.setSkipped(p.path, false)
        pushUndo(UndoEntry(p.path, null, null, skippedBefore = true, skippedAfter = false))
        sync()
    }

    /** Makes every skipped photo of this album count as not reviewed again. */
    fun resetSkipped() {
        var changed = false
        for (photo in photos) if (Store.skipped.remove(photo.path)) changed = true
        if (changed) Store.compactSkipped()
        undo.clear()
        sync()
    }

    fun jumpTo(target: Int) {
        if (busy || scanning) return
        index = target.coerceIn(0, photos.size)
        sync()
    }

    fun jumpToFirstUnsorted() = jumpTo(firstUnsorted())

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
            val wasSkipped = p.path in Store.skipped
            if (wasSkipped) Store.setSkipped(p.path, false)
            Store.setPending(p.path, dest.path)
            pushUndo(UndoEntry(p.path, before, dest.path, skippedBefore = wasSkipped))
        }
        flash = Flash(++flashId, dest.label, Store.dests.indexOf(dest))
        // Jumps past photos that already have a folder, so fixing an old choice returns to where you left off
        index = firstUnsorted(index + 1)
        sync()
    }

    fun flashShown(id: Int) {
        if (flash?.id == id) flash = null
    }

    fun clearAssignment() {
        if (busy || scanning) return
        val p = current ?: return
        val before = Store.pending[p.path] ?: return
        Store.setPending(p.path, null)
        pushUndo(UndoEntry(p.path, before, null))
        sync()
    }

    private fun pushUndo(e: UndoEntry) {
        undo.addLast(e)
        if (undo.size > 1000) undo.removeFirst()
    }

    fun undoLast() {
        if (busy || scanning) return
        val e = undo.removeLastOrNull() ?: return
        if (e.before != e.after) Store.setPending(e.path, e.before)
        if (e.skippedBefore != e.skippedAfter) Store.setSkipped(e.path, e.skippedBefore)
        indexOf[e.path]?.let { index = it }
        sync()
    }

    fun reload() {
        if (busy || scanning) return
        ImageLoader.clear()
        Store.lastPath = current?.path
        Store.scanVersion++
        refresh()
    }

    fun clearQueue() {
        Store.pending.clear()
        Store.compactPending()
        undo.clear()
        sync()
    }

    private fun lastBatch(): Pair<File, Int>? = try {
        // One line per moved file. Counting lines is cheap enough to run on every refresh
        Store.journals().firstOrNull()?.let { file -> file to file.useLines { lines -> lines.count { it.isNotBlank() } } }
    } catch (e: Exception) {
        null
    }

    fun askApply() {
        if (busy || scanning || Store.pending.isEmpty()) return
        dialog = Dialog.ConfirmApply
    }

    /** Folder label to queued count, in button order. */
    fun applySummary(): List<Pair<String, Int>> {
        val counts = Store.counts()
        val known = Store.dests.map { it.path }
        return (known + counts.keys.filter { it !in known }).mapNotNull { path ->
            counts[path]?.let { Store.labelFor(path) to it }
        }
    }

    fun apply() {
        if (busy) return
        val entries = Store.pending.map { it.key to it.value }
        val journal = Store.newJournal()
        val stop = AtomicBoolean(false)
        cancel = stop
        progress = 0
        dialog = Dialog.Progress("Moving ${entries.size} photos", entries.size, cancellable = true)
        busy = true
        // Where to land afterwards: the first photo from here on that isn't being moved
        Store.lastPath = (index until photos.size).map { photos[it].path }.firstOrNull { !Store.pending.containsKey(it) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                Mover.apply(ctx, entries, journal, stop) { done -> if (done % 8 == 0) progress = done }
            }
            for (path in result.settled) Store.pending.remove(path)
            Store.compactPending()
            undo.clear()
            ImageLoader.clear()
            busy = false
            Store.scanVersion++
            dialog = Dialog.ApplyDone(result, Store.journals().firstOrNull().takeIf { result.moved > 0 })
            refresh()
        }
    }

    fun stopApply() {
        cancel?.set(true)
    }

    fun undoBatch(journal: File, count: Int) {
        if (busy) return
        progress = 0
        dialog = Dialog.Progress("Moving $count photos back", count, cancellable = false)
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                Mover.undo(ctx, journal) { done -> if (done % 8 == 0) progress = done }
            }
            for ((path, dest) in result.restored) Store.pending[path] = dest
            Store.compactPending()
            undo.clear()
            ImageLoader.clear()
            busy = false
            Store.lastPath = result.restored.lastOrNull()?.first
            Store.scanVersion++
            dialog = if (result.failed.isEmpty()) null else Dialog.UndoFailed(result.restored.size, result.failed)
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
        counts = Store.counts()
    }

    private fun changed(rescan: Boolean) {
        if (rescan) Store.scanVersion++
        Store.saveConfig()
        syncSettings()
    }

    fun changeRecursive(on: Boolean) {
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
        val n = counts[dest.path] ?: 0
        if (n == 0) removeDest(dest) else dialog = Dialog.ConfirmRemoveDest(dest, n)
    }

    fun removeDest(dest: Dest) {
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
