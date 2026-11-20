package moe.nikableh.sift.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import moe.nikableh.sift.Dest
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Gate
import moe.nikableh.sift.MediaView
import moe.nikableh.sift.Scanner
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import moe.nikableh.sift.VideoPlayer

/**
 * The sorting screen: one photo or video, one tap on a folder button queues it and shows the next.
 * [settled] is false while the screen is still sliding in or out
 */
@Composable
fun SortScreen(vm: SiftViewModel, snackbar: SnackbarHostState, settled: Boolean) {
    val context = LocalContext.current
    // Held above the two layouts, so turning the phone doesn't start the video over
    val player = remember { VideoPlayer(context.applicationContext) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    // Reading, Empty, End and None all show the sorter, so a read doesn't slide it in over itself
    val step = when (vm.gate) {
        Gate.Permission -> Step.Access
        Gate.Setup -> Step.Welcome
        else -> Step.Content
    }
    val steps = updateTransition(step, label = "step")
    Playback(vm, player, settled && !steps.isRunning)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        PaneSwap(step, steps) { shown ->
            when (shown) {
                Step.Access -> AccessScreen(vm)

                Step.Welcome -> Welcome(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Pick your folders",
                    body = "Choose the albums to sort and the folders their photos can go to.",
                    action = "Set up folders",
                ) { vm.open(Screen.Setup) }

                Step.Content -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    // From the window width, not the orientation, so a portrait tablet gets the side panel
                    // and a narrow landscape window doesn't
                    val wide = maxWidth >= 600.dp
                    // Under the header and the folder buttons and as large as the screen, so an open photo
                    // can come up over them. Shared by the two layouts, so turning the phone keeps the zoom
                    val stage = remember { Stage() }
                    Media(vm, player, stage)
                    if (wide) {
                        // The fixed pane is 360dp at expanded window widths
                        val panelWidth = if (maxWidth >= 840.dp) 360.dp else 280.dp
                        Row(Modifier.fillMaxSize()) {
                            Viewer(vm, player, stage, snackbar, landscape = true, Modifier.weight(1f).fillMaxHeight())
                            Panel(vm, landscape = true, Modifier.width(panelWidth).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Viewer(vm, player, stage, snackbar, landscape = false, Modifier.weight(1f).fillMaxWidth())
                            Panel(vm, landscape = false, Modifier.fillMaxWidth())
                        }
                    }
                    // Over the open video, which draws over everything above
                    OpenVideoBar(vm, player, stage, Modifier.align(Alignment.BottomCenter).zIndex(2f))
                }
            }
        }
    }
}

/** How long the video on screen has to stay there before it starts. */
private const val VIDEO_START_MS = 200L

/** Starts the video on screen a moment after landing on it, and lets go of it when the screen moves on. */
@Composable
private fun Playback(vm: SiftViewModel, player: VideoPlayer, settled: Boolean) {
    val path = vm.current?.takeIf { it.video && vm.gate == Gate.None && !vm.busy && settled }?.path
    // The delay means tapping through an album at speed never gets as far as setting up a decoder
    VideoPlayback(player, path, vm.videoMuted, VIDEO_START_MS)
}

