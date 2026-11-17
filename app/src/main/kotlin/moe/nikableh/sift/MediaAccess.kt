package moe.nikableh.sift

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Finds the MediaStore entries of photos, so Sift can ask Android for write access to them. Without
 * all-files access a photo another app made can only be moved after that
 */
object MediaAccess {
    /** Android refuses a bigger request from apps that target API 37 and later. */
    const val MAX_REQUEST = 2000

    /** SQLite caps the arguments of one query at 999. */
    private const val QUERY_BATCH = 500

    /** One entry per path that still exists. A path MediaStore hasn't indexed yet is scanned first. */
    fun urisFor(ctx: Context, paths: Collection<String>): List<Uri> {
        val want = paths.filter { File(it).exists() }
        val found = HashMap<String, Uri>(want.size * 2)
        @Suppress("DEPRECATION")
        val data = MediaStore.MediaColumns.DATA
        val columns = arrayOf(MediaStore.MediaColumns._ID, data, MediaStore.MediaColumns.VOLUME_NAME)
        val all = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        for (batch in want.chunked(QUERY_BATCH)) {
            val selection = "$data IN (${batch.joinToString(",") { "?" }})"
            ctx.contentResolver.query(all, columns, selection, batch.toTypedArray(), null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(1) ?: continue
                    val volume = c.getString(2) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                    found[path] = ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volume), c.getLong(0))
                }
            }
        }
        val missing = want.filter { it !in found }
        if (missing.isNotEmpty()) found.putAll(scan(ctx, missing))
        return want.mapNotNull { found[it] }
    }

    private fun scan(ctx: Context, paths: List<String>): Map<String, Uri> {
        val out = ConcurrentHashMap<String, Uri>()
        val left = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(ctx.applicationContext, paths.toTypedArray(), null) { path, uri ->
            if (uri != null) out[path] = uri
            left.countDown()
        }
        left.await(2, TimeUnit.MINUTES)
        return out
    }
}
