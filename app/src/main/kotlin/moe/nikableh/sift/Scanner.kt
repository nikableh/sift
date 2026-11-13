package moe.nikableh.sift

import java.io.File

class Photo(val file: File, val mtime: Long, val size: Long) {
    val path: String = file.path
}

object Scanner {
    private val exts = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif", "bmp", "dng")

    fun isImage(name: String): Boolean {
        // dot > 0 also drops dotfiles such as MediaStore's ".trashed-..." entries
        val dot = name.lastIndexOf('.')
        return dot > 0 && name.substring(dot + 1).lowercase() in exts
    }

    /** [exclude] holds destination folders, so already sorted photos don't come back in a recursive scan. */
    fun scan(root: File, recursive: Boolean, exclude: Set<String>, order: Int): List<Photo> {
        val out = ArrayList<Photo>()
        walk(root, recursive, exclude, out)
        val byName = compareBy<Photo, String>(String.CASE_INSENSITIVE_ORDER) { it.file.name }
        when (order) {
            Store.ORDER_NEWEST -> out.sortWith(compareByDescending<Photo> { it.mtime }.then(byName))
            Store.ORDER_OLDEST -> out.sortWith(compareBy<Photo> { it.mtime }.then(byName))
            else -> out.sortWith(byName)
        }
        return out
    }

    private fun walk(dir: File, recursive: Boolean, exclude: Set<String>, out: MutableList<Photo>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (recursive && f.path !in exclude) walk(f, true, exclude, out)
            } else if (isImage(f.name)) {
                out.add(Photo(f, f.lastModified(), f.length()))
            }
        }
    }

    fun countImages(dir: File): Int = dir.list()?.count { isImage(it) } ?: 0

    private val plainFiles = setOf(
        "mp4", "mov", "mkv", "webm", "3gp", "mp3", "m4a", "ogg", "opus", "aac", "wav", "flac",
        "pdf", "txt", "json", "xml", "zip", "apk", "db", "log", "tmp", "doc", "docx", "nomedia",
    )

    class FoundAlbum(val path: String, val count: Int, val cover: String)

    /**
     * Every folder under [roots] that directly holds images. Skips hidden folders and anything
     * under a .nomedia marker, which is what keeps a folder out of gallery apps.
     */
    fun albums(roots: List<File>): List<FoundAlbum> {
        val out = ArrayList<FoundAlbum>()
        for (root in roots) walkAlbums(root, 0, out)
        return out
    }

    private fun walkAlbums(dir: File, depth: Int, out: MutableList<FoundAlbum>) {
        val names = dir.list() ?: return
        if (".nomedia" in names) return
        var images = 0
        var cover: String? = null
        for (name in names) {
            if (name.startsWith(".")) continue
            if (isImage(name)) {
                images++
                // Camera file names sort by date, so the last name is usually the newest photo
                if (cover == null || name > cover) cover = name
                continue
            }
            // Known file types skip the isDirectory call, which is the slow part on big folders
            if (name.substringAfterLast('.', "").lowercase() in plainFiles) continue
            if (depth == 0 && name == "Android") continue
            if (depth < 6) {
                val child = File(dir, name)
                if (child.isDirectory) walkAlbums(child, depth + 1, out)
            }
        }
        if (cover != null) out.add(FoundAlbum(dir.path, images, File(dir, cover).path))
    }
}
