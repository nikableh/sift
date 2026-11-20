package moe.nikableh.sift.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import moe.nikableh.sift.Access
import moe.nikableh.sift.SiftViewModel

fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

fun openManageMedia(context: Context) {
    if (Build.VERSION.SDK_INT < 31) return
    try {
        context.startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")))
    } catch (e: Exception) {
        openAppSettings(context)
    }
}

/** What Home and the sorting screen show until Android lets Sift see and move photos. */
@Composable
fun AccessScreen(vm: SiftViewModel, onAbout: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // After two refusals Android no longer offers full access: it answers at once, or with
    // limited access only opens the photo picker again. Only the app's settings can change it then
    fun refusedForGood() = vm.access != Access.AskPhotos &&
        activity?.shouldShowRequestPermissionRationale(vm.photoPermissions()[0]) == false
    var refused by remember { mutableStateOf(refusedForGood()) }
    // A granted permission has no rationale either, so the pane sliding out after a grant keeps the variant it showed
    fun recheck() {
        if (vm.access != Access.Ready) refused = refusedForGood()
    }
    // Android doesn't report rationale changes, so check again whenever Sift comes back from settings or a dialog
    LifecycleResumeEffect(vm.access) {
        recheck()
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refresh()
        recheck()
    }
    val askPhotos = {
        vm.photosAsked()
        launcher.launch(vm.photoPermissions())
    }

    // Ready slides this pane out, and it keeps the last question on screen until it is gone
    var lastAsked by remember { mutableStateOf(vm.access) }
    val access = vm.access.takeIf { it != Access.Ready } ?: lastAsked
    SideEffect { lastAsked = access }

    when (access) {
        Access.AskPhotos, Access.AskManage -> {
            val photosDone = access == Access.AskManage
            AccessLayout(
                icon = Icons.Rounded.PhotoLibrary,
                title = "Let Sift sort your photos",
                body = if (photosDone) "One more thing and Sift is ready" else "Sift needs two things",
                onAbout = onAbout,
            ) {
                Steps(photosDone, showManage = Build.VERSION.SDK_INT >= 31)
                if (photosDone) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigAction("Open Media management") { openManageMedia(context) }
                        Note("Turn on \"Allow app to manage media\", then come back")
                        TextButton(onClick = vm::skipManage) { Text("Not now") }
                    }
                } else {
                    BigAction("Allow photo access", askPhotos)
                }
            }
        }

        Access.PhotosDenied -> AccessLayout(
            icon = Icons.Rounded.HideImage,
            title = "Sift can't see your photos",
            body = "Photo access is off. Sift needs it to show your albums.",
            onAbout = onAbout,
        ) {
            if (refused) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigAction("Open app settings") { openAppSettings(context) }
                    Note("Android won't ask again. In settings, open Photos and videos and allow access.")
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BigAction("Allow photo access", askPhotos)
                    TextButton(onClick = { openAppSettings(context) }) { Text("Open app settings") }
                }
            }
        }

        Access.PhotosLimited -> AccessLayout(
            icon = Icons.Rounded.Collections,
            title = "Sift sees only some photos",
            body = "You chose limited access, so albums would be missing photos. Allow all photos to sort whole albums.",
            onAbout = onAbout,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (refused) {
                    BigAction("Open app settings") { openAppSettings(context) }
                    Note("Android won't ask again. In settings, open Photos and videos and choose \"Allow all\".")
                } else {
                    BigAction("Allow all photos", askPhotos)
                    Note("Android asks again. Choose \"Allow all\".")
                }
            }
        }

        Access.Ready -> {}
    }
}

@Composable
private fun AccessLayout(
    icon: ImageVector,
    title: String,
    body: String,
    onAbout: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AccessContent(icon, title, body, content)
        // About and Licenses work without photo access, so the gate on Home keeps a way to them
        if (onAbout != null) {
            IconButton(
                onClick = onAbout,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
            ) { Icon(Icons.Rounded.Info, "About") }
        }
    }
}

@Composable
private fun AccessContent(icon: ImageVector, title: String, body: String, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Box(
                Modifier.size(128.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(
                Modifier.padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            content()
        }
    }
}

/** The two things Android asks for, ticked off as they are allowed. Android 11 has no Media management, so one row */
@Composable
private fun Steps(photosDone: Boolean, showManage: Boolean) {
    val count = if (showManage) 2 else 1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            modifier = Modifier.semantics { stateDescription = if (photosDone) "Done" else "Current step" },
            colors = segmentColors(),
            leadingContent = { StepMark(1, done = photosDone, current = !photosDone) },
            supportingContent = if (photosDone) ({ Text("Allowed") }) else null,
        ) { Text("See your photos") }
        if (showManage) {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
                modifier = Modifier.semantics { stateDescription = if (photosDone) "Current step" else "Next step" },
                colors = segmentColors(),
                leadingContent = { StepMark(2, done = false, current = photosDone) },
            ) { Text("Move your photos") }
        }
    }
}

@Composable
private fun StepMark(number: Int, done: Boolean, current: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        done -> colors.primaryContainer to colors.onPrimaryContainer
        current -> colors.primary to colors.onPrimary
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Box(Modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        if (done) {
            Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = content)
        } else {
            Text("$number", style = MaterialTheme.typography.titleMedium, color = content)
        }
    }
}

@Composable
private fun BigAction(label: String, onClick: () -> Unit) {
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(size),
        modifier = Modifier.heightIn(size),
        contentPadding = ButtonDefaults.contentPaddingFor(size),
    ) {
        Text(label, style = ButtonDefaults.textStyleFor(size))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Shown on Home when Media management was turned off after setup. Sift still works, Android just asks on every Move */
@Composable
fun MediaManagementCard(vm: SiftViewModel) {
    if (vm.manageMedia) return
    val context = LocalContext.current
    // Default card colours, so the tonal button keeps its container contrast
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Info, null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Media management is off", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text("Android asks you to confirm every Move until it is on", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = { openManageMedia(context) }, shapes = ButtonDefaults.shapes()) { Text("Turn on") }
            }
        }
    }
}

/**
 * Asks for video access where only photo access is there, which is an install from before Sift sorted
 * videos. Android grants it without a dialog while photos are allowed
 */
@Composable
fun VideoAccess(vm: SiftViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.videoAnswered() }
    val ask = vm.askVideo
    LaunchedEffect(ask) {
        if (ask) launcher.launch(Manifest.permission.READ_MEDIA_VIDEO)
    }
}

/** Launches the view model's write requests. With Media management on, Android answers them without showing anything */
@Composable
fun WriteRequests(vm: SiftViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.writeAnswered(it.resultCode == Activity.RESULT_OK)
    }
    val request = vm.writeRequest
    LaunchedEffect(request) {
        if (request != null) launcher.launch(IntentSenderRequest.Builder(request).build())
    }
}
