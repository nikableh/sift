package moe.nikableh.sift.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.nikableh.sift.Batch
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.MoveRunner
import moe.nikableh.sift.Mover
import moe.nikableh.sift.SiftViewModel

@Composable
private fun MoveProgress(run: MoveRunner.Run) {
    val done = MoveRunner.done
    val progress by animateFloatAsState(
        targetValue = if (run.total == 0) 0f else done / run.total.toFloat(),
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = run.title },
        )
        Text("${done.grouped()} of ${run.total.grouped()}")
    }
}

@Composable
fun Dialogs(vm: SiftViewModel, snackbar: SnackbarHostState) {
    val close = { vm.dialog = null }
    // Clearing the dialog ends a Report, so its snackbar runs in this longer scope
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Back leaves Sift instead of cancelling, the work goes on while it is away
    val activity = LocalActivity.current
    val goHome = { activity?.moveTaskToBack(true); Unit }
    // Read from the run itself, so a view model made while it is on shows it too
    val run = MoveRunner.run
    if (run != null) {
        AlertDialog(
            onDismissRequest = goHome,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(run.title) },
            text = { MoveProgress(run) },
            confirmButton = {
                if (run.cancellable) TextButton(onClick = { vm.stopMove() }) { Text("Stop") }
            },
        )
        return
    }
    when (val d = vm.dialog) {
        // The wait shows on the Move FAB and the Undo button instead
        null, is Dialog.Preparing -> {}

        is Dialog.MoveInterrupted -> if (d.left == 0) {
            Report(vm, snackbar, scope, d, "Moved ${files(d.moved)} before Sift was closed", null)
        } else {
            Confirm(
                title = "The move didn't finish",
                confirm = if (d.moved == 0) "Move them" else "Move the rest",
                dismiss = "Not now",
                onConfirm = {
                    close()
                    vm.move()
                },
                onDismiss = close,
            ) {
                val moved = if (d.moved == 0) "before any file moved" else "after moving ${files(d.moved)}"
                val rest = if (d.left == 1) "1 is still queued. Continue moves it to its folder now" else "${d.left.grouped()} are still queued. Continue moves those ${d.left.grouped()} to their folders now"
                Text("Sift was closed $moved. $rest, and Undo on Home then moves that last batch back.")
            }
        }

        is Dialog.UndoInterrupted -> {
            val b = d.batch
            Confirm(
                icon = Icons.Rounded.History,
                title = "Moving back didn't finish",
                confirm = "Move the rest back",
                dismiss = "Not now",
                onConfirm = { vm.undoBatch(b) },
                onDismiss = close,
            ) {
                Text("Sift was closed while moving ${files(b.moved)} back. Move the rest back to return them and put them all back in the queue.")
            }
        }

        is Dialog.MoveDone -> {
            LaunchedEffect(d) {
                haptic.performHapticFeedback(if (d.result.failed.isEmpty()) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            }
            val details = applyDetails(d.result)
            if (details == null) {
                Report(vm, snackbar, scope, d, movedTitle(d.result), d.batch)
            } else {
                AlertDialog(
                    onDismissRequest = close,
                    icon = { Icon(Icons.Rounded.DoneAll, null) },
                    title = { Text(movedTitle(d.result)) },
                    text = details?.let { text -> { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text) } } },
                    confirmButton = { Button(onClick = close, shapes = ButtonDefaults.shapes()) { Text("Done") } },
                    dismissButton = {
                        val batch = d.batch
                        if (batch != null) {
                            TextButton(onClick = { vm.undoBatch(batch) }) { Text("Undo this batch") }
                        }
                    },
                )
            }
        }

        is Dialog.ConfirmUndoBatch -> {
            val b = d.batch
            Confirm(
                icon = Icons.Rounded.History,
                title = "Move ${files(b.moved)} back?",
                confirm = "Move back",
                onConfirm = { vm.undoBatch(b) },
                onDismiss = close,
            ) {
                Text("They return to the folders they came from and go back into the queue, so you can change them and apply again")
            }
        }

        is Dialog.UndoFailed -> {
            LaunchedEffect(d) { haptic.performHapticFeedback(HapticFeedbackType.Reject) }
            AlertDialog(
                onDismissRequest = close,
                title = { Text("Moved ${d.restored.grouped()} back") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("${d.failed.size.grouped()} could not be moved back:\n" + failureLines(d.failed) + "\n\nThey stay in the folders they were moved to.")
                    }
                },
                confirmButton = { TextButton(onClick = close) { Text("OK") } },
            )
        }

        is Dialog.ConfirmDiscardPicks -> Confirm(
            title = if (d.unticked == 0) "Leave without adding?" else "Leave without saving?",
            confirm = "Leave",
            onConfirm = {
                close()
                vm.clearPicks()
                vm.back()
            },
            onDismiss = close,
        ) {
            Text(
                when {
                    d.unticked > 0 -> "Your albums stay as they were"
                    d.count == 1 -> "1 ticked folder has not been added"
                    else -> "${d.count.grouped()} ticked folders have not been added"
                }
            )
        }

        Dialog.ConfirmClear -> Confirm(
            title = "Clear selections?",
            confirm = "Clear",
            onConfirm = {
                vm.clearSelections()
                close()
            },
            onDismiss = close,
        ) {
            val one = vm.queued == 1
            Text("${selections(vm.queued)} ${if (one) "is" else "are"} dropped and can't be restored. No files are touched.")
        }

        Dialog.SelectionsLocked -> AlertDialog(
            onDismissRequest = close,
            title = { Text(if (vm.sources.size == 1) "Finish this album first" else "Finish these albums first") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val one = vm.queued == 1
                    Text(
                        "${selections(vm.queued)} ${if (one) "is" else "are"} waiting. " +
                            "Move or clear ${if (one) "it" else "them"} before leaving subfolders out."
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.reviewSelections() }, shapes = ButtonDefaults.shapes()) { Text("Review") }
            },
            // Clear lives on the Selections screen that Review opens
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        )

        is Dialog.ConfirmRemoveSource -> AlertDialog(
            onDismissRequest = close,
            title = { Text(if (d.paths.size == 1) "Remove ${File(d.paths[0]).name}?" else "Remove ${d.paths.size.grouped()} albums?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val where = if (d.paths.size == 1) "this album" else "these albums"
                    Text("${selections(d.queued)} in $where ${if (d.queued == 1) "is" else "are"} dropped and can't be restored. No files are touched.")
                    // Counted, not listed, so the line stays one line at any number of albums
                    val others = vm.activeAlbums.count { it !in d.paths }
                    if (d.elsewhere > 0 && others > 0) {
                        Text(if (others == 1) "Selections in the other album stay" else "Selections in the other ${others.grouped()} albums stay")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Closes first, because saving the picker can open the next dialog
                        close()
                        if (d.picker) vm.savePicked(forSource = true, confirmed = true) else vm.removeSource(d.paths.single())
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        )

        is Dialog.ConfirmRemoveDest -> Confirm(
            title = "Remove ${d.dest.label}?",
            confirm = "Remove",
            onConfirm = {
                vm.removeDest(d.dest)
                close()
            },
            onDismiss = close,
        ) {
            Text(
                if (d.queued == 1) "1 queued file loses this choice and stays where it is. This can't be undone."
                else "${d.queued.grouped()} queued files lose this choice and stay where they are. This can't be undone."
            )
        }

        is Dialog.RenameDest -> TextPrompt(
            title = "Button label",
            hint = "Changes the button text only. The folder keeps its name.",
            initial = d.dest.label,
            confirm = "Save",
            onConfirm = {
                vm.renameDest(d.dest, it)
                close()
            },
            onDismiss = close,
        )

        is Dialog.NewFolder -> TextPrompt(
            icon = Icons.Rounded.CreateNewFolder,
            title = if (d.album) "New album" else "New folder in ${d.parent.name}",
            hint = if (d.album) "Created in ${d.parent.name}, where gallery apps look for albums" else null,
            initial = "",
            confirm = "Create",
            required = true,
            problemOf = { if ('/' in it) "Can't contain /" else null },
            onConfirm = {
                close()
                vm.createFolder(d.parent, it, d.forSource)
            },
            onDismiss = close,
        )

        is Dialog.GoToPhoto -> NumberPrompt(
            title = "Go to photo",
            current = d.current,
            max = d.total,
            onConfirm = {
                close()
                vm.jumpTo(it - 1)
            },
            onDismiss = close,
        )
    }
}

@Composable
private fun NumberPrompt(title: String, current: Int, max: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    // Starts with the current number selected, so typing replaces it
    val initial = current.toString()
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val number = value.text.toIntOrNull()?.takeIf { it in 1..max }
    val invalid = value.text.isNotEmpty() && number == null
    val submit = { number?.let(onConfirm) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Numbers, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { v -> if (v.text.all { it.isDigit() } && v.text.length <= 9) value = v },
                label = { Text("Photo number") },
                singleLine = true,
                suffix = { Text("/ ${max.grouped()}") },
                isError = invalid,
                // No description, the field's error semantics already announce it
                trailingIcon = if (invalid) { { Icon(Icons.Rounded.Error, null) } } else null,
                supportingText = { Text("From 1 to ${max.grouped()}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics {
                    if (invalid) error("Enter a number from 1 to ${max.grouped()}")
                },
            )
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = number != null, shapes = ButtonDefaults.shapes()) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Text of the big buttons that open Selections. */
fun reviewLabel(vm: SiftViewModel): String = "Review ${selections(vm.queued)}"

private fun failureLines(failed: List<Pair<String, String>>): String {
    val lines = failed.take(8).joinToString("\n") { "${it.first}: ${it.second}" }
    return if (failed.size > 8) "$lines\nand ${(failed.size - 8).grouped()} more" else lines
}

/**
 * A clean finish only reports status, so it goes to a snackbar instead of a dialog. It goes away with
 * the screen it came up on: left up, it would follow to screens where Undo means nothing and hold back
 * their own snackbars, and Home keeps an Undo for the last batch anyway
 */
@Composable
private fun Report(
    vm: SiftViewModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    dialog: Dialog,
    message: String,
    batch: Batch?,
) {
    LaunchedEffect(dialog) {
        vm.dialog = null
        val shownOn = vm.screen to vm.backStack.size
        val report = scope.launch {
            val result = snackbar.showSnackbar(message, actionLabel = batch?.let { "Undo" }, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed && batch != null) vm.undoBatch(batch)
        }
        val left = scope.launch { snapshotFlow { vm.screen to vm.backStack.size }.first { it != shownOn } }
        left.invokeOnCompletion { report.cancel() }
        report.invokeOnCompletion { left.cancel() }
    }
}

private fun movedTitle(r: Mover.Result): String {
    val copies = if (r.copied == 1) "1 copy" else "${r.copied.grouped()} copies"
    return if (r.copied == 0) "Moved ${files(r.moved)}" else "Moved ${files(r.moved)}, made $copies"
}

private fun applyDetails(r: Mover.Result): String? {
    val parts = ArrayList<String>()
    if (r.missing > 0) parts.add("${r.missing.grouped()} were already gone from the folder.")
    if (r.cancelled) parts.add("Stopped early. The rest is still queued.")
    if (r.failed.isNotEmpty()) {
        parts.add("${r.failed.size.grouped()} could not be moved and are still queued:\n" + failureLines(r.failed))
    }
    return parts.joinToString("\n\n").ifEmpty { null }
}

@Composable
private fun Confirm(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    dismiss: String = "Cancel",
    body: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null) } },
        title = { Text(title) },
        // At large font scales the body can outgrow the window, and only the body should scroll
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { body() } },
        confirmButton = { Button(onClick = onConfirm, shapes = ButtonDefaults.shapes()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

@Composable
private fun TextPrompt(
    title: String,
    hint: String?,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    required: Boolean = false,
    problemOf: (String) -> String? = { null },
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val problem = problemOf(value.text)
    val valid = problem == null && !(required && value.text.isBlank())
    val submit = { if (valid) onConfirm(value.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hint != null) Text(hint)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    isError = problem != null,
                    supportingText = problem?.let { { Text(it) } },
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics {
                        if (problem != null) error(problem)
                    },
                )
            }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = valid, shapes = ButtonDefaults.shapes()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
