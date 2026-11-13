package moe.nikableh.sift.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import moe.nikableh.sift.Dest
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Gate
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import moe.nikableh.sift.ZoomImageView
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.pow

/** The sorting screen: one photo, one tap on a folder button queues it and shows the next. */
@Composable
fun SortScreen(vm: SiftViewModel) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (vm.gate) {
            Gate.Permission -> Welcome(
                icon = Icons.Rounded.FolderOpen,
                title = "Let Sift move your photos",
                body = "Sift moves photos between folders on your phone. Android only lets an app do that " +
                    "with the \"All files access\" permission.",
                action = "Grant access",
            ) { openAllFilesSettings(context) }

            Gate.Setup -> Welcome(
                icon = Icons.Rounded.PhotoLibrary,
                title = "Pick your folders",
                body = "Choose the album to sort and the folders its photos can go to.",
                action = "Set up folders",
            ) { vm.open(Screen.Setup) }

            else -> if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Viewer(vm, Modifier.weight(1f).fillMaxHeight())
                    Panel(vm, landscape = true, Modifier.width(280.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Viewer(vm, Modifier.weight(1f).fillMaxWidth())
                    Panel(vm, landscape = false, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

fun openAllFilesSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        )
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

fun sharePhoto(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "image/*"
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

@Composable
private fun Viewer(vm: SiftViewModel, modifier: Modifier) {
    val folder = File(vm.source ?: "").name

    Column(
        modifier.background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
    ) {
        TopBar(vm, folder)
        Photo(vm, folder, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun Photo(vm: SiftViewModel, folder: String, modifier: Modifier) {
    val gate = vm.gate

    Box(modifier) {
        AndroidView(
            factory = { context ->
                ZoomImageView(context).apply {
                    onSwipe = { vm.go(it) }
                    onZoomIn = { vm.loadSharper() }
                }
            },
            update = {
                val pages = !vm.busy && (gate == Gate.None || gate == Gate.End)
                it.show(
                    vm.bitmapPath, vm.bitmap, vm.prevBitmap, vm.nextBitmap,
                    canPrev = pages && vm.index > 0,
                    canNext = pages && vm.index < vm.photos.size,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        when (gate) {
            Gate.Reading -> Status(Modifier.align(Alignment.Center), "Reading $folder") { LoadingIndicator() }
            Gate.Empty -> if (vm.hiddenHere > 0) {
                Status(
                    Modifier.align(Alignment.Center),
                    "Nothing left to sort in $folder",
                    if (vm.hiddenHere == 1) "1 skipped photo is hidden" else "${vm.hiddenHere.grouped()} skipped photos are hidden",
                ) {
                    BigButton("Review skipped") { vm.enterReview() }
                }
            } else {
                Status(Modifier.align(Alignment.Center), "No photos in $folder") {
                    BigButton("Change folders") { vm.open(Screen.Setup) }
                }
            }
            Gate.End -> if (vm.reviewing) {
                Status(Modifier.align(Alignment.Center), "That was the last skipped photo") {
                    BigButton("Back to sorting") { vm.exitReview() }
                }
            } else {
                Status(
                    Modifier.align(Alignment.Center),
                    "That was the last photo",
                    "${vm.queuedHere.grouped()} queued to move, ${vm.skippedHere.grouped()} skipped, ${vm.leftHere.grouped()} not reviewed",
                ) {
                    if (vm.selections > 0) BigButton(applyLabel(vm)) { vm.askApply() }
                }
            }
            else -> if (vm.decodeFailed) {
                Status(Modifier.align(Alignment.Center), "Can't display ${vm.current?.file?.name}") {}
            }
        }

        val assigned = vm.assigned
        if (assigned != null) {
            val i = vm.dests.indexOfFirst { it.path == assigned }
            Pill(
                label = Store.labelFor(assigned),
                color = if (i >= 0) folderColor(i).dimmed() else MaterialTheme.colorScheme.inverseSurface,
                contentColor = if (i >= 0) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                onClear = { vm.clearAssignment() },
            )
        } else if (vm.isSkipped) {
            // Seen and left in place. Tapping it makes the photo count as not reviewed again
            Pill(
                label = "Skipped",
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                icon = Icons.Rounded.SkipNext,
                onClear = { vm.clearSkip() },
            )
        } else {
            FlashPill(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
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
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val photo = vm.current
    val idle = !vm.busy && !vm.scanning

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
        val barWidth = maxWidth
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Not tied to idle, because the system Back it mirrors works during a move too
            FilledTonalIconButton(onClick = { vm.back() }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, if (vm.reviewing) "Back to sorting" else "Back")
            }

            if (photo == null) {
                Text(
                    if (vm.reviewing) "Skipped in $folder" else folder,
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            } else {
                val date = dateFormat.format(Date(photo.mtime))
                // Persistent, so a long name stays up until a tap outside instead of timing out mid-read
                val tooltip = rememberTooltipState(isPersistent = true)
                val scope = rememberCoroutineScope()
                // The popup closes itself on any tap outside it, the header included, so a tap that just closed it must not
                // reopen it. Which window sees the tap first is not fixed, so compare times instead of reading isVisible.
                var pressedAt by remember { mutableLongStateOf(0L) }
                var hiddenAt by remember { mutableLongStateOf(0L) }
                LaunchedEffect(tooltip) {
                    snapshotFlow { tooltip.isVisible }.drop(1).collect { if (!it) hiddenAt = SystemClock.uptimeMillis() }
                }
                // TooltipBox does not pass its modifier to the node the Row measures, so the weight sits on a wrapper
                Box(Modifier.weight(1f)) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                        tooltip = {
                            RichTooltip(title = { Text(photo.file.name) }) { Text(date) }
                        },
                        state = tooltip,
                        // Its own input only reacts to a long press, and only over the text it measured
                        enableUserInput = false,
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        pressedAt = awaitFirstDown(false, PointerEventPass.Initial).uptimeMillis
                                    }
                                }
                                .clickable {
                                    when {
                                        tooltip.isVisible -> tooltip.dismiss()
                                        hiddenAt < pressedAt - 200 -> scope.launch { tooltip.show() }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            val list = if (vm.reviewing) "Skipped " else ""
                            // The position never gets cut. When even a line of its own is too narrow it breaks
                            // after the slash, which a no-break space keeps with the first number
                            Text(
                                "$list${(vm.index + 1).grouped()}\u00A0/ ${vm.photos.size.grouped()}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val parent = if (vm.recursive) "${photo.file.parentFile?.name}  ·  " else ""
                            Text(
                                "$parent${Formatter.formatShortFileSize(context, photo.size)}  ·  ${photo.file.name}  ·  $date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // The count drops out where it would take more than a fair share of the bar, which at large
            // font sizes would squeeze the photo details down to a few letters
            val style = MaterialTheme.typography.labelLarge
            val withCount = "Apply ${vm.queued.grouped()}"
            val maxButton = barWidth * 0.45f
            val padding = ButtonDefaults.ButtonWithIconContentPadding
            val room = maxButton - ButtonDefaults.IconSize - ButtonDefaults.IconSpacing -
                padding.calculateLeftPadding(LayoutDirection.Ltr) - padding.calculateRightPadding(LayoutDirection.Ltr)
            val fits = vm.queued > 0 && with(density) { measurer.measure(withCount, style).size.width.toDp() } <= room
            Button(
                onClick = { vm.askApply() },
                shapes = ButtonDefaults.shapes(),
                enabled = vm.selections > 0 && idle,
                contentPadding = padding,
                modifier = Modifier.widthIn(max = maxButton),
            ) {
                Icon(Icons.Rounded.DoneAll, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                FitLabel(if (fits) withCount else "Apply", style)
            }
        }
    }
}

/** Shows where the photo on screen is queued to go. Tapping it takes the photo out of the queue. */
@Composable
private fun Pill(
    label: String,
    color: Color,
    modifier: Modifier,
    icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward,
    contentColor: Color = Color.White,
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
            Icon(icon, null, Modifier.size(18.dp))
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
    Pill(
        flash.label,
        folderColor(flash.colorIndex).dimmed(),
        modifier.graphicsLayer { this.alpha = alpha.value },
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
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(sides)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Controls(vm, landscape)
            FolderGrid(vm, landscape)
        }
    }
}

@Composable
private fun Controls(vm: SiftViewModel, landscape: Boolean) {
    val idle = !vm.busy && !vm.scanning
    val canGoBack = vm.index > 0
    val canSkip = vm.current != null
    val gap = Arrangement.spacedBy(8.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
        // More sits outside the group, so its overflow handling can never hide it
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            modifier = Modifier.weight(1f),
            horizontalArrangement = gap,
        ) {
            customItem({
                if (landscape) {
                    // The side panel has no room for two labelled buttons next to More
                    ControlIconButton("Previous", Icons.AutoMirrored.Rounded.NavigateBefore, canGoBack) { vm.go(-1) }
                } else {
                    ControlButton(
                        "Previous", Icons.AutoMirrored.Rounded.NavigateBefore, canGoBack, Modifier.weight(1f),
                    ) { vm.go(-1) }
                }
            }, {})
            customItem({
                ControlButton("Skip", Icons.AutoMirrored.Rounded.NavigateNext, canSkip, Modifier.weight(1f)) { vm.skip() }
            }, {})
        }
        MoreMenu(vm, idle)
    }
}

@Composable
private fun ControlIconButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, label)
    }
}

@Composable
private fun MoreMenu(vm: SiftViewModel, idle: Boolean) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val photo = vm.current
    val hasPhotos = vm.photos.isNotEmpty()
    // A review list can run out of things to offer, and an empty menu would open as a blank box
    val hasItems = !vm.reviewing || hasPhotos || vm.hiddenHere > 0 || vm.canUndo

    Box {
        ControlIconButton("More", Icons.Rounded.MoreVert, idle && hasItems) { menu = true }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            @Composable
            fun item(label: String, icon: ImageVector, action: () -> Unit) = DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { Icon(icon, null) },
                onClick = {
                    menu = false
                    action()
                },
            )
            if (vm.canUndo) item("Undo last step", Icons.AutoMirrored.Rounded.Undo) { vm.undoLast() }
            if (photo != null) item("Share", Icons.Rounded.Share) { sharePhoto(context, photo.file) }
            if (hasPhotos) {
                item("Go to photo…", Icons.Rounded.Numbers) {
                    vm.dialog = Dialog.GoToPhoto((vm.index + 1).coerceAtMost(vm.photos.size), vm.photos.size)
                }
            }
            if (vm.reviewing) {
                if (vm.hiddenHere > 0) {
                    item("Unhide all skipped (${vm.hiddenHere.grouped()})", Icons.Rounded.RestartAlt) {
                        vm.dialog = Dialog.ConfirmUnhide(vm.hiddenHere)
                    }
                }
            } else {
                item("Reload folder", Icons.Rounded.Refresh) { vm.reload() }
                if (vm.hiddenHere > 0) {
                    item("Review skipped (${vm.hiddenHere.grouped()})", Icons.Rounded.SkipNext) { vm.enterReview() }
                }
                if (vm.selections > 0) {
                    item("Clear selections (${vm.selections.grouped()})", Icons.Rounded.ClearAll) {
                        vm.dialog = Dialog.ConfirmClear
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        enabled = enabled,
        modifier = modifier.height(48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        interactionSource = remember { MutableInteractionSource() },
    ) {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(6.dp))
        FitLabel(label)
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
    val container = folderColor(index).dimmed()

    Box(modifier) {
        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                vm.assign(dest)
            },
            // Past about 16dp the corner curve reaches the badge and the selection ring cuts through it
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(14.dp), pressedShape = RoundedCornerShape(6.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
            // A dark ring and a tick mark the queued folder. Brightness alone is too subtle on a light panel
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
            contentPadding = PaddingValues(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                // The badge row is laid out even at zero, so a label never moves when its first count shows up,
                // and the badge sits above the label instead of over it
                val badge = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
                Badge(
                    Modifier.align(Alignment.End)
                        .then(if (count > 0) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
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
