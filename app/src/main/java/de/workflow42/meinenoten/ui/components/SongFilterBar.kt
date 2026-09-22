package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.ui.util.SongSortMode

/**
 * Filter and sort controls above the song list.
 *
 * Kept to a single scrollable row plus one menu. A full filter sheet would be more
 * capable, but this list is used while standing up with an instrument in hand – every
 * extra step between "I need that song" and seeing it is a step too many.
 *
 * Both filters are single-select rather than multi-select: combining three genres is a
 * cataloguing task, not a performance one, and the tri-state chips it needs are hard to
 * read at a glance.
 */
@Composable
fun SongFilterBar(
    genres: List<String>,
    setlists: List<Setlist>,
    selectedGenre: String?,
    selectedSetlistId: String?,
    sortMode: SongSortMode,
    onGenreSelected: (String?) -> Unit,
    onSetlistSelected: (String?) -> Unit,
    onSortModeSelected: (SongSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing to filter by yet – a row of empty controls would only take up space.
    if (genres.isEmpty() && setlists.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                SortMenuChip(
                    sortMode = sortMode,
                    setlistFilterActive = selectedSetlistId != null,
                    onSortModeSelected = onSortModeSelected
                )
            }

            if (setlists.isNotEmpty()) {
                item {
                    SetlistMenuChip(
                        setlists = setlists,
                        selectedSetlistId = selectedSetlistId,
                        onSetlistSelected = onSetlistSelected
                    )
                }
            }

            items(genres, key = { it }) { genre ->
                val isSelected = genre == selectedGenre
                FilterChip(
                    selected = isSelected,
                    onClick = { onGenreSelected(if (isSelected) null else genre) },
                    label = { Text(genre) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/**
 * Sort order as a menu rather than chips: the modes are mutually exclusive and only one
 * is ever relevant, so showing all four permanently would waste the row.
 */
@Composable
private fun SortMenuChip(
    sortMode: SongSortMode,
    setlistFilterActive: Boolean,
    onSortModeSelected: (SongSortMode) -> Unit
) {
    var expanded by remember { mutableStateOf(value = false) }

    // Following a running order is meaningless without a setlist to take it from.
    val available = remember(setlistFilterActive) {
        SongSortMode.entries.filter {
            (it != SongSortMode.SETLIST_ORDER) || setlistFilterActive
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("Sortierung: ${sortMode.label}") }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onSortModeSelected(mode)
                        expanded = false
                    },
                    leadingIcon = if (mode == sortMode) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/**
 * Setlists as a menu, because their titles are long and would push the genre chips off
 * the screen. The chip doubles as the clear button once a setlist is picked.
 */
@Composable
private fun SetlistMenuChip(
    setlists: List<Setlist>,
    selectedSetlistId: String?,
    onSetlistSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(value = false) }
    val selected = setlists.find { it.id == selectedSetlistId }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (selected != null) {
            AssistChip(
                onClick = { onSetlistSelected(null) },
                label = { Text(selected.title) },
                trailingIcon = {
                    Icon(Icons.Default.Clear, contentDescription = "Filter entfernen")
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        } else {
            AssistChip(
                onClick = { expanded = true },
                label = { Text("Setlist") }
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            setlists.forEach { setlist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (setlist.date.isNotBlank()) {
                                "${setlist.title} (${setlist.date})"
                            } else {
                                setlist.title
                            }
                        )
                    },
                    onClick = {
                        onSetlistSelected(setlist.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
