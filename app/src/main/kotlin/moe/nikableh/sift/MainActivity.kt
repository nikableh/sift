package moe.nikableh.sift

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import moe.nikableh.sift.ui.SiftApp

private const val SPLASH_FADE = 150L

class MainActivity : ComponentActivity() {
    private val vm: SiftViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splash ->
            // A cold start is often ready before the icon animation ends, and a cut-off animation reads as a
            // twitch, so the splash stays up for the rest of it. The start time is 0 on Android 11, which
            // leaves nothing to wait for there.
            val length = resources.getInteger(R.integer.splash_duration).toLong()
            val left = if (ValueAnimator.areAnimatorsEnabled()) {
                (length - (System.currentTimeMillis() - splash.iconAnimationStartMillis)).coerceIn(0, length)
            } else 0
            // From Android 12 the icon is drawn apart from the splash view and ignores its alpha
            splash.iconView.animate().alpha(0f).setStartDelay(left).setDuration(SPLASH_FADE)
            splash.view.animate().alpha(0f).setStartDelay(left).setDuration(SPLASH_FADE)
                .withLayer().withEndAction { splash.remove() }
        }
        styleSystemBars()
        super.onCreate(savedInstanceState)
        val bounds = windowManager.currentWindowMetrics.bounds
        ImageLoader.setScreen(bounds.width(), bounds.height())
        // Lets the thumbnails on Home, Folders, the picker and Selections show a frame of a video
        SingletonImageLoader.setSafe { context ->
            coil3.ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
        }
        setContent { SiftApp(vm) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The manifest handles uiMode, so a theme switch doesn't recreate the activity
        // and the bar icons have to be flipped by hand
        styleSystemBars()
    }

    private fun styleSystemBars() {
        val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }

    override fun onResume() {
        super.onResume()
        // The all-files permission is granted on a system screen, so it has to be rechecked on return
        vm.resumed()
    }

    override fun onPause() {
        super.onPause()
        vm.persist()
    }
}
