package moe.nikableh.sift

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import java.io.File

/**
 * Plays the video on the sorting screen. One player for the whole screen, holding one file at a
 * time. Main thread only
 */
@OptIn(UnstableApi::class)
class VideoPlayer(private val ctx: Context) {
    /** The file it holds, from [open] until [close]. */
    var path by mutableStateOf<String?>(null)
        private set

    /** What the play button shows. Losing the sound to another app pauses it too */
    var playing by mutableStateOf(false)
        private set

    /** Set once the first picture of [path] is on the surface, so the surface can cover the still frame. */
    var showing by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    /** Width over height, or 0 until the decoder says. */
    var aspect by mutableFloatStateOf(0f)
        private set

    var duration by mutableLongStateOf(0L)
        private set

    /** Changes every frame while playing, so read it only where it is drawn. */
    var position by mutableLongStateOf(0L)
        private set

    /** Set once the video has played to its end. The screen is kept awake only until then */
    var looped by mutableStateOf(false)
        private set

    private var exo: ExoPlayer? = null
    private var surface: SurfaceView? = null
    private var muted = true
    private var scrubbing = false
    private var exactSeeks = true
    private val main = Handler(Looper.getMainLooper())

    /** Lands on the exact frame once the thumb has held still. */
    private val settle = Runnable { exo?.let { seek(it, position, exact = true) } }

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            // One from the file before can still arrive after a close
            if (path != null) showing = true
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) duration = exo?.duration?.coerceAtLeast(0L) ?: 0L
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            playing = playWhenReady
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) looped = true
        }

        override fun onPlayerError(error: PlaybackException) {
            failed = true
            showing = false
            playing = false
        }
    }

    fun attach(view: SurfaceView) {
        surface = view
        exo?.setVideoSurfaceView(view)
    }

    fun detach(view: SurfaceView) {
        if (surface !== view) return
        surface = null
        exo?.clearVideoSurfaceView(view)
    }

    /** Starts [file] from its beginning. The player itself is only built for the first video. */
    fun open(file: File) {
        val player = exo ?: build().also { exo = it }
        path = file.path
        showing = false
        failed = false
        looped = false
        aspect = 0f
        duration = 0L
        position = 0L
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.playWhenReady = true
        playing = true
        player.prepare()
    }

    /** Lets go of the file and of the decoders. */
    fun close() {
        main.removeCallbacks(settle)
        path = null
        showing = false
        playing = false
        val player = exo ?: return
        if (scrubbing) player.setScrubbingModeEnabled(false)
        scrubbing = false
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        close()
        exo?.release()
        exo = null
    }

    fun toggle() {
        val player = exo ?: return
        if (path != null && !failed) player.playWhenReady = !player.playWhenReady
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        exo?.let { applySound(it) }
    }

    private fun applySound(player: ExoPlayer) {
        player.volume = if (muted) 0f else 1f
        // Without sound it must not take the audio away from whatever else is playing
        player.setAudioAttributes(AUDIO, !muted)
    }

    /**
     * Follows the timeline's thumb. While it moves the picture jumps between keyframes, which is
     * what lets it keep up, and [settle] shows the exact frame once the thumb rests
     */
    fun scrubTo(ms: Long) {
        val player = exo ?: return
        if (path == null || failed) return
        if (!scrubbing) {
            scrubbing = true
            player.setScrubbingModeEnabled(true)
        }
        position = ms
        seek(player, ms, exact = false)
        main.removeCallbacks(settle)
        main.postDelayed(settle, REST_MS)
    }

    fun scrubEnd() {
        val player = exo ?: return
        if (!scrubbing) return
        main.removeCallbacks(settle)
        seek(player, position, exact = true)
        player.setScrubbingModeEnabled(false)
        scrubbing = false
    }

    private fun seek(player: ExoPlayer, ms: Long, exact: Boolean) {
        if (exact != exactSeeks) {
            exactSeeks = exact
            player.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
        }
        player.seekTo(ms)
    }

    /** Reads the playing position into [position]. Called once a frame while playing. */
    fun tick() {
        if (!scrubbing) exo?.let { position = it.currentPosition }
    }

    private fun build(): ExoPlayer {
        // The decoders and file readers are named here, so the ones never used aren't packed into the app
        val renderers = RenderersFactory { handler, videoListener, audioListener, _, _ ->
            arrayOf<Renderer>(
                // Falls back to the next decoder, usually the software one, when the first won't start
                MediaCodecVideoRenderer(ctx, MediaCodecSelector.DEFAULT, 0L, true, handler, videoListener, 50),
                MediaCodecAudioRenderer(ctx, MediaCodecSelector.DEFAULT, handler, audioListener),
            )
        }
        val extractors = ExtractorsFactory {
            arrayOf<Extractor>(
                Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED),
                FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED),
                MatroskaExtractor(SubtitleParser.Factory.UNSUPPORTED),
            )
        }
        // A file on the phone needs no reserve against a slow network. This keeps what is read
        // ahead small whatever the video's size
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 2000, 0, 0)
            .setTargetBufferBytes(BUFFER_BYTES)
            .build()
        return ExoPlayer.Builder(ctx, renderers, ProgressiveMediaSource.Factory(FileDataSource.Factory(), extractors))
            .setLoadControl(load)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player ->
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.addListener(listener)
                surface?.let { player.setVideoSurfaceView(it) }
                applySound(player)
            }
    }

    private companion object {
        const val BUFFER_BYTES = 16 shl 20

        /** How long the thumb has to hold still before the exact frame is fetched. */
        const val REST_MS = 120L

        val AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
}
