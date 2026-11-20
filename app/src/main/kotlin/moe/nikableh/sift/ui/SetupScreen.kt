package moe.nikableh.sift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel

/** The destination folders, in the order of the sorting buttons. The albums are handled on Home. */
@Composable
fun SetupScreen(vm: SiftViewModel, snackbar: SnackbarHostState) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val dests = vm.dests
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
            TopAppBar(
                title = { Text("Folders", Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                text = { Text("Add folder") },
                icon = { Icon(Icons.Rounded.Add, null) },
                onClick = { vm.open(Screen.Picker(forSource = false)) },
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
                    // The FAB's 56dp, the 16dp Scaffold keeps under it and a gap, so the last row's buttons stay clear of it
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item(key = "destinations:about") {
                    Text(
                        "Each folder becomes a button under the photo. Pressing it queues the photo for that folder. " +
                            "Nothing moves until you review and press Move. For photos you want to delete, add a folder " +
                            "such as \"To delete\" and empty it later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = itemMotion().padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 16.dp).widthIn(max = 480.dp),
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
