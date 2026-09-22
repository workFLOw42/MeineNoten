package de.workflow42.meinenoten.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.ui.components.DateField
import de.workflow42.meinenoten.ui.util.formatSetlistDate
import de.workflow42.meinenoten.ui.util.sortedForDisplay
import kotlinx.coroutines.launch

@Composable
fun SetlistScreen(
    setlists: List<Setlist>,
    onSetlistClick: (Setlist) -> Unit,
    onCreateSetlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Newest concert first – that is nearly always the one being looked for.
    val sortedSetlists = remember(setlists.toList()) { setlists.sortedForDisplay() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateSetlist) {
                Icon(Icons.Default.Add, contentDescription = "Create Setlist")
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        if (sortedSetlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Noch keine Setlisten vorhanden.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(sortedSetlists, key = { it.id }) { setlist ->
                val songCount = setlist.songIds.size
                val dateLabel = formatSetlistDate(setlist.date)
                ListItem(
                    headlineContent = { Text(text = setlist.title) },
                    supportingContent = {
                        val songLabel = if (songCount == 1) "1 Lied" else "$songCount Lieder"
                        Text(
                            if (dateLabel.isNotBlank()) "$dateLabel • $songLabel" else songLabel
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetlistClick(setlist) }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    setlist: Setlist,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    onSetlistUpdated: (Setlist) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    /** Opens the stored position: song id and zero-based page. */
    onResume: (String, Int) -> Unit = { _, _ -> },
    /** Opens the first song of the programme and clears the stored position. */
    onStartFromBeginning: (Song) -> Unit = {},
) {
    // Paired with their position in songIds rather than mapped through it: a song can
    // appear twice in a programme (reprise, encore), and an id left behind by a deleted
    // song would shift every following index and make the wrong row move or vanish.
    // Keyed on a content snapshot: `songs` is a SnapshotStateList whose identity never
    // changes, so keying on the instance would leave this empty until something else
    // forced a recomposition.
    val setlistSongs = remember(setlist.songIds, songs.toList()) {
        setlist.songIds.mapIndexedNotNull { index, id ->
            songs.find { it.id == id }?.let { index to it }
        }
    }

    // Song to resume at, or null when there is nothing worth resuming.
    //
    // Null once the programme was played to its end, so reopening it then offers a fresh
    // start rather than parking on the closing page. "At the end" can only be judged for
    // the last song, since the page count of a PDF is not known here – being on the final
    // song is treated as done, which is the case that matters after a service.
    val resumeTarget = remember(setlist.lastSongId, setlistSongs) {
        val lastId = setlist.lastSongId ?: return@remember null
        val position = setlistSongs.indexOfFirst { (_, song) -> song.id == lastId }
        if (position == -1) {
            // The song was removed from the programme in the meantime.
            null
        } else {
            setlistSongs[position].second
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Hoisted out of the row callbacks on purpose. The row that triggers the removal
    // leaves the composition immediately, and its captured copy of songIds ages the
    // moment anything else changes the order, so both the cut and the undo are resolved
    // here against the setlist that is current when the tap happens.
    val currentSongIds by rememberUpdatedState(setlist.songIds)

    val onRemoveAt: (Int, Song) -> Unit = { position, song ->
        // Removing by position, not by id: a repeated song (reprise, encore) must lose
        // the row that was actually tapped, not its first occurrence.
        val restore = currentSongIds
        onOrderChanged(restore.filterIndexed { i, _ -> i != position })

        // Undo instead of a confirmation dialog: the action is cheap to reverse, so
        // interrupting every removal with a question would cost more than the
        // occasional mistake. Restoring the whole snapshot puts the song back at its
        // original position rather than appending it to the end.
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "\"${song.displayTitle}\" entfernt",
                actionLabel = "Widerrufen",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onOrderChanged(restore)
            }
        }
    }
    
    var showEditDialog by remember { mutableStateOf(value = false) }
    var editTitle by remember(setlist) { mutableStateOf(setlist.title) }
    var editDate by remember(setlist) { mutableStateOf(setlist.date) }
    var editNotes by remember(setlist) { mutableStateOf(setlist.notes) }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Setlist") },
            text = {
                Column {
                    TextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                    )
                    DateField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetlistUpdated(
                            setlist.copy(
                                title = editTitle,
                                date = editDate,
                                notes = editNotes,
                            )
                        )
                        showEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(text = setlist.title)
                        val dateLabel = formatSetlistDate(setlist.date)
                        if (dateLabel.isNotBlank()) {
                            Text(text = dateLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Setlist")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (setlist.notes.isNotBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = setlist.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    HorizontalDivider()
                }
            }

            // Playback actions. Only shown when there is something to play, and the
            // resume entry only once a position has actually been reached.
            if (setlistSongs.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (resumeTarget != null) {
                            Button(
                                onClick = { onResume(resumeTarget.id, setlist.lastPage) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    text = "Fortsetzen: ${resumeTarget.displayTitle}" +
                                        if (setlist.lastPage > 0) {
                                            " (S. ${setlist.lastPage + 1})"
                                        } else {
                                            ""
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // Always available: a programme is often run through more than
                        // once, and after a service the position has to be resettable.
                        OutlinedButton(
                            onClick = { onStartFromBeginning(setlistSongs.first().second) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (resumeTarget != null) 8.dp else 0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text("Von Anfang an")
                        }
                    }
                    HorizontalDivider()
                }
            }
            itemsIndexed(
                items = setlistSongs,
                // The position in songIds is unique even when a song repeats in the
                // programme, so it survives reordering as a stable key.
                key = { _, (position, _) -> position }
            ) { displayIndex, (position, song) ->
                SetlistSongRow(
                    number = displayIndex + 1,
                    song = song,
                    canMoveUp = displayIndex > 0,
                    canMoveDown = displayIndex < (setlistSongs.size - 1),
                    onClick = { onSongClick(song) },
                    onMoveUp = {
                        val target = setlistSongs[displayIndex - 1].first
                        onOrderChanged(currentSongIds.withSwapped(position, target))
                    },
                    onMoveDown = {
                        val target = setlistSongs[displayIndex + 1].first
                        onOrderChanged(currentSongIds.withSwapped(position, target))
                    },
                    onRemove = { onRemoveAt(position, song) },
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * One song in the running order.
 *
 * Moving is on visible buttons, removing is behind the overflow menu. That split follows
 * the platform convention of promoting frequent actions and hiding rare, destructive
 * ones: the order gets nudged repeatedly while building a programme, whereas removing
 * happens once and would be painful to hit by accident next to the arrows.
 */
@Composable
private fun SetlistSongRow(
    number: Int,
    song: Song,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(value = false) }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Songs get called out by their number in rehearsal ("let's do the four").
                Text(
                    text = "$number.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text(text = song.displayTitle)
            }
        },
        supportingContent = song.artist.takeIf { it.isNotBlank() }?.let { artist ->
            { Text(text = artist) }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nach oben")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nach unten")
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Weitere Aktionen")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Aus Setlist entfernen") },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

/**
 * Swaps the entries at [first] and [second].
 *
 * Swapping rather than remove-and-reinsert: with a song repeated in the programme the
 * two operations differ, and only a swap is guaranteed to leave the other copy alone.
 */
private fun List<String>.withSwapped(first: Int, second: Int): List<String> =
    toMutableList().also { list ->
        val carried = list[first]
        list[first] = list[second]
        list[second] = carried
    }
