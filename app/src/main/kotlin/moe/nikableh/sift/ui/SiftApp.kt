package moe.nikableh.sift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel

@Composable
fun SiftApp(vm: SiftViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
    if (vm.busy) KeepScreenOn()

    SiftTheme {
        val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val surface = MaterialTheme.colorScheme.surface
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            AnimatedContent(
                // The depth tells a step forward from a step back
                targetState = vm.screen to vm.backStack.size,
                // A slide instead of a fade: the first fade after launch stutters for a third of a
                // second while the GPU sets up drawing at partial alpha, and opaque screens need none
                transitionSpec = {
                    if (targetState.second >= initialState.second) {
                        slideInHorizontally(slide) { it } togetherWith slideOutHorizontally(slide) { -it / 3 }
                    } else {
                        // The screen being left slides off on top of the one it came from
                        (slideInHorizontally(slide) { -it / 3 } togetherWith slideOutHorizontally(slide) { it })
                            .apply { targetContentZIndex = -1f }
                    }
                },
                label = "screen",
            ) { (screen) ->
                // Opaque even where a screen draws no background of its own, or the one under it shows through
                Box(Modifier.fillMaxSize().background(surface)) {
                    when (screen) {
                        Screen.Home -> HomeScreen(vm)
                        Screen.Sort -> SortScreen(vm)
                        Screen.Setup -> SetupScreen(vm, snackbar)
                        Screen.Selections -> SelectionsScreen(vm)
                        Screen.Skipped -> SkippedScreen(vm)
                        is Screen.Picker -> PickerScreen(vm, screen.forSource, snackbar)
                    }
                }
            }
            Dialogs(vm)
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
