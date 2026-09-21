package de.workflow42.meinenoten.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.util.sortedForDisplay

@Composable
fun SongListScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onImportPdf: () -> Unit,
    onAddManual: () -> Unit,
    onAddToSetlist: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ordering is derived for display only; storage keeps insertion order.
    val sortedSongs = remember(songs.toList()) { songs.sortedForDisplay() }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = onAddManual,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Add Manual Note")
                }
                FloatingActionButton(onClick = onImportPdf) {
                    Icon(Icons.Default.Add, contentDescription = "Import PDF")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (sortedSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Noch keine Lieder vorhanden.",
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
            items(sortedSongs, key = { it.id }) { song ->
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.listLabel,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (song.sourceType != SongSource.PDF) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Text(
                                        text = when (song.sourceType) {
                                            SongSource.MUSIC_XML -> "Noten"
                                            else -> "Text"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onAddToSetlist(song) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to Setlist"
                            )
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

/**
 * "Artist – Title" on one line, or just the title when no artist is set. Keeping both
 * parts on a single line lets the eye scan straight down the list.
 */
private val Song.listLabel: String
    get() = if (artist.isNotBlank()) "$artist – $displayTitle" else displayTitle
