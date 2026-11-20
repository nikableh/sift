package moe.nikableh.sift.ui

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.min
import kotlin.math.roundToInt
import moe.nikableh.sift.MediaView
import moe.nikableh.sift.Pages
import moe.nikableh.sift.Scanner
import moe.nikableh.sift.Selection
import moe.nikableh.sift.SelectionGroup
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import moe.nikableh.sift.VideoPlayer

/**
 * Ties the review viewer, which covers the screen, to the list under it: which row it shows, and
 * where that row's thumbnail is for the picture to grow out of and shrink back into
 */
class ReviewStage {
    /** The row on screen, or null with the viewer closed. */
    var row by mutableStateOf<Selection?>(null)
    var raised by mutableStateOf(false)

    /** Opening or open, and not yet on its way back. Unlike [raised] it turns false as the close starts */
    var open by mutableStateOf(false)

    /** Whether the details and the video bar show. A tap on the picture flips them */
    var controls by mutableStateOf(true)

    /** Thumbnails of the rows laid out right now. */
    val thumbs = HashMap<Selection, LayoutCoordinates>()
    var view: MediaView? = null
    var screen: LayoutCoordinates? = null

    /** Whether the row's picture or its failure is in, so there is something to open. */
    var ready = false
    private var placed = false
    private var opened = false

    fun reset() {
        placed = false
        opened = false
        ready = false
    }

    /** Straight to the view and not through state, which would show the picture in the wrong place for a frame. */
    fun place() {
        val view = view ?: return
        val screen = screen?.takeIf { it.isAttached } ?: return
        val thumb = row?.let { thumbs[it] }?.takeIf { it.isAttached }
        if (thumb != null) {
            val box = screen.localBoundingBoxOf(thumb, clipBounds = false)
            view.setSlot(box.left.roundToInt(), box.top.roundToInt(), box.right.roundToInt(), box.bottom.roundToInt())
        } else {
            // A row in a folded group or scrolled out of reach shrinks away into the middle
            val w = screen.size.width
            val h = screen.size.height
            val half = min(w, h) / 8
            view.setSlot(w / 2 - half, h / 2 - half, w / 2 + half, h / 2 + half)
        }
        placed = true
        openOnce()
    }

    /** Waits for both the thumbnail's place and something to show, or it would grow from a corner or open on nothing. */
    fun openOnce() {
        if (opened || !placed || !ready) return
        opened = true
        view?.pager?.open(evenEmpty = true)
    }

    fun close() {
        if (raised) view?.pager?.close() else row = null
    }
}

/** The row's key in the list and in the pager. One photo can wait for several folders, once for each. */
fun Selection.key() = "$dest\n${file.path}"

/** How long the video on screen has to stay there before it starts, so paging past one sets up no decoder. */
private const val VIDEO_START_MS = 200L

/**
 * Every queued file full screen on black, the way the sorting screen opens a photo. A sideways
 * swipe goes on to the next file, past the end of its folder into the next one, a tap hides the
 * details, and a drag up or down closes it
 */
