package moe.nikableh.sift.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.launch
import moe.nikableh.sift.R

// Heights are in the icon's 108 unit canvas before its 0.78 shrink, so at the 28dp header size one unit is about 0.37dp.
// Higher throws or stronger turns cut the print off at the top of the canvas: at 26 and 25 it clears the edge by 1.7 units.
// The time in the air follows from the heights, with Earth's gravity scaled as if the 62 unit wide folders were a real desk
// folder this wide. A bigger folder falls slower on screen
private const val FOLDER_METRES = 0.45f
private const val GRAVITY = 9.81f * 62f / FOLDER_METRES
private const val FOLDERS_UP = 12f
private const val PHOTO_UP = 26f

// At the top of each throw, negative to the left. The print turns and drifts against the folders' tilt so the two balance
private const val FOLDERS_TILT = -10f
private const val PHOTO_TURN = 25f
private const val PHOTO_DRIFT = 4f

private val photoFlight = flight(PHOTO_UP)

/**
 * The launcher icon. A tap makes the folders hop and throws the print out of them, a hidden extra.
 * Tapping again while it plays does nothing, because a restart would snap the print back mid-air
 */
@Composable
fun HeaderIcon(height: Dp) {
    val image = ImageVector.vectorResource(R.drawable.ic_launcher_foreground)
    val elapsed = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val animationsOn by rememberUpdatedState(rememberAnimationsOn())
    val painter = rememberVectorPainter(
        defaultWidth = image.defaultWidth,
        defaultHeight = image.defaultHeight,
        viewportWidth = image.viewportWidth,
        viewportHeight = image.viewportHeight,
        name = image.name,
        tintColor = image.tintColor,
        tintBlendMode = image.tintBlendMode,
        autoMirror = image.autoMirror,
    ) { _, _ -> RenderVectorGroup(image.root, hopPose(elapsed.value)) }
    // The drawing fills only the middle 58 of the adaptive foreground's 108 units. Lay the whole canvas out larger and let it
    // overflow, because a scale modifier would stretch the bitmap the vector painter drew at the small size
    Image(
        painter,
        null,
        Modifier.size(height)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (elapsed.isRunning) return@detectTapGestures
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scope.launch {
                        elapsed.snapTo(0f)
                        elapsed.animateTo(photoFlight, tween((photoFlight * 1000).roundToInt(), easing = LinearEasing))
                        // With animations off the hop ends in the same frame, and a second buzz right after the first feels like a fault
                        if (animationsOn) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            .requiredSize(height * 108f / 58f),
    )
}

/** Seconds a throw that peaks at [up] spends in the air. */
private fun flight(up: Float) = 2f * sqrt(2f * up / GRAVITY)

/** Height after [t] seconds of a throw that peaks at [up], until it lands. */
private fun height(t: Float, up: Float): Float {
    val speed = sqrt(2f * GRAVITY * up)
    return max(0f, speed * t - GRAVITY * t * t / 2f)
}

private fun hopPose(t: Float): Map<String, VectorConfig> {
    if (t <= 0f || t >= photoFlight) return emptyMap()
    val folders = height(t, FOLDERS_UP) / FOLDERS_UP
    val photo = height(t, PHOTO_UP) / PHOTO_UP
    // Drift and tilts follow the height, so each part goes out and back along one straight line and lands level
    val hop = Pose(0f, -FOLDERS_UP * folders, FOLDERS_TILT * folders)
    return mapOf(
        "hop_back" to hop,
        "hop_front" to hop,
        "fly" to Pose(PHOTO_DRIFT * photo, -PHOTO_UP * photo, PHOTO_TURN * photo),
    )
}

private class Pose(val x: Float, val y: Float, val turn: Float) : VectorConfig {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T = when (property) {
        VectorProperty.TranslateX -> x as T
        VectorProperty.TranslateY -> y as T
        VectorProperty.Rotation -> turn as T
        else -> defaultValue
    }
}
