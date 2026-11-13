package moe.nikableh.sift.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.ImageLoader
import moe.nikableh.sift.Photo
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.ZoomImageView
import java.io.File
import kotlin.math.roundToInt

/** The skips a Move took out of the album, as a grid. A tap opens one, where its skip can be undone. */
@Composable
fun SkippedScreen(vm: SiftViewModel) {
    val photos = vm.hiddenPhotos
    val grid = rememberLazyGridState()
    var viewing by rememberSaveable { mutableIntStateOf(-1) }
    // Undoing a skip shortens the list under the viewer
    val at = viewing.coerceAtMost(photos.lastIndex)
    SideEffect { viewing = at }

    // The grid stays under the viewer, so closing has nothing to build and a lifted photo shows it
    Box(Modifier.fillMaxSize()) {
        SkippedGrid(vm, photos, grid, covered = at >= 0) { viewing = it }
        if (at >= 0) {
            // Swiping in the viewer can end far from the thumbnail that opened it
            LaunchedEffect(at) {
                if (grid.layoutInfo.visibleItemsInfo.none { it.index == at }) grid.requestScrollToItem(at)
            }
            PhotoViewer(vm, photos, at, onMove = { viewing = it }) { viewing = -1 }
        }
    }
}

private val Gap = 4.dp
private val MinCell = 104.dp

@Composable
private fun SkippedGrid(
    vm: SiftViewModel,
    photos: List<Photo>,
    grid: LazyGridState,
    covered: Boolean,
    onOpen: (Int) -> Unit,
) {
    val ready = !vm.busy && !vm.scanning
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(grid)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            // Under the viewer it is only a backdrop, which a screen reader shouldn't walk into
            .then(if (covered) Modifier.clearAndSetSemantics {} else Modifier),
        topBar = {
            TopAppBar(
                title = { Text("Skipped") },
                subtitle = {
                    val album = File(vm.source ?: "").name
                    Text(
                        if (photos.size == 1) "1 photo in $album" else "${photos.size.grouped()} photos in $album",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { vm.dialog = Dialog.ConfirmUnhide(photos.size) },
                        enabled = ready && photos.isNotEmpty(),
                    ) { Icon(Icons.Rounded.RestartAlt, "Undo all skips") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (vm.scanning) "Reading the album" else "No skipped photos",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        val direction = LocalLayoutDirection.current
        val start = padding.calculateStartPadding(direction)
        val end = padding.calculateEndPadding(direction)
        val bottom = padding.calculateBottomPadding()
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            // Material's window margins are 16dp on a compact width and 24dp on an expanded one
            val margin = if (maxWidth < 600.dp) 16.dp else 24.dp
            val room = maxWidth - start - end - margin * 2
            val columns = ((room + Gap) / (MinCell + Gap)).toInt().coerceAtLeast(3)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = grid,
                contentPadding = PaddingValues(start = start + margin, end = end + margin, top = Gap, bottom = bottom + margin),
                horizontalArrangement = Arrangement.spacedBy(Gap),
                verticalArrangement = Arrangement.spacedBy(Gap),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(photos.size, key = { photos[it].path }, contentType = { "photo" }) { i ->
                    Thumbnail(photos[i].file) { onOpen(i) }
                }
            }
            ScrollHandle(grid, columns, Modifier.align(Alignment.TopEnd).padding(end = end, bottom = bottom))
        }
    }
}

@Composable
private fun Thumbnail(file: File, onOpen: () -> Unit) {
    // Coil decodes at the cell's size off the main thread and drops the work when the cell scrolls away
    AsyncImage(
        model = file,
        contentDescription = file.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = "Open photo", role = Role.Image, onClick = onOpen),
    )
}

/** Where a grid of equal rows is scrolled to, in pixels, and how far it can go. */
private class Scrolled(val stride: Int, val at: Int, val max: Int)

/** One visible row gives the length of the whole grid, since every row has its height. Null when nothing scrolls. */
private fun LazyGridState.scrolled(columns: Int): Scrolled? {
    val info = layoutInfo
    val first = info.visibleItemsInfo.firstOrNull() ?: return null
    val stride = first.size.height + info.mainAxisItemSpacing
    val rows = (info.totalItemsCount + columns - 1) / columns
    val length = rows * stride - info.mainAxisItemSpacing + info.beforeContentPadding + info.afterContentPadding
    val max = length - info.viewportSize.height
    if (max <= 0) return null
    return Scrolled(stride, firstVisibleItemIndex / columns * stride + firstVisibleItemScrollOffset, max)
}

private val HandleSize = 48.dp

/** Touch room above and below the tab, which a thumb moving fast misses by a few pixels. */
private val HandleReach = 17.dp

/**
 * Shows up at the edge while the grid moves. Dragging it scrolls in proportion, so the end of
 * 10,000 photos is one drag away. Built from Material colour and shape roles, there being no
 * ready component for it in material3.
 */
