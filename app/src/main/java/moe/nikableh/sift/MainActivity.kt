package moe.nikableh.sift

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import moe.nikableh.sift.ui.SiftApp

class MainActivity : ComponentActivity() {
    private val vm: SiftViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is light only, so bar icons stay dark even when the phone is in dark mode
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        val bounds = windowManager.currentWindowMetrics.bounds
        ImageLoader.setScreen(bounds.width(), bounds.height())
        setContent { SiftApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // The all-files permission is granted on a system screen, so it has to be rechecked on return
        vm.refresh()
    }

    override fun onPause() {
        super.onPause()
        vm.persist()
    }
}
