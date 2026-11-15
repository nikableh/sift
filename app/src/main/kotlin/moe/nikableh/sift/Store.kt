package moe.nikableh.sift

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.Executors

class Dest(val path: String, var label: String)

/** Settings and the queue of planned moves. Touch it from the main thread only. */
object Store {
    const val ORDER_NEWEST = 0
    const val ORDER_OLDEST = 1
    const val ORDER_NAME = 2

    private const val MAX_JOURNALS = 20

    private lateinit var dir: File
    private var configLoaded = false
    private val io = Executors.newSingleThreadExecutor()

    /** Albums to sort, in the order they were added. */
    val sources = ArrayList<String>()
    var recursive = false
    var order = ORDER_NEWEST
    val dests = ArrayList<Dest>()
    var lastPath: String? = null
    var lastPickerDir: String? = null

    /** Photo path to destination folder path. Nothing on disk changes until Move. */
    var pending = LinkedHashMap<String, String>()
        private set

    /**
     * False until [useQueue] has the log in. Until then [pending] is empty, and a change to it
     * would be lost when the read one replaces it
     */
    var ready = false
        private set

    /** Bumped whenever the photo list has to be scanned again. */
    var scanVersion = 0

    private val configFile get() = File(dir, "config.json")
    private val pendingFile get() = File(dir, "pending.log")
    private val journalDir get() = File(dir, "journals")

    /** Reads the settings. The queue log can hold 200,000 lines, so it comes separately from [readQueue] */
    fun init(ctx: Context) {
        if (configLoaded) return
        dir = ctx.applicationContext.filesDir
        loadConfig()
        configLoaded = true
    }

    /**
     * Parses the log into a new map and rewrites it when it holds removals, overrides or a torn
     * line. It touches no shared state, so it can run off the main thread, where a big queue would
     * take seconds
     */
    fun readQueue(): LinkedHashMap<String, String> {
        val pending = LinkedHashMap<String, String>()
        val lines = readLines(pendingFile) { a ->
            val dest = a.optString(1)
            if (dest.isEmpty()) pending.remove(a.getString(0)) else pending[a.getString(0)] = dest
        }
        // One line per entry means nothing to drop, and rewriting a big queue anyway costs seconds.
        // A file torn right before its last newline still parses, but the next append would join
        // that line, so it gets rewritten too. Nothing else writes the log before [useQueue]
        if (lines >= 0 && (lines != pending.size || !endsWithNewline(pendingFile))) {
            writeAtomic(pendingFile, pendingText(pending))
        }
        // Left by versions that could skip photos
        File(dir, "skipped.log").delete()
        return pending
    }

    /** Main thread: takes over what [readQueue] read. */
    fun useQueue(pending: LinkedHashMap<String, String>) {
        this.pending = pending
        ready = true
    }

    /** Counts the lines that aren't blank, or returns -1 when the file can't be read. */
    private fun readLines(file: File, use: (JSONArray) -> Unit): Int {
        var lines = 0
        try {
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                lines++
                try {
                    use(JSONArray(line))
                } catch (e: Exception) {
                    // A torn last line after a crash is expected, skip it
                }
            }
        } catch (e: Exception) {
            return -1
        }
        return lines
    }

    private fun loadConfig() {
        val json = try {
            JSONObject(configFile.readText())
        } catch (e: Exception) {
            return
        }
        sources.clear()
        val albums = json.optJSONArray("sources")
        if (albums != null) {
            for (i in 0 until albums.length()) albums.optString(i).ifEmpty { null }?.let { sources.add(it) }
        } else {
            // Written by versions that sorted one album at a time
            json.optString("source").ifEmpty { null }?.let { sources.add(it) }
        }
        recursive = json.optBoolean("recursive")
        order = json.optInt("order", ORDER_NEWEST)
        lastPath = json.optString("lastPath").ifEmpty { null }
        lastPickerDir = json.optString("lastPickerDir").ifEmpty { null }
        dests.clear()
        val arr = json.optJSONArray("dests") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            dests.add(Dest(d.getString("path"), d.getString("label")))
        }
    }

    fun saveConfig() {
        val json = JSONObject()
        json.put("sources", JSONArray(sources))
        json.put("recursive", recursive)
        json.put("order", order)
        json.put("lastPath", lastPath ?: "")
        json.put("lastPickerDir", lastPickerDir ?: "")
        val arr = JSONArray()
        for (d in dests) arr.put(JSONObject().put("path", d.path).put("label", d.label))
        json.put("dests", arr)
        val text = json.toString()
        io.execute { writeAtomic(configFile, text) }
    }

    /** Appends one decision to the log, so a tap costs the same at 10 or 10000 queued photos. */
    fun setPending(path: String, dest: String?) {
        if (dest == null) pending.remove(path) else pending[path] = dest
        val line = JSONArray().put(path).put(dest ?: "").toString() + "\n"
        io.execute {
            try {
                FileOutputStream(pendingFile, true).use { it.write(line.toByteArray()) }
            } catch (e: Exception) {
            }
        }
    }

    fun compactPending() {
        val text = pendingText(pending)
        io.execute { writeAtomic(pendingFile, text) }
    }

    private fun pendingText(pending: Map<String, String>): String {
        val sb = StringBuilder()
        for ((path, dest) in pending) sb.append(JSONArray().put(path).put(dest).toString()).append('\n')
        return sb.toString()
    }

    fun counts(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (dest in pending.values) out[dest] = (out[dest] ?: 0) + 1
        return out
    }

    /**
     * Index in [sources] of the album a photo belongs to, or -1. An album inside another one is
     * the closer of the two, the way a folder read counts it
     */
    fun albumOf(photoPath: String): Int {
        val parent = photoPath.substringBeforeLast('/')
        var best = -1
        sources.forEachIndexed { i, album ->
            val holds = parent == album || (recursive && parent.startsWith("$album/"))
            if (holds && (best < 0 || album.length > sources[best].length)) best = i
        }
        return best
    }

    fun labelFor(destPath: String): String =
        dests.firstOrNull { it.path == destPath }?.label ?: File(destPath).name

    /** Newest first. Each file lists the moves of one batch. */
    fun journals(): List<File> =
        (journalDir.listFiles { f -> f.name.endsWith(".log") } ?: emptyArray())
            .sortedByDescending { it.name }

    fun newJournal(): File {
        journalDir.mkdirs()
        journals().drop(MAX_JOURNALS - 1).forEach { it.delete() }
        return File(journalDir, "batch-${System.currentTimeMillis()}.log")
    }

    private fun endsWithNewline(f: File): Boolean = try {
        f.length() == 0L || RandomAccessFile(f, "r").use {
            it.seek(it.length() - 1)
            it.read() == '\n'.code
        }
    } catch (e: Exception) {
        false
    }

    private fun writeAtomic(target: File, text: String) {
        try {
            val tmp = File(target.path + ".tmp")
            FileOutputStream(tmp).use {
                it.write(text.toByteArray())
                it.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (e: Exception) {
        }
    }
}
