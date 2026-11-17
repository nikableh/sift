package moe.nikableh.sift.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/** Follows the system light or dark setting, there is no in-app switch. */
@Composable
fun SiftTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Wallpaper colours exist from Android 12, and minSdk is 30
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else expressiveLightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}

/**
 * One fixed colour per folder button, deliberately outside the dynamic palette.
 * The thumb learns "blue is Keep" faster than it reads a label.
 */
private val folderPalette = listOf(
    Color(0xFF4F8DF7), Color(0xFFE5604D), Color(0xFF3FAE6A), Color(0xFFE0A526),
    Color(0xFFA56BE8), Color(0xFF22B5C4), Color(0xFFE86BA8), Color(0xFF9AA63A),
    Color(0xFFE98A3C), Color(0xFF7C8CA8),
)

/** Segmented list rows sit one tone above the page, so each segment reads as its own shape. */
@Composable
fun segmentColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

fun folderColor(index: Int): Color = folderPalette[index.mod(folderPalette.size)]

/**
 * Fill of a folder button, its pill and its dot on the other screens. The same in light and dark,
 * so what sits on it has fixed colours too
 */
fun Color.tile(): Color = lerp(this, Color.White, 0.45f)

/** Text and icons on [tile]. */
fun Color.ink(): Color = lerp(this, Color.Black, 0.78f)
