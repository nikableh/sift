package moe.nikableh.sift.ui

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

// Built once per locale, since a tap redraws a dozen counts. Main thread only: the format isn't thread safe
private var groupedFormat: Pair<Locale, NumberFormat>? = null

/** 123456 as 123,456, or however the locale groups digits. Not for numbers the user types back in */
fun Int.grouped(): String {
    val locale = Locale.getDefault()
    val format = groupedFormat?.takeIf { it.first == locale }?.second
        ?: NumberFormat.getIntegerInstance(locale).also { groupedFormat = locale to it }
    return format.format(this)
}

fun photos(n: Int) = if (n == 1) "1 photo" else "${n.grouped()} photos"

/** Photos and videos under one word, where the split doesn't matter. */
fun items(n: Int) = if (n == 1) "1 item" else "${n.grouped()} items"

fun files(n: Int) = if (n == 1) "1 file" else "${n.grouped()} files"

fun videos(n: Int) = if (n == 1) "1 video" else "${n.grouped()} videos"

/** "12 photos", "3 videos" or "12 photos and 3 videos". [videos] is the part of [total] that is videos */
fun media(total: Int, videos: Int) = when (videos) {
    0 -> photos(total)
    total -> videos(videos)
    else -> "${photos(total - videos)} and ${videos(videos)}"
}

/** 47,000 as 0:47 and 3,725,000 as 1:02:05. */
fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun selections(n: Int) = if (n == 1) "1 selection" else "${n.grouped()} selections"

/**
 * Shrinks text toward [minRatio] of its size before it gets cut, for places that can't grow. A size
 * only fits when no word got split, since a split word reads worse than small text
 */
fun shrinkToFit(style: TextStyle, minRatio: Float = 0.5f): TextAutoSize {
    val max = if (style.fontSize.isSp) style.fontSize.value else 16f
    return WholeWordsAutoSize(max * minRatio, max)
}

private const val STEP = 0.5f

/** StepBased keeps a big size by breaking "Keep" into "Kee" and "p", which still counts as fitting for it */
private data class WholeWordsAutoSize(val min: Float, val max: Float) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        fun fits(size: Float): Boolean {
            val layout = performLayout(constraints, text, size.sp)
            return !layout.didOverflowHeight && !layout.didOverflowWidth && !layout.splitsWord(text)
        }
        if (fits(max)) return max.sp
        // A smaller size overflows and splits less, so halving finds the largest step that fits
        var lo = 0
        var hi = ((max - min) / STEP).toInt() - 1
        var best = min
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val size = min + mid * STEP
            if (fits(size)) {
                best = size
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best.sp
    }
}

private fun TextLayoutResult.splitsWord(text: AnnotatedString): Boolean {
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-') {
            return true
        }
    }
    return false
}

/** One line cut in the middle, so the storage root and the folder stay. A long press shows the whole path. */
@Composable
fun PathText(path: String) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(path) } },
        state = rememberTooltipState(),
    ) {
        Text(path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
    }
}

/** A label for a button that can grow taller. It keeps its type role and wraps to a second line before it gets cut */
@Composable
fun ButtonLabel(text: String, style: TextStyle = LocalTextStyle.current) {
    Text(text, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
}

/**
 * A button label on one line, for places that can't grow. It drops to [smaller]'s size, never a size in
 * between, before it gets cut. Only the width counts: a large font's line height already overflows a
 * fixed-height button's padding without harm
 */
@Composable
fun FitLabel(text: String, smaller: TextStyle, style: TextStyle = LocalTextStyle.current) {
    Text(
        text,
        Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true),
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(smaller.fontSize, style.fontSize, (style.fontSize.value - smaller.fontSize.value).sp),
    )
}
