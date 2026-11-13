package moe.nikableh.sift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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

/**
 * Short parts joined by dots. A part moves to the next line as a whole and only wraps inside when
 * it is wider than a line on its own, so "131,354 to move" never splits as "131,354 to / move"
 */
@Composable
fun DotParts(parts: List<String>, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    FlowRow(modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { i, part ->
            Text(if (i < parts.lastIndex) "$part  ·" else part, style = style)
        }
    }
}

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

/**
 * A button label on one line that shrinks to the width it gets. Only the width counts: a large
 * font's line height already overflows a fixed-height button's padding without harm
 */
@Composable
fun FitLabel(text: String, style: TextStyle = LocalTextStyle.current) {
    Text(
        text,
        Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true),
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = shrinkToFit(style),
    )
}
