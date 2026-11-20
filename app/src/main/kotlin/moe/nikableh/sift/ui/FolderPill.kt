package moe.nikableh.sift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Names a folder a file is queued to go to, in that folder's colour. [colorIndex] is the folder's place among
 * the destinations, or -1 for one that is no longer among them. With [onClear] a tap takes the folder off.
 */
@Composable
fun FolderPill(label: String, colorIndex: Int, modifier: Modifier = Modifier, onClear: (() -> Unit)? = null) {
    val color = if (colorIndex >= 0) folderColor(colorIndex).tile() else MaterialTheme.colorScheme.inverseSurface
    val contentColor = if (colorIndex >= 0) folderColor(colorIndex).ink() else MaterialTheme.colorScheme.inverseOnSurface
    // Close to the folder buttons' corners, which a round end would not match
    val shape = MaterialTheme.shapes.medium
    val content = @Composable {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (onClear != null) Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp))
        }
    }
    if (onClear != null) {
        Surface(
            onClick = onClear,
            modifier = modifier.semantics {
                role = Role.Button
                onClick(label = "Take off this photo", action = null)
            },
            shape = shape,
            color = color,
            contentColor = contentColor,
            // The pill sits on a picture, which can be any colour, so the shadow gives it its edge
            shadowElevation = 6.dp,
            content = content,
        )
    } else {
        // A pill that takes no tap must not show up in TalkBack as a disabled button
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            shadowElevation = 6.dp,
            content = content,
        )
    }
}
