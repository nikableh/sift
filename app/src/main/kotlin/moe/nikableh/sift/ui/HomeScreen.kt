package moe.nikableh.sift.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import moe.nikableh.sift.AlbumStat
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel

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

        vm.sources.isEmpty() || vm.dests.isEmpty() -> Welcome(
            icon = Icons.Rounded.PhotoLibrary,
            title = "Pick your folders",
            body = "Choose the albums to sort and the folders their photos can go to.",
            action = "Set up folders",
        ) { vm.open(Screen.Setup) }

        else -> Home(vm)
    }
}

@Composable
private fun Home(vm: SiftViewModel) {
    // Whether the album list is unfolded. Kept above the layouts that get tried out for size, so they share it
    var expanded by rememberSaveable { mutableStateOf(false) }
    val listed = if (vm.sources.size > 1) vm.sources.size else 0
    Scaffold(bottomBar = { Actions(vm) }) { padding ->
        // Padding before the scroll ends the viewport above the buttons, which have no background
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val viewport = constraints.maxHeight
            // Folded, the page is meant to fit the screen without scrolling. How many album rows that
            // leaves room for depends on the font size, the folder names and whether Review is showing,
            // so the page is measured with fewer and fewer rows until it fits
            SubcomposeLayout(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { outer ->
                val width = Constraints.fixedWidth(outer.maxWidth)
                var rows = minOf(SHOWN_ALBUMS, listed)
                var page: Placeable
                while (true) {
                    val shown = rows
                    page = subcompose(if (expanded) "all" else shown) {
                        HomePage(vm, shown, expanded) { expanded = !expanded }
                    }.single().measure(width)
                    if (expanded || rows == 0 || page.height <= viewport) break
                    rows--
                }
                layout(page.width, page.height) { page.place(0, 0) }
            }
        }
    }
}

/** Home's content with at most [albumRows] albums listed while their list is folded. */
@Composable
private fun HomePage(vm: SiftViewModel, albumRows: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Sift",
            style = MaterialTheme.typography.displaySmallEmphasized,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 2.dp),
        )
        AlbumCard(vm, albumRows, expanded, onToggle)
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

/** The most albums listed while the list is folded. Fewer are when the screen has no room for them. */
private const val SHOWN_ALBUMS = 3

/** The albums being sorted, with how far along they are. */
@Composable
private fun AlbumCard(vm: SiftViewModel, albumRows: Int, expanded: Boolean, onToggle: () -> Unit) {
    val sources = vm.sources
    if (sources.isEmpty()) return
    val albums = vm.albumStats
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
            AlbumHeader(vm)
            when {
                vm.scanning -> Box(contentAlignment = Alignment.CenterStart) {
                    // An invisible copy of the loaded stats gives the indicator the same height at any font scale
                    AlbumStats(0, 0, 0, Modifier.alpha(0f).clearAndSetSemantics {})
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (sources.size == 1) "Reading the album" else "Reading the albums",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                total == 0 -> Text(
                    if (sources.size == 1) "No photos in this album" else "No photos in these albums",
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> AlbumStats(total, queuedHere, vm.leftHere)
            }
            // One album is the whole card already. The rows match the albums only once those have been read
            if (!vm.scanning && sources.size > 1 && albums.map { it.path } == sources) {
                AlbumRows(albums, albumRows, expanded, onToggle)
            }
        }
    }
}

/** One album by its cover, name and path. Several by a stack of covers, their number and their names. */
@Composable
private fun AlbumHeader(vm: SiftViewModel) {
    val sources = vm.sources
    val single = sources.singleOrNull()
    val covers = if (single != null) listOf(vm.photos.firstOrNull()?.file) else sources.take(3).map { vm.statOf(it)?.cover }
    val placeholder = !vm.scanning
    BoxWithConstraints {
        // Beside the cover the name only gets the rest of the row, a few letters a line at large
        // font sizes, so then it goes under the cover
        val beside = maxWidth - 152.dp >= 120.dp * LocalDensity.current.fontScale
        val titles = @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    single?.let { File(it).name } ?: "${sources.size} albums",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = if (beside) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (single != null) {
                    // The folder name is already above, so the middle of a long path is the part to lose
                    Text(single, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                } else {
                    Text(albumNames(sources), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // The card itself opens sorting, so the way to change what is sorted sits on it as its own button
        val edit = @Composable {
            FilledTonalIconButton(
                onClick = { vm.open(Screen.Setup) },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = cardInsetColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Rounded.Edit, "Change albums")
            }
        }
        if (beside) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Covers(covers, placeholder)
                Spacer(Modifier.width(16.dp))
                titles(Modifier.weight(1f))
                edit()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Covers(covers, placeholder)
                    Spacer(Modifier.weight(1f))
                    edit()
                }
                titles(Modifier)
            }
        }
    }
}

/** "Camera, Screenshots" while the names fit a line, otherwise the first two and how many follow. */
private fun albumNames(sources: List<String>): String {
    val names = sources.map { File(it).name }
    return if (names.size <= 4) names.joinToString(", ") else "${names[0]}, ${names[1]} and ${names.size - 2} more"
}

/** One cover as it is, two or three fanned out with the first album's on top. */
@Composable
private fun Covers(files: List<File?>, placeholder: Boolean) {
    val n = files.size
    if (n == 1) {
        Cover(files[0], placeholder, Modifier.size(88.dp), MaterialTheme.shapes.large)
        return
    }
    val side = if (n == 2) 60.dp else 56.dp
    val stepX = if (n == 2) 20.dp else 16.dp
    val stepY = if (n == 2) 12.dp else 8.dp
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.size(side + stepX * (n - 1), side + stepY * (n - 1))) {
        // Drawn back to front. A ring in the card's colour keeps the covers apart
        for (k in 0 until n) {
            Cover(
                files[n - 1 - k],
                placeholder,
                Modifier.offset(stepX * k, stepY * (n - 1 - k)).size(side)
                    .border(2.dp, MaterialTheme.colorScheme.primaryContainer, shape),
                shape,
            )
        }
    }
}

/** Square like a gallery thumbnail. The box is drawn before the read so the card keeps its height */
@Composable
private fun Cover(file: File?, placeholder: Boolean, modifier: Modifier, shape: Shape) {
    Box(
        // An album without a photo to show gets a blank cover, which needs to stand off the card like the bars do
        modifier.clip(shape).background(cardInsetColor()),
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

/** What is left in each album. Folded, it lists [maxRows] of them and counts the rest in a line that a tap unfolds. */
@Composable
private fun AlbumRows(albums: List<AlbumStat>, maxRows: Int, expanded: Boolean, onToggle: () -> Unit) {
    val shown = if (expanded) albums.size else minOf(maxRows, albums.size)
    val hidden = albums.size - shown
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
        for (album in albums.take(shown)) AlbumRow(album)
        if (expanded || hidden > 0) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = if (expanded) "Show fewer albums" else "Show all albums", onClick = onToggle)
                    .heightIn(min = 48.dp)
                    // Lines up with the album names
                    .padding(start = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        expanded -> "Show fewer"
                        // Not even one row fits, so the line stands for the whole list
                        shown == 0 -> "${albums.size} albums"
                        hidden == 1 -> "+1 more album"
                        else -> "+$hidden more albums"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumStat) {
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Cover(album.cover, placeholder = true, Modifier.size(36.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    album.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text("${album.left.grouped()} left", style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { if (album.total == 0) 0f else album.queued / album.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                trackColor = cardInsetColor(),
            )
        }
    }
}

/**
 * What sits on the album card as a quiet shape of its own: the empty part of a progress bar, a
 * blank cover and the edit button. Their default container tones all but vanish on the card's container tone, and a
 * tint of the card's text reads as a bar that is already full. The page's own colour does neither
 */
@Composable
private fun cardInsetColor(): Color = MaterialTheme.colorScheme.surface

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
            trackColor = cardInsetColor(),
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
