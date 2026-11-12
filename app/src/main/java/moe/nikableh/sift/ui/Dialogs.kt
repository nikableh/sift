package moe.nikableh.sift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.History
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

        Dialog.ConfirmApply -> Confirm(
            icon = Icons.AutoMirrored.Rounded.DriveFileMove,
            title = "Move ${vm.queued} photos?",
            confirm = "Move",
            onConfirm = { vm.apply() },
            onDismiss = close,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((label, count) in vm.applySummary()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(label, Modifier.weight(1f))
                        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

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
                    Text("${vm.progress} of ${d.total}")
                }
            },
            confirmButton = {
                if (d.cancellable) TextButton(onClick = { vm.stopApply() }) { Text("Stop") }
            },
        )

        is Dialog.ApplyDone -> AlertDialog(
            onDismissRequest = close,
            icon = { Icon(Icons.Rounded.DoneAll, null) },
            title = { Text("Moved ${d.result.moved} photos") },
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
            title = "Move ${d.count} photos back?",
            confirm = "Move back",
            onConfirm = { vm.undoBatch(d.journal, d.count) },
            onDismiss = close,
        ) {
            Text("They return to the folders they came from and go back into the queue, so you can change them and apply again.")
        }

        is Dialog.UndoFailed -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Moved ${d.restored} back") },
            text = { Text("${d.failed.size} could not be moved back:\n" + failureLines(d.failed)) },
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

        is Dialog.ConfirmResetSkipped -> Confirm(
            title = "Reset skipped photos?",
            confirm = "Reset",
            onConfirm = {
                vm.resetSkipped()
                close()
            },
            onDismiss = close,
        ) {
            Text(
                (if (d.count == 1) "1 skipped photo in this album counts" else "${d.count} skipped photos in this album count") +
                    " as not reviewed again. No files are touched."
            )
        }

        Dialog.ConfirmClear -> Confirm(
            title = "Clear the queue?",
            confirm = "Clear",
            onConfirm = {
                vm.clearQueue()
                close()
            },
            onDismiss = close,
        ) { Text("${vm.queued} photos lose their folder choice. No files are touched.") }

        is Dialog.ConfirmRemoveDest -> Confirm(
            title = "Remove ${d.dest.label}?",
            confirm = "Remove",
            onConfirm = {
                vm.removeDest(d.dest)
                close()
            },
            onDismiss = close,
        ) { Text("${d.queued} queued photos lose this choice and stay where they are.") }

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
    }
}

private fun failureLines(failed: List<Pair<String, String>>): String {
    val lines = failed.take(8).joinToString("\n") { "${it.first}: ${it.second}" }
    return if (failed.size > 8) "$lines\nand ${failed.size - 8} more" else lines
}

private fun applyDetails(r: Mover.Result): String? {
    val parts = ArrayList<String>()
    if (r.missing > 0) parts.add("${r.missing} were already gone from the folder.")
    if (r.cancelled) parts.add("Stopped early. The rest is still queued.")
    if (r.failed.isNotEmpty()) {
        parts.add("${r.failed.size} could not be moved and are still queued:\n" + failureLines(r.failed))
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
