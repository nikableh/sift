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
        /** Source path and folder pairs that are settled: moved, copied, already in place, or gone. */
        val settled: List<Pair<String, String>>,
        val moved: Int,
        val copied: Int,
        val missing: Int,
        val failed: List<Pair<String, String>>,
        val cancelled: Boolean,
    )

    class UndoResult(
        /** Original path to the destination folder it had been moved or copied to. */
        val restored: List<Pair<String, String>>,
        val failed: List<Pair<String, String>>,
    )

    fun move(
        ctx: Context,
        entries: List<Pair<String, List<String>>>,
        journal: File,
        cancel: AtomicBoolean,
        progress: (Int) -> Unit,
    ): Result {
        val settled = ArrayList<Pair<String, String>>()
        val failed = ArrayList<Pair<String, String>>()
        val touched = ArrayList<String>()
        var moved = 0
        var copied = 0
        var missing = 0
        var cancelled = false
        val next = RandomAccessFile(nextFile(journal), "rw")
        FileOutputStream(journal, true).use { log ->
            for ((i, entry) in entries.withIndex()) {
                if (cancel.get()) {
                    cancelled = true
                    break
                }
                val (path, dests) = entry
                val src = File(path)
                if (!src.exists()) {
                    missing++
                    for (d in dests) settled.add(path to d)
                    progress(i + 1)
                    continue
                }
                val away = dests.filter { File(it) != src.parentFile }
                for (d in dests) if (d !in away) settled.add(path to d)
                // A photo already in one of its folders stays there, otherwise it ends up in the last one
                val moveTo = if (away.size < dests.size) null else away.last()
                var failure: String? = null
                for (d in away) {
                    if (d == moveTo) continue
                    try {
                        val target = copy(src, File(d), next)
                        // Flushed per file, so a crash mid-batch still leaves an undoable record
                        log.write(line(src.path, target.path, copy = true))
                        log.flush()
                        touched.add(target.path)
                        copied++
                        settled.add(path to d)
                    } catch (e: Exception) {
                        if (failure == null) failure = reason(e)
                    }
                }
                // The move waits for every copy, or a folder whose copy failed would find the photo
                // gone on the next run and silently drop it
                if (moveTo != null && failure == null) {
                    try {
                        val target = move(src, File(moveTo), src.name, next)
                        log.write(line(src.path, target.path))
                        log.flush()
                        touched.add(src.path)
                        touched.add(target.path)
                        moved++
                        settled.add(path to moveTo)
                    } catch (e: Exception) {
                        failure = reason(e)
                    }
                }
                failure?.let { failed.add(src.name to it) }
                progress(i + 1)
            }
        }
        next.close()
        nextFile(journal).delete()
        if (moved + copied == 0) journal.delete()
        rescan(ctx, touched)
        return Result(settled, moved, copied, missing, failed, cancelled)
    }

    /**
     * Tells a move or copy line without parsing it, for counting a 100,000-line journal. Journals
     * from versions that could skip photos also hold skip lines, which end in a number
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
        // A copy only gets its own name once it is whole, and the original stays
        if (owed != null && File(owed.to).exists() && (owed.copy || !File(owed.from).exists()) && readJournal(log).lastOrNull() != owed) {
            try {
                FileOutputStream(log, true).use { it.write(line(owed.from, owed.to, owed.copy)) }
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
        // Moved-to or copy path to the path an earlier undo put the photo back at
        val back = readJournal(notes).associate { it.from to it.to }
        // Original path to where this undo put the photo back, which a name taken meanwhile can change
        val putBack = HashMap<String, String>()
        val restored = ArrayList<Pair<String, String>>()
        val failed = ArrayList<Pair<String, String>>()
        val touched = ArrayList<String>()
        val next = RandomAccessFile(nextFile(notes), "rw")
        FileOutputStream(notes, true).use { log ->
            // Backwards, so a photo is back where it was before its copies are checked against it
            for ((i, m) in lines.asReversed().withIndex()) {
                val original = File(m.from)
                val current = File(m.to)
                try {
                    val was = back[current.path]
                    when {
                        was != null -> {
                            if (!m.copy) putBack[m.from] = was
                            restored.add(was to current.parent!!)
                        }
                        m.copy -> {
                            val photo = File(putBack[m.from] ?: m.from)
                            // A copy already deleted by hand is as good as undone
                            if (current.exists()) {
                                if (!photo.exists() || photo.length() != current.length()) {
                                    throw Plain("the original isn't back, kept the copy in ${current.parentFile?.name}")
                                }
                                announce(next, line(current.path, photo.path))
                                if (!current.delete()) throw Plain("can't remove the copy in ${current.parentFile?.name}")
                                log.write(line(current.path, photo.path))
                                log.flush()
                                touched.add(current.path)
                            }
                            restored.add(photo.path to current.parent!!)
                        }
                        else -> {
                            if (!current.exists()) throw Plain("no longer in ${current.parentFile?.name}")
                            val to = move(current, original.parentFile!!, original.name, next)
                            log.write(line(current.path, to.path))
                            log.flush()
                            touched.add(current.path)
                            touched.add(to.path)
                            putBack[m.from] = to.path
                            restored.add(to.path to current.parent!!)
                        }
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

    private fun line(from: String, to: String, copy: Boolean = false): ByteArray {
        val a = JSONArray().put(from).put(to)
        if (copy) a.put(COPY)
        return (a.toString() + "\n").toByteArray()
    }

    private const val COPY = "copy"

    /** One journal line: a photo moved from [from] to [to], or with [copy] a copy of it made at [to]. */
    data class Entry(val from: String, val to: String, val copy: Boolean) {
        val destDir: String get() = File(to).parent!!
    }

    fun readJournal(journal: File): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            journal.forEachLine { line ->
                try {
                    val a = JSONArray(line)
                    (a.get(1) as? String)?.let { out.add(Entry(a.getString(0), it, a.optString(2) == COPY)) }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun announce(next: RandomAccessFile, line: ByteArray) {
        next.seek(0)
        next.write(line)
        next.setLength(line.size.toLong())
    }

    private fun makeDir(dir: File) {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw Plain("can't create ${dir.path}")
    }

    private fun copy(src: File, destDir: File, next: RandomAccessFile): File {
        makeDir(destDir)
        val target = freeName(destDir, src.name)
        announce(next, line(src.path, target.path, copy = true))
        copyTo(src, target)
        return target
    }

    private fun move(src: File, destDir: File, name: String, next: RandomAccessFile): File {
        makeDir(destDir)
        val target = freeName(destDir, name)
        announce(next, line(src.path, target.path))
        // Same volume: a rename only rewrites directory entries, which is what makes a batch fast
        if (src.renameTo(target)) return target

        // Other volume (SD card): copy, check, then drop the original
        copyTo(src, target)
        if (!src.delete()) {
            // Roll back, so a retry doesn't pile up duplicates
            target.delete()
            throw Plain("the original can't be removed, left it in place")
        }
        return target
    }

    /** Writes under a hidden name and takes the real one only once the copy is whole. */
    private fun copyTo(src: File, target: File) {
        // Keeps the photo's extension last: without all-files access a photo folder only takes media files
        val part = File(target.parentFile, ".sift-part-${target.name}")
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
            if (!part.renameTo(target)) throw Plain("can't finish the copy in ${target.parentFile?.name}")
        } catch (e: Exception) {
            part.delete()
            throw e
        }
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
