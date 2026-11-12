package moe.nikableh.sift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            AnimatedContent(
                targetState = vm.screen,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
                label = "screen",
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Sort -> SortScreen(vm)
                    Screen.Setup -> SetupScreen(vm, snackbar)
                    is Screen.Picker -> PickerScreen(vm, screen.forSource, snackbar)
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