fun sharePhoto(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
        ?: if (Scanner.isVideo(file.name)) "video/*" else "image/*"
    val send = Intent(Intent.ACTION_SEND).apply {
        setType(type)
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData carries the read grant through the chooser and lets it show a preview
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
fun Welcome(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(128.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            BigButton(action, onAction)
        }
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(size),
        modifier = Modifier.heightIn(size),
        contentPadding = ButtonDefaults.contentPaddingFor(size),
    ) {
        Text(label, style = ButtonDefaults.textStyleFor(size))
    }
}

/** Ties the photo's view, which covers the screen, to the room the header and the folder buttons leave for it. */
private class Stage {
    var view: MediaView? = null
    var screen: LayoutCoordinates? = null
    var slot: LayoutCoordinates? = null
    var raised by mutableStateOf(false)

    /** Opening or open, and not yet on its way back. Unlike [raised] it turns false as the close starts */
    var open by mutableStateOf(false)

    /** Whether the open video shows its bar. A tap on the video flips it */
    var controls by mutableStateOf(true)

    /** Straight to the view and not through state, which would show the photo in the wrong place for a frame. */
    fun place() {
        val screen = screen?.takeIf { it.isAttached } ?: return
        val slot = slot?.takeIf { it.isAttached } ?: return
        val box = screen.localBoundingBoxOf(slot, clipBounds = false)
        view?.setSlot(box.left.roundToInt(), box.top.roundToInt(), box.right.roundToInt(), box.bottom.roundToInt())
    }
}

@Composable
private fun Media(vm: SiftViewModel, player: VideoPlayer, stage: Stage) {
    val gate = vm.gate
    val video = gate == Gate.None && vm.current?.video == true
    val raised = stage.raised
    // The header's back button is under the open photo, and leaving the screen from there would come as a surprise
    BackHandler(enabled = raised) { stage.view?.pager?.close() }
    if (raised && !isSystemInDarkTheme()) PaleBarIcons()

    AndroidView(
        factory = { context ->
            MediaView(context).apply {
                // The slot over it in Photo carries the description
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                pager.onSwipe = { vm.go(it) }
                pager.onZoomIn = { vm.loadSharper() }
                // An open photo has no bar, so flipping it there changes nothing anyone sees
                pager.onTap = { stage.controls = !stage.controls }
                pager.onRaise = { stage.raised = it }
                pager.onOpen = {
                    stage.open = it
                    if (it) stage.controls = true
                }
                player.attach(surface)
                stage.view = this
                stage.place()
            }
        },
        update = {
            val pages = !vm.busy && (gate == Gate.None || gate == Gate.End)
            val bitmap = vm.bitmap
            it.pager.show(
                vm.bitmapPath, bitmap, vm.prevBitmap, vm.nextBitmap,
                canPrev = pages && vm.index > 0,
                canNext = pages && vm.index < vm.photos.size,
                animation = vm.animation,
            )
            // If the page under an open photo or video turns into a status card, it belongs under the header
            if (gate != Gate.None) it.pager.close()
            // The still frame's shape stands in until the decoder reports the video's own
            val aspect = player.aspect.takeIf { a -> a > 0f } ?: bitmap?.let { b -> b.width / b.height.toFloat() } ?: 0f
            it.video(aspect, show = video && player.showing && player.path == vm.bitmapPath)
        },
        onRelease = {
            player.detach(it.surface)
            stage.view = null
        },
        modifier = Modifier
            .fillMaxSize()
            // An open photo draws over the header and the buttons, and takes the touches meant for them
            .zIndex(if (raised) 1f else 0f)
            .onGloballyPositioned {
                stage.screen = it
                stage.place()
            },
    )
}

/** The light theme's dark clock and status icons can't be seen on the black behind an open photo. */
@Composable
fun PaleBarIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window) {
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            // Read now, because a switch to the dark theme is one of the ways to get here
            val night = (view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            bars.isAppearanceLightStatusBars = !night
            bars.isAppearanceLightNavigationBars = !night
        }
    }
}

@Composable
private fun Viewer(vm: SiftViewModel, player: VideoPlayer, stage: Stage, snackbar: SnackbarHostState, landscape: Boolean, modifier: Modifier) {
    val folder = vm.albumsLabel

    Column(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))) {
        TopBar(vm, folder)
        Photo(
            vm, player, stage, snackbar, folder, landscape,
            Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                stage.slot = it
                stage.place()
            },
        )
    }
}

