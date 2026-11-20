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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

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

/**
 * Segmented list rows sit one tone above the page, so each segment reads as its own shape.
 */
@Composable
fun segmentColors(): ListItemColors {
    return ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
}

fun folderColor(index: Int): Color = folderPalette[index.mod(folderPalette.size)]

/**
 * Fill of a folder button and its pill, and the text and icons on it.
 * Each is a tone of the folder's hue, light tiles with dark ink in light theme and the reverse in dark,
 * like a container and on-container role pair. HCT tone is CIELAB L*, so Lab keeps the hue while the tone moves
 */
@Composable
@ReadOnlyComposable
fun Color.tile(): Color = atTone(if (isSystemInDarkTheme()) 30f else 80f)

/** Text and icons on [tile]. */
@Composable
@ReadOnlyComposable
fun Color.ink(): Color = atTone(if (isSystemInDarkTheme()) 90f else 10f)

/**
 * A folder's dot or swatch on the other screens. It stands on the page rather than holding content,
 * so it keeps the light tile tone in both themes, which in dark is well above 3:1 on the surface
 */
fun Color.dot(): Color = atTone(80f)

/** Chroma shrinks until the colour fits sRGB, so the tone stays exact. */
private fun Color.atTone(tone: Float): Color {
    val lab = convert(ColorSpaces.CieLab)
    var scale = 1f
    while (true) {
        val a = lab.green * scale
        val b = lab.blue * scale
        val rgb = Color(tone, a, b, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)
        val back = rgb.convert(ColorSpaces.CieLab)
        val fits = abs(back.red - tone) < 0.5f && abs(back.green - a) < 1f && abs(back.blue - b) < 1f
        if (fits || scale <= 0f) return rgb
        scale -= 0.05f
    }
}
