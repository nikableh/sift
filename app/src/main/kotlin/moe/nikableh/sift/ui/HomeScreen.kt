package moe.nikableh.sift.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
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
            // Padding before the scroll ends the viewport above the buttons, which have no background
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sift",
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 2.dp),
            )
            AlbumCard(vm)
            DestinationsCard(vm)
            vm.lastBatchInfo?.let { batch ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Last batch moved ${photos(batch.moved)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.dialog = Dialog.ConfirmUndoBatch(batch) }) { Text("Undo") }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Past this many the names stop telling the folders apart at a glance, and the card outgrows the album's. */
private const val SHOWN_DESTS = 8

/** Where photos can go, in the colours of the sorting buttons. The way into folder setup. */
@Composable
private fun DestinationsCard(vm: SiftViewModel) {
    val dests = vm.dests
    Card(
        onClick = { vm.open(Screen.Setup) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (dests.size == 1) "Sorting into 1 folder" else "Sorting into ${dests.size} folders",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dests.take(SHOWN_DESTS).forEachIndexed { i, dest ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(folderColor(i))
                        Spacer(Modifier.width(6.dp))
                        Text(dest.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (dests.size > SHOWN_DESTS) {
                    Text(
                        "+${dests.size - SHOWN_DESTS} more",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The album being sorted, with how far along it is. */
@Composable
private fun AlbumCard(vm: SiftViewModel) {
    val source = vm.source ?: return
    val total = vm.photos.size
    val queuedHere = vm.queuedHere
    Card(
        // Opens sorting like the button at the bottom. A tap does nothing while that button is
        // disabled, and the card stays enabled so it doesn't grey out during a folder read
        onClick = { if (!vm.scanning && !vm.busy && total > 0) vm.open(Screen.Sort) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AlbumHeader(File(source).name, source, vm.photos.firstOrNull()?.file, placeholder = !vm.scanning)
            when {
                vm.scanning -> Box(contentAlignment = Alignment.CenterStart) {
                    // An invisible copy of the loaded stats gives the indicator the same height at any font scale
                    AlbumStats(0, 0, 0, Modifier.alpha(0f).clearAndSetSemantics {})
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Reading the album", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                total == 0 -> Text("No photos in this album", style = MaterialTheme.typography.bodyLarge)
                else -> AlbumStats(total, queuedHere, vm.leftHere)
            }
        }
    }
}

@Composable
private fun AlbumHeader(name: String, path: String, cover: File?, placeholder: Boolean) {
    BoxWithConstraints {
        // Beside the cover the name only gets the rest of the row, a few letters a line at large
        // font sizes, so then it goes under the cover
        val beside = maxWidth - 104.dp >= 120.dp * LocalDensity.current.fontScale
        val titles = @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = if (beside) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                // The folder name is already above, so the middle of a long path is the part to lose
                Text(path, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
        if (beside) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(cover, placeholder)
                Spacer(Modifier.width(16.dp))
                titles(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Cover(cover, placeholder)
                titles(Modifier)
            }
        }
    }
}

/** Square like a gallery thumbnail. The box is drawn before the read so the card keeps its height */
@Composable
private fun Cover(file: File?, placeholder: Boolean) {
    Box(
        Modifier.size(88.dp).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (placeholder) {
            Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumStats(total: Int, queued: Int, left: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(photos(total), style = MaterialTheme.typography.bodyMedium)
        StatGrid {
            Stat(queued, "queued")
            Stat(left, "left")
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else queued / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Equal columns while the widest stat fits one, otherwise fewer per row. Six-digit counts at a
 * large font size don't fit a third of a phone, and a cut-off digit reads as a different number
 */
@Composable
private fun StatGrid(content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { items, constraints ->
        val gap = 16.dp.roundToPx()
        val rowGap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val widest = items.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val perRow = (items.size downTo 1).firstOrNull { it * widest + (it - 1) * gap <= width } ?: 1
        val cell = (width - (perRow - 1) * gap) / perRow
        val rows = items.map { it.measure(Constraints.fixedWidth(cell)) }.chunked(perRow)
        val height = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * rowGap
        layout(width, height) {
            var y = 0
            for (row in rows) {
                row.forEachIndexed { i, p -> p.placeRelative(i * (cell + gap), y) }
                y += row.maxOf { it.height } + rowGap
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(value.grouped(), style = MaterialTheme.typography.titleLargeEmphasized)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Start and Review sit at the bottom, in thumb reach. */
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
        val review = @Composable { modifier: Modifier ->
            FilledTonalButton(
                onClick = { vm.reviewSelections() },
                enabled = ready,
                shapes = ButtonDefaults.shapes(),
                modifier = modifier.height(size),
            ) {
                Icon(Icons.Rounded.Checklist, null, Modifier.size(ButtonDefaults.iconSizeFor(size)))
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                val style = ButtonDefaults.textStyleFor(size)
                FitLabel(reviewLabel(vm), style)
            }
        }
        val start = @Composable { modifier: Modifier ->
            Button(
                onClick = { vm.open(Screen.Sort) },
                enabled = ready && vm.photos.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
                modifier = modifier.height(size),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.iconSizeFor(size)))
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                val style = ButtonDefaults.textStyleFor(size)
                FitLabel(
                    if (vm.index > 0 || vm.queuedHere > 0) "Continue sorting" else "Start sorting",
                    style,
                )
            }
        }
        if (elsewhere > 0 && !vm.scanning) {
            // The queue spans albums, and the count on the button covers all of it
            Text(
                "Includes ${elsewhere.grouped()} queued in other albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (landscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (queued > 0) review(Modifier.weight(1f))
                start(Modifier.weight(1f))
            }
        } else {
            if (queued > 0) review(Modifier.fillMaxWidth())
            start(Modifier.fillMaxWidth())
        }
    }
}