@Composable
fun ReviewViewer(vm: SiftViewModel, groups: List<SelectionGroup>, stage: ReviewStage, player: VideoPlayer) {
    val row = stage.row ?: return
    val rows = remember(groups) { groups.flatMap { it.rows } }
    val files = remember(rows) { rows.map { it.file } }
    val at = remember(rows, row) { rows.indexOf(row) }
    val pages = remember { Pages("review") }
    DisposableEffect(pages) { onDispose { pages.clear() } }

    // A Move or a clear took the row away
    if (at < 0) {
        LaunchedEffect(Unit) { stage.row = null }
        return
    }
    LaunchedEffect(files, at) { pages.show(files, at) }
    val go by rememberUpdatedState { dir: Int ->
        val to = at + dir
        if (to in rows.indices) {
            stage.row = rows[to]
            // Its thumbnail may never lay out again, folded or out of the list, and closing must not shrink into the last one
            stage.place()
            // Before the next frame, which must hand the pager the bitmap it just slid in
            pages.show(files, to)
        }
    }

    val video = Scanner.isVideo(row.file.name)
    val path = row.file.path
    val mine = player.path == path
    VideoPlayback(player, path.takeIf { video && stage.open }, vm.videoMuted, VIDEO_START_MS)
    BackHandler { stage.close() }
    if (stage.raised && !isSystemInDarkTheme()) PaleBarIcons()

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                stage.reset()
                MediaView(context).apply {
                    // The box over it carries the description
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    pager.gallery = true
                    pager.onSwipe = { go(it) }
                    pager.onZoomIn = { pages.loadSharper() }
                    pager.onTap = { stage.controls = !stage.controls }
                    pager.onRaise = {
                        stage.raised = it
                        if (!it) stage.row = null
                    }
                    pager.onOpen = {
                        stage.open = it
                        if (it) stage.controls = true
                    }
                    player.attach(surface)
                    stage.view = this
                }
            },
            update = {
                val shown = rows.getOrNull(pages.shown)
                val bitmap = pages.bitmap
                it.pager.show(
                    shown?.key(), bitmap, pages.prev, pages.next,
                    canPrev = pages.shown > 0,
                    canNext = pages.shown in 0 until rows.size - 1,
                    animation = pages.animation,
                )
                stage.ready = shown === row && (bitmap != null || pages.failed)
                stage.openOnce()
                // The still frame's shape stands in until the decoder reports the video's own
                val aspect = player.aspect.takeIf { a -> mine && a > 0f } ?: bitmap?.let { b -> b.width / b.height.toFloat() } ?: 0f
                it.video(aspect, show = video && mine && player.showing)
            },
            onRelease = {
                player.detach(it.surface)
                stage.view = null
                // Torn down without the close, when a Move took the row away, and the next open starts closed
                stage.raised = false
                stage.open = false
            },
            modifier = Modifier.fillMaxSize().onGloballyPositioned {
                stage.screen = it
                stage.place()
            },
        )

        if (stage.open) {
            val failed = when {
                video && mine && player.failed -> "Can't play this video"
                pages.failed && !(video && mine && player.showing) -> "Can't display ${row.file.name}"
                else -> null
            }
            // The picture is a plain view that TalkBack can't see, so the space it shows through names it
            val kind = if (video) "Video" else if (pages.animation != null) "Animated GIF" else "Photo"
            val turns = listOfNotNull(
                if (at > 0) CustomAccessibilityAction("Previous") { go(-1); true } else null,
                if (at < rows.size - 1) CustomAccessibilityAction("Next") { go(1); true } else null,
            )
            Box(
                Modifier.fillMaxSize().semantics {
                    paneTitle = if (video) "Video viewer" else "Photo viewer"
                    contentDescription = "$kind ${(at + 1).grouped()} of ${rows.size.grouped()}, going to ${Store.labelFor(row.dest)}"
                    customActions = turns
                    onClick(label = if (stage.controls) "Hide details" else "Show details") { stage.view?.pager?.performClick() ?: false }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (failed != null) {
                    Text(
                        failed,
                        Modifier.padding(32.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        val motion = MaterialTheme.motionScheme
        // Reduced motion asks for a plain fade, never a slide
        val animationsOn = rememberAnimationsOn()
        val shown = stage.open && stage.controls
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = if (animationsOn) slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec()) else fadeIn(motion.fastEffectsSpec()),
            exit = if (animationsOn) slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()) else fadeOut(motion.fastEffectsSpec()),
        ) {
            Details(vm, row, at, rows.size) { stage.close() }
        }
        AnimatedVisibility(
            visible = shown && video && !(mine && player.failed),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (animationsOn) slideInVertically(motion.defaultSpatialSpec()) { it } else fadeIn(motion.fastEffectsSpec()),
            exit = if (animationsOn) slideOutVertically(motion.fastSpatialSpec()) { it } else fadeOut(motion.fastEffectsSpec()),
        ) {
            VideoBar(
                player,
                vm.videoMuted,
                vm::changeMuted,
                // The insets and the offset count toward the height it slides by, so it leaves the screen entirely
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, end = 16.dp, bottom = FloatingToolbarDefaults.ScreenOffset)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    // The toolbar takes no touches between its buttons, and one there would reach the video and hide the bar
                    .pointerInput(Unit) {},
            )
        }
    }
}

/** The name, the place and the folder of the file on screen, in full, over a shade that keeps them readable on any picture. */
@Composable
private fun Details(vm: SiftViewModel, row: Selection, at: Int, count: Int, onClose: () -> Unit) {
    // With one album the place is only the folders under it, and a file at its top has none
    val place = vm.placeOf(row.file) ?: row.file.parentFile?.name
    val i = vm.dests.indexOfFirst { it.path == row.dest }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                // The shade runs on below the text and lets touches through to the picture there
                .padding(bottom = 32.dp)
                // Touches between the texts would reach the picture and hide the details
                .pointerInput(Unit) {}
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close") }
            Column(
                Modifier.weight(1f).padding(start = 4.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(row.file.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (place != null) {
                    Text(place, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
                Surface(
                    Modifier.padding(top = 4.dp),
                    shape = CircleShape,
                    color = if (i >= 0) folderColor(i).tile() else MaterialTheme.colorScheme.inverseSurface,
                    contentColor = if (i >= 0) folderColor(i).ink() else MaterialTheme.colorScheme.inverseOnSurface,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
                        Text(
                            Store.labelFor(row.dest),
                            Modifier.semantics { contentDescription = "Going to ${Store.labelFor(row.dest)}" },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Text(
                "${(at + 1).grouped()} / ${count.grouped()}",
                Modifier.padding(start = 12.dp, top = 14.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}
