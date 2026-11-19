package moe.nikableh.sift.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.nikableh.sift.R
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel

private const val REPO = "github.com/nikableh/sift"

/** What Sift is, and the ways out to its source, issue tracker and licenses. */
@Composable
fun AboutScreen(vm: SiftViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    val browse = { url: String ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            vm.messages.tryEmit("No app on this device opens links")
        }
        Unit
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { BackBar("About", vm) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Page(padding, Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            AppIcon()
            Spacer(Modifier.height(16.dp))
            Text("Sift", style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(
                "Version $version",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Sorts photos and videos into folders, one tap each.",
                Modifier.padding(horizontal = 16.dp),
                // Evens out the lines, so a narrow screen doesn't leave the last word on its own
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SegmentedListItem(
                    onClick = { browse("https://$REPO") },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                    colors = segmentColors(),
                    leadingContent = { Icon(Icons.Rounded.Code, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                    supportingContent = { Text(REPO) },
                ) { Text("Source code") }
                SegmentedListItem(
                    onClick = { browse("https://$REPO/issues") },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                    colors = segmentColors(),
                    leadingContent = { Icon(Icons.Rounded.Feedback, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                    supportingContent = { Text("Report bugs and ideas on GitHub") },
                ) { Text("Send feedback") }
                SegmentedListItem(
                    onClick = { vm.open(Screen.Licenses) },
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                    colors = segmentColors(),
                    leadingContent = { Icon(Icons.Rounded.Balance, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    supportingContent = { Text("MIT for Sift, Apache 2.0 for the libraries in it") },
                ) { Text("Licenses") }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "© 2026 Nika Krasnova",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The launcher icon's two layers on a fixed tile. The system's own copy comes masked to the launcher's
 * shape and tinted by its themed icons, neither of which belongs on this page
 */
@Composable
private fun AppIcon() {
    Box(
        Modifier.size(96.dp).clip(MaterialTheme.shapes.extraLarge).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        // An adaptive icon shows the middle two thirds of its 108dp layers
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(144.dp))
    }
}

/**
 * Every project on the release runtime classpath, by Maven group. All 161 modules there declare
 * Apache 2.0. After a dependency change, list them again with
 * `gradlew :app:dependencies --configuration releaseRuntimeClasspath` and check the new POMs
 */
private val libraries = listOf(
    "AndroidX and Jetpack Compose" to "androidx.*",
    "Media3 ExoPlayer" to "androidx.media3",
    "Coil" to "io.coil-kt.coil3",
    "Kotlin" to "org.jetbrains.kotlin",
    "kotlinx.coroutines and kotlinx.serialization" to "org.jetbrains.kotlinx",
    "Compose Multiplatform" to "org.jetbrains.compose.*, org.jetbrains.androidx.*",
    "Okio" to "com.squareup.okio",
    "Accompanist" to "com.google.accompanist",
    "Guava" to "com.google.guava",
    "JetBrains Annotations" to "org.jetbrains:annotations",
    "JSpecify" to "org.jspecify",
)

/** The libraries built into Sift, then the license they come under. */
@Composable
fun LicensesScreen(vm: SiftViewModel) {
    val context = LocalContext.current
    val apache = remember { paragraphs(context, R.raw.license_apache_2_0) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { BackBar("Licenses", vm) },
    ) { padding ->
        Page(padding) {
            SectionTitle("Libraries")
            Text(
                "Sift is built with these. All of them come under the Apache License 2.0, which follows the list.",
                Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                libraries.forEachIndexed { i, (name, group) ->
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = i, count = libraries.size),
                        colors = segmentColors(),
                        supportingContent = { Text(group) },
                    ) { Text(name) }
                }
            }
            SectionTitle("Apache License 2.0")
            LicenseText(apache)
        }
    }
}

/** Scrolls behind the navigation bar instead of stopping at a line above it. */
@Composable
private fun Page(
    padding: PaddingValues,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val direction = LocalLayoutDirection.current
    Column(
        Modifier.fillMaxSize()
            .padding(
                start = padding.calculateStartPadding(direction) + 16.dp,
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction) + 16.dp,
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = padding.calculateBottomPadding() + 24.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
private fun BackBar(title: String, vm: SiftViewModel) {
    TopAppBar(
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        title = { Text(title, Modifier.semantics { heading() }) },
        navigationIcon = {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp, top = 24.dp, bottom = 12.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun LicenseText(paragraphs: List<String>) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (paragraph in paragraphs) {
                Text(
                    paragraph,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A license file as paragraphs. The files wrap at 80 columns, and a phone would wrap those lines a second time */
private fun paragraphs(context: Context, @RawRes id: Int): List<String> =
    context.resources.openRawResource(id).bufferedReader().use { it.readText() }
        .split(Regex("\\r?\\n\\s*\\r?\\n"))
        .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }
