package moe.nikableh.sift

import android.content.Context
import android.media.MediaScannerConnection
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Runs the queued moves. Never overwrites a file and never deletes a source it hasn't verified a copy of. */
object Mover {
    class Result(
        /** Source paths that are settled: moved, already in place, or gone. */
        val settled: List<String>,
        val moved: Int,
        val missing: Int,
        val failed: List<Pair<String, String>>,
        val cancelled: Boolean,
    )

    class UndoResult(
        /** Original path to the destination folder it had been moved to. */
        val restored: List<Pair<String, String>>,
        val failed: List<Pair<String, String>>,
    )

    fun move(
        ctx: Context,
        entries: List<Pair<String, String>>,
        journal: File,
        cancel: AtomicBoolean,
        progress: (Int) -> Unit,
    ): Result {
        val settled = ArrayList<String>()
        val failed = ArrayList<Pair<String, String>>()
        val touched = ArrayList<String>()
        var moved = 0
        var missing = 0
        var cancelled = false
        FileOutputStream(journal, true).use { log ->
            for ((i, entry) in entries.withIndex()) {
                if (cancel.get()) {
                    cancelled = true
                    break
                }
                val src = File(entry.first)
                val destDir = File(entry.second)
                try {
                    when {
                        !src.exists() -> {
                            missing++
                            settled.add(entry.first)
                        }
                        src.parentFile == destDir -> settled.add(entry.first)
                        else -> {
                            val target = move(src, destDir, src.name)
                            // Flushed per file, so a crash mid-batch still leaves an undoable record
                            log.write((JSONArray().put(src.path).put(target.path).toString() + "\n").toByteArray())
                            log.flush()
                            touched.add(src.path)
                            touched.add(target.path)
                            moved++
                            settled.add(entry.first)
                        }
                    }
                } catch (e: Exception) {
                    failed.add(src.name to (e.message ?: e.javaClass.simpleName))
                }
                progress(i + 1)
            }
        }
        if (moved == 0) journal.delete()
        rescan(ctx, touched)
        return Result(settled, moved, missing, failed, cancelled)
    }

    /**
     * Tells a move line without parsing it, for counting a 100,000-line journal. Journals from
     * versions that could skip photos also hold skip lines, which end in a number
     */
    fun isMoveLine(line: String) = line.endsWith("\"]")

    fun undo(ctx: Context, journal: File, progress: (Int) -> Unit): UndoResult {
        val lines = readJournal(journal)
        val restored = ArrayList<Pair<String, String>>()
        val failed = ArrayList<Pair<String, String>>()
        val touched = ArrayList<String>()
        for ((i, m) in lines.asReversed().withIndex()) {
            val original = File(m.first)
            val current = File(m.second)
            try {
                if (!current.exists()) throw IOException("no longer in ${current.parentFile?.name}")
                val back = move(current, original.parentFile!!, original.name)
                touched.add(current.path)
                touched.add(back.path)
                restored.add(back.path to current.parent!!)
            } catch (e: Exception) {
                failed.add(current.name to (e.message ?: e.javaClass.simpleName))
            }
            progress(i + 1)
        }
        journal.delete()
        rescan(ctx, touched)
        return UndoResult(restored, failed)
    }

    /** Original path to new path, one pair per moved photo. */
    fun readJournal(journal: File): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        try {
            journal.forEachLine { line ->
                try {
                    val a = JSONArray(line)
                    (a.get(1) as? String)?.let { out.add(a.getString(0) to it) }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun move(src: File, destDir: File, name: String): File {
        if (!destDir.isDirectory && !destDir.mkdirs() && !destDir.isDirectory) {
            throw IOException("can't create ${destDir.path}")
        }
        val target = freeName(destDir, name)
        // Same volume: a rename only rewrites directory entries, which is what makes a batch fast
        if (src.renameTo(target)) return target

        // Other volume (SD card): copy, check, then drop the original
        val part = File(destDir, ".${target.name}.sift-part")
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(part).use { output ->
                    val from = input.channel
                    val to = output.channel
                    var pos = 0L
                    val size = from.size()
                    while (pos < size) pos += from.transferTo(pos, size - pos, to)
                    output.fd.sync()
                }
            }
            if (part.length() != src.length()) throw IOException("copy came out a different size")
            part.setLastModified(src.lastModified())
            if (!part.renameTo(target)) throw IOException("can't finish the copy in ${destDir.name}")
        } catch (e: Exception) {
            part.delete()
            throw e
        }
        if (!src.delete()) {
            // Roll back, so a retry doesn't pile up duplicates
            target.delete()
            throw IOException("the original can't be removed, left it in place")
        }
        return target
    }

    private fun freeName(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            candidate = File(dir, "$stem ($n)$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    /** Tells MediaStore about old and new paths, so gallery apps don't show ghosts. */
    private fun rescan(ctx: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(ctx.applicationContext, paths.toTypedArray(), null, null)
    }
}
