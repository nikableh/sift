package moe.nikableh.sift

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes screen-sized bitmaps off the main thread and keeps the neighbours of the current photo warm.
 * A video gives its first frame
 */
object ImageLoader {
    private const val FULL_MAX_SIDE = 4700

    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val cache = LruCache<String, Bitmap>(6)
    private val durations = LruCache<String, Long>(64)
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

    /**
     * The moving picture of a GIF, to draw in place of its still. Not cached, since only the photo
     * on screen has one. [cb] runs on the main thread, with null for a GIF of a single frame
     */
    fun loadAnimation(file: File, cb: (AnimatedImageDrawable?) -> Unit) {
        pool.execute {
            val animation = if (file.path in wanted) animation(file) else null
            main.post { cb(animation) }
        }
    }

    private fun animation(file: File): AnimatedImageDrawable? = try {
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            fit(decoder, info, fitScreen = true)
        }
        // A GIF that doesn't ask to loop would play once, maybe before anyone has looked at it
        (drawable as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** A video's length in milliseconds, known once its still frame has been decoded. */
    fun durationOf(file: File): Long? = durations.get(file.path)

    /**
     * A video's first frame. That is where playback starts, so the still and the player's first
     * picture are the same one
     */
    private fun frame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            fun number(key: Int) = retriever.extractMetadata(key)?.toLongOrNull() ?: 0L
            number(MediaMetadataRetriever.METADATA_KEY_DURATION).takeIf { it > 0 }?.let { durations.put(file.path, it) }
            val width = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val long = max(width, height)
            val short = min(width, height)
            // The frame is fitted into a square of this side, so it only has to hold the long one
            val side = if (short > 0) {
                (long * min(1f, min(screenLong / long.toFloat(), screenShort / short.toFloat()))).toInt().coerceAtLeast(1)
            } else {
                screenLong
            }
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, side, side)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun decode(file: File, fitScreen: Boolean): Bitmap? = if (Scanner.isVideo(file.name)) frame(file) else try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ -> fit(decoder, info, fitScreen) }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun fit(decoder: ImageDecoder, info: ImageDecoder.ImageInfo, fitScreen: Boolean) {
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
}
