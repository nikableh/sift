package moe.nikableh.sift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import moe.nikableh.sift.Dialog
import moe.nikableh.sift.Mover
import moe.nikableh.sift.SiftViewModel

@Composable
fun Dialogs(vm: SiftViewModel) {
    val close = { vm.dialog = null }
    when (val d = vm.dialog) {
        null -> {}

        is Dialog.Progress -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(d.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearWavyProgressIndicator(
                        progress = { if (d.total == 0) 0f else vm.progress / d.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${vm.progress.grouped()} of ${d.total.grouped()}")
                }
            },
            confirmButton = {
                if (d.cancellable) TextButton(onClick = { vm.stopMove() }) { Text("Stop") }
            },
        )

        is Dialog.MoveDone -> AlertDialog(
            onDismissRequest = close,
            icon = { Icon(Icons.Rounded.DoneAll, null) },
            title = { Text("Moved ${d.result.moved.grouped()} photos") },
            text = { applyDetails(d.result)?.let { Text(it) } },
            confirmButton = { Button(onClick = close, shapes = ButtonDefaults.shapes()) { Text("Done") } },
            dismissButton = {
                val journal = d.journal
                if (journal != null) {
                    TextButton(onClick = { vm.undoBatch(journal, d.result.moved) }) { Text("Undo this batch") }
                }
            },
        )

        is Dialog.ConfirmUndoBatch -> Confirm(
            icon = Icons.Rounded.History,
            title = "Move ${d.count.grouped()} photos back?",
            confirm = "Move back",
            onConfirm = { vm.undoBatch(d.journal, d.count) },
            onDismiss = close,
        ) {
            Text("They return to the folders they came from and go back into the queue, so you can change them and apply again.")
        }

        is Dialog.UndoFailed -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Moved ${d.restored.grouped()} back") },
            text = { Text("${d.failed.size.grouped()} could not be moved back:\n" + failureLines(d.failed)) },
            confirmButton = { TextButton(onClick = close) { Text("OK") } },
        )

        is Dialog.ConfirmDiscardPicks -> Confirm(
            title = "Leave without adding?",
            confirm = "Leave",
            onConfirm = {
                close()
                vm.picked.clear()
                vm.back()
            },
            onDismiss = close,
        ) { Text(if (d.count == 1) "1 ticked folder has not been added." else "${d.count} ticked folders have not been added.") }

        is Dialog.ConfirmUnhide -> Confirm(
            title = "Unhide skipped photos?",
            confirm = "Unhide",
            onConfirm = {
                vm.unhideAll()
                close()
            },
            onDismiss = close,
        ) {
            Text(
                (if (d.count == 1) "1 skipped photo returns" else "${d.count.grouped()} skipped photos return") +
                    " to this album as not reviewed. No files are touched."
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
            val one = vm.selections == 1
            Text("${selectionCounts(vm)} ${if (one) "is" else "are"} dropped. No files are touched.")
        }

        Dialog.SelectionsLocked -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Finish this album first") },
            text = {
                val one = vm.selections == 1
                Text(
                    "${selectionCounts(vm)} ${if (one) "is" else "are"} waiting. " +
                        "${if (vm.queued > 0) "Move" else "Hide"} or clear ${if (one) "it" else "them"} before changing what is sorted."
                )
            },
            confirmButton = {
                Button(onClick = { vm.reviewSelections() }, shapes = ButtonDefaults.shapes()) { Text("Review") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = close) { Text("Cancel") }
                    TextButton(onClick = { vm.dialog = Dialog.ConfirmClear }) { Text("Clear") }
                }
            },
        )

        is Dialog.ConfirmRemoveDest -> Confirm(
            title = "Remove ${d.dest.label}?",
            confirm = "Remove",
            onConfirm = {
                vm.removeDest(d.dest)
                close()
            },
            onDismiss = close,
        ) { Text("${d.queued.grouped()} queued photos lose this choice and stay where they are.") }

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
            hint = if (d.album) "Created in ${d.parent.name}, where gallery apps look for albums." else null,
            initial = "",
            confirm = "Create",
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
    val submit = { number?.let(onConfirm) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Numbers, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { v -> if (v.text.all { it.isDigit() } && v.text.length <= 9) value = v },
                singleLine = true,
                suffix = { Text("/ $max") },
                isError = value.text.isNotEmpty() && number == null,
                supportingText = { Text("From 1 to $max") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = number != null, shapes = ButtonDefaults.shapes()) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Text of the big buttons that open Selections. Moves and skips count together, since both are listed there. */
fun reviewLabel(vm: SiftViewModel): String =
    if (vm.selections == 1) "Review 1 selection" else "Review ${vm.selections.grouped()} selections"

/** "80 folder choices and 15 skips", leaving out the part that is zero. */
private fun selectionCounts(vm: SiftViewModel): String {
    val skips = vm.selections - vm.queued
    val parts = ArrayList<String>()
    if (vm.queued > 0) parts.add(if (vm.queued == 1) "1 folder choice" else "${vm.queued.grouped()} folder choices")
    if (skips > 0) parts.add(if (skips == 1) "1 skip" else "${skips.grouped()} skips")
    return parts.joinToString(" and ")
}

private fun failureLines(failed: List<Pair<String, String>>): String {
    val lines = failed.take(8).joinToString("\n") { "${it.first}: ${it.second}" }
    return if (failed.size > 8) "$lines\nand ${(failed.size - 8).grouped()} more" else lines
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
    body: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null) } },
        title = { Text(title) },
        text = body,
        confirmButton = { Button(onClick = onConfirm, shapes = ButtonDefaults.shapes()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(value.text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.text) }, shapes = ButtonDefaults.shapes()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
