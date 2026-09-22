package de.workflow42.meinenoten.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.components.SongFilterBar
import de.workflow42.meinenoten.ui.components.SongOverflowMenu
import de.workflow42.meinenoten.ui.util.SongSortMode
import de.workflow42.meinenoten.ui.util.groupHeading
import de.workflow42.meinenoten.ui.util.sortedBySetlistOrder
import de.workflow42.meinenoten.ui.util.sortedForDisplay

@Composable
fun SongListScreen(
    songs: List<Song>,
    /** Second parameter carries the active setlist filter, if any. */
    onSongClick: (Song, String?) -> Unit,
    onImportPdf: () -> Unit,
    onAddManual: () -> Unit,
    onAddToSetlist: (Song) -> Unit,
    /** Opens the shared metadata editor for a song. */
    onEditSong: (Song) -> Unit,
    /** Asks for confirmation before deleting; the caller owns the actual removal. */
    onDeleteSong: (Song) -> Unit,
    modifier: Modifier = Modifier,
    /** Offered as a filter, so a service can be prepared without leaving this screen. */
    setlists: List<Setlist> = emptyList(),
) {
    // Filter and sort state is view state, not data: it survives rotation but is
    // deliberately not persisted, so the app always opens on the full library.
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(SongSortMode.ARTIST) }

    // Keyed on a snapshot of the contents, not on `songs` itself: the caller passes a
    // SnapshotStateList whose identity never changes, so keying on the instance would
    // never recompute these. Songs are loaded asynchronously after the first frame,
    // which used to leave the list stuck on its initial empty state until a tab switch
    // disposed the screen.
    val songList = songs.toList()

    val genres = remember(songList) {
        songList.asSequence().map { it.genre }.filter { it.isNotBlank() }.distinct().sorted()
            .toList()
    }

    // A filter can outlive the thing it points at – a deleted setlist or a genre that was
    // renamed on its last song would otherwise leave an empty list with no way back.
    val activeSetlist = setlists.find { it.id == selectedSetlistId }
    val activeGenre = selectedGenre?.takeIf { genres.contains(it) }

    val visibleSongs = remember(songList, activeGenre, activeSetlist, sortMode) {
        val filtered = songList
            .filter { activeGenre == null || (it.genre.equals(activeGenre, ignoreCase = true)) }
            .filter { activeSetlist == null || activeSetlist.songIds.contains(it.id) }

        if (sortMode == SongSortMode.SETLIST_ORDER && activeSetlist != null) {
            filtered.sortedBySetlistOrder(activeSetlist.songIds)
        } else {
            filtered.sortedForDisplay(sortMode)
        }
    }

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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SongFilterBar(
                genres = genres,
                setlists = setlists,
                selectedGenre = activeGenre,
                selectedSetlistId = activeSetlist?.id,
                sortMode = sortMode,
                onGenreSelected = { selectedGenre = it },
                onSetlistSelected = { id ->
                    selectedSetlistId = id
                    // Picking a setlist almost always means "play it in order".
                    if (id != null) {
                        sortMode = SongSortMode.SETLIST_ORDER
                    } else if (sortMode == SongSortMode.SETLIST_ORDER) {
                        // The order it referred to is gone; fall back to the default.
                        sortMode = SongSortMode.ARTIST
                    }
                },
                onSortModeSelected = { sortMode = it }
            )

            if (visibleSongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        // Distinguishing the two cases matters: an empty library needs an
                        // import, an empty filter result needs the filter cleared.
                        text = if (songList.isEmpty()) {
                            "Noch keine Lieder vorhanden."
                        } else {
                            "Keine Lieder passen zum Filter."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visibleSongs, key = { it.id }) { song ->
                    // Headings only appear when sorting by genre, where they turn a flat
                    // list into browsable sections.
                    val heading = song.groupHeading(sortMode)
                    val previousHeading = visibleSongs
                        .getOrNull(visibleSongs.indexOf(song) - 1)
                        ?.groupHeading(sortMode)

                    if (heading != null && heading != previousHeading) {
                        Text(
                            text = heading,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 4.dp
                            )
                        )
                    }

                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // In running order the position is what people call out
                                // ("number four"), so it leads the line.
                                if (sortMode == SongSortMode.SETLIST_ORDER) {
                                    Text(
                                        text = "${visibleSongs.indexOf(song) + 1}.",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                }
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
                        // Genre as a quiet second line: helpful when scanning for
                        // "something for Advent", never competing with the title. Omitted
                        // while grouping by genre, where the heading already says it.
                        supportingContent = song.genre
                            .takeIf { it.isNotBlank() && sortMode != SongSortMode.GENRE }
                            ?.let { genre ->
                                {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            },
                        trailingContent = {
                            // One overflow menu instead of a single visible action: the
                            // list offers the same three song actions as the detail
                            // screen, and a destructive one among them must not sit
                            // exposed next to a row that is tapped to open a song.
                            SongOverflowMenu(
                                onEdit = { onEditSong(song) },
                                onAddToSetlist = { onAddToSetlist(song) },
                                onDelete = { onDeleteSong(song) },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            // Passing the active setlist along keeps pedal paging and the
                            // jump strip working, exactly as if opened from the setlist.
                            .clickable { onSongClick(song, activeSetlist?.id) }
                    )
                    HorizontalDivider()
                }
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