/** What lies over the photo. The photo itself is [Media], under all of this. */
@Composable
private fun Photo(
    vm: SiftViewModel,
    player: VideoPlayer,
    stage: Stage,
    snackbar: SnackbarHostState,
    folder: String,
    landscape: Boolean,
    modifier: Modifier,
) {
    val view = LocalView.current
    val gate = vm.gate
    val video = gate == Gate.None && vm.current?.video == true
    val dayFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val current = vm.current
    // The picture itself is a plain view that TalkBack can't see, so the space it shows through names it
    val description = if (gate == Gate.None && current != null) {
        val kind = when {
            current.video -> vm.duration?.let { "Video, ${clock(it)}," } ?: "Video"
            vm.animation != null -> "Animated GIF"
            else -> "Photo"
        }
        "$kind ${(vm.index + 1).grouped()} of ${vm.photos.size.grouped()}, " + dayFormat.format(Date(current.mtime))
    } else {
        null
    }
    // The same pages a swipe may turn to. An open photo takes no swipes, so it offers no actions either
    val raised = stage.raised
    val pages = !raised && !vm.busy && (gate == Gate.None || gate == Gate.End)
    val turns = listOfNotNull(
        if (pages && vm.index > 0) CustomAccessibilityAction("Previous") { vm.go(-1); true } else null,
        if (pages && vm.index < vm.photos.size) CustomAccessibilityAction("Next") { vm.go(1); true } else null,
    )
    val tapLabel = when {
        gate != Gate.None || current == null -> null
        raised -> if (video && stage.open) (if (stage.controls) "Hide controls" else "Show controls") else null
        vm.bitmap == null -> null
        else -> "Open"
    }

    Box(
        modifier.semantics {
            if (description != null) contentDescription = description
            if (turns.isNotEmpty()) customActions = turns
            if (tapLabel != null) onClick(label = tapLabel) { stage.view?.pager?.performClick() ?: false }
            if (raised) paneTitle = if (video) "Video viewer" else "Photo viewer"
        },
    ) {
        when (gate) {
            Gate.Reading -> Status(Modifier.align(Alignment.Center), "Reading $folder") {
                Loading(Modifier.semantics { contentDescription = "Reading $folder" })
            }
            Gate.Empty -> Status(
                Modifier.align(Alignment.Center),
                if (vm.allSorted) "All sorted" else "No photos or videos in $folder",
                if (vm.allSorted) "Nothing is left in ${if (vm.sources.size == 1) folder else "these albums"}" else null,
            ) {
                BigButton("Add albums") { vm.pickAlbums() }
            }
            Gate.End -> Status(
                Modifier.align(Alignment.Center),
                "That was the last one",
                "${vm.queuedHere.grouped()} queued to move, ${vm.leftHere.grouped()} left",
            ) {
                if (vm.queued > 0) BigButton(reviewLabel(vm)) { vm.reviewSelections() }
            }
            else -> when {
                video && player.failed -> Status(Modifier.align(Alignment.Center), "Can't play this video", "You can still sort it with the folder buttons") {}
                // A video without a still frame can play all the same, and then it shows itself
                vm.decodeFailed && !(video && player.showing) -> {
                    Status(Modifier.align(Alignment.Center), "Can't display ${vm.current?.file?.name}", "You can still sort it with the folder buttons") {}
                }
            }
        }

        Column(
            // Beside the folder panel nothing else keeps the pill and the bar off the navigation bar
            Modifier.align(Alignment.BottomCenter)
                .then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Above the pill and the video bar, so a snackbar never covers them or the folder buttons
            SnackbarHost(snackbar)
            val assigned = vm.assigned
            if (assigned.isNotEmpty()) {
                // Anchored at the bottom, so a pill that doesn't fit starts a row below and lifts the full ones
                FlowRow(
                    // The vertical padding keeps one row as tall as the flash pill, so a pill doesn't jump when it replaces it
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // The 48dp touch height would put 12dp more between rows
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        for (dest in assigned) {
                            val i = vm.dests.indexOfFirst { it.path == dest }
                            Pill(
                                label = Store.labelFor(dest),
                                color = if (i >= 0) folderColor(i).tile() else MaterialTheme.colorScheme.inverseSurface,
                                contentColor = if (i >= 0) folderColor(i).ink() else MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier,
                                onClear = { if (vm.unassign(dest)) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
                            )
                        }
                    }
                }
            } else {
                FlashPill(vm, Modifier)
            }
            if (video && !player.failed) {
                // The open video has a bar of its own, and this one would show through its black as it comes in
                val open = stage.open
                val alpha by animateFloatAsState(if (open) 0f else 1f, MaterialTheme.motionScheme.fastEffectsSpec())
                VideoBar(
                    player,
                    vm.videoMuted,
                    vm::changeMuted,
                    Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth()
                        .graphicsLayer { this.alpha = alpha }
                        .then(if (open) Modifier.clearAndSetSemantics {} else Modifier),
                )
            }
        }
    }
}

/** The video bar over an open video, slid away and back by a tap on the video. */
@Composable
private fun OpenVideoBar(vm: SiftViewModel, player: VideoPlayer, stage: Stage, modifier: Modifier) {
    val video = vm.gate == Gate.None && vm.current?.video == true && !player.failed
    val motion = MaterialTheme.motionScheme
    // Reduced motion asks for a plain fade, never a slide
    val animationsOn = rememberAnimationsOn()
    AnimatedVisibility(
        visible = video && stage.open && stage.controls,
        modifier = modifier,
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

@Composable
private fun Status(modifier: Modifier, title: String, body: String? = null, extra: @Composable () -> Unit) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            title,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        extra()
    }
}

