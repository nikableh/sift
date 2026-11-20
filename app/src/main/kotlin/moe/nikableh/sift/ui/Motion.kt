package moe.nikableh.sift.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** False while Remove animations is on. Follows the animator duration scale, so turning Remove animations on or off applies without a restart */
@Composable
fun rememberAnimationsOn(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    var on by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                on = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        on = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return on
}

/** A morphing indicator, or a still shape when animations are removed, since the morph is decoration */
@Composable
fun Loading(modifier: Modifier = Modifier) {
    if (rememberAnimationsOn()) LoadingIndicator(modifier) else LoadingIndicator(progress = { 0f }, modifier = modifier)
}

/**
 * A screen change made by a button, shared by the screens in SiftApp and the panes that swap within
 * one, so both slide alike. The screen being left on the way back slides off on top of the one it
 * came from
 */
fun screenSlide(forward: Boolean, animationsOn: Boolean): ContentTransform = when {
    // Reduced motion asks for a plain fade, never a slide
    !animationsOn -> fadeIn() togetherWith fadeOut()
    forward -> slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it } togetherWith
        slideOutHorizontally(tween(400, easing = Emphasized)) { -it / 3 }
    else -> (slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 3 } togetherWith
        slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { it })
        .apply { targetContentZIndex = -1f }
}

// Material's emphasized easing curves. The library keeps its own copies internal
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
