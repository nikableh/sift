package moe.nikableh.sift

import android.content.Context
import android.media.MediaScannerConnection
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
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
        val next = RandomAccessFile(nextFile(journal), "rw")
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
                            val target = move(src, destDir, src.name, next)
                            // Flushed per file, so a crash mid-batch still leaves an undoable record
                            log.write(line(src.path, target.path))
                            log.flush()
                            touched.add(src.path)
                            touched.add(target.path)
                            moved++
                            settled.add(entry.first)
                        }
                    }
                } catch (e: Exception) {
                    failed.add(src.name to reason(e))
                }
                progress(i + 1)
            }
        }
        next.close()
        nextFile(journal).delete()
        if (moved == 0) journal.delete()
        rescan(ctx, touched)
        return Result(settled, moved, missing, failed, cancelled)
    }

    /**
     * Tells a move line without parsing it, for counting a 100,000-line journal. Journals from
     * versions that could skip photos also hold skip lines, which end in a number
     */
    fun isMoveLine(line: String) = line.endsWith("\"]")

    /**
     * Where an undo notes each photo it has put back. An undo cut short by the death of its
     * process leaves the file behind, and the next one reads it instead of failing on those photos
     */
    fun backLog(journal: File) = File(journal.path + ".back")

    /** Names the one rename under way, see [settle]. */
    private fun nextFile(log: File) = File(log.path + ".next")

    /**
     * A kill can land after a rename and before its line reaches the log, so each rename is
     * announced first. This adds the line a dead run owed its log, if that rename went through
     */
    fun settle(log: File) {
        val next = nextFile(log)
        val owed = readJournal(next).firstOrNull()
        if (owed != null && !File(owed.first).exists() && File(owed.second).exists() && readJournal(log).lastOrNull() != owed) {
            try {
                FileOutputStream(log, true).use { it.write(line(owed.first, owed.second)) }
            } catch (e: Exception) {
            }
        }
        next.delete()
    }

    /** Deletes a journal together with what an undo keeps beside it. */
    fun discard(journal: File) {
        val notes = backLog(journal)
        for (f in listOf(journal, nextFile(journal), notes, nextFile(notes))) f.delete()
    }

    fun undo(ctx: Context, journal: File, progress: (Int) -> Unit): UndoResult {
        val lines = readJournal(journal)
        val notes = backLog(journal)
        settle(notes)
        // Moved-to path to the path an earlier undo put the photo back at
        val back = readJournal(notes).toMap()
        val restored = ArrayList<Pair<String, String>>()
        val failed = ArrayList<Pair<String, String>>()
        val touched = ArrayList<String>()
        val next = RandomAccessFile(nextFile(notes), "rw")
        FileOutputStream(notes, true).use { log ->
            for ((i, m) in lines.asReversed().withIndex()) {
                val original = File(m.first)
                val current = File(m.second)
                try {
                    val was = back[current.path]
                    if (was != null) {
                        restored.add(was to current.parent!!)
                    } else {
                        if (!current.exists()) throw Plain("no longer in ${current.parentFile?.name}")
                        val to = move(current, original.parentFile!!, original.name, next)
                        log.write(line(current.path, to.path))
                        log.flush()
                        touched.add(current.path)
                        touched.add(to.path)
                        restored.add(to.path to current.parent!!)
                    }
                } catch (e: Exception) {
                    failed.add(current.name to reason(e))
                }
                progress(i + 1)
            }
        }
        next.close()
        discard(journal)
        rescan(ctx, touched)
        return UndoResult(restored, failed)
    }

    /** A failure this file describes itself, already in words the dialog can show. */
    private class Plain(message: String) : IOException(message)

    /** Turns a failure into words for the dialog. The raw message goes only to the log. */
    private fun reason(e: Exception): String {
        Log.w("Sift", "move failed", e)
        if (e is Plain) return e.message!!
        val errno = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<ErrnoException>().firstOrNull()?.errno
        val text = e.message.orEmpty()
        return when {
            errno == OsConstants.ENOSPC || "ENOSPC" in text -> "Not enough space"
            errno == OsConstants.EACCES || errno == OsConstants.EPERM || e is SecurityException ||
                "EACCES" in text || "EPERM" in text -> "Android refused access"
            e is FileNotFoundException -> "File is gone"
            else -> "Couldn't be moved"
        }
    }

    private fun line(from: String, to: String) = (JSONArray().put(from).put(to).toString() + "\n").toByteArray()

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

    private fun move(src: File, destDir: File, name: String, next: RandomAccessFile): File {
        if (!destDir.isDirectory && !destDir.mkdirs() && !destDir.isDirectory) {
            throw Plain("can't create ${destDir.path}")
        }
        val target = freeName(destDir, name)
        val announced = line(src.path, target.path)
        next.seek(0)
        next.write(announced)
        next.setLength(announced.size.toLong())
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
            if (part.length() != src.length()) throw Plain("copy came out a different size")
            part.setLastModified(src.lastModified())
            if (!part.renameTo(target)) throw Plain("can't finish the copy in ${destDir.name}")
        } catch (e: Exception) {
            part.delete()
            throw e
        }
        if (!src.delete()) {
            // Roll back, so a retry doesn't pile up duplicates
            target.delete()
            throw Plain("the original can't be removed, left it in place")
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
    fun rescan(ctx: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(ctx.applicationContext, paths.toTypedArray(), null, null)
    }
}
