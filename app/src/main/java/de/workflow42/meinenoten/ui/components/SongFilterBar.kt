package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.ui.util.SongSortMode
import de.workflow42.meinenoten.ui.util.formatSetlistDate

/**
 * Search field, filter and sort controls above the song list.
 *
 * Two rows: the search field on its own, the three controls below it. Everything is a
 * menu rather than a spread of chips, so the control row stays one line however many
 * genres the library grows – this list is used while standing up with an instrument in
 * hand, and a wrapping, shifting set of controls is hard to hit.
 *
 * The search field gets its own row rather than joining the scrollable one: a text field
 * that can slide out of reach is awkward to tap, and search is the widest-reaching control
 * here, so it leads.
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onSetlistSelected: (String?) -> Unit,
    onSortModeSelected: (SongSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SongSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 4.dp,
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

            if (genres.isNotEmpty()) {
                item {
                    GenreMenuChip(
                        genres = genres,
                        selectedGenre = selectedGenre,
                        onGenreSelected = onGenreSelected
                    )
                }
            }
        }
    }
}

/**
 * Genres as a menu rather than one chip each.
 *
 * A chip per genre reads well with four of them and badly with twenty: the row turns into
 * a horizontal scroll where the sort and setlist controls get pushed out of sight. The
 * menu keeps the row a fixed width no matter how the library grows.
 *
 * Like the setlist chip, it doubles as its own clear button once a genre is picked.
 */
@Composable
private fun GenreMenuChip(
    genres: List<String>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(value = false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (selectedGenre != null) {
            AssistChip(
                onClick = { onGenreSelected(null) },
                label = { Text(selectedGenre) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.cd_clear_filter),
                    )
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
                label = { Text(stringResource(R.string.label_genre)) }
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            genres.forEach { genre ->
                DropdownMenuItem(
                    text = { Text(genre) },
                    onClick = {
                        onGenreSelected(genre)
                        expanded = false
                    },
                    leadingIcon = if (genre == selectedGenre) {
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
            label = {
                Text(
                    stringResource(
                        R.string.label_sort,
                        stringResource(sortMode.labelRes),
                    )
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelRes)) },
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
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.cd_clear_filter),
                    )
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
                label = { Text(stringResource(R.string.label_setlist)) }
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            setlists.forEach { setlist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (setlist.date.isNotBlank()) {
                                stringResource(
                                    R.string.msg_title_with_date,
                                    setlist.title,
                                    formatSetlistDate(setlist.date),
                                )
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
