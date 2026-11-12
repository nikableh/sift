package moe.nikableh.sift.ui

import android.os.Build
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/** Light only for now, whatever the system setting. */
@Composable
fun SiftTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Wallpaper colours exist from Android 12, and minSdk is 30
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        expressiveLightColorScheme()
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

/** Dark enough for white labels on every palette colour, amber and cyan included. */
fun Color.dimmed(): Color = lerp(this, Color.Black, 0.3f)
