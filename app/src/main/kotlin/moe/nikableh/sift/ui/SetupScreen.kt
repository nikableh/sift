package moe.nikableh.sift.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import java.io.File

/** The albums to sort, the scan options and the list of destination folders. */
@Composable
fun SetupScreen(vm: SiftViewModel, snackbar: SnackbarHostState) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val sources = vm.sources
    val active = vm.activeAlbums
    val sorted = vm.sortedAlbums
    val shown = vm.shownAlbums
    val dests = vm.dests
    var sortedOpen by rememberSaveable { mutableStateOf(false) }
    val drag = remember { DestDrag() }
    val gap = with(LocalDensity.current) { ListItemDefaults.SegmentedGap.roundToPx() }
    val motion = MaterialTheme.motionScheme
    val itemMotion: LazyItemScope.() -> Modifier = {
        Modifier.animateItem(
            fadeInSpec = motion.defaultEffectsSpec(),
            placementSpec = motion.defaultSpatialSpec(),
            fadeOutSpec = motion.fastEffectsSpec(),
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Folders", Modifier.semantics { heading() }) },
                subtitle = { Text("What to sort and where it can go") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        BoxWithConstraints {
            val start = padding.calculateStartPadding(direction)
            val end = padding.calculateEndPadding(direction)
            val margin = pageMargins(maxWidth - start - end)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = start + margin,
                    end = end + margin,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                // Each header carries the button that adds to its list
                item(key = "header:albums") {
                    SectionHeader("Albums to sort", "Add albums", itemMotion(), reading = vm.scanning) { vm.pickAlbums() }
                }
                if (sources.isEmpty()) {
                    item(key = "albums:none") {
                        SegmentedListItem(
                            onClick = { vm.pickAlbums() },
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                            colors = segmentColors(),
                            modifier = itemMotion(),
                            leadingContent = { Icon(Icons.Rounded.PhotoLibrary, null) },
                            supportingContent = { Text("Tap to choose what to sort") },
                        ) { Text("No album chosen") }
                    }
                }
                itemsIndexed(active, key = { _, path -> "album:$path" }) { i, path ->
                    val stat = vm.statOf(path)
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = i, count = active.size),
                        colors = segmentColors(),
                        modifier = itemMotion(),
                        leadingContent = { AlbumThumb(stat?.cover) },
                        supportingContent = {
                            Column {
                                PathText(path)
                                // An album added a moment ago has no counts until it has been read
                                if (stat != null && !vm.scanning) {
                                    // What is queued only gets a mention when there is something, or the line says one number twice
                                    val holds = media(stat.total, stat.videos)
                                    Text(if (stat.queued > 0) "$holds  ·  ${stat.queued.grouped()} queued" else holds)
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.askRemoveSource(path) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Remove ${File(path).name}") }
                        },
                    ) { Text(File(path).name) }
                }
                if (sorted.isNotEmpty()) {
                    // One folded row, however many albums are done, so the albums still to sort stay the list
                    if (active.isNotEmpty()) item(key = "sorted:gap") { Spacer(itemMotion().height(4.dp)) }
                    val rows = if (sortedOpen) sorted.size + 1 else 1
                    item(key = "sorted") {
                        val label = if (sortedOpen) "Hide the sorted albums" else "Show the sorted albums"
                        val turn by animateFloatAsState(if (sortedOpen) 180f else 0f, motion.fastSpatialSpec())
                        SegmentedListItem(
                            onClick = { sortedOpen = !sortedOpen },
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = rows),
                            colors = segmentColors(),
                            modifier = itemMotion().semantics {
                                onClick(label = label, action = null)
                                stateDescription = if (sortedOpen) "Expanded" else "Collapsed"
                            },
                            leadingContent = { AlbumThumb(null, Icons.Rounded.Done) },
                            supportingContent = { Text(sorted.joinToString(", ") { File(it).name }, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { vm.clearSorted() }) { Text("Clear") }
                                    // The row is already surfaceContainer, so the open fill goes one step higher to show
                                    val fill = if (sortedOpen) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                                    Box(Modifier.size(32.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(turn))
                                    }
                                }
                            },
                        ) { Text(if (sorted.size == 1) "1 album sorted" else "${sorted.size} albums sorted") }
                    }
                    if (sortedOpen) {
                        itemsIndexed(sorted, key = { _, path -> "album:$path" }) { i, path ->
                            SegmentedListItem(
                                shapes = ListItemDefaults.segmentedShapes(index = i + 1, count = rows),
                                colors = segmentColors(),
                                modifier = itemMotion(),
                                leadingContent = { AlbumThumb(null) },
                                supportingContent = { PathText(path) },
                                trailingContent = {
                                    IconButton(onClick = { vm.askRemoveSource(path) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Remove ${File(path).name}") }
                                },
                            ) { Text(File(path).name) }
                        }
                    }
                }
                item(key = "options:gap") { Spacer(itemMotion().height(8.dp)) }
                item(key = "options:subfolders") {
                    SegmentedListItem(
                        checked = vm.recursive,
                        onCheckedChange = { vm.changeRecursive(it) },
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                        colors = segmentColors(),
                        modifier = itemMotion().semantics { role = Role.Switch },
                        trailingContent = { Switch(checked = vm.recursive, onCheckedChange = null) },
                        supportingContent = {
                            Text(
                                if (shown.size > 1) "Of every album. Destination folders inside them are left out"
                                else "Destination folders inside it are left out"
                            )
                        },
                    ) { Text("Include subfolders") }
                }
                item(key = "options:order") {
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                        colors = segmentColors(),
                        modifier = itemMotion(),
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrderButtons(vm)
                                if (shown.size > 1) Text("All albums are sorted as one list")
                            }
                        },
                    ) { Text("Order") }
                }

                item(key = "header:destinations") {
                    SectionHeader("Destination folders", "Add destinations", itemMotion()) {
                        vm.open(Screen.Picker(forSource = false))
                    }
                }
                item(key = "destinations:about") {
                    Text(
                        "Each folder becomes a button under the photo. One tap queues the photo for that folder and shows " +
                            "the next one. Nothing moves until you press Move. For photos you want to delete, add a folder " +
                            "such as \"To delete\" and empty it later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = itemMotion().padding(start = 4.dp, end = 4.dp, bottom = 10.dp).widthIn(max = 480.dp),
                    )
                }
                itemsIndexed(dests, key = { _, d -> d.path }) { i, dest ->
                    val interactions = remember { MutableInteractionSource() }
                    var held by remember { mutableStateOf(false) }
                    val lifted = drag.path == dest.path
                    // The lifted row follows the finger, so only the rows it passes slide
                    val rowMotion = if (lifted) {
                        Modifier.animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            placementSpec = null,
                            fadeOutSpec = motion.fastEffectsSpec(),
                        )
                    } else {
                        itemMotion()
                    }
                    SegmentedListItem(
                        onClick = { vm.dialog = Dialog.RenameDest(dest) },
                        // The long click, not a drag detector, so letting go without moving doesn't open Rename
                        onLongClick = {
                            if (held) {
                                drag.start = DragInteraction.Start().also { interactions.tryEmit(it) }
                                drag.offset = 0f
                                drag.path = dest.path
                            }
                        },
                        interactionSource = interactions,
                        shapes = ListItemDefaults.segmentedShapes(index = i, count = dests.size),
                        colors = segmentColors(),
                        modifier = rowMotion
                            .onSizeChanged { drag.heights[dest.path] = it.height }
                            .graphicsLayer { translationY = if (drag.path == dest.path) drag.offset else 0f }
                            .pointerInput(dest.path) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    held = true
                                    try {
                                        do {
                                            val event = awaitPointerEvent()
                                            if (drag.path == dest.path) {
                                                event.changes.forEach {
                                                    drag.offset += it.positionChangeIgnoreConsumed().y
                                                    it.consume()
                                                }
                                                drag.follow(vm, dest.path, gap)
                                            }
                                        } while (event.changes.any { it.pressed })
                                    } finally {
                                        held = false
                                        if (drag.path == dest.path) {
                                            drag.start?.let { interactions.tryEmit(DragInteraction.Stop(it)) }
                                            drag.path = null
                                            drag.offset = 0f
                                        }
                                    }
                                }
                            },
                        leadingContent = {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(folderColor(i).dot()))
                        },
                        supportingContent = { PathText(dest.path) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.moveDest(i, i - 1) }, enabled = i > 0, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, "Move ${dest.label} up")
                                }
                                IconButton(onClick = { vm.moveDest(i, i + 1) }, enabled = i < dests.size - 1, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, "Move ${dest.label} down")
                                }
                                IconButton(onClick = { vm.askRemoveDest(dest) }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.Close, "Remove ${dest.label}")
                                }
                            }
                        },
                    ) { Text(dest.label) }
                }
            }
        }
    }
}

