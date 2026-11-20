package moe.nikableh.sift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
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
    when {
        !vm.allowed -> AccessScreen(vm, onAbout = { vm.open(Screen.About) })

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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A window this wide is most often a phone on its side, too short for the two-row bar, so a
        // single row slides fully away instead
        val wide = maxWidth >= 600.dp
        val scroll = if (wide) {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        } else {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        }
        val direction = LocalLayoutDirection.current
        val title = @Composable { Text("Sift", Modifier.semantics { heading() }) }
        val about = @Composable {
            IconButton(
                onClick = { vm.open(Screen.About) },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonVibrantColors(),
            ) { Icon(Icons.Rounded.Info, "About") }
        }
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = {
                if (wide) {
                    TopAppBar(title = title, actions = { about() }, scrollBehavior = scroll)
                } else {
                    LargeFlexibleTopAppBar(title = title, actions = { about() }, scrollBehavior = scroll)
                }
            },
            bottomBar = { Actions(vm) },
        ) { padding ->
            Column(
                // Bottom padding before the scroll ends the viewport above the buttons, which have no background.
                // Top padding inside it lets the cards pass under the collapsing bar
                Modifier.fillMaxSize()
                    .padding(
                        start = padding.calculateStartPadding(direction),
                        end = padding.calculateEndPadding(direction),
                        bottom = padding.calculateBottomPadding(),
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaManagementCard(vm)
                AlbumCard(vm)
                DestinationsCard(vm)
                vm.lastBatchInfo?.let { batch ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Last batch moved ${files(batch.moved)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.dialog = Dialog.ConfirmUndoBatch(batch) }) { Text("Undo") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
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
        modifier = Modifier.semantics {
            role = Role.Button
            onClick(label = "Change folders", action = null)
        },
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
                        Dot(folderColor(i).tile())
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

/** The albums being sorted, with how many photos they hold. */
@Composable
private fun AlbumCard(vm: SiftViewModel) {
    if (vm.sources.isEmpty()) return
    val active = vm.activeAlbums
    // A sorted album holds nothing to count
    val albums = vm.albumStats.filter { it.path in active }
    // One album is the whole card already. The rows match the albums only once those have been read
    val rows = !vm.scanning && active.size > 1 && albums.map { it.path } == active
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        AlbumHeader(vm, Modifier.padding(16.dp))
        if (rows) {
            // The default outlineVariant all but vanishes on the card's primaryContainer
            HorizontalDivider(color = MaterialTheme.colorScheme.background)
            AlbumRows(albums)
        }
    }
}

/**
 * One album by its cover, name, count and path. Several by a stack of covers, their number, the count
 * and their names
 */
@Composable
private fun AlbumHeader(vm: SiftViewModel, modifier: Modifier) {
    val sources = vm.shownAlbums
    val single = sources.singleOrNull()
    val covers = if (single != null) listOf(vm.photos.firstOrNull()?.file) else sources.take(3).map { vm.statOf(it)?.cover }
    val placeholder = when {
        vm.scanning -> null
        vm.allSorted -> Icons.Rounded.Done
        else -> Icons.Rounded.PhotoLibrary
    }
    val total = vm.photos.size
    BoxWithConstraints(modifier) {
        // Beside the cover the name only gets the rest of the row, a few letters a line at large
        // font sizes, so then it goes under the cover
        val beside = maxWidth - 152.dp >= 120.dp * LocalDensity.current.fontScale
        val titles = @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    single?.let { File(it).name } ?: "${sources.size} albums",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = if (beside) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val count = MaterialTheme.typography.titleSmall
                // TalkBack announces a live region most reliably when the changed text is its own
                Box(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    if (vm.scanning) {
                        val reading = if (single != null) "Reading the album" else "Reading the albums"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // As tall as the line of the count that replaces it, so the card keeps its height
                            Loading(
                                Modifier.size(with(LocalDensity.current) { count.lineHeight.toDp() })
                                    .semantics { contentDescription = reading },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(reading, style = count, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        val holds = when {
                            vm.allSorted -> "All sorted"
                            total == 0 -> "No items"
                            else -> items(total)
                        }
                        Text(holds, style = count, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (single != null) {
                    // The folder name is already above, so the middle of a long path is the part to lose
                    Text(single, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                } else {
                    Text(albumNames(sources), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val edit = @Composable {
            FilledIconButton(
                onClick = { vm.open(Screen.Setup) },
                shapes = IconButtonDefaults.shapes(),
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
    return if (names.size <= 4) names.joinToString(", ") else "${names[0]}, ${names[1]}, and ${names.size - 2} more"
}

/** One cover as it is, two or three fanned out with the first album's on top. */
@Composable
private fun Covers(files: List<File?>, placeholder: ImageVector?) {
    val n = files.size
    if (n == 1) {
        Cover(files[0], placeholder, Modifier.size(88.dp), MaterialTheme.shapes.large)
        return
    }
    val side = if (n == 2) 60.dp else 56.dp
    val stepX = if (n == 2) 20.dp else 16.dp
    val stepY = if (n == 2) 12.dp else 8.dp
    val shape = MaterialTheme.shapes.medium
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
private fun Cover(file: File?, placeholder: ImageVector?, modifier: Modifier, shape: Shape) {
    Box(
        // An album without a photo to show gets a blank cover, which needs to stand off the card
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
        } else if (placeholder != null) {
            Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * How many photos each album holds, behind a line that unfolds it. Folded is how it starts, so the card is
 * the same height at any number of albums and Home fits a screen. Whether it is unfolded is kept
 */
@Composable
private fun AlbumRows(albums: List<AlbumStat>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    val label = if (expanded) "Hide the albums" else "Show each album"
    Column {
        // As wide as the card, so the label sits on the card's margin and the press highlight reaches its edges
        ListItem(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics {
                onClick(label = label, action = null)
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = content,
                trailingContentColor = content,
            ),
            trailingContent = {
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (expanded) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                        .padding(4.dp),
                ) { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) }
            },
        ) { Text("By album") }
        if (expanded) {
            Column(
                Modifier.padding(top = 4.dp, bottom = 16.dp)
                    .semantics { collectionInfo = CollectionInfo(albums.size, 1) },
            ) {
                albums.forEachIndexed { index, album -> AlbumRow(album, index, content) }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumStat, index: Int, content: Color) {
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) {
            collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = content,
            trailingContentColor = content,
        ),
        leadingContent = {
            Cover(album.cover, Icons.Rounded.PhotoLibrary, Modifier.size(56.dp), MaterialTheme.shapes.small)
        },
        trailingContent = { Text(items(album.total)) },
    ) {
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A blank cover sits on the album card as a quiet shape of its own.
 * The default container tones all but vanish on the card's container tone. The page's own colour doesn't
 */
@Composable
private fun cardInsetColor(): Color = MaterialTheme.colorScheme.surface

/** Start and Review sit at the bottom, in thumb reach. */
@Composable
private fun Actions(vm: SiftViewModel) {
    val size = ButtonDefaults.MediumContainerHeight
    val queued = vm.queued
    val elsewhere = queued - vm.queuedHere
    val ready = !vm.scanning && !vm.busy
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val review = @Composable { modifier: Modifier, interactions: MutableInteractionSource? ->
                FilledTonalButton(
                    onClick = { vm.reviewSelections() },
                    enabled = ready,
                    shapes = ButtonDefaults.shapes(),
                    interactionSource = interactions,
                    modifier = modifier.height(size),
                ) {
                    Icon(Icons.Rounded.Checklist, null, Modifier.size(ButtonDefaults.iconSizeFor(size)))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                    val style = ButtonDefaults.textStyleFor(size)
                    FitLabel(reviewLabel(vm), style)
                }
            }
            val start = @Composable { modifier: Modifier, interactions: MutableInteractionSource? ->
                // With nothing to sort the button leads to more albums, like the one on the sorting screen
                val add = !vm.scanning && vm.photos.isEmpty()
                Button(
                    onClick = { if (add) vm.pickAlbums() else vm.open(Screen.Sort) },
                    enabled = ready,
                    shapes = ButtonDefaults.shapes(),
                    interactionSource = interactions,
                    modifier = modifier.height(size),
                ) {
                    Icon(
                        if (add) Icons.Rounded.Add else Icons.Rounded.PlayArrow,
                        null,
                        Modifier.size(ButtonDefaults.iconSizeFor(size)),
                    )
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                    val style = ButtonDefaults.textStyleFor(size)
                    FitLabel(
                        when {
                            add -> "Add albums"
                            vm.index > 0 || vm.queuedHere > 0 -> "Continue sorting"
                            else -> "Start sorting"
                        },
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
            if (wide) {
                val reviewInteractions = remember { MutableInteractionSource() }
                val startInteractions = remember { MutableInteractionSource() }
                // Two buttons always fit the width, so the overflow menu never shows
                ButtonGroup(
                    overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (queued > 0) {
                        customItem(
                            buttonGroupContent = {
                                review(Modifier.weight(1f).animateWidth(reviewInteractions), reviewInteractions)
                            },
                            menuContent = {},
                        )
                    }
                    customItem(
                        buttonGroupContent = {
                            start(Modifier.weight(1f).animateWidth(startInteractions), startInteractions)
                        },
                        menuContent = {},
                    )
                }
            } else {
                if (queued > 0) review(Modifier.fillMaxWidth(), null)
                start(Modifier.fillMaxWidth(), null)
            }
        }
    }
}
