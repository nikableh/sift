package moe.nikableh.sift.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.draw.rotate
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
import moe.nikableh.sift.Store

/** Where the app opens and where Back from sorting lands. */
@Composable
fun HomeScreen(vm: SiftViewModel, snackbar: SnackbarHostState) {
    val step = when {
        !vm.allowed -> Step.Access
        vm.sources.isEmpty() || vm.dests.isEmpty() -> Step.Welcome
        else -> Step.Content
    }
    PaneSwap(step) {
        when (it) {
            Step.Access -> AccessScreen(vm, onAbout = { vm.open(Screen.About) })

            Step.Welcome -> SetupWelcome(vm)

            Step.Content -> Home(vm, snackbar)
        }
    }
}

/** The steps before a screen shows its content, in the order the user goes through them. */
enum class Step { Access, Welcome, Content }

/**
 * Slides between the steps the way screens slide in SiftApp: forward when access or folders come in,
 * back when they go away
 */
@Composable
fun PaneSwap(
    step: Step,
    transition: Transition<Step> = updateTransition(step, label = "step"),
    content: @Composable (Step) -> Unit,
) {
    val animationsOn = rememberAnimationsOn()
    val surface = MaterialTheme.colorScheme.surface
    transition.AnimatedContent(
        transitionSpec = { screenSlide(targetState > initialState, animationsOn) },
    ) {
        // Opaque, or the step sliding away shows through the one coming in
        Box(Modifier.fillMaxSize().background(surface)) { content(it) }
    }
}

