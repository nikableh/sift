package moe.nikableh.sift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.nikableh.sift.Album
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.SiftViewModel
import moe.nikableh.sift.Store
import java.io.File

/**
 * Picks folders, for the albums to sort and for the destinations alike. The first view lists
 * albums, the folders a gallery app would show. Browsing the file system is one tap away for
 * folders that hold no photos yet.
 *
 * Folders are ticked in any number and added together.
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
                title = { Text(if (forSource) "Albums to sort" else "Add destinations") },
                navigationIcon = {
                    IconButton(onClick = { if (browsing) vm.browseUp() else vm.leavePicker() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = { PickedBar(vm, forSource, browsing, dir) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (browsing) FolderList(vm, forSource, padding) else AlbumList(vm, forSource, padding)
    }
}

@Composable
private fun AlbumList(vm: SiftViewModel, forSource: Boolean, padding: PaddingValues) {
    val albums = vm.albums
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
        item {
            SegmentedListItem(
                onClick = { vm.browse(Store.lastPickerDir?.let { File(it) }?.takeIf { it.isDirectory }) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = segmentColors(),
                modifier = Modifier.padding(bottom = 10.dp),
                leadingContent = { Icon(Icons.Rounded.FolderOpen, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                supportingContent = { Text("For a folder that isn't an album yet") },
            ) { Text("Browse all folders") }
        }
        when {
            albums == null -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            albums.isEmpty() -> item {
                Text(
                    "No albums found on this device",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> itemsIndexed(albums, key = { _, album -> album.path }) { i, album ->
                AlbumRow(vm, album, forSource, i, albums.size)
            }
        }
    }
}

@Composable
private fun AlbumRow(vm: SiftViewModel, album: Album, forSource: Boolean, index: Int, count: Int) {
    val sorting = vm.isSource(album.path)
    val destination = vm.isDest(album.path)
    // Tap order, because for destinations it becomes the order of the buttons on the sorting screen
    val order = vm.picked.indexOf(album.path)
    val selected = order >= 0
    SegmentedListItem(
        checked = selected,
        onCheckedChange = { vm.togglePicked(album.path, forSource) },
        enabled = !sorting && !destination,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentColors(),
        leadingContent = {
            // Square like a gallery thumbnail
            AsyncImage(
                model = File(album.cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        trailingContent = {
            when {
                sorting -> Tag("Sorting")
                destination -> Tag("Destination")
                selected && !forSource -> OrderBadge(order + 1)
                else -> Checkbox(checked = selected, onCheckedChange = null)
            }
        },
        supportingContent = {
            Column {
                Text(photos(album.count))
                // Whole path, wrapped. Two albums can share a name and differ only here
                Text(album.path, style = MaterialTheme.typography.bodySmall)
            }
        },
    ) { Text(album.name) }
}

/** Says why a folder can't be ticked: it is already an album being sorted, or a destination. */
@Composable
private fun Tag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** The place a ticked destination gets among the sorting buttons. */
@Composable
private fun OrderBadge(number: Int) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun FolderList(vm: SiftViewModel, forSource: Boolean, padding: PaddingValues) {
    val folders = vm.folders
    val dir = vm.browseDir
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
            item {
                val taken = vm.isTaken(dir.path)
                SegmentedListItem(
                    checked = dir.path in vm.picked,
                    onCheckedChange = { vm.togglePicked(dir.path, forSource) },
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
                    if (dir != null) {
                        // Ticks a subfolder without having to open it first
                        Checkbox(
                            checked = path in vm.picked,
                            onCheckedChange = { vm.togglePicked(path, forSource) },
                            enabled = !vm.isTaken(path),
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

/** Shows what is ticked so far and adds it all in one go. [browseDir] is null at the list of storage volumes. */
@Composable
private fun PickedBar(vm: SiftViewModel, forSource: Boolean, browsing: Boolean, browseDir: File?) {
    val picked = vm.picked
    // A new album only makes sense as a place to sort into, and the list of volumes has no folder to create one in
    val newFolder: Dialog.NewFolder? = when {
        browsing && browseDir != null -> Dialog.NewFolder(browseDir, forSource)
        forSource -> null
        else -> Dialog.NewFolder(vm.picturesDir(), forSource = false, album = true)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 12.dp)) {
            if (picked.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    picked.forEachIndexed { i, path ->
                        val file = File(path)
                        val name = "${file.parentFile?.name ?: ""}/${file.name}"
                        InputChip(
                            selected = true,
                            onClick = { vm.togglePicked(path, forSource) },
                            // The number is the button's place, which albums to sort don't have
                            label = { Text(if (forSource) name else "${i + 1}  $name") },
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
                if (newFolder != null) {
                    FilledTonalButton(
                        onClick = { vm.dialog = newFolder },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).height(size),
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        FitLabel(if (newFolder.album) "New album" else "New folder")
                    }
                }
                Button(
                    onClick = { vm.addPicked(forSource) },
                    enabled = picked.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(size),
                ) {
                    FitLabel(
                        when {
                            picked.isEmpty() -> "Add"
                            !forSource -> "Add ${picked.size}"
                            picked.size == 1 -> "Add 1 album"
                            else -> "Add ${picked.size} albums"
                        }
                    )
                }
            }
        }
    }
}
