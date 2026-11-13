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

    // Values of the second field of a skipped.log line
    const val SKIP_NONE = 0
    const val SKIP_FRESH = 1
    const val SKIP_HIDDEN = 2

    private const val MAX_JOURNALS = 20

    private lateinit var dir: File
    private var configLoaded = false
    private val io = Executors.newSingleThreadExecutor()

    var source: String? = null
    var recursive = false
    var order = ORDER_NEWEST
    val dests = ArrayList<Dest>()
    var lastPath: String? = null
    var lastPickerDir: String? = null

    /** Photo path to destination folder path. Nothing on disk changes until Move. */
    var pending = LinkedHashMap<String, String>()
        private set

    /**
     * Photos the user looked at and chose to leave where they are, since the last Move. Kept
     * apart from [pending], so a photo is either queued, skipped or not reviewed yet.
     */
    var skipped = LinkedHashSet<String>()
        private set

    /** Skips a Move has committed. These photos stay out of the album until they are unhidden. */
    var hidden = LinkedHashSet<String>()
        private set

    /**
     * False until [useQueues] has the logs in. Until then the collections above are empty, and a
     * change to them would be lost when the read ones replace them
     */
    var ready = false
        private set

    /** Bumped whenever the photo list has to be scanned again. */
    var scanVersion = 0

    private val configFile get() = File(dir, "config.json")
    private val pendingFile get() = File(dir, "pending.log")
    private val skippedFile get() = File(dir, "skipped.log")
    private val journalDir get() = File(dir, "journals")

    /** Reads the settings. The queue logs can hold 200,000 lines, so they come separately from [readQueues] */
    fun init(ctx: Context) {
        if (configLoaded) return
        dir = ctx.applicationContext.filesDir
        loadConfig()
        configLoaded = true
    }

    class Queues(
        val pending: LinkedHashMap<String, String>,
        val skipped: LinkedHashSet<String>,
        val hidden: LinkedHashSet<String>,
    )

    /**
     * Parses both logs into new collections and rewrites a log that holds removals, overrides or a
     * torn line. It touches no shared state, so it can run off the main thread, where a big queue
     * would take seconds
     */
    fun readQueues(): Queues {
        val q = Queues(LinkedHashMap(), LinkedHashSet(), LinkedHashSet())
        val pendingLines = readLines(pendingFile) { a ->
            val dest = a.optString(1)
            if (dest.isEmpty()) q.pending.remove(a.getString(0)) else q.pending[a.getString(0)] = dest
        }
        // One line per entry means nothing to drop, and rewriting a big queue anyway costs seconds.
        // A file torn right before its last newline still parses, but the next append would join
        // that line, so it gets rewritten too. Nothing else writes the logs before [useQueues]
        if (pendingLines >= 0 && (pendingLines != q.pending.size || !endsWithNewline(pendingFile))) {
            writeAtomic(pendingFile, pendingText(q.pending))
        }
        val skippedLines = readLines(skippedFile) { a ->
            putSkip(q.skipped, q.hidden, a.getString(0), a.optInt(1, SKIP_FRESH))
        }
        if (skippedLines >= 0 && (skippedLines != q.skipped.size + q.hidden.size || !endsWithNewline(skippedFile))) {
            writeAtomic(skippedFile, skippedText(q.skipped, q.hidden))
        }
        return q
    }

    /** Main thread: takes over what [readQueues] read. */
    fun useQueues(q: Queues) {
        pending = q.pending
        skipped = q.skipped
        hidden = q.hidden
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
        source = json.optString("source").ifEmpty { null }
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
        json.put("source", source ?: "")
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

    private fun putSkip(skipped: MutableSet<String>, hidden: MutableSet<String>, path: String, state: Int) {
        skipped.remove(path)
        hidden.remove(path)
        when (state) {
            SKIP_FRESH -> skipped.add(path)
            SKIP_HIDDEN -> hidden.add(path)
        }
    }

    fun skipState(path: String): Int = when (path) {
        in skipped -> SKIP_FRESH
        in hidden -> SKIP_HIDDEN
        else -> SKIP_NONE
    }

    fun setSkip(path: String, state: Int) {
        putSkip(skipped, hidden, path, state)
        val line = JSONArray().put(path).put(state).toString() + "\n"
        io.execute {
            try {
                FileOutputStream(skippedFile, true).use { it.write(line.toByteArray()) }
            } catch (e: Exception) {
            }
        }
    }

    /** Move commits the skips made since the last one. */
    fun hideSkipped() {
        if (skipped.isEmpty()) return
        hidden.addAll(skipped)
        skipped.clear()
        compactSkipped()
    }

    fun compactSkipped() {
        val text = skippedText(skipped, hidden)
        io.execute { writeAtomic(skippedFile, text) }
    }

    private fun skippedText(skipped: Set<String>, hidden: Set<String>): String {
        val sb = StringBuilder()
        for (path in skipped) sb.append(JSONArray().put(path).put(SKIP_FRESH).toString()).append('\n')
        for (path in hidden) sb.append(JSONArray().put(path).put(SKIP_HIDDEN).toString()).append('\n')
        return sb.toString()
    }

    fun counts(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (dest in pending.values) out[dest] = (out[dest] ?: 0) + 1
        return out
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