/** The destination row lifted by a long press, and how far the finger has moved it from its slot. */
private class DestDrag {
    var path by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    var start: DragInteraction.Start? = null
    val heights = mutableMapOf<String, Int>()

    /** Swaps the row with a neighbour once it has passed half of it, and keeps it under the finger. */
    fun follow(vm: SiftViewModel, path: String, gap: Int) {
        val list = vm.dests
        val at = list.indexOfFirst { it.path == path }
        if (offset > 0 && at < list.size - 1) {
            val step = (heights[list[at + 1].path] ?: return) + gap
            if (offset > step / 2f) {
                vm.moveDest(at, at + 1)
                offset -= step
            }
        } else if (offset < 0 && at > 0) {
            val step = (heights[list[at - 1].path] ?: return) + gap
            if (-offset > step / 2f) {
                vm.moveDest(at, at - 1)
                offset += step
            }
        }
    }
}

/**
 * A section's name with the button that adds to its list. [addLabel] is what a screen reader says for the button.
 * [reading] shows one indicator for the whole list while its rows are still being read.
 */
@Composable
private fun SectionHeader(text: String, addLabel: String, modifier: Modifier = Modifier, reading: Boolean = false, onAdd: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (reading) {
            // As tall as the button beside it, so the header keeps its height when the read ends
            LoadingIndicator(Modifier.size(ButtonDefaults.MinHeight).semantics { contentDescription = "Reading the albums" })
            Spacer(Modifier.width(8.dp))
        }
        FilledTonalButton(
            onClick = onAdd,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
            modifier = Modifier.semantics { contentDescription = addLabel },
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
            Text("Add")
        }
    }
}

/** One line cut in the middle, so the storage root and the folder stay. A long press shows the whole path. */
@Composable
private fun PathText(path: String) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(path) } },
        state = rememberTooltipState(),
    ) {
        Text(path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
    }
}

/** The album's first photo, or [placeholder] when there is none or the album hasn't been read. */
@Composable
private fun AlbumThumb(cover: File?, placeholder: ImageVector = Icons.Rounded.PhotoLibrary) {
    Box(
        Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(placeholder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
        Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, (value, label) ->
            ToggleButton(
                checked = vm.order == value,
                onCheckedChange = { vm.changeOrder(value) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                // outline rather than the outlined default outlineVariant, so each option reaches 3:1 on the row
                border = if (vm.order == value) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { FitLabel(label, MaterialTheme.typography.labelMedium) }
        }
    }
}