@Composable
private fun TopBar(vm: SiftViewModel, folder: String) {
    val context = LocalContext.current
    val dayFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val timeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val photo = vm.current
    val idle = !vm.busy && !vm.scanning
    var menu by remember { mutableStateOf(false) }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val barWidth = maxWidth

        // The count drops out where it would take more than a fair share of the bar, which at large
        // font sizes would squeeze the photo details down to a few letters
        val style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
        val withCount = "Review ${vm.queued.grouped()}"
        val maxButton = barWidth * 0.45f
        val height = ButtonDefaults.MinHeight
        val padding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true)
        val iconSize = ButtonDefaults.iconSizeFor(height)
        val iconSpacing = ButtonDefaults.iconSpacingFor(height)
        val room = maxButton - iconSize - iconSpacing -
            padding.calculateLeftPadding(LayoutDirection.Ltr) - padding.calculateRightPadding(LayoutDirection.Ltr)
        val fits = vm.queued > 0 && with(density) { measurer.measure(withCount, style).size.width.toDp() } <= room
        val review = @Composable {
            val label: @Composable RowScope.() -> Unit = {
                Icon(Icons.Rounded.Checklist, null, Modifier.size(iconSize))
                Spacer(Modifier.width(iconSpacing))
                FitLabel(if (fits) withCount else "Review", MaterialTheme.typography.labelMedium, style)
            }
            // The end card has its own Review, and two buttons for one action is one too many
            if (vm.gate != Gate.End) {
                Button(
                    onClick = { vm.reviewSelections() },
                    shapes = ButtonDefaults.shapes(),
                    enabled = vm.queued > 0 && idle,
                    contentPadding = padding,
                    modifier = Modifier.widthIn(max = maxButton),
                    content = label,
                )
            }
        }
        // Not tied to idle, because the system Back it mirrors works during a move too
        val back = @Composable {
            IconButton(onClick = { vm.back() }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
        }
        val title = if (photo == null) folder else vm.titleOf(photo)
        // With no photo the details line is gone, and the title alone would make a thin pressed shape
        val menuTarget = Modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = idle, onClickLabel = "Open menu", role = Role.Button) { menu = true }
            .semantics { stateDescription = if (menu) "Expanded" else "Collapsed" }

        @Composable
        fun details(base: TextStyle, color: Color) {
            if (photo == null) return
            val style = base.copy(fontFeatureSettings = "tnum")
            Row {
                // The position never gets cut. When even a line of its own is too narrow it breaks
                // after the slash, which a no-break space keeps with the first number
                Text("${(vm.index + 1).grouped()} / ${vm.photos.size.grouped()}", style = style, color = color)
                // A video's length, once its still frame is in
                vm.duration?.let { Text("  ·  ${clock(it)}", style = style, color = color, maxLines = 1) }
                // The date gives way first. The menu has it in full, with the name and the size
                Text(
                    "  ·  ${dayFormat.format(Date(photo.mtime))}",
                    Modifier.weight(1f, fill = false),
                    style = style,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Decided from every album title in the set rather than per photo, so the photo below keeps its
        // height from one swipe to the next. Review is taken at its widest so the count can't flip it
        val titleStyle = MaterialTheme.typography.titleSmall
        val titles = remember(vm.photos, folder) { vm.photos.mapTo(HashSet()) { vm.titleOf(it) } + folder }
        // The bar keeps 4dp at each end and at each side of the title
        val titleRoom = barWidth - 48.dp - 16.dp - 24.dp - 20.dp - maxButton
        val flexible = remember(titles, titleStyle, titleRoom, density) {
            val roomPx = with(density) { titleRoom.roundToPx() }
            titles.any { measurer.measure(it, titleStyle).size.width > roomPx }
        }

        if (flexible) {
            TwoRowsTopAppBar(
                title = { expanded ->
                    // The collapsed row's copy stays hidden without a scroll behaviour, so it keeps one
                    // line and no tap target and can't make that row taller than it looks
                    if (expanded) {
                        Row(menuTarget, verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Rounded.ArrowDropDown, null)
                        }
                    } else {
                        Text(title, maxLines = 1)
                    }
                },
                subtitle = if (photo == null) null else { expanded ->
                    if (expanded) details(LocalTextStyle.current, LocalContentColor.current)
                },
                navigationIcon = back,
                actions = { review() },
                // The viewer already keeps clear of the status bar and the start side
                windowInsets = WindowInsets(0),
            )
        } else {
            TopAppBar(
                title = {
                    // The menu hangs off the details, so the panel under the photo holds nothing but folder buttons
                    Column(
                        Modifier.fillMaxWidth().then(menuTarget).padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The album leads. With several of them it is what changes from one photo to the next
                            Text(
                                title,
                                Modifier.weight(1f, fill = false),
                                style = titleStyle,
                                maxLines = 1,
                                // Album at the start and the photo's own folder at the end both say where it is
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                            Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(20.dp))
                        }
                        details(MaterialTheme.typography.labelMedium, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = back,
                actions = { review() },
                windowInsets = WindowInsets(0),
            )
        }
        // Hangs off the bar as a whole
        SortMenu(
            vm,
            photo?.file,
            photo?.let { "${Formatter.formatShortFileSize(context, it.size)}  ·  ${timeFormat.format(Date(it.mtime))}" },
            expanded = menu,
        ) { menu = false }
    }
}

/** Opens from the header and leads with what the header has no room for: the photo's name, size, time and folder. */
@Composable
private fun SortMenu(vm: SiftViewModel, file: File?, details: String?, expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items = buildList {
        if (file != null) add(Triple("Share", Icons.Rounded.Share) { sharePhoto(context, file) })
        if (vm.photos.isNotEmpty()) {
            add(
                Triple("Go to photo", Icons.Rounded.Numbers) {
                    vm.dialog = Dialog.GoToPhoto((vm.index + 1).coerceAtMost(vm.photos.size), vm.photos.size)
                },
            )
        }
        add(Triple(if (vm.sources.size == 1) "Reload album" else "Reload albums", Icons.Rounded.Refresh) { vm.reload() })
    }
    val groups = if (file != null) 2 else 1
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 280.dp),
        // Lines it up 12dp in from the bar's start, where the full width menu used to start
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
            MenuAnchorPosition.Below,
            DpOffset(12.dp, 0.dp),
        ),
    ) {
        if (file != null) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, groups), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(file.name, style = MaterialTheme.typography.titleSmall)
                    if (details != null) Text(details, style = MaterialTheme.typography.bodySmall, color = quiet)
                    // The whole path of its folder. Two albums can share a name and differ only here
                    file.parent?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = quiet) }
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        }
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(groups - 1, groups), modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, (label, icon, action) ->
                DropdownMenuItem(
                    onClick = {
                        onDismiss()
                        action()
                    },
                    text = { Text(label) },
                    shape = MenuDefaults.itemShape(index, items.size).shape,
                    leadingIcon = { Icon(icon, null) },
                    // The details text can set a group wider than the item would take on its own
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Shows one folder the photo on screen is queued to go to. Tapping it takes that folder off. */
@Composable
private fun Pill(
    label: String,
    color: Color,
    modifier: Modifier,
    contentColor: Color,
    onClear: (() -> Unit)?,
) {
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
            shape = CircleShape,
            color = color,
            contentColor = contentColor,
            // The pill sits on the photo, which can be any colour, so the shadow gives it its edge
            shadowElevation = 6.dp,
            content = content,
        )
    } else {
        // The flash pill takes no tap, so TalkBack must not find a disabled button there. The minimum
        // size keeps it as tall as the queued pill that takes its place
        Surface(
            modifier = modifier.minimumInteractiveComponentSize(),
            shape = CircleShape,
            color = color,
            contentColor = contentColor,
            shadowElevation = 6.dp,
            content = content,
        )
    }
}

