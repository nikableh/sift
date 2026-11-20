package moe.nikableh.sift.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.delay
import moe.nikableh.sift.VideoPlayer

/**
 * Plays [path] in [player] while the app is on screen, and lets go of it once [path] changes or
 * turns null. [startDelayMs] is how long a path has to stay before its decoder is set up
 */
@Composable
fun VideoPlayback(player: VideoPlayer, path: String?, muted: Boolean, startDelayMs: Long = 0L) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Counted up on every return to the app, which starts the video again after the stop below
    var starts by remember { mutableIntStateOf(0) }
    LifecycleStartEffect(player) {
        starts++
        // Closed here and not through state: nothing recomposes while the app is in the background,
        // and a video with sound would play on
        onStopOrDispose { player.close() }
    }
    LaunchedEffect(path, starts) {
        player.close()
        if (path != null) {
            if (startDelayMs > 0) delay(startDelayMs)
            // The wait runs on while the app is in the background, where nothing must start playing.
            // Coming back counts [starts] up and gets here again
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.open(File(path))
        }
    }
    LaunchedEffect(muted) { player.setMuted(muted) }

    val running = player.playing && player.showing
    LaunchedEffect(running) {
        if (running) while (true) {
            withFrameNanos {}
            player.tick()
        }
    }
    // Awake for one play through. A video on repeat would otherwise never let the screen sleep
    if (running && !player.looped) KeepScreenOn()
}

/** Play or pause, the timeline, the time and sound. */
@Composable
fun VideoBar(player: VideoPlayer, muted: Boolean, onMuted: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        // The bar sits on the video, which can be any colour, so the shadow gives it its edge
        expandedShadowElevation = 6.dp,
    ) {
        // A video starts by itself, so until the player has it the button already shows what is about to happen
        val playing = player.playing || player.path == null
        // Unfilled like the sound button, and primary in both states because it is the bar's main control
        IconToggleButton(
            checked = playing,
            onCheckedChange = { player.toggle() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.iconToggleButtonVibrantColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            if (playing) Icon(Icons.Rounded.Pause, "Playing") else Icon(Icons.Rounded.PlayArrow, "Playing")
        }
        Timeline(player, Modifier.weight(1f).padding(horizontal = 8.dp))
        Elapsed(player, Modifier.align(Alignment.CenterVertically))
        val sound = !muted
        IconToggleButton(
            checked = sound,
            onCheckedChange = { onMuted(!it) },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            if (sound) Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Sound")
            else Icon(Icons.AutoMirrored.Rounded.VolumeOff, "Sound")
        }
    }
}

/** On its own, so the position changing every frame redraws the slider and nothing around it. */
@Composable
private fun Timeline(player: VideoPlayer, modifier: Modifier) {
    val duration = player.duration
    val state = rememberSliderState()
    state.value = if (duration > 0) (player.position / duration.toFloat()).coerceIn(0f, 1f) else 0f
    Slider(
        state = state,
        onValueChange = { player.scrubTo((it * duration).toLong()) },
        onValueChangeFinished = { player.scrubEnd() },
        modifier = modifier.semantics { contentDescription = "Timeline" },
    )
}

@Composable
private fun Elapsed(player: VideoPlayer, modifier: Modifier) {
    Text(
        clock(player.position),
        modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}
