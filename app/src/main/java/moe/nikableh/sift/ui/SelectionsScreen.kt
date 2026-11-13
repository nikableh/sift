package moe.nikableh.sift.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Selection
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import java.io.File
import androidx.compose.ui.window.Dialog as WindowDialog

/** Every photo waiting for Apply, with where it goes. A list, so long folder names have room. */
@Composable
fun SelectionsScreen(vm: SiftViewModel) {
    val groups = vm.selectionGroups
    val ready = !vm.busy && !vm.scanning
    var collapsed by rememberSaveable(stateSaver = KeySetSaver) { mutableStateOf(emptySet<String>()) }
    val allCollapsed = groups.all { groupKey(it.dest) in collapsed }
    var preview by remember { mutableStateOf<File?>(null) }

    preview?.let { PhotoPreview(it) { preview = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selections") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    if (groups.isNotEmpty()) {
                        IconButton(onClick = {
                            collapsed = if (allCollapsed) emptySet() else groups.mapTo(HashSet()) { groupKey(it.dest) }
                        }) {
                            if (allCollapsed) Icon(Icons.Rounded.UnfoldMore, "Expand all")
                            else Icon(Icons.Rounded.UnfoldLess, "Collapse all")
                        }
                        IconButton(onClick = { vm.dialog = Dialog.ConfirmClear }, enabled = ready) {
                            Icon(Icons.Rounded.ClearAll, "Clear selections")
                        }
                    }
                },
            )
        },
        floatingActionButton = { ApplyFab(vm, visible = vm.selections > 0 && ready) },
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
                    // The FAB floats over the list, so the last row needs room to scroll out from under it
                    bottom = padding.calculateBottomPadding() + FabHeight + 32.dp,
                ),
                // Not content padding: a sticky header pins to the viewport's edge, which would be under the app bar
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            ) {
                for (group in groups) {
                    val dest = group.dest
                    val rows = group.rows
                    val color = destColor(vm, dest)
                    val key = groupKey(dest)
                    val folded = key in collapsed
                    stickyHeader(key = "group:$dest", contentType = "group") {
                        GroupHeader(dest, rows.size, color, folded) {
                            collapsed = if (folded) collapsed - key else collapsed + key
                        }
                    }
                    if (!folded) items(rows, key = { it.file.path }, contentType = { "photo" }) { row ->
                        SelectionRow(row, vm.source, ready, onPreview = { preview = row.file }) { vm.dropSelection(row) }
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

/** Folder paths are never empty, so the empty string can stand for the skipped group. */
private fun groupKey(dest: String?) = dest ?: ""

private val KeySetSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })

@Composable
private fun GroupHeader(dest: String?, count: Int, color: Color?, collapsed: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    Column(
        Modifier.fillMaxWidth()
            // Opaque, so rows don't show through while the header is pinned
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (collapsed) "Expand" else "Collapse", onClick = onToggle)
            .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color)
            Spacer(Modifier.width(8.dp))
            Text(
                if (dest == null) "Skipped" else Store.labelFor(dest),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.weight(1f),
            )
            Text(count.grouped(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(turn))
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
fun Dot(color: Color?) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(color ?: MaterialTheme.colorScheme.outline))
}

@Composable
private fun SelectionRow(
    row: Selection,
    source: String?,
    enabled: Boolean,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = row.file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClickLabel = "Preview photo", role = Role.Image, onClick = onPreview),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            // Only a recursive scan puts photos outside the album's own folder
            val parent = row.file.parent
            if (parent != null && parent != source) Text(
                if (source != null && parent.startsWith("$source/")) parent.substring(source.length + 1) else parent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
        IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Rounded.Close, "Remove from selections") }
    }
}

/** The whole photo fitted to the screen, so a thumbnail's crop doesn't hide what is being moved. */
@Composable
private fun PhotoPreview(file: File, onDismiss: () -> Unit) {
    WindowDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Height of the small extended FAB, which material3 doesn't expose. */
private val FabHeight = 56.dp

/** FABs have no disabled state, so it steps away while a scan or a move is running. */
@Composable
private fun ApplyFab(vm: SiftViewModel, visible: Boolean) {
    AnimatedVisibility(visible, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        SmallExtendedFloatingActionButton(
            text = { FitLabel(if (vm.queued > 0) "Apply ${vm.queued.grouped()}" else "Apply") },
            icon = { Icon(Icons.Rounded.DoneAll, null) },
            onClick = { vm.apply() },
            containerColor = MaterialTheme.colorScheme.primary,
        )
    }
}
