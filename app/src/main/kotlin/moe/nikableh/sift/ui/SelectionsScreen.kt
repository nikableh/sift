package moe.nikableh.sift.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.MoveRunner
import moe.nikableh.sift.Scanner
import moe.nikableh.sift.Selection
import moe.nikableh.sift.SelectionGroup
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import moe.nikableh.sift.VideoPlayer

/** Every queued photo, with where it goes. A list, so long folder names have room. */
@Composable
fun SelectionsScreen(vm: SiftViewModel, snackbar: SnackbarHostState) {
    val groups = vm.selectionGroups
    val ready = !vm.busy && !vm.scanning
    var collapsed by rememberSaveable(stateSaver = KeySetSaver) { mutableStateOf(emptySet<String>()) }
    val allCollapsed = groups.all { it.dest in collapsed }
    val stage = remember { ReviewStage() }
    val layoutDirection = LocalLayoutDirection.current
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val list = rememberLazyListState()
    val context = LocalContext.current
    val player = remember { VideoPlayer(context.applicationContext) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    // Paging in the viewer brings its row into the list, so closing shrinks the picture back into the thumbnail
    LaunchedEffect(stage.row) {
        val row = stage.row ?: return@LaunchedEffect
        if (!stage.raised || list.layoutInfo.visibleItemsInfo.any { it.key == row.key() }) return@LaunchedEffect
        val item = itemIndexOf(groups, collapsed, row) ?: return@LaunchedEffect
        list.scrollToItem(item)
        // Clear of the app bar and the pinned folder header
        list.scrollBy(-list.layoutInfo.viewportSize.height / 3f)
    }

    // In the tree rather than a dialog window, so the photo can grow out of its thumbnail
    Box(Modifier.fillMaxSize()) {
        // The defaults cover system bars only, so in landscape the list and app bar ran under the camera cutout
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    // Ahead of the FAB, which TalkBack would otherwise reach only after every row
                    modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -2f },
                    scrollBehavior = scroll,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    title = { Text("Selections", Modifier.semantics { heading() }) },
                    navigationIcon = {
                        IconButton(onClick = { vm.back() }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = {
                        if (groups.isNotEmpty()) {
                            IconButton(onClick = { vm.dialog = Dialog.ConfirmClear }, enabled = ready, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.ClearAll, "Clear selections")
                            }
                            IconButton(onClick = {
                                collapsed = if (allCollapsed) emptySet() else groups.mapTo(HashSet()) { it.dest }
                            }, shapes = IconButtonDefaults.shapes()) {
                                if (allCollapsed) Icon(Icons.Rounded.UnfoldMore, "Expand all")
                                else Icon(Icons.Rounded.UnfoldLess, "Collapse all")
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                MoveFab(
                    vm,
                    visible = vm.queued > 0 && !vm.scanning && MoveRunner.run == null,
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing is waiting to move",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                BoxWithConstraints(
                    // Not content padding: a sticky header pins to the viewport's edge, which would be under the app bar
                    Modifier.fillMaxSize().padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection),
                    ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val margin = if (maxWidth >= 600.dp) 24.dp else 16.dp
                    LazyColumn(
                        state = list,
                        contentPadding = PaddingValues(
                            // The header and the rows bring 16dp of the margin themselves
                            start = margin - 16.dp,
                            end = margin - 16.dp,
                            // The FAB floats over the list, so the last row needs room to scroll out from under it
                            bottom = padding.calculateBottomPadding() + FabHeight + 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        // Rows stretched across a landscape window put the remove button far from the name
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    ) {
                        for (group in groups) {
                            val dest = group.dest
                            val rows = group.rows
                            val color = destColor(vm, dest)
                            val folded = dest in collapsed
                            stickyHeader(key = "group:$dest", contentType = "group") {
                                GroupHeader(dest, rows.size, color, folded) {
                                    collapsed = if (folded) collapsed - dest else collapsed + dest
                                }
                            }
                            // One photo can wait in several groups, so the path alone repeats
                            if (!folded) itemsIndexed(rows, key = { _, row -> row.key() }, contentType = { _, _ -> "photo" }) { i, row ->
                                SelectionRow(
                                    row,
                                    vm.placeOf(row.file),
                                    ready,
                                    i,
                                    rows.size,
                                    stage,
                                    onPreview = { stage.row = row },
                                ) { vm.dropSelection(row) }
                            }
                        }
                    }
                }
            }
        }
        ReviewViewer(vm, groups, stage, player)
    }
}

/** Where [row] sits in the list, counting the group headers, or null while its group is folded. */
private fun itemIndexOf(groups: List<SelectionGroup>, collapsed: Set<String>, row: Selection): Int? {
    var item = 0
    for (group in groups) {
        val folded = group.dest in collapsed
        if (group.dest == row.dest) {
            val at = group.rows.indexOf(row)
            return if (folded || at < 0) null else item + 1 + at
        }
        item += 1 + if (folded) 0 else group.rows.size
    }
    return null
}

/** Folders that are no longer buttons get no colour. */
private fun destColor(vm: SiftViewModel, dest: String): Color? {
    val i = vm.dests.indexOfFirst { it.path == dest }
    return if (i >= 0) folderColor(i).dot() else null
}

private val KeySetSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })

