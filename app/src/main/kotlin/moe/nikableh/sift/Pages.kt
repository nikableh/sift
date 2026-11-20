package moe.nikableh.sift

import android.graphics.Bitmap
import android.graphics.drawable.AnimatedImageDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The decoded file at one place in a list, with its neighbours ready for the pager to slide in.
 * The review viewer pages through the selections with it. [owner] names its decodes to [ImageLoader]
 */
class Pages(private val owner: String) {
    /** The place in the list that [bitmap] shows, or -1 with nothing shown. */
    var shown by mutableIntStateOf(-1)
        private set
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var prev by mutableStateOf<Bitmap?>(null)
        private set
    var next by mutableStateOf<Bitmap?>(null)
        private set

    /** The moving picture of a GIF, which [bitmap] is the first frame of. */
    var animation by mutableStateOf<AnimatedImageDrawable?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    private var files: List<File> = emptyList()

    /** Main thread only. */
    fun show(files: List<File>, at: Int) {
        val file = files.getOrNull(at)
        if (file == null) {
            clear()
            return
        }
        val moved = files !== this.files || at != shown
        this.files = files
        val want = HashSet<String>()
        for (d in -2..2) files.getOrNull(at + d)?.let { want.add(it.path) }
        ImageLoader.want(owner, want)
        if (!moved) return
        shown = at
        // From the cache first, so the bitmap the pager slid in is the one it is handed, and the slide carries on
        bitmap = ImageLoader.cached(file)
        animation = null
        failed = false
        if (bitmap == null) {
            ImageLoader.load(file) { bmp ->
                if (shown != at || this.files !== files) return@load
                if (bmp != null) bitmap = bmp else failed = true
            }
        }
        if (Scanner.isGif(file.name)) {
            ImageLoader.loadAnimation(file) { if (shown == at && this.files === files) animation = it }
        }
        neighbour(files, at, 1) { next = it }
        neighbour(files, at, -1) { prev = it }
        for (d in intArrayOf(2, -2)) files.getOrNull(at + d)?.let { ImageLoader.prefetch(it) }
    }

    private fun neighbour(files: List<File>, at: Int, delta: Int, set: (Bitmap?) -> Unit) {
        val file = files.getOrNull(at + delta)
        set(file?.let { ImageLoader.cached(it) })
        if (file == null) return
        ImageLoader.load(file) { bmp -> if (shown == at && this.files === files) set(bmp) }
    }

    /** The pager asks for a sharper bitmap the first time the user zooms in. */
    fun loadSharper() {
        val at = shown
        val file = files.getOrNull(at)?.takeIf { !Scanner.isVideo(it.name) && animation == null } ?: return
        val list = files
        ImageLoader.loadFull(file) { bmp -> if (bmp != null && shown == at && files === list) bitmap = bmp }
    }

    fun clear() {
        ImageLoader.want(owner, emptySet())
        files = emptyList()
        shown = -1
        bitmap = null
        prev = null
        next = null
        animation = null
        failed = false
    }
}
