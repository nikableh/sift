package moe.nikableh.sift.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    // The depth tells a step forward from a step back
    val target = vm.screen to vm.backStack.size
    val screens = remember { SeekableTransitionState(target) }
    val scope = rememberCoroutineScope()
    var goingBack by remember { mutableStateOf<Job?>(null) }
    var swiping by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        goingBack?.cancel()
        // A released back swipe has already taken the slide part of the way there
        val released = screens.targetState == target && screens.currentState != target
        screens.animateTo(target, if (released) Settle else null)
    }
    // A back swipe drags the slide along with the finger, and letting go finishes it from there
    PredictiveBackHandler(enabled = vm.backStack.size > 1) { events ->
        goingBack?.cancel()
        val previous = vm.backStack[vm.backStack.size - 2] to vm.backStack.size - 1
        try {
            // A swipe that cuts into a running slide waits in seekTo until that slide lets go, and
            // the newest progress must not queue behind it
            events.collectLatest {
                swiping = true
                screens.seekTo(it.progress, previous)
            }
            vm.back()
        } catch (e: CancellationException) {
            // The gesture's job is already cancelled here, so the slide back from where the finger
            // left it runs in a job of its own
            goingBack = scope.launch {
                if (screens.fraction > 0f) {
                    animate(screens.fraction, 0f, animationSpec = Settle) { value, _ -> launch { screens.seekTo(value) } }
                }
                screens.snapTo(screens.currentState)
            }
            throw e
        } finally {
            swiping = false
        }
    }
    if (vm.busy) KeepScreenOn()

    val animationsOn = rememberAnimationsOn()
    SiftTheme {
        val surface = MaterialTheme.colorScheme.surface
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            rememberTransition(screens, label = "screen").AnimatedContent(
                // A slide instead of a fade: the first fade after launch stutters for a third of a
                // second while the GPU sets up drawing at partial alpha, and opaque screens need none
                transitionSpec = {
                    if (!animationsOn) {
                        // Reduced motion asks for a plain fade, never a slide
                        fadeIn() togetherWith fadeOut()
                    } else if (targetState.second >= initialState.second) {
                        slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it } togetherWith
                            slideOutHorizontally(tween(400, easing = Emphasized)) { -it / 3 }
                    } else {
                        // The screen being left slides off on top of the one it came from. During a back
                        // swipe both move in step with the finger, which the curves would run ahead of
                        val follow = tween<IntOffset>(400, easing = LinearEasing)
                        val reveal = if (swiping) follow else tween(400, easing = EmphasizedDecelerate)
                        val leave = if (swiping) follow else tween(200, easing = EmphasizedAccelerate)
                        (slideInHorizontally(reveal) { -it / 3 } togetherWith slideOutHorizontally(leave) { it })
                            .apply { targetContentZIndex = -1f }
                    }
                },
            ) { (screen, depth) ->
                // Only the screen on top of the back stack shows the snackbar, so the one sliding out never
                // draws a second copy, and a back swipe that is called off leaves it where it was
                val idle = remember { SnackbarHostState() }
                val host = if (screen == vm.screen && depth == vm.backStack.size) snackbar else idle
                // Opaque even where a screen draws no background of its own, or the one under it shows through
                Box(Modifier.fillMaxSize().background(surface)) {
                    when (screen) {
                        Screen.Home -> saved.SaveableStateProvider("home") { HomeScreen(vm, host) }
                        // A video waits for the slide to end, so its picture never comes in halfway through one
                        Screen.Sort -> SortScreen(vm, settled = !transition.isRunning)
                        Screen.Setup -> SetupScreen(vm, host)
                        Screen.Selections -> SelectionsScreen(vm, host)
                        Screen.About -> AboutScreen(vm, host)
                        Screen.Licenses -> LicensesScreen(vm)
                        is Screen.Picker -> PickerScreen(vm, screen.forSource, host)
                    }
                }
            }
            Dialogs(vm, snackbar)
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

// Material's emphasized easing curves. The library keeps its own copies internal
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Runs a back swipe's slide on to its end, or back to its start, once the finger lets go */
private val Settle = tween<Float>(400, easing = EmphasizedDecelerate)