@Composable
private fun GroupHeader(dest: String, count: Int, color: Color?, collapsed: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (collapsed) -90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
    ListItem(
        onClick = onToggle,
        modifier = Modifier
            // Opaque, so rows don't show through the gap while the header is pinned
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 12.dp)
            // ListItem takes no click label
            .semantics {
                onClick(label = if (collapsed) "Expand" else "Collapse") { onToggle(); true }
                role = Role.Button
                heading()
                stateDescription = if (collapsed) "Collapsed" else "Expanded"
            },
        leadingContent = { Dot(color) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    count.grouped(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { contentDescription = files(count) },
                )
                Spacer(Modifier.width(4.dp))
                // Hand-built because no public material3 API reads ExpandedListTokens
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (collapsed) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
                        .padding(4.dp),
                ) {
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(turn))
                }
            }
        },
        // The whole path wraps instead of being cut, it is what tells two folders apart
        supportingContent = { Text(dest) },
    ) {
        Text(Store.labelFor(dest), style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
fun Dot(color: Color?) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(color ?: MaterialTheme.colorScheme.outline))
}

@Composable
private fun SelectionRow(
    row: Selection,
    place: String?,
    enabled: Boolean,
    index: Int,
    count: Int,
    stage: ReviewStage,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
) {
    // Hidden only once the viewer is up, so the thumbnail stays while the full picture decodes
    val previewing = stage.row === row && stage.raised
    DisposableEffect(stage, row) { onDispose { stage.thumbs.remove(row) } }
    SegmentedListItem(
        onClick = onPreview,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentColors(),
        modifier = Modifier
            // Not list padding, which would also push in the header, a list item with the same inset of its own
            .padding(horizontal = 16.dp)
            // SegmentedListItem takes no click label, and TalkBack would otherwise announce a bare "activate"
            .semantics { onClick(label = "Preview") { onPreview(); true } },
        leadingContent = {
            // List spec leading media sizes: 114x64dp for a video, 56dp for an image
            Box(
                (if (Scanner.isVideo(row.file.name)) Modifier.size(width = 114.dp, height = 64.dp) else Modifier.size(56.dp))
                    .onGloballyPositioned {
                        stage.thumbs[row] = it
                        if (stage.row === row) stage.place()
                    },
            ) {
                AnimatedVisibility(!previewing, enter = fadeIn(), exit = fadeOut()) {
                    AsyncImage(
                        model = row.file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove, enabled = enabled, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Remove ${row.file.name} from selections") }
        },
        // With several albums the one it comes from. With subfolders included, the folders under that
        supportingContent = place?.let { { Text(it, maxLines = 1, overflow = TextOverflow.StartEllipsis) } },
    ) { Text(row.file.name, maxLines = 3, overflow = TextOverflow.Ellipsis) }
}

/** Height of the small extended FAB, which material3 doesn't expose. */
private val FabHeight = 56.dp

/** FABs have no disabled state, so it steps away while a scan or a move is running, and shows the wait before one. */
@Composable
private fun MoveFab(vm: SiftViewModel, visible: Boolean) {
    val context = LocalContext.current
    // The answer only decides whether the progress notification shows, so the move starts either way
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.move() }
    AnimatedVisibility(visible, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        val label = if (vm.preparing) "Getting ready to move" else "Move ${vm.queued.grouped()}"
        SmallExtendedFloatingActionButton(
            text = { Text(label) },
            icon = {
                val size = Modifier.size(24.dp)
                if (vm.preparing) {
                    // The default indicator colour is primary, the colour of this FAB
                    val color = LocalContentColor.current
                    if (rememberAnimationsOn()) LoadingIndicator(size, color = color)
                    else LoadingIndicator(progress = { 0f }, modifier = size, color = color)
                } else {
                    Icon(Icons.Rounded.DoneAll, null, size)
                }
            },
            onClick = {
                if (!vm.preparing) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.move()
                    }
                }
            },
            // This overload clears the text's semantics, so the label has to be set on the FAB itself
            modifier = Modifier.semantics {
                traversalIndex = -1f
                contentDescription = label
            },
            expanded = true,
            containerColor = MaterialTheme.colorScheme.primary,
        )
    }
}