@Composable
private fun Home(vm: SiftViewModel, snackbar: SnackbarHostState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val content = rememberScrollState()
        val scroll = TopAppBarDefaults.pinnedScrollBehavior()
        val direction = LocalLayoutDirection.current
        val title = @Composable {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderIcon(28.dp)
                Text("Sift", Modifier.semantics { heading() })
            }
        }
        val about = @Composable {
            IconButton(
                onClick = { vm.open(Screen.About) },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonVibrantColors(),
            ) { Icon(Icons.Outlined.Info, "About") }
        }
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = { TopAppBar(title = title, actions = { about() }, scrollBehavior = scroll) },
            bottomBar = { Actions(vm) },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val start = padding.calculateStartPadding(direction)
            val end = padding.calculateEndPadding(direction)
            val margin = pageMargins(maxWidth - start - end)
            Column(
                // Bottom padding before the scroll ends the viewport above the buttons, which have no background.
                // Top padding inside it lets the cards pass under the bar
                Modifier.fillMaxSize()
                    .padding(start = start, end = end, bottom = padding.calculateBottomPadding())
                    .verticalScroll(content)
                    .padding(top = padding.calculateTopPadding())
                    .padding(horizontal = margin),
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
                        TextButton(onClick = { if (!vm.preparing) vm.dialog = Dialog.ConfirmUndoBatch(batch) }) {
                            if (vm.preparing) {
                                Loading(Modifier.size(24.dp).semantics { contentDescription = "Getting ready to move back" })
                            } else {
                                Text("Undo")
                            }
                        }
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
                        Dot(folderColor(i).dot())
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

/** The albums being sorted, with how many photos they hold. Albums are added, removed and read from here. */
@Composable
private fun AlbumCard(vm: SiftViewModel) {
    if (vm.sources.isEmpty()) return
    val active = vm.activeAlbums
    // A sorted album holds nothing to count
    val albums = vm.albumStats.filter { it.path in active }
    // The rows match the albums only once those have been read. Until then the row waits disabled, so the
    // cards below don't jump when reading ends. Later reads keep it open, since its own controls start them
    val read = albums.map { it.path } == active
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        AlbumHeader(vm, Modifier.padding(16.dp))
        // The default outlineVariant all but vanishes on the card's primaryContainer
        HorizontalDivider(color = MaterialTheme.colorScheme.background)
        Details(vm, albums, enabled = read)
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
                            Loading(Modifier.size(24.dp).semantics { contentDescription = reading })
                            Spacer(Modifier.width(6.dp))
                            Text(reading, style = count, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        val holds = when {
                            vm.allSorted -> "All sorted"
                            total == 0 -> "No items"
                            else -> items(total)
                        }
                        // As tall as the reading line it replaces, so the card keeps its height
                        Text(
                            holds,
                            modifier = Modifier.heightIn(min = 24.dp).wrapContentHeight(Alignment.CenterVertically),
                            style = count,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
        val add = @Composable {
            FilledIconButton(
                onClick = { vm.pickAlbums() },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.Add, "Add albums")
            }
        }
        if (beside) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Covers(covers, placeholder)
                Spacer(Modifier.width(16.dp))
                titles(Modifier.weight(1f))
                add()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Covers(covers, placeholder)
                    Spacer(Modifier.weight(1f))
                    add()
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
 * Each album with its count and a way to remove it, the sorted albums and the read options, behind a line that
 * unfolds them. Folded is how it starts, so the card is the same height at any number of albums and Home fits a
 * screen. Whether it is unfolded is kept
 */
@Composable
private fun Details(vm: SiftViewModel, albums: List<AlbumStat>, enabled: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // A row that waits for the albums stays folded, whatever was kept
    val open = expanded && enabled
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    // The default disabled onSurface reads as grey on the card's primaryContainer
    val disabled = content.copy(alpha = 0.38f)
    val label = if (open) "Hide details" else "Show details"
    val motion = MaterialTheme.motionScheme
    val arrow by animateFloatAsState(if (open) 180f else 0f, motion.fastSpatialSpec())
    Column {
        // As wide as the card, so the label sits on the card's margin and the press highlight reaches its edges
        ListItem(
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.semantics {
                onClick(label = label, action = null)
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = content,
                trailingContentColor = content,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = disabled,
                disabledTrailingContentColor = disabled,
            ),
            trailingContent = {
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (open) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                        .padding(4.dp),
                ) { Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(arrow)) }
            },
        ) { Text("Details") }
        // From the top, so the rows stay in place under the growing clip instead of sliding down with its edge
        AnimatedVisibility(
            open,
            enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) +
                fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec(), shrinkTowards = Alignment.Top) +
                fadeOut(motion.fastEffectsSpec()),
        ) {
            Column {
                val colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = content,
                    supportingContentColor = content,
                    trailingContentColor = content,
                )
                val sorted = vm.sortedAlbums
                Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    if (albums.isNotEmpty()) {
                        Column(Modifier.semantics { collectionInfo = CollectionInfo(albums.size, 1) }) {
                            albums.forEachIndexed { index, album -> AlbumRow(vm, album, index, colors) }
                        }
                    }
                    if (sorted.isNotEmpty()) SortedRow(vm, sorted, colors)
                }
                // Inset, because it splits related content inside the open card. The full-width line above marks the fold
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.background)
                Column(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                    val several = vm.shownAlbums.size > 1
                    ListItem(
                        checked = vm.recursive,
                        onCheckedChange = { vm.changeRecursive(it) },
                        colors = colors,
                        supportingContent = {
                            Text(if (several) "Of every album. Destination folders inside them are left out" else "Destination folders inside it are left out")
                        },
                        trailingContent = { Switch(checked = vm.recursive, onCheckedChange = null) },
                    ) { Text("Include subfolders") }
                    ListItem(
                        colors = colors,
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrderButtons(vm)
                                if (several) Text("All albums are sorted as one list")
                            }
                        },
                    ) { Text("Order") }
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(vm: SiftViewModel, album: AlbumStat, index: Int, colors: ListItemColors) {
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) {
            collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
        },
        colors = colors,
        leadingContent = {
            Cover(album.cover, Icons.Rounded.PhotoLibrary, Modifier.size(56.dp), MaterialTheme.shapes.small)
        },
        supportingContent = { PathText(album.path) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(items(album.total), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.askRemoveSource(album.path) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, "Remove ${album.name}")
                }
            }
        },
    ) {
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Albums a Move emptied, in one row however many there are. They come back by themselves when they fill up again */
@Composable
private fun SortedRow(vm: SiftViewModel, sorted: List<String>, colors: ListItemColors) {
    ListItem(
        colors = colors,
        leadingContent = {
            Cover(null, Icons.Rounded.Done, Modifier.size(56.dp), MaterialTheme.shapes.small)
        },
        supportingContent = { Text(sorted.joinToString(", ") { File(it).name }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            // The default primary text is too faint on the card's primaryContainer
            TextButton(
                onClick = { vm.clearSorted() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            ) { Text("Clear") }
        },
    ) { Text(if (sorted.size == 1) "1 album sorted" else "${sorted.size} albums sorted") }
}

/** Connected toggle buttons, the Expressive replacement for a segmented control. */
@Composable
private fun OrderButtons(vm: SiftViewModel) {
    val options = listOf(
        Store.ORDER_NEWEST to "Newest",
        Store.ORDER_OLDEST to "Oldest",
        Store.ORDER_NAME to "By name",
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, (value, label) ->
            ToggleButton(
                checked = vm.order == value,
                onCheckedChange = { vm.changeOrder(value) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                // outline rather than the outlined default outlineVariant, so each option reaches 3:1 on the card
                border = if (vm.order == value) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { FitLabel(label, MaterialTheme.typography.labelMedium) }
        }
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
        val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        val direction = LocalLayoutDirection.current
        val sides = insets.asPaddingValues().let { it.calculateStartPadding(direction) + it.calculateEndPadding(direction) }
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(insets)
                .padding(horizontal = pageMargins(maxWidth - sides), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val review = @Composable { modifier: Modifier, interactions: MutableInteractionSource? ->
                FilledTonalButton(
                    onClick = { vm.reviewSelections() },
                    enabled = ready,
                    shapes = ButtonDefaults.shapesFor(size),
                    interactionSource = interactions,
                    modifier = modifier.heightIn(min = size),
                ) {
                    Icon(Icons.Rounded.Checklist, null, Modifier.size(ButtonDefaults.iconSizeFor(size)))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                    val style = ButtonDefaults.textStyleFor(size)
                    ButtonLabel(reviewLabel(vm), style)
                }
            }
            val start = @Composable { modifier: Modifier, interactions: MutableInteractionSource? ->
                // With nothing to sort the button leads to more albums, like the one on the sorting screen
                val add = !vm.scanning && vm.photos.isEmpty()
                Button(
                    onClick = { if (add) vm.pickAlbums() else vm.open(Screen.Sort) },
                    enabled = ready,
                    shapes = ButtonDefaults.shapesFor(size),
                    interactionSource = interactions,
                    modifier = modifier.heightIn(min = size),
                ) {
                    Icon(
                        if (add) Icons.Rounded.Add else Icons.Rounded.PlayArrow,
                        null,
                        Modifier.size(ButtonDefaults.iconSizeFor(size)),
                    )
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                    val style = ButtonDefaults.textStyleFor(size)
                    ButtonLabel(
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
                    // A label that wraps makes its button taller, and the other one matches it
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (queued > 0) {
                        customItem(
                            buttonGroupContent = {
                                review(Modifier.weight(1f).fillMaxHeight().animateWidth(reviewInteractions), reviewInteractions)
                            },
                            menuContent = {},
                        )
                    }
                    customItem(
                        buttonGroupContent = {
                            start(Modifier.weight(1f).fillMaxHeight().animateWidth(startInteractions), startInteractions)
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
