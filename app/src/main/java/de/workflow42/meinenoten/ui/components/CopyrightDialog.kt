package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R

/**
 * Copyright notice shown before a setlist with its score files is shared.
 *
 * "Don't show again" is a checkbox rather than a third button: it is a modifier on the
 * decision, not a decision of its own. Both buttons report its state, so ticking it and
 * then cancelling silences the notice just as well as ticking it and sharing.
 */
@Composable
fun CopyrightDialog(
    onConfirm: (dontShowAgain: Boolean) -> Unit,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(false) },
        title = { Text(stringResource(R.string.dialog_copyright_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_copyright_msg))
                DontShowAgainCheckbox(
                    checked = dontShowAgain,
                    onCheckedChange = { dontShowAgain = it },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontShowAgain) }) {
                Text(stringResource(R.string.dialog_copyright_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(dontShowAgain) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * "Nicht wieder anzeigen" as a checkbox row. The whole row toggles, as the box alone is a
 * small target.
 */
@Composable
fun DontShowAgainCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        // Null callback: the row handles the toggle, otherwise it would flip twice.
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = stringResource(R.string.dialog_copyright_dont_show_again),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
