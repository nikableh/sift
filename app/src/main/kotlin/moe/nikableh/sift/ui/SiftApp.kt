package moe.nikableh.sift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import moe.nikableh.sift.Screen
import moe.nikableh.sift.SiftViewModel

@Composable
fun SiftApp(vm: SiftViewModel) {
    val snackbar = remember { SnackbarHostState() }
    // Holds what Home keeps with rememberSaveable, such as its unfolded album list, while another screen covers it
    val saved = rememberSaveableStateHolder()
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
    if (vm.busy) KeepScreenOn()

    val animationsOn = rememberAnimationsOn()
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
                    if (!animationsOn) {
                        // Reduced motion asks for a plain fade, never a slide
                        fadeIn() togetherWith fadeOut()
                    } else if (targetState.second >= initialState.second) {
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
                        Screen.Home -> saved.SaveableStateProvider("home") { HomeScreen(vm) }
                        // A video waits for the slide to end, so its picture never comes in halfway through one
                        Screen.Sort -> SortScreen(vm, settled = !transition.isRunning)
                        Screen.Setup -> SetupScreen(vm, snackbar)
                        Screen.Selections -> SelectionsScreen(vm)
                        Screen.About -> AboutScreen(vm, snackbar)
                        Screen.Licenses -> LicensesScreen(vm)
                        is Screen.Picker -> PickerScreen(vm, screen.forSource, snackbar)
                    }
                }
            }
            Dialogs(vm)
            WriteRequests(vm)
            VideoAccess(vm)
        }
    }
}

/** How many callers want the screen awake right now. The view has one flag for all of them. Main thread only */
private var awake = 0

/** Keeps the screen on for as long as any caller is in the composition: a move, or a video's first play through. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        awake++
        view.keepScreenOn = true
        onDispose {
            // A video starting under the Move dialog must not take the flag away from the undo that follows
            if (--awake == 0) view.keepScreenOn = false
        }
    }
}
