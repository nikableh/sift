package moe.nikableh.sift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import java.io.File

/** Source album, scan options and the list of destination folders. */
@Composable
fun SetupScreen(vm: SiftViewModel, snackbar: SnackbarHostState) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dests = vm.dests

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Folders") },
                subtitle = { Text("What to sort and where it can go") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                text = { Text("Add folders") },
                icon = { Icon(Icons.Rounded.Add, null) },
                onClick = { vm.open(Screen.Picker(forSource = false)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionLabel("Photos to sort") }
            item {
                val source = vm.source
                SegmentedListItem(
                    onClick = { vm.askPickSource() },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                    colors = segmentColors(),
                    leadingContent = { Icon(Icons.Rounded.PhotoLibrary, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    // Paths wrap instead of being cut, the whole path matters here
                    supportingContent = { Text(source ?: "Tap to choose an album") },
                ) { Text(source?.let { File(it).name } ?: "No album chosen") }
            }
            item {
                SegmentedListItem(
                    checked = vm.recursive,
                    onCheckedChange = { vm.changeRecursive(it) },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                    colors = segmentColors(),
                    trailingContent = { Switch(checked = vm.recursive, onCheckedChange = null) },
                    supportingContent = { Text("Destination folders inside it are left out") },
                ) { Text("Include subfolders") }
            }
            item {
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                    colors = segmentColors(),
                    supportingContent = { OrderButtons(vm) },
                ) { Text("Order") }
            }

            item { SectionLabel("Destination folders") }
            item {
                Text(
                    "Each folder becomes a button under the photo. One tap queues the photo for that folder and shows " +
                        "the next one. Nothing moves until you press Apply. For photos you want to delete, add a folder " +
                        "such as \"To delete\" and empty it later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
                )
            }
            itemsIndexed(dests, key = { _, d -> d.path }) { i, dest ->
                SegmentedListItem(
                    onClick = { vm.dialog = Dialog.RenameDest(dest) },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = dests.size),
                    colors = segmentColors(),
                    modifier = Modifier.animateItem(),
                    leadingContent = {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(folderColor(i)))
                    },
                    supportingContent = { Text(dest.path) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.moveDest(i, i - 1) }, enabled = i > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                            }
                            IconButton(onClick = { vm.moveDest(i, i + 1) }, enabled = i < dests.size - 1) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                            }
                            IconButton(onClick = { vm.askRemoveDest(dest) }) {
                                Icon(Icons.Rounded.Close, "Remove")
                            }
                        }
                    },
                ) { Text(dest.label) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Connected toggle buttons, the Expressive replacement for a segmented control. */
@Composable
private fun OrderButtons(vm: SiftViewModel) {
    val options = listOf(
        Store.ORDER_NEWEST to "Newest",
        Store.ORDER_OLDEST to "Oldest",
        Store.ORDER_NAME to "By name",
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, (value, label) ->
            ToggleButton(
                checked = vm.order == value,
                onCheckedChange = { vm.changeOrder(value) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                // One tone above the row, or the unselected options vanish into it
                colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { FitLabel(label) }
        }
    }
}
