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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.ui.components.DateField
import de.workflow42.meinenoten.ui.util.formatSetlistDate
import de.workflow42.meinenoten.ui.util.sortedForDisplay

@Composable
fun SetlistScreen(
    setlists: List<Setlist>,
    onSetlistClick: (Setlist) -> Unit,
    onCreateSetlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Newest concert first – that is nearly always the one being looked for.
    val sortedSetlists = remember(setlists.toList()) { setlists.sortedForDisplay() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateSetlist) {
                Icon(Icons.Default.Add, contentDescription = "Create Setlist")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (sortedSetlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
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
    showBackButton: Boolean = true
) {
    val setlistSongs = remember(setlist.songIds, songs) {
        setlist.songIds.mapNotNull { id -> songs.find { it.id == id } }
    }
    
    var showEditDialog by remember { mutableStateOf(false) }
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
                Button(onClick = {
                    onSetlistUpdated(setlist.copy(
                        title = editTitle, 
                        date = editDate,
                        notes = editNotes
                    ))
                    showEditDialog = false
                }) {
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
            itemsIndexed(setlistSongs) { index, song ->
                ListItem(
                    headlineContent = { Text(text = song.displayTitle) },
                    supportingContent = {
                        if (song.artist.isNotBlank()) {
                            Text(text = song.artist)
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val newList = setlist.songIds.toMutableList()
                                        val item = newList.removeAt(index)
                                        newList.add(index - 1, item)
                                        onOrderChanged(newList)
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                            }
                            IconButton(
                                onClick = {
                                    if (index < setlistSongs.size - 1) {
                                        val newList = setlist.songIds.toMutableList()
                                        val item = newList.removeAt(index)
                                        newList.add(index + 1, item)
                                        onOrderChanged(newList)
                                    }
                                },
                                enabled = index < setlistSongs.size - 1
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                )
                HorizontalDivider()
            }
        }
    }
}
