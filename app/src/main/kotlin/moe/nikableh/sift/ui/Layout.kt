package moe.nikableh.sift.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where Material's compact windows end and medium ones begin */
private val MediumWidth = 600.dp

/** The same cap as About's card. Wider, a row's checkbox or count sits too far from its name */
private val MaxContentWidth = 560.dp

/**
 * The margin on each side of a page [width] wide: 16dp on compact windows and 24dp from medium up, grown to
 * centre a column no wider than [MaxContentWidth]. [width] leaves out the side insets, which come on top
 */
fun pageMargins(width: Dp): Dp {
    val margin = if (width < MediumWidth) 16.dp else 24.dp
    return margin + ((width - margin * 2 - MaxContentWidth) / 2).coerceAtLeast(0.dp)
}
