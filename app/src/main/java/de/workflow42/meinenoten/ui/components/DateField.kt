package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.workflow42.meinenoten.ui.util.formatSetlistDate
import de.workflow42.meinenoten.ui.util.parseSetlistDate
import de.workflow42.meinenoten.ui.util.toStorageString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Read-only text field that opens a date picker when tapped.
 *
 * [value] is the stored representation (`yyyy-MM-dd`); [onValueChange] receives the same
 * format, or an empty string when the date is cleared. Values that predate the picker
 * and cannot be interpreted are shown verbatim so nothing the user typed is lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Datum (Termin)",
) {
    var showPicker by remember { mutableStateOf(value = false) }
    val parsed = remember(value) { parseSetlistDate(value) }

    if (showPicker) {
        val initialMillis = (parsed ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onValueChange(picked.toStorageString())
                        }
                        showPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Abbrechen")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    OutlinedTextField(
        value = formatSetlistDate(value),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Kein Termin") },
        trailingIcon = {
            Row {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Datum entfernen")
                    }
                }
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Datum wählen")
                }
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
