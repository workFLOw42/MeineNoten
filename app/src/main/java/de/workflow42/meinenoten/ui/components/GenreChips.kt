package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One-tap chips for the genres already in use.
 *
 * Genre is free text, which keeps it flexible but invites near-duplicates: typing
 * "Gospel" once and "gospel" the next time silently splits one category into two, and
 * the list then looks correct while filtering never matches both. Offering the existing
 * spellings makes reuse the path of least effort.
 *
 * Tapping the active chip clears the field, so a wrong pick can be undone without
 * reaching for the keyboard.
 */
@Composable
fun GenreChips(
    suggestions: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .fillMaxWidth()
    ) {
        suggestions.forEach { suggestion ->
            val isSelected = selected.equals(suggestion, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) "" else suggestion) },
                label = { Text(suggestion) }
            )
        }
    }
}
