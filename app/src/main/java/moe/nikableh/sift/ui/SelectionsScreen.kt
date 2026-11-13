package moe.nikableh.sift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Selection
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store

/** Every photo waiting for Apply, with where it goes. A list, so long folder names have room. */
@Composable
fun SelectionsScreen(vm: SiftViewModel) {
    val groups = vm.selectionGroups
    val ready = !vm.busy && !vm.scanning

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selections") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    if (groups.isNotEmpty()) {
                        IconButton(onClick = { vm.dialog = Dialog.ConfirmClear }, enabled = ready) {
                            Icon(Icons.Rounded.ClearAll, "Clear selections")
                        }
                    }
                },
            )
        },
        bottomBar = { if (vm.selections > 0) ApplyBar(vm, ready) },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing is waiting for Apply",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 8.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                for (group in groups) {
                    val dest = group.dest
                    val rows = group.rows
                    val color = destColor(vm, dest)
                    item(key = "group:$dest", contentType = "group") { GroupHeader(dest, rows.size, color) }
                    items(rows, key = { it.file.path }, contentType = { "photo" }) { row ->
                        SelectionRow(row, color, ready) { vm.dropSelection(row) }
                    }
                }
            }
        }
    }
}

/** Skips and folders that are no longer buttons get no colour. */
private fun destColor(vm: SiftViewModel, dest: String?): Color? {
    val i = vm.dests.indexOfFirst { it.path == dest }
    return if (i >= 0) folderColor(i) else null
}

@Composable
private fun GroupHeader(dest: String?, count: Int, color: Color?) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp, end = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color)
            Spacer(Modifier.width(8.dp))
            Text(
                if (dest == null) "Skipped" else Store.labelFor(dest),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.weight(1f),
            )
            Text(count.grouped(), style = MaterialTheme.typography.titleMedium)
        }
        // The whole path wraps instead of being cut, it is what tells two folders apart
        Text(
            dest ?: "These stay where they are. Apply takes them out of the album's list.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Dot(color: Color?) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(color ?: MaterialTheme.colorScheme.outline))
}

@Composable
private fun SelectionRow(row: Selection, color: Color?, enabled: Boolean, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = row.file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color)
                Spacer(Modifier.width(6.dp))
                Text(
                    row.dest?.let { "To ${Store.labelFor(it)}" } ?: "Skipped, gets hidden",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(row.file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                row.file.parent ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
        IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Rounded.Close, "Remove from selections") }
    }
}

@Composable
private fun ApplyBar(vm: SiftViewModel, ready: Boolean) {
    val size = ButtonDefaults.MediumContainerHeight
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
        Box(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { vm.apply() },
                enabled = ready,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(size),
            ) {
                Icon(Icons.Rounded.DoneAll, null, Modifier.size(ButtonDefaults.iconSizeFor(size)))
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                FitLabel(applyLabel(vm), ButtonDefaults.textStyleFor(size))
            }
        }
    }
}
