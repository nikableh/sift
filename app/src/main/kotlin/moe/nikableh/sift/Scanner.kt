package moe.nikableh.sift

import java.io.File

/** A photo or a video. [album] is the index of the album it was read from, in the list that was scanned. */
class Photo(val file: File, val mtime: Long, val size: Long, val album: Int, val video: Boolean) {
    val path: String = file.path
}

object Scanner {
    private val images = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif", "bmp", "dng")

    /** What the player has readers for: MP4 and its relatives, Matroska and WebM. */
    private val videos = setOf("mp4", "m4v", "mov", "3gp", "mkv", "webm")

    private fun ext(name: String): String? {
        // dot > 0 also drops dotfiles such as MediaStore's ".trashed-..." entries
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(dot + 1).lowercase() else null
    }

    fun isVideo(name: String): Boolean = ext(name) in videos

    fun isMedia(name: String): Boolean = ext(name).let { it in images || it in videos }

    /**
     * Reads every album into one list. [exclude] holds destination folders, so already sorted
     * photos don't come back in a recursive scan. An album inside another one is read on its own,
     * so its photos show up once and count for the closer album.
     */
    fun scan(roots: List<File>, recursive: Boolean, exclude: Set<String>, order: Int): List<Photo> {
        val out = ArrayList<Photo>()
        val albums = roots.mapTo(HashSet()) { it.path }
        roots.forEachIndexed { i, root -> walk(root, recursive, exclude + (albums - root.path), i, out) }
        val byName = compareBy<Photo, String>(String.CASE_INSENSITIVE_ORDER) { it.file.name }
        when (order) {
            Store.ORDER_NEWEST -> out.sortWith(compareByDescending<Photo> { it.mtime }.then(byName))
            Store.ORDER_OLDEST -> out.sortWith(compareBy<Photo> { it.mtime }.then(byName))
            else -> out.sortWith(byName)
        }
        return out
    }

    private fun walk(dir: File, recursive: Boolean, exclude: Set<String>, album: Int, out: MutableList<Photo>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (recursive && f.path !in exclude) walk(f, true, exclude, album, out)
            } else {
                val ext = ext(f.name)
                val video = ext in videos
                if (video || ext in images) out.add(Photo(f, f.lastModified(), f.length(), album, video))
            }
        }
    }

    /** How many photos and videos a folder holds directly. [videos] is the part of [total] that is videos. */
    class Count(val total: Int, val videos: Int)

    fun count(dir: File): Count {
        var total = 0
        var videoCount = 0
        for (name in dir.list() ?: emptyArray()) {
            val ext = ext(name)
            if (ext in videos) videoCount++ else if (ext !in images) continue
            total++
        }
        return Count(total, videoCount)
    }

    private val plainFiles = setOf(
        "mp3", "m4a", "ogg", "opus", "aac", "wav", "flac", "avi", "ts", "flv", "wmv",
        "pdf", "txt", "json", "xml", "zip", "apk", "db", "log", "tmp", "doc", "docx", "nomedia",
    )

    class FoundAlbum(val path: String, val count: Int, val videos: Int, val cover: String)

    /**
     * Every folder under [roots] that directly holds photos or videos. Skips hidden folders and
     * anything under a .nomedia marker, which is what keeps a folder out of gallery apps.
     */
    fun albums(roots: List<File>): List<FoundAlbum> {
        val out = ArrayList<FoundAlbum>()
        for (root in roots) walkAlbums(root, 0, out)
        return out
    }

    private fun walkAlbums(dir: File, depth: Int, out: MutableList<FoundAlbum>) {
        val names = dir.list() ?: return
        if (".nomedia" in names) return
        var count = 0
        var videoCount = 0
        var cover: String? = null
        for (name in names) {
            if (name.startsWith(".")) continue
            val ext = ext(name)
            val video = ext in videos
            if (video || ext in images) {
                count++
                if (video) videoCount++
                // Camera file names sort by date, so the last name is usually the newest one
                if (cover == null || name > cover) cover = name
                continue
            }
            // Known file types skip the isDirectory call, which is the slow part on big folders
            if (ext in plainFiles) continue
            if (depth == 0 && name == "Android") continue
            if (depth < 6) {
                val child = File(dir, name)
                if (child.isDirectory) walkAlbums(child, depth + 1, out)
            }
        }
        if (cover != null) out.add(FoundAlbum(dir.path, count, videoCount, File(dir, cover).path))
    }
}
