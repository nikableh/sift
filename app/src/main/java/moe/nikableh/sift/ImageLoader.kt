package moe.nikableh.sift

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Decodes screen-sized bitmaps off the main thread and keeps the neighbours of the current photo warm. */
object ImageLoader {
    private const val FULL_MAX_SIDE = 4700

    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val cache = LruCache<String, Bitmap>(6)
    private val inFlight = HashMap<String, MutableList<(Bitmap?) -> Unit>>()

    private var screenLong = 2400
    private var screenShort = 1080

    /** Paths worth decoding right now. Queued work for anything else is dropped, so fast tapping doesn't pile up. */
    @Volatile
    var wanted: Set<String> = emptySet()

    fun setScreen(w: Int, h: Int) {
        screenLong = max(w, h)
        screenShort = min(w, h)
    }

    fun cached(file: File): Bitmap? = cache.get(file.path)

    fun clear() = cache.evictAll()

    /** Main thread only. [cb] runs on the main thread, with null when decoding failed. */
    fun load(file: File, cb: (Bitmap?) -> Unit) {
        val key = file.path
        cache.get(key)?.let { cb(it); return }
        val waiting = inFlight[key]
        if (waiting != null) {
            waiting.add(cb)
            return
        }
        inFlight[key] = mutableListOf(cb)
        submit(file)
    }

    fun prefetch(file: File) = load(file) {}

    private fun submit(file: File) {
        val key = file.path
        pool.execute {
            val skip = key !in wanted
            val bmp = if (skip) null else decode(file, fitScreen = true)
            main.post {
                if (skip && key in wanted) {
                    submit(file)
                    return@post
                }
                val cbs = inFlight.remove(key) ?: return@post
                if (bmp != null) cache.put(key, bmp)
                if (!skip) cbs.forEach { it(bmp) }
            }
        }
    }

    /** Higher resolution for zooming. Not cached. */
    fun loadFull(file: File, cb: (Bitmap?) -> Unit) {
        pool.execute {
            val bmp = decode(file, fitScreen = false)
            main.post { cb(bmp) }
        }
    }

    private fun decode(file: File, fitScreen: Boolean): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val long = max(info.size.width, info.size.height)
            val short = min(info.size.width, info.size.height)
            var sample = 1
            if (fitScreen) {
                // Long and short sides instead of width and height, so the bitmap is sharp
                // in both screen orientations and whichever way EXIF rotates it
                val ratio = max(long.toFloat() / screenLong, short.toFloat() / screenShort)
                while (sample * 2 <= ratio) sample *= 2
            } else {
                while (long / sample > FULL_MAX_SIDE) sample *= 2
            }
            decoder.setTargetSampleSize(sample)
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