@Composable
private fun ScrollHandle(grid: LazyGridState, columns: Int, modifier: Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var track by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    val handle = with(LocalDensity.current) { HandleSize.roundToPx() }
    val reach = with(LocalDensity.current) { HandleReach.roundToPx() }
    val moving = grid.isScrollInProgress || dragging
    LaunchedEffect(moving) {
        if (!moving) delay(1500)
        shown = moving
    }

    Box(modifier.fillMaxHeight().onSizeChanged { track = (it.height - handle).coerceAtLeast(0) }) {
        AnimatedVisibility(
            visible = shown && (grid.canScrollForward || grid.canScrollBackward),
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            // Read while placing, so a scroll moves the handle without recomposing anything
            modifier = Modifier.align(Alignment.TopEnd).offset {
                val y = if (dragging) dragY else grid.scrolled(columns)?.let { track * it.at.toFloat() / it.max } ?: 0f
                // The track is measured for the tab alone, so the touch room hangs over its ends
                IntOffset(0, y.roundToInt() - reach)
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Touch exploration scrolls the grid itself
                // The handle sits where the system's back swipe starts
                modifier = Modifier.clearAndSetSemantics {}.systemGestureExclusion().draggable(
                    state = rememberDraggableState { delta ->
                        val s = grid.scrolled(columns)
                        if (s != null && track > 0) {
                            // Here and not in onDragStarted, which runs in a coroutine and can come after the first delta
                            if (!dragging) {
                                dragY = track * s.at.toFloat() / s.max
                                dragging = true
                            }
                            dragY = (dragY + delta).coerceIn(0f, track.toFloat())
                            val to = (dragY / track * s.max).roundToInt()
                            // Not animated and not a scroll gesture, so every drag event is one cheap remeasure
                            grid.requestScrollToItem(to / s.stride * columns, to % s.stride)
                        }
                    },
                    orientation = Orientation.Vertical,
                    startDragImmediately = true,
                    onDragStopped = { dragging = false },
                ),
            ) {
                if (dragging) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ) {
                        Text(
                            "${(grid.firstVisibleItemIndex + 1).grouped()} / ${grid.layoutInfo.totalItemsCount.grouped()}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                // A target wider and taller than the tab, which sits against the edge
                Box(
                    Modifier.size(width = HandleSize, height = HandleSize + HandleReach * 2),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(width = 32.dp, height = HandleSize),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}

/** One skipped photo on the whole screen, with zoom, a swipe to its neighbours and a swipe up or down to close. */
@Composable
private fun PhotoViewer(vm: SiftViewModel, photos: List<Photo>, at: Int, onMove: (Int) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val photo = photos[at]
    val prev = photos.getOrNull(at - 1)
    val next = photos.getOrNull(at + 1)
    val bitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    var failed by remember(photo.path) { mutableStateOf(false) }
    val shown by rememberUpdatedState(photo.path)
    // Read only while drawing, so lifting the photo fades the viewer without recomposing it
    val lift = remember { mutableFloatStateOf(0f) }
    val fade = Modifier.graphicsLayer { alpha = 1f - lift.floatValue }
    val surface = MaterialTheme.colorScheme.surface

    BackHandler(onBack = onClose)
    DisposableEffect(prev?.path, photo.path, next?.path) {
        var live = true
        val around = listOfNotNull(photo, next, prev)
        // The loader drops queued work for any other photo, so a fast run of swipes decodes only where it ends
        ImageLoader.wanted = around.mapTo(HashSet()) { it.path }
        bitmaps.keys.retainAll(ImageLoader.wanted)
        for (p in around) {
            ImageLoader.load(p.file) { bitmap ->
                if (!live) return@load
                if (bitmap != null) bitmaps[p.path] = bitmap else if (p === photo) failed = true
            }
        }
        onDispose { live = false }
    }

    Scaffold(
        // Its own background instead of the scaffold's, which can't fade. The empty pointer input keeps taps off the grid
        modifier = Modifier.drawBehind { drawRect(surface, alpha = 1f - lift.floatValue) }.pointerInput(Unit) {},
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("${(at + 1).grouped()} / ${photos.size.grouped()}") },
                subtitle = { Text(photo.file.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to the grid") }
                },
                actions = {
                    IconButton(onClick = { sharePhoto(context, photo.file) }) { Icon(Icons.Rounded.Share, "Share") }
                },
            )
        },
        floatingActionButton = {
            // FABs have no disabled state, so it steps away while a scan or a move is running
            AnimatedVisibility(!vm.busy && !vm.scanning, fade, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                SmallExtendedFloatingActionButton(
                    text = { FitLabel("Undo skip") },
                    icon = { Icon(Icons.AutoMirrored.Rounded.Undo, null) },
                    onClick = { vm.unhide(photo) },
                    containerColor = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ZoomImageView(it) },
                update = { view ->
                    view.onSwipe = { onMove(at + it) }
                    view.onDismiss = onClose
                    view.onLift = { lift.floatValue = it }
                    view.onZoomIn = {
                        ImageLoader.loadFull(photo.file) { sharper ->
                            if (sharper != null && shown == photo.path) bitmaps[photo.path] = sharper
                        }
                    }
                    view.show(
                        photo.path, bitmaps[photo.path], prev?.let { bitmaps[it.path] }, next?.let { bitmaps[it.path] },
                        canPrev = prev != null,
                        canNext = next != null,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (failed) {
                Text(
                    "Can't display ${photo.file.name}",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
        }
    }
}
