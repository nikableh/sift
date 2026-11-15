package moe.nikableh.sift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.nikableh.sift.Album
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import java.io.File

/**
 * Picks folders. The first view is a grid of albums, the folders a gallery app would show.
 * Browsing the file system is one tap away for folders that hold no photos yet.
 *
 * The source is a single tap. Destinations are ticked in any number and added together.
 */
@Composable
fun PickerScreen(vm: SiftViewModel, forSource: Boolean, snackbar: SnackbarHostState) {
    val browsing = vm.browsing
    val dir = vm.browseDir
    BackHandler(enabled = browsing || vm.picked.isNotEmpty()) {
        if (browsing) vm.browseUp() else vm.leavePicker()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (forSource) "Album to sort" else "Add destinations") },
                navigationIcon = {
                    IconButton(onClick = { if (browsing) vm.browseUp() else vm.leavePicker() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (!forSource) {
                DestinationBar(vm, dir.takeIf { browsing })
            } else if (browsing && dir != null) {
                SourceBrowseBar(vm, dir)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (browsing) FolderList(vm, forSource, padding) else AlbumGrid(vm, forSource, padding)
    }
}

@Composable
private fun AlbumGrid(vm: SiftViewModel, forSource: Boolean, padding: PaddingValues) {
    val albums = vm.albums
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SegmentedListItem(
                onClick = { vm.browse(Store.lastPickerDir?.let { File(it) }?.takeIf { it.isDirectory }) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = segmentColors(),
                leadingContent = { Icon(Icons.Rounded.FolderOpen, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                supportingContent = { Text("For a folder that isn't an album yet") },
            ) { Text("Browse all folders") }
        }
        when {
            albums == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No albums found on this device",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> items(albums, key = { it.path }) { album -> AlbumCard(vm, album, forSource) }
        }
    }
}

@Composable
private fun AlbumCard(vm: SiftViewModel, album: Album, forSource: Boolean) {
    val inUse = vm.isTaken(album.path, forSource)
    // Tap order, because it becomes the order of the buttons on the sorting screen
    val order = vm.picked.indexOf(album.path)
    val selected = order >= 0
    Card(
        onClick = { if (forSource) vm.pickSource(album.path) else vm.togglePicked(album.path) },
        enabled = !inUse,
        shape = MaterialTheme.shapes.extraLarge,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box {
            AsyncImage(
                model = File(album.cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            if (inUse) {
                Tag(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("In use", style = MaterialTheme.typography.labelMedium)
                }
            } else if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (order + 1).toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)) {
            Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (album.count == 1) "1 photo" else "${album.count.grouped()} photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Whole path, wrapped. Two albums can share a name and differ only here
            Text(
                album.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Tag(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun FolderList(vm: SiftViewModel, forSource: Boolean, padding: PaddingValues) {
    val folders = vm.folders
    val dir = vm.browseDir
    val ticking = !forSource && dir != null
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (dir != null) {
            item {
                val n = vm.imagesHere
                Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)) {
                    Text(dir.path, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (n == 1) "1 image in this folder" else "${n.grouped()} images in this folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (dir != null && !forSource) {
            item {
                val taken = vm.isTaken(dir.path, forSource)
                SegmentedListItem(
                    checked = dir.path in vm.picked,
                    onCheckedChange = { vm.togglePicked(dir.path) },
                    enabled = !taken,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    colors = segmentColors(),
                    modifier = Modifier.padding(bottom = 10.dp),
                    trailingContent = { Checkbox(checked = dir.path in vm.picked, onCheckedChange = null, enabled = !taken) },
                    supportingContent = { if (taken) Text("Already in use") },
                ) { Text("Select this folder") }
            }
        }
        itemsIndexed(folders, key = { _, f -> f.dir.path }) { i, folder ->
            val path = folder.dir.path
            SegmentedListItem(
                onClick = { vm.browse(folder.dir) },
                shapes = ListItemDefaults.segmentedShapes(index = i, count = folders.size),
                colors = segmentColors(),
                leadingContent = { Icon(if (dir == null) Icons.Rounded.SdStorage else Icons.Rounded.Folder, null) },
                trailingContent = {
                    if (ticking) {
                        // Ticks a subfolder without having to open it first
                        Checkbox(
                            checked = path in vm.picked,
                            onCheckedChange = { vm.togglePicked(path) },
                            enabled = !vm.isTaken(path, forSource),
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                },
                supportingContent = { Text(folder.subtitle) },
            ) { Text(folder.title) }
        }
    }
}

/** Shows what is ticked so far and adds it all in one go. [browseDir] is set while browsing folders. */
@Composable
private fun DestinationBar(vm: SiftViewModel, browseDir: File?) {
    val picked = vm.picked
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 12.dp)) {
            if (picked.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    picked.forEachIndexed { i, path ->
                        val file = File(path)
                        InputChip(
                            selected = true,
                            onClick = { vm.togglePicked(path) },
                            label = { Text("${i + 1}  ${file.parentFile?.name ?: ""}/${file.name}") },
                            trailingIcon = { Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val size = ButtonDefaults.MediumContainerHeight
                FilledTonalButton(
                    onClick = {
                        vm.dialog = if (browseDir != null) {
                            Dialog.NewFolder(browseDir, forSource = false)
                        } else {
                            Dialog.NewFolder(vm.picturesDir(), forSource = false, album = true)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(size),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(if (browseDir != null) "New folder" else "New album", maxLines = 1)
                }
                Button(
                    onClick = { vm.addPicked() },
                    enabled = picked.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(size),
                ) {
                    Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SourceBrowseBar(vm: SiftViewModel, dir: File) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val size = ButtonDefaults.MediumContainerHeight
            FilledTonalButton(
                onClick = { vm.dialog = Dialog.NewFolder(dir, forSource = true) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).height(size),
            ) {
                Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("New folder", maxLines = 1)
            }
            Button(
                onClick = { vm.pickSource(dir.path) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1.3f).height(size),
            ) {
                Text("Use this folder", maxLines = 1)
            }
        }
    }
}
