package moe.nikableh.sift.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
fun SortScreen(vm: SiftViewModel, settled: Boolean) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    // Held above the two layouts, so turning the phone doesn't start the video over
    val player = remember { VideoPlayer(context.applicationContext) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Playback(vm, player, settled)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (vm.gate) {
            Gate.Permission -> AccessScreen(vm)

            Gate.Setup -> Welcome(
                icon = Icons.Rounded.PhotoLibrary,
                title = "Pick your folders",
                body = "Choose the albums to sort and the folders their photos can go to.",
                action = "Set up folders",
            ) { vm.open(Screen.Setup) }

            else -> Box(Modifier.fillMaxSize()) {
                // Under the header and the folder buttons and as large as the screen, so an open photo
                // can come up over them. Shared by the two layouts, so turning the phone keeps the zoom
                val stage = remember { Stage() }
                Media(vm, player, stage)
                if (landscape) {
                    Row(Modifier.fillMaxSize()) {
                        Viewer(vm, player, stage, landscape = true, Modifier.weight(1f).fillMaxHeight())
                        Panel(vm, landscape = true, Modifier.width(280.dp).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Viewer(vm, player, stage, landscape = false, Modifier.weight(1f).fillMaxWidth())
                        Panel(vm, landscape = false, Modifier.fillMaxWidth())
                    }
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Counted up on every return to the app, which starts the video again after the stop below
    var starts by remember { mutableIntStateOf(0) }
    LifecycleStartEffect(player) {
        starts++
        // Closed here and not through state: nothing recomposes while the app is in the background,
        // and a video with sound would play on
        onStopOrDispose { player.close() }
    }
    LaunchedEffect(path, starts) {
        player.close()
        if (path != null) {
            // Tapping through an album at speed never gets as far as setting up a decoder
            delay(VIDEO_START_MS)
            // The wait runs on while the app is in the background, where nothing must start playing.
            // Coming back counts [starts] up and gets here again
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.open(File(path))
        }
    }
    val muted = vm.videoMuted
    LaunchedEffect(muted) { player.setMuted(muted) }

    val running = player.playing && player.showing
    LaunchedEffect(running) {
        if (running) while (true) {
            withFrameNanos {}
            player.tick()
        }
    }
    // Awake for one play through. A video on repeat would otherwise never let the screen sleep
    if (running && !player.looped) KeepScreenOn()
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
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(128.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
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

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
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
    var raised by remember { mutableStateOf(false) }
    // The header's back button is under the open photo, and leaving the screen from there would come as a surprise
    BackHandler(enabled = raised) { stage.view?.pager?.close() }
    if (raised && !isSystemInDarkTheme()) PaleBarIcons()

    AndroidView(
        factory = { context ->
            MediaView(context).apply {
                pager.onSwipe = { vm.go(it) }
                pager.onZoomIn = { vm.loadSharper() }
                pager.onTap = { player.toggle() }
                pager.onRaise = { raised = it }
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
                zoomable = !video,
            )
            // Only a photo opens. If the page under an open one turns into anything else, it belongs under the header
            if (video || gate != Gate.None) it.pager.close()
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
private fun PaleBarIcons() {
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
private fun Viewer(vm: SiftViewModel, player: VideoPlayer, stage: Stage, landscape: Boolean, modifier: Modifier) {
    val folder = vm.albumsLabel

    Column(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))) {
        TopBar(vm, folder)
        Photo(
            vm, player, folder, landscape,
            Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                stage.slot = it
                stage.place()
            },
        )
    }
}

/** What lies over the photo. The photo itself is [Media], under all of this. */
@Composable
private fun Photo(vm: SiftViewModel, player: VideoPlayer, folder: String, landscape: Boolean, modifier: Modifier) {
    val gate = vm.gate
    val video = gate == Gate.None && vm.current?.video == true

    Box(modifier) {
        when (gate) {
            Gate.Reading -> Status(Modifier.align(Alignment.Center), "Reading $folder") { LoadingIndicator() }
            Gate.Empty -> Status(Modifier.align(Alignment.Center), "No photos or videos in $folder") {
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
                video && player.failed -> Status(Modifier.align(Alignment.Center), "Can't play this video") {}
                // A video without a still frame can play all the same, and then it shows itself
                vm.decodeFailed && !(video && player.showing) -> {
                    Status(Modifier.align(Alignment.Center), "Can't display ${vm.current?.file?.name}") {}
                }
            }
        }

        Column(
            // Beside the folder panel nothing else keeps the bar off the navigation bar
            Modifier.align(Alignment.BottomCenter)
                .then(if (video && landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val assigned = vm.assigned
            if (assigned != null) {
                val i = vm.dests.indexOfFirst { it.path == assigned }
                Pill(
                    label = Store.labelFor(assigned),
                    color = if (i >= 0) folderColor(i).tile() else MaterialTheme.colorScheme.inverseSurface,
                    contentColor = if (i >= 0) folderColor(i).ink() else MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier,
                    onClear = { vm.clearAssignment() },
                )
            } else {
                FlashPill(vm, Modifier)
            }
            if (video && !player.failed) VideoBar(vm, player)
        }
    }
}

/** Play or pause, the timeline, the time and sound, floating over the bottom of the viewer. */
@Composable
private fun VideoBar(vm: SiftViewModel, player: VideoPlayer) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth(),
    ) {
        // A video starts by itself, so until the player has it the button already shows what is about to happen
        val playing = player.playing || player.path == null
        // The timeline's two colours, so the bar reads as one piece: a button that is on has the
        // colour of the played part, one that is off the colour of the part still to come. All four
        // are set, because the defaults of the ones left out follow the ones given
        val scheme = MaterialTheme.colorScheme
        val colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.primary,
            checkedContainerColor = scheme.primary,
            checkedContentColor = scheme.onPrimary,
        )
        FilledIconToggleButton(
            checked = playing,
            onCheckedChange = { player.toggle() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = colors,
        ) {
            if (playing) Icon(Icons.Rounded.Pause, "Pause") else Icon(Icons.Rounded.PlayArrow, "Play")
        }
        Timeline(player, Modifier.weight(1f).padding(horizontal = 8.dp))
        Elapsed(player, Modifier.align(Alignment.CenterVertically))
        val sound = !vm.videoMuted
        FilledIconToggleButton(
            checked = sound,
            onCheckedChange = { vm.changeMuted(!it) },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = colors,
        ) {
            if (sound) Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Turn sound off")
            else Icon(Icons.AutoMirrored.Rounded.VolumeOff, "Turn sound on")
        }
    }
}

/** On its own, so the position changing every frame redraws the slider and nothing around it. */
@Composable
private fun Timeline(player: VideoPlayer, modifier: Modifier) {
    val duration = player.duration
    Slider(
        value = if (duration > 0) (player.position / duration.toFloat()).coerceIn(0f, 1f) else 0f,
        onValueChange = { player.scrubTo((it * duration).toLong()) },
        onValueChangeFinished = { player.scrubEnd() },
        modifier = modifier.semantics { contentDescription = "Timeline" },
    )
}

@Composable
private fun Elapsed(player: VideoPlayer, modifier: Modifier) {
    Text(
        clock(player.position),
        modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun Status(modifier: Modifier, title: String, body: String? = null, extra: @Composable () -> Unit) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
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
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
        val barWidth = maxWidth
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Not tied to idle, because the system Back it mirrors works during a move too
            FilledTonalIconButton(onClick = { vm.back() }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }

            // The menu hangs off the details, so the panel under the photo holds nothing but folder buttons
            Column(
                Modifier
                    .weight(1f)
                    // The back button's touch area keeps the highlight this far from it, and Review gets the same
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = idle, onClickLabel = "Open menu") { menu = true }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The album leads. With several of them it is what changes from one photo to the next
                    Text(
                        if (photo == null) folder else vm.titleOf(photo),
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        // Album at the start and the photo's own folder at the end both say where it is
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                    Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(20.dp))
                }
                if (photo != null) {
                    val details = MaterialTheme.typography.bodySmall
                    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
                    Row {
                        // The position never gets cut. When even a line of its own is too narrow it breaks
                        // after the slash, which a no-break space keeps with the first number
                        Text("${(vm.index + 1).grouped()} / ${vm.photos.size.grouped()}", style = details, color = quiet)
                        // A video's length, once its still frame is in
                        vm.duration?.let { Text("  ·  ${clock(it)}", style = details, color = quiet, maxLines = 1) }
                        // The date gives way first. The menu has it in full, with the name and the size
                        Text(
                            "  ·  ${dayFormat.format(Date(photo.mtime))}",
                            Modifier.weight(1f, fill = false),
                            style = details,
                            color = quiet,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // The count drops out where it would take more than a fair share of the bar, which at large
            // font sizes would squeeze the photo details down to a few letters
            val style = MaterialTheme.typography.labelLarge
            val withCount = "Review ${vm.queued.grouped()}"
            val maxButton = barWidth * 0.45f
            val padding = ButtonDefaults.ButtonWithIconContentPadding
            val room = maxButton - ButtonDefaults.IconSize - ButtonDefaults.IconSpacing -
                padding.calculateLeftPadding(LayoutDirection.Ltr) - padding.calculateRightPadding(LayoutDirection.Ltr)
            val fits = vm.queued > 0 && with(density) { measurer.measure(withCount, style).size.width.toDp() } <= room
            Button(
                onClick = { vm.reviewSelections() },
                shapes = ButtonDefaults.shapes(),
                enabled = vm.queued > 0 && idle,
                contentPadding = padding,
                modifier = Modifier.widthIn(max = maxButton),
            ) {
                Icon(Icons.Rounded.Checklist, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                FitLabel(if (fits) withCount else "Review", style)
            }
        }
        // Hangs off the bar as a whole, so it can be as wide as the bar. The cap keeps it a panel on a tablet
        SortMenu(
            vm,
            photo?.file,
            photo?.let { "${Formatter.formatShortFileSize(context, it.size)}  ·  ${timeFormat.format(Date(it.mtime))}" },
            width = min(barWidth - 8.dp, 480.dp),
            expanded = menu,
        ) { menu = false }
    }
}

/** Opens from the header and leads with what the header has no room for: the photo's name, size, time and folder. */
@Composable
private fun SortMenu(vm: SiftViewModel, file: File?, details: String?, width: Dp, expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // The offset centres it under the bar, whose padding leaves 8dp at each side
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.width(width), offset = DpOffset(4.dp, 0.dp)) {
        @Composable
        fun item(label: String, icon: ImageVector, action: () -> Unit) = DropdownMenuItem(
            text = { Text(label) },
            leadingIcon = { Icon(icon, null) },
            onClick = {
                onDismiss()
                action()
            },
            // An item stops at 280dp on its own, which would leave the touch feedback short of the edge
            modifier = Modifier.fillMaxWidth(),
        )
        if (file != null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val quiet = MaterialTheme.colorScheme.onSurfaceVariant
                Text(file.name, style = MaterialTheme.typography.titleSmall)
                if (details != null) Text(details, style = MaterialTheme.typography.bodySmall, color = quiet)
                // The whole path of its folder. Two albums can share a name and differ only here
                file.parent?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = quiet) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            item("Share", Icons.Rounded.Share) { sharePhoto(context, file) }
        }
        if (vm.photos.isNotEmpty()) {
            item("Go to photo…", Icons.Rounded.Numbers) {
                vm.dialog = Dialog.GoToPhoto((vm.index + 1).coerceAtMost(vm.photos.size), vm.photos.size)
            }
        }
        item(if (vm.sources.size == 1) "Reload album" else "Reload albums", Icons.Rounded.Refresh) { vm.reload() }
    }
}

/** Shows where the photo on screen is queued to go. Tapping it takes the photo out of the queue. */
@Composable
private fun Pill(
    label: String,
    color: Color,
    modifier: Modifier,
    contentColor: Color,
    onClear: (() -> Unit)?,
) {
    Surface(
        onClick = { onClear?.invoke() },
        enabled = onClear != null,
        modifier = modifier,
        shape = CircleShape,
        color = color,
        contentColor = contentColor,
    ) {
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
}

/** Brief confirmation of the folder just tapped, placed off the middle of the next photo. */
@Composable
private fun FlashPill(vm: SiftViewModel, modifier: Modifier) {
    val flash = vm.flash ?: return
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(flash.id) {
        alpha.snapTo(1f)
        delay(250)
        alpha.animateTo(0f, tween(200))
        // Consumed, so coming back to this spot later doesn't replay a stale confirmation
        vm.flashShown(flash.id)
    }
    val color = folderColor(flash.colorIndex)
    Pill(
        flash.label,
        color.tile(),
        modifier.graphicsLayer { this.alpha = alpha.value },
        color.ink(),
        onClear = null,
    )
}

@Composable
private fun Panel(vm: SiftViewModel, landscape: Boolean, modifier: Modifier) {
    val shape = if (landscape) {
        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
    val rowHeight: Dp = (if (landscape) 56.dp else if (rows == 1) 72.dp else 64.dp) * grow
    val gap = 8.dp

    val grid = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        if (i < n) {
                            FolderButton(vm, dests[i], i, Modifier.weight(1f).height(rowHeight))
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
    val selected = vm.assigned == dest.path
    val count = vm.counts[dest.path] ?: 0
    val color = folderColor(index)
    val ink = color.ink()
    val dark = isSystemInDarkTheme()

    Box(modifier) {
        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                vm.assign(dest)
            },
            // Past about 16dp the corner curve reaches the badge and the selection ring cuts through it
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(14.dp), pressedShape = RoundedCornerShape(6.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = color.tile(), contentColor = ink),
            // A dark ring and a tick mark the queued folder. Brightness alone is too subtle on a light panel.
            // In dark theme a ring at the edge is either pale on a pale tile or dark on the dark panel,
            // so there it moves inside the tile
            border = if (selected && !dark) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
            // The padding sits on the column inside, so the dark theme ring can sit 3dp from the tile's edge
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (selected && dark) {
                    Box(Modifier.fillMaxSize().padding(3.dp).border(3.dp, ink, RoundedCornerShape(11.dp)))
                }
                Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 4.dp)) {
                    // The badge row is laid out even at zero, so a label never moves when its first count shows up,
                    // and the badge sits above the label instead of over it
                    val badge = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
                    Badge(
                        Modifier.align(Alignment.End)
                            .then(if (count > 0) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}),
                        containerColor = ink,
                        contentColor = Color.White,
                    ) { FitLabel(count.grouped(), badge) }
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(end = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        // A line height relative to the size, so two shrunk lines fit where two full ones would not
                        val style = MaterialTheme.typography.titleMedium.copy(lineHeight = 1.25.em)
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
}
