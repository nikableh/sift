package moe.nikableh.sift.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel
import java.io.File

/** Where the app opens and where Back from sorting lands. */
@Composable
fun HomeScreen(vm: SiftViewModel) {
    val context = LocalContext.current
    when {
        !vm.allowed -> Welcome(
            icon = Icons.Rounded.FolderOpen,
            title = "Let Sift move your photos",
            body = "Sift moves photos between folders on your phone. Android only lets an app do that " +
                "with the \"All files access\" permission.",
            action = "Grant access",
        ) { openAllFilesSettings(context) }

        vm.source == null || vm.dests.isEmpty() -> Welcome(
            icon = Icons.Rounded.PhotoLibrary,
            title = "Pick your folders",
            body = "Choose the album to sort and the folders its photos can go to.",
            action = "Set up folders",
        ) { vm.open(Screen.Setup) }

        else -> Home(vm)
    }
}

@Composable
private fun Home(vm: SiftViewModel) {
    Scaffold(bottomBar = { Actions(vm) }) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            Text(
                "Sift",
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 14.dp),
            )
            AlbumCard(vm)
            Spacer(Modifier.height(12.dp))

            val batch = vm.lastBatchInfo
            val selections = vm.selections
            val rows = 1 + (if (selections > 0) 1 else 0) + (if (batch != null) 1 else 0)
            if (selections > 0) {
                val skips = selections - vm.queued
                SegmentedListItem(
                    onClick = { vm.open(Screen.Selections) },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = rows),
                    colors = segmentColors(),
                    leadingContent = { Icon(Icons.Rounded.Checklist, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    supportingContent = { Text("${vm.queued} to move  ·  $skips skipped") },
                ) { Text("View selections") }
            }
            SegmentedListItem(
                onClick = { vm.open(Screen.Setup) },
                shapes = ListItemDefaults.segmentedShapes(index = rows - 1 - (if (batch != null) 1 else 0), count = rows),
                colors = segmentColors(),
                leadingContent = { Icon(Icons.Rounded.Tune, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                supportingContent = {
                    val n = vm.dests.size
                    Text(if (n == 1) "1 destination folder" else "$n destination folders")
                },
            ) { Text("Folders and settings") }
            if (batch != null) {
                SegmentedListItem(
                    onClick = { vm.dialog = Dialog.ConfirmUndoBatch(batch.first, batch.second) },
                    shapes = ListItemDefaults.segmentedShapes(index = rows - 1, count = rows),
                    colors = segmentColors(),
                    leadingContent = { Icon(Icons.Rounded.History, null) },
                    supportingContent = {
                        Text(if (batch.second == 1) "1 photo goes back" else "${batch.second} photos go back")
                    },
                ) { Text("Undo last applied batch") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The album being sorted, with how far along it is. */
@Composable
private fun AlbumCard(vm: SiftViewModel) {
    val source = vm.source ?: return
    val total = vm.total
    val hidden = vm.hiddenHere
    val queuedHere = vm.queuedHere
    val skippedHere = vm.skippedHere
    Card(
        // Opens sorting like the button at the bottom. A tap does nothing while that button is
        // disabled, and the card stays enabled so it doesn't grey out during a folder read
        onClick = { if (!vm.scanning && !vm.busy && total + hidden > 0) vm.open(Screen.Sort) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        val cover = vm.photos.firstOrNull()
        val coverModifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        if (cover != null) {
            AsyncImage(
                model = cover.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = coverModifier,
            )
        } else if (vm.scanning) {
            // Most albums have a cover, so reserving its space keeps the card from growing after the read
            Box(coverModifier)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(File(source).name, style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(source, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            when {
                vm.scanning -> Box(contentAlignment = Alignment.CenterStart) {
                    // An invisible copy of the loaded stats gives the indicator the same height at any font scale
                    AlbumStats("0 photos", "0 queued  ·  0 skipped  ·  0 left", 0f,
                        Modifier.alpha(0f).clearAndSetSemantics {})
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Reading the album", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                total == 0 && hidden == 0 -> Text("No photos in this album", style = MaterialTheme.typography.bodyLarge)
                total == 0 -> {
                    Text("Nothing left to sort", style = MaterialTheme.typography.titleMedium)
                    Text(hiddenLine(hidden), style = MaterialTheme.typography.bodyLarge)
                }
                // Skips an Apply has committed are out of the album, so they are out of the total too.
                // Skipped photos were looked at and left in place, so they no longer count as left
                else -> AlbumStats(
                    (if (total == 1) "1 photo" else "$total photos") + if (hidden > 0) "  ·  ${hiddenLine(hidden)}" else "",
                    "$queuedHere queued  ·  $skippedHere skipped  ·  ${vm.leftHere} left",
                    (queuedHere + skippedHere) / total.toFloat(),
                )
            }
        }
    }
}

@Composable
private fun AlbumStats(count: String, stats: String, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(count, style = MaterialTheme.typography.bodyLarge)
        Text(stats, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

private fun hiddenLine(n: Int) = if (n == 1) "1 skipped hidden" else "$n skipped hidden"

/** Start and Apply sit at the bottom, in thumb reach. */
@Composable
private fun Actions(vm: SiftViewModel) {
    val size = ButtonDefaults.MediumContainerHeight
    val queued = vm.queued
    val elsewhere = queued - vm.queuedHere
    val ready = !vm.scanning && !vm.busy
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val apply = @Composable { modifier: Modifier ->
            FilledTonalButton(
                onClick = { vm.askApply() },
                enabled = ready,
                shapes = ButtonDefaults.shapes(),
                modifier = modifier.height(size),
            ) {
                Icon(Icons.Rounded.DoneAll, null, Modifier.size(ButtonDefaults.iconSizeFor(size)))
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                Text(
                    applyLabel(vm),
                    style = ButtonDefaults.textStyleFor(size),
                    maxLines = 1,
                )
            }
        }
        val start = @Composable { modifier: Modifier ->
            Button(
                onClick = { vm.open(Screen.Sort) },
                enabled = ready && vm.total + vm.hiddenHere > 0,
                shapes = ButtonDefaults.shapes(),
                modifier = modifier.height(size),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.iconSizeFor(size)))
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                Text(
                    if (vm.index > 0 || vm.queuedHere > 0 || vm.skippedHere > 0 || vm.hiddenHere > 0) "Continue sorting" else "Start sorting",
                    style = ButtonDefaults.textStyleFor(size),
                    maxLines = 1,
                )
            }
        }
        if (elsewhere > 0) {
            // The queue spans albums, and Apply moves all of it
            Text(
                "Apply includes $elsewhere queued in other albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (landscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (vm.selections > 0) apply(Modifier.weight(1f))
                start(Modifier.weight(1f))
            }
        } else {
            if (vm.selections > 0) apply(Modifier.fillMaxWidth())
            start(Modifier.fillMaxWidth())
        }
    }
}