/** Brief confirmation of the folder just tapped, placed off the middle of the next photo. */
@Composable
private fun FlashPill(vm: SiftViewModel, modifier: Modifier) {
    val flash = vm.flash ?: return
    val alpha = remember { Animatable(0f) }
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(flash.id) {
        alpha.snapTo(1f)
        delay(250)
        alpha.animateTo(0f, fade)
        // Consumed, so coming back to this spot later doesn't replay a stale confirmation
        vm.flashShown(flash.id)
    }
    val color = folderColor(flash.colorIndex)
    Pill(
        flash.label,
        color.tile(),
        modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .graphicsLayer { this.alpha = alpha.value },
        color.ink(),
        onClear = null,
    )
}

@Composable
private fun Panel(vm: SiftViewModel, landscape: Boolean, modifier: Modifier) {
    val shape = if (landscape) {
        MaterialTheme.shapes.extraLarge.copy(topEnd = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    } else {
        MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    }
    val sides = if (landscape) {
        WindowInsetsSides.Vertical + WindowInsetsSides.End
    } else {
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
    }
    Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(sides)).padding(10.dp)) {
            FolderGrid(vm, landscape)
        }
    }
}

/** Folder buttons keep a fixed position per folder, so the thumb learns where each one is. */
@Composable
private fun FolderGrid(vm: SiftViewModel, landscape: Boolean) {
    val dests = vm.dests
    val n = dests.size
    val fontScale = LocalDensity.current.fontScale
    val cols = when {
        landscape -> if (n <= 5) 1 else 2
        n <= 3 -> n
        n == 4 -> 2
        n <= 6 || n == 9 -> 3
        // A quarter of a phone holds about four letters at a large font size
        fontScale >= 1.5f -> 3
        else -> 4
    }.coerceAtLeast(1)
    val rows = ceil(n / cols.toFloat()).toInt()
    // Rows grow with the font size, or a large font leaves no room for the label under the count
    val grow = fontScale.coerceAtLeast(1f).pow(0.8f)
    val rowHeight: Dp = ButtonDefaults.MediumContainerHeight * grow
    val gap = 8.dp

    val grid = @Composable {
        Column(
            Modifier.semantics { collectionInfo = CollectionInfo(rows, cols) },
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        if (i < n) {
                            FolderButton(
                                vm, dests[i], i,
                                Modifier.weight(1f).height(rowHeight)
                                    .semantics { collectionItemInfo = CollectionItemInfo(r, 1, c, 1) },
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
    when {
        landscape -> Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) { grid() }
        // Past three rows the grid scrolls, and half a row peeks out to show there is more
        rows > 3 -> Column(Modifier.height(rowHeight * 3.5f + gap * 3).verticalScroll(rememberScrollState())) { grid() }
        else -> grid()
    }
}

@Composable
private fun FolderButton(vm: SiftViewModel, dest: Dest, index: Int, modifier: Modifier) {
    val view = LocalView.current
    val selected = dest.path in vm.assigned
    val count = vm.counts[dest.path] ?: 0
    val color = folderColor(index)
    val ink = color.ink()

    // A hold adds the folder and stays on the photo, so one photo can go to several folders.
    // The library Button has no long click, so the hold is timed from its press
    val interactions = remember { MutableInteractionSource() }
    var held by remember { mutableStateOf(false) }
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
    val current by rememberUpdatedState(dest)
    LaunchedEffect(interactions) {
        interactions.interactions.collectLatest { i ->
            if (i is PressInteraction.Press) {
                held = false
                delay(longPress)
                held = true
                if (vm.hold(current)) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    Box(modifier) {
        Button(
            onClick = {
                if (held) held = false
                else if (vm.assign(dest)) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            },
            interactionSource = interactions,
            // Past about 16dp the corner curve reaches the badge and the selection ring cuts through it
            shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.large, pressedShape = ButtonDefaults.mediumPressedShape),
            colors = ButtonDefaults.buttonColors(containerColor = color.tile(), contentColor = ink),
            // A ring marks the queued folder. Brightness alone is too subtle on a light panel
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxSize().semantics {
                this.selected = selected
                onLongClick(label = if (selected) "Take off and stay" else "Add and stay") { vm.hold(dest) }
                val state = listOfNotNull(
                    if (selected) "This photo is queued here" else null,
                    if (count > 0) "${count.grouped()} queued" else null,
                ).joinToString()
                if (state.isNotEmpty()) stateDescription = state
            },
        ) {
            Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 4.dp)) {
                // The badge row is laid out even at zero, so a label never moves when its first count shows up,
                // and the badge sits above the label instead of over it
                Badge(
                    Modifier.align(Alignment.End)
                        .then(if (count > 0) Modifier else Modifier.alpha(0f))
                        .clearAndSetSemantics {},
                    containerColor = ink,
                    contentColor = color.tile(),
                ) {
                    val badge = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    Text(
                        if (count > 999) "999+" else count.grouped(),
                        Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true),
                        style = badge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = shrinkToFit(badge),
                    )
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(end = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A line height relative to the size, so two shrunk lines fit where two full ones would not
                    val style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight).copy(lineHeight = 1.25.em)
                    Text(
                        dest.label,
                        style = style,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        autoSize = shrinkToFit(style, minRatio = 0.4f),
                    )
                }
            }
        }
    }
}
