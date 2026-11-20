package moe.nikableh.sift.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
