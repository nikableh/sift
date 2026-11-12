package moe.nikableh.sift.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
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

/** The sorting screen: one photo, one tap on a folder button queues it and shows the next. */
@Composable
fun SortScreen(vm: SiftViewModel) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var chrome by rememberSaveable { mutableStateOf(true) }
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
                    Viewer(vm, chrome, { chrome = !chrome }, Modifier.weight(1f).fillMaxHeight())
                    Panel(vm, landscape = true, Modifier.width(280.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Viewer(vm, chrome, { chrome = !chrome }, Modifier.weight(1f).fillMaxWidth())
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
private fun Viewer(vm: SiftViewModel, chrome: Boolean, onToggleChrome: () -> Unit, modifier: Modifier) {
    val toggle by rememberUpdatedState(onToggleChrome)
    val gate = vm.gate
    val folder = File(vm.source ?: "").name

    Box(
        modifier.background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
    ) {
        AndroidView(
            factory = { context ->
                ZoomImageView(context).apply {
                    onSwipe = { vm.go(it) }
                    onTap = { toggle() }
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
                    if (vm.hiddenHere == 1) "1 skipped photo is hidden" else "${vm.hiddenHere} skipped photos are hidden",
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
                    "${vm.queuedHere} queued to move, ${vm.skippedHere} skipped, ${vm.leftHere} not reviewed",
                ) {
                    if (vm.selections > 0) BigButton(applyLabel(vm)) { vm.askApply() }
                }
            }
            else -> if (vm.decodeFailed) {
                Status(Modifier.align(Alignment.Center), "Can't display ${vm.current?.file?.name}") {}
            }
        }

        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            TopBar(vm, folder)
        }

        val assigned = vm.assigned
        if (assigned != null) {
            val i = vm.dests.indexOfFirst { it.path == assigned }
            Pill(
                label = Store.labelFor(assigned),
                color = if (i >= 0) folderColor(i).dimmed() else MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                onClear = { vm.clearAssignment() },
            )
        } else if (vm.isSkipped) {
            // Seen and left in place. Tapping it makes the photo count as not reviewed again
            Pill(
                label = "Skipped",
                color = MaterialTheme.colorScheme.inverseSurface,
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
    var menu by remember { mutableStateOf(false) }
    val photo = vm.current
    val idle = !vm.busy && !vm.scanning
    // Solid behind the text and fading only below it, so a tall dark photo can't swallow the info line
    val scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)

    Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth()
            .background(scrim)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FilledTonalIconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes(), enabled = idle) {
                Icon(Icons.Rounded.MoreVert, "Menu")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val batch = vm.lastBatchInfo

                @Composable
                fun item(label: String, icon: ImageVector, action: () -> Unit) = DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, null) },
                    onClick = {
                        menu = false
                        action()
                    },
                )
                @Composable
                fun goToPhoto() {
                    if (vm.photos.isNotEmpty()) {
                        item("Go to photo…", Icons.Rounded.Numbers) {
                            vm.dialog = Dialog.GoToPhoto((vm.index + 1).coerceAtMost(vm.photos.size), vm.photos.size)
                        }
                    }
                }
                if (vm.reviewing) {
                    item("Back to sorting", Icons.AutoMirrored.Rounded.ArrowBack) { vm.exitReview() }
                    if (vm.canUndo) item("Undo last step", Icons.AutoMirrored.Rounded.Undo) { vm.undoLast() }
                    goToPhoto()
                    if (vm.hiddenHere > 0) {
                        item("Unhide all skipped (${vm.hiddenHere})", Icons.Rounded.RestartAlt) {
                            vm.dialog = Dialog.ConfirmUnhide(vm.hiddenHere)
                        }
                    }
                } else {
                    item("Home", Icons.Rounded.Home) { vm.back() }
                    if (vm.canUndo) item("Undo last step", Icons.AutoMirrored.Rounded.Undo) { vm.undoLast() }
                    goToPhoto()
                    item("Reload folder", Icons.Rounded.Refresh) { vm.reload() }
                    if (vm.hiddenHere > 0) {
                        item("Review skipped (${vm.hiddenHere})", Icons.Rounded.SkipNext) { vm.enterReview() }
                    }
                    if (vm.selections > 0) {
                        item("Clear selections (${vm.selections})", Icons.Rounded.ClearAll) {
                            vm.dialog = Dialog.ConfirmClear
                        }
                    }
                    if (batch != null) {
                        item("Undo last applied batch (${batch.second})", Icons.Rounded.History) {
                            vm.dialog = Dialog.ConfirmUndoBatch(batch.first, batch.second)
                        }
                    }
                }
            }
        }

        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (photo == null) {
                Text(if (vm.reviewing) "Skipped in $folder" else folder, style = MaterialTheme.typography.titleSmall)
            } else {
                val parent = if (vm.recursive) "  ·  ${photo.file.parentFile?.name}" else ""
                val list = if (vm.reviewing) "Skipped " else ""
                Text(
                    "$list${vm.index + 1} / ${vm.photos.size}$parent  ·  ${Formatter.formatShortFileSize(context, photo.size)}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${photo.file.name}  ·  ${dateFormat.format(Date(photo.mtime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Button(
            onClick = { vm.askApply() },
            shapes = ButtonDefaults.shapes(),
            enabled = vm.selections > 0 && idle,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(Icons.Rounded.DoneAll, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(if (vm.queued > 0) "Apply ${vm.queued}" else "Apply")
        }
    }
    Box(
        Modifier.fillMaxWidth().height(14.dp)
            .background(Brush.verticalGradient(listOf(scrim, scrim.copy(alpha = 0f))))
    )
    }
}

/** Shows where the photo on screen is queued to go. Tapping it takes the photo out of the queue. */
@Composable
private fun Pill(
    label: String,
    color: Color,
    modifier: Modifier,
    icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward,
    onClear: (() -> Unit)?,
) {
    Surface(
        onClick = { onClear?.invoke() },
        enabled = onClear != null,
        modifier = modifier,
        shape = CircleShape,
        color = color,
        contentColor = Color.White,
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
            Controls(vm)
            FolderGrid(vm, landscape)
        }
    }
}

@Composable
private fun Controls(vm: SiftViewModel) {
    val canGoBack = vm.index > 0
    val canSkip = vm.current != null
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        customItem({
            ControlButton("Back", Icons.AutoMirrored.Rounded.NavigateBefore, canGoBack, Modifier.weight(1f)) { vm.go(-1) }
        }, {})
        customItem({
            ControlButton("Skip", Icons.AutoMirrored.Rounded.NavigateNext, canSkip, Modifier.weight(1f)) { vm.skip() }
        }, {})
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
        Text(label, maxLines = 1)
    }
}

/** Folder buttons keep a fixed position per folder, so the thumb learns where each one is. */
@Composable
private fun FolderGrid(vm: SiftViewModel, landscape: Boolean) {
    val dests = vm.dests
    val n = dests.size
    val cols = when {
        landscape -> if (n <= 5) 1 else 2
        n <= 3 -> n
        n == 4 -> 2
        n <= 6 || n == 9 -> 3
        else -> 4
    }.coerceAtLeast(1)
    val rows = ceil(n / cols.toFloat()).toInt()
    val rowHeight: Dp = if (landscape) 56.dp else if (rows == 1) 72.dp else 64.dp
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
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(22.dp), pressedShape = RoundedCornerShape(10.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
            // A dark ring and a tick mark the queued folder. Brightness alone is too subtle on a light panel
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                dest.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (count > 0) {
            Badge(
                Modifier.align(Alignment.TopEnd).padding(6.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) { Text(count.toString()) }
        }
    }
}
