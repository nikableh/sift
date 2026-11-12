package moe.nikableh.sift

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class Dest(val path: String, var label: String)

/** Settings and the queue of planned moves. Touch it from the main thread only. */
object Store {
    const val ORDER_NEWEST = 0
    const val ORDER_OLDEST = 1
    const val ORDER_NAME = 2

    private const val MAX_JOURNALS = 20

    private lateinit var dir: File
    private var loaded = false
    private val io = Executors.newSingleThreadExecutor()

    var source: String? = null
    var recursive = false
    var order = ORDER_NEWEST
    val dests = ArrayList<Dest>()
    var lastPath: String? = null
    var lastPickerDir: String? = null

    /** Photo path to destination folder path. Nothing on disk changes until Apply. */
    val pending = LinkedHashMap<String, String>()

    /**
     * Photos the user looked at and chose to leave where they are. Kept apart from [pending],
     * so a photo is either queued, skipped or not reviewed yet. Survives Apply.
     */
    val skipped = LinkedHashSet<String>()

    /** Bumped whenever the photo list has to be scanned again. */
    var scanVersion = 0

    private val configFile get() = File(dir, "config.json")
    private val pendingFile get() = File(dir, "pending.log")
    private val skippedFile get() = File(dir, "skipped.log")
    private val journalDir get() = File(dir, "journals")

    fun init(ctx: Context) {
        if (loaded) return
        dir = ctx.applicationContext.filesDir
        loadConfig()
        loadPending()
        loadSkipped()
        loaded = true
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

    private fun loadPending() {
        pending.clear()
        try {
            pendingFile.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                try {
                    val a = JSONArray(line)
                    val dest = a.optString(1)
                    if (dest.isEmpty()) pending.remove(a.getString(0)) else pending[a.getString(0)] = dest
                } catch (e: Exception) {
                    // A torn last line after a crash is expected, skip it
                }
            }
        } catch (e: Exception) {
            return
        }
        compactPending()
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
        val sb = StringBuilder()
        for ((path, dest) in pending) sb.append(JSONArray().put(path).put(dest).toString()).append('\n')
        val text = sb.toString()
        io.execute { writeAtomic(pendingFile, text) }
    }

    private fun loadSkipped() {
        skipped.clear()
        try {
            skippedFile.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                try {
                    val a = JSONArray(line)
                    if (a.optInt(1, 1) == 1) skipped.add(a.getString(0)) else skipped.remove(a.getString(0))
                } catch (e: Exception) {
                    // A torn last line after a crash is expected, skip it
                }
            }
        } catch (e: Exception) {
            return
        }
        compactSkipped()
    }

    fun setSkipped(path: String, on: Boolean) {
        if (on) skipped.add(path) else skipped.remove(path)
        val line = JSONArray().put(path).put(if (on) 1 else 0).toString() + "\n"
        io.execute {
            try {
                FileOutputStream(skippedFile, true).use { it.write(line.toByteArray()) }
            } catch (e: Exception) {
            }
        }
    }

    fun compactSkipped() {
        val sb = StringBuilder()
        for (path in skipped) sb.append(JSONArray().put(path).put(1).toString()).append('\n')
        val text = sb.toString()
        io.execute { writeAtomic(skippedFile, text) }
    }

    fun counts(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (dest in pending.values) out[dest] = (out[dest] ?: 0) + 1
        return out
    }

    fun labelFor(destPath: String): String =
        dests.firstOrNull { it.path == destPath }?.label ?: File(destPath).name

    /** Newest first. Each file lists the moves of one Apply. */
    fun journals(): List<File> =
        (journalDir.listFiles { f -> f.name.endsWith(".log") } ?: emptyArray())
            .sortedByDescending { it.name }

    fun newJournal(): File {
        journalDir.mkdirs()
        journals().drop(MAX_JOURNALS - 1).forEach { it.delete() }
        return File(journalDir, "batch-${System.currentTimeMillis()}.log")
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
