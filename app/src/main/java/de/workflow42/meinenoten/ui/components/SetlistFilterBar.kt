package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.ui.util.SetlistPeriod
import de.workflow42.meinenoten.ui.util.SetlistSortMode

/**
 * Search field, filter and sort controls above the setlist list.
 *
 * Deliberately the same shape as [SongFilterBar] – search on its own row, a single row of
 * menu chips below – so moving between the two lists costs no relearning.
 *
 * [songs] is expected already in display order; this component only renders.
 */
@Composable
fun SetlistFilterBar(
    songs: List<Song>,
    genres: List<String>,
    searchQuery: String,
    sortMode: SetlistSortMode,
    period: SetlistPeriod,
    selectedSongId: String?,
    selectedGenre: String?,
    onSearchQueryChange: (String) -> Unit,
    onSortModeSelected: (SetlistSortMode) -> Unit,
    onPeriodSelected: (SetlistPeriod) -> Unit,
    onSongSelected: (String?) -> Unit,
    onGenreSelected: (String?) -> Unit,
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                SetlistSortChip(sortMode = sortMode, onSortModeSelected = onSortModeSelected)
            }
            item {
                PeriodChip(period = period, onPeriodSelected = onPeriodSelected)
            }
            if (songs.isNotEmpty()) {
                item {
                    ClearableMenuChip(
                        placeholder = stringResource(R.string.label_contains_song),
                        options = songs.map { it.id to it.menuLabel },
                        selectedKey = selectedSongId,
                        onSelected = onSongSelected,
                    )
                }
            }
            if (genres.isNotEmpty()) {
                item {
                    ClearableMenuChip(
                        placeholder = stringResource(R.string.label_genre),
                        options = genres.map { it to it },
                        selectedKey = selectedGenre,
                        onSelected = onGenreSelected,
                    )
                }
            }
        }
    }
}

/** Sort order as a menu, exactly like the song list's sort chip. */
@Composable
private fun SetlistSortChip(
    sortMode: SetlistSortMode,
    onSortModeSelected: (SetlistSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(stringResource(R.string.label_sort, stringResource(sortMode.labelRes)))
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SetlistSortMode.entries.forEach { mode ->
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
                    },
                )
            }
        }
    }
}

/**
 * Period filter. [SetlistPeriod.ALL] is the resting state and shows as the plain menu
 * chip; the other two show as a selected chip that clears back to ALL on tap.
 */
@Composable
private fun PeriodChip(
    period: SetlistPeriod,
    onPeriodSelected: (SetlistPeriod) -> Unit,
) {
    val choices = SetlistPeriod.entries.filter { it != SetlistPeriod.ALL }
    ClearableMenuChip(
        placeholder = stringResource(SetlistPeriod.ALL.labelRes),
        options = choices.map { it.name to stringResource(it.labelRes) },
        selectedKey = period.name.takeIf { period != SetlistPeriod.ALL },
        onSelected = { key ->
            onPeriodSelected(key?.let { SetlistPeriod.valueOf(it) } ?: SetlistPeriod.ALL)
        },
    )
}

/**
 * A menu chip that turns into its own clear button once something is picked – the
 * pattern of the song list's genre and setlist chips, factored out here because this
 * bar has three of them.
 *
 * [options] pairs a stable key with its display label.
 */
@Composable
private fun ClearableMenuChip(
    placeholder: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (selectedLabel != null) {
            AssistChip(
                onClick = { onSelected(null) },
                label = {
                    Text(selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.cd_clear_filter),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        } else {
            AssistChip(onClick = { expanded = true }, label = { Text(placeholder) })
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // A library of a few hundred songs must not make the menu taller than the
            // screen; it scrolls instead.
            modifier = Modifier.heightIn(max = 400.dp),
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(key)
                        expanded = false
                    },
                    leadingIcon = if (key == selectedKey) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** "Artist – Title", or just the title, matching the song list's rows. */
private val Song.menuLabel: String
    @Composable get() = if (artist.isNotBlank()) {
        stringResource(R.string.msg_list_label, artist, displayTitle)
    } else {
        displayTitle
    }
