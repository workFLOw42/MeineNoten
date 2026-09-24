package de.workflow42.meinenoten.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import de.workflow42.meinenoten.ui.components.DontShowAgainCheckbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.components.AlphabetIndex
import de.workflow42.meinenoten.ui.components.SongFilterBar
import de.workflow42.meinenoten.ui.components.SongOverflowMenu
import de.workflow42.meinenoten.ui.components.SongSectionHeader
import de.workflow42.meinenoten.ui.util.SongHeading
import de.workflow42.meinenoten.ui.util.SongSortMode
import de.workflow42.meinenoten.ui.util.groupHeading
import de.workflow42.meinenoten.ui.util.sortedBySetlistOrder
import de.workflow42.meinenoten.ui.util.sortedForDisplay

/**
 * A song together with its 1-based place in the list.
 *
 * The position is carried alongside the song because once headers are interleaved a row
 * can no longer derive its own place, and the running order is called out by number.
 */
private data class SongEntry(val song: Song, val position: Int)

/**
 * A run of songs under one heading, or a single unheaded run when the sort mode is not
 * grouped ([label] is then null).
 *
 * The screen groups its sorted songs once and both renders from this and derives the jump
 * index from it, so the letters down the edge cannot disagree with the headings in the
 * list about where a section begins.
 */
private data class SongSection(val label: String?, val entries: List<SongEntry>)

@OptIn(ExperimentalMaterial3Api::class)
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
    /** Opens the app's navigation drawer, which replaced the navigation rail. */
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Offered as a filter, so a service can be prepared without leaving this screen. */
    setlists: List<Setlist> = emptyList(),
    lastBackupAt: Long = 0L,
    showBackupReminder: Boolean = true,
    onNavigateToSettings: () -> Unit = {},
    onDisableBackupReminder: () -> Unit = {},
) {
    var reminderDismissed by rememberSaveable { mutableStateOf(false) }
    val showBackupReminderActive = showBackupReminder &&
            !reminderDismissed &&
            songs.isNotEmpty() &&
            (lastBackupAt <= 0L || (System.currentTimeMillis() - lastBackupAt) > 30L * 24 * 60 * 60 * 1000L)
    // Filter and sort state is view state, not data: it survives rotation but is
    // deliberately not persisted, so the app always opens on the full library.
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(SongSortMode.ARTIST) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val listState = rememberLazyListState()

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

    val visibleSongs = remember(songList, activeGenre, activeSetlist, sortMode, searchQuery) {
        val filtered = songList
            .filter { activeGenre == null || (it.genre.equals(activeGenre, ignoreCase = true)) }
            .filter { activeSetlist == null || activeSetlist.songIds.contains(it.id) }
            .filter { it.matches(searchQuery) }

        if (sortMode == SongSortMode.SETLIST_ORDER && activeSetlist != null) {
            filtered.sortedBySetlistOrder(activeSetlist.songIds)
        } else {
            filtered.sortedForDisplay(sortMode)
        }
    }

    // Grouped once, up front. Songs keep their overall position across sections, because
    // "number four" means fourth in the programme, not fourth under its letter.
    //
    // The heading for genre-less songs is resolved here, outside the remember block:
    // stringResource needs a composable scope, and SortUtils deliberately holds no
    // display text of its own.
    val noGenreHeading = stringResource(R.string.empty_no_genre)
    val sections = remember(visibleSongs, sortMode, noGenreHeading) {
        visibleSongs
            .mapIndexed { index, song -> SongEntry(song, position = index + 1) }
            .groupBy { it.song.groupHeading(sortMode) }
            .map { (heading, entries) ->
                val label = when (heading) {
                    is SongHeading.Text -> heading.value
                    SongHeading.Untitled -> noGenreHeading
                    SongHeading.None -> null
                }
                SongSection(label, entries)
            }
    }

    // Only the letter-based modes get an index: it can only jump by whatever the list is
    // sorted on, so under genre sorting the letters down the edge would be genre initials
    // while the eye expects titles.
    val indexSections = remember(sections, sortMode) {
        if (sortMode != SongSortMode.ARTIST && sortMode != SongSortMode.TITLE) {
            emptyList()
        } else {
            // Walking the sections in order mirrors exactly how they are emitted below,
            // counting one item per header plus one per song, so each letter maps to the
            // list index of its own header.
            var itemIndex = 0
            sections.mapNotNull { section ->
                val label = section.label
                val headerIndex = itemIndex
                if (label != null) itemIndex++
                itemIndex += section.entries.size
                label?.let { it to headerIndex }
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                // The total, not the filtered count: a mismatch with the rows below makes
                // an active filter obvious at a glance.
                title = {
                    Text(
                        stringResource(
                            R.string.msg_nav_with_count,
                            stringResource(R.string.nav_songs),
                            songList.size,
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.cd_open_menu),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = onAddManual,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_add_manual_song))
                }
                FloatingActionButton(onClick = onImportPdf) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_import_song))
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (showBackupReminderActive) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.backup_reminder_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.backup_reminder_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        // A checkbox, not a third button: it qualifies whichever of the two
                        // choices follows instead of being a choice of its own.
                        var dontShowAgain by rememberSaveable { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onTertiaryContainer,
                            ) {
                                DontShowAgainCheckbox(
                                    checked = dontShowAgain,
                                    onCheckedChange = { dontShowAgain = it },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            TextButton(onClick = {
                                if (dontShowAgain) onDisableBackupReminder()
                                reminderDismissed = true
                            }) {
                                Text(stringResource(R.string.backup_reminder_dismiss))
                            }
                            TextButton(onClick = {
                                if (dontShowAgain) onDisableBackupReminder()
                                reminderDismissed = true
                                onNavigateToSettings()
                            }) {
                                Text(stringResource(R.string.backup_reminder_action))
                            }
                        }
                    }
                }
            }

            SongFilterBar(
                genres = genres,
                setlists = setlists,
                selectedGenre = activeGenre,
                selectedSetlistId = activeSetlist?.id,
                sortMode = sortMode,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
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
                        // Three different dead ends needing three different remedies: add
                        // a song, clear the search, or clear the filter. A single "nothing
                        // found" would leave the reader guessing which.
                        text = when {
                            songList.isEmpty() -> stringResource(R.string.empty_no_songs)
                            searchQuery.isNotBlank() ->
                                stringResource(R.string.empty_no_search_results, searchQuery)

                            else -> stringResource(R.string.empty_no_filter_results)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                return@Column
            }

            // The index overlays the list rather than sitting beside it: reserving a
            // permanent column would narrow every title for the sake of a control that is
            // only visible while scrolling.
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Emitted section by section so the headings can stick: while
                    // scrolling through a long stretch of one letter, that letter stays
                    // pinned at the top instead of scrolling away and leaving no clue
                    // where you are. Each header is still exactly one item, so the flat
                    // positions the jump index was built from stay correct.
                    sections.forEach { section ->
                        if (section.label != null) {
                            stickyHeader(key = "header-${sortMode.name}-${section.label}") {
                                SongSectionHeader(label = section.label)
                            }
                        }

                        items(
                            items = section.entries,
                            key = { it.song.id }
                        ) { entry ->
                            SongListRow(
                                song = entry.song,
                                position = entry.position,
                                sortMode = sortMode,
                                onClick = { onSongClick(entry.song, activeSetlist?.id) },
                                onEdit = { onEditSong(entry.song) },
                                onAddToSetlist = { onAddToSetlist(entry.song) },
                                onDelete = { onDeleteSong(entry.song) },
                            )
                        }
                    }
                }

                AlphabetIndex(
                    sections = indexSections,
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * One song in the list.
 *
 * [position] is its 1-based place in the list, shown only in running order – it comes
 * from the caller because the row cannot see its own place once headers are interleaved.
 */
@Composable
private fun SongListRow(
    song: Song,
    position: Int,
    sortMode: SongSortMode,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onAddToSetlist: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // In running order the position is what people call out
                    // ("number four"), so it leads the line.
                    if (sortMode == SongSortMode.SETLIST_ORDER) {
                        Text(
                            text = "$position.",
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
                                text = stringResource(
                                    when (song.sourceType) {
                                        SongSource.MUSIC_XML -> R.string.badge_score
                                        else -> R.string.badge_text
                                    }
                                ),
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
                    onEdit = onEdit,
                    onAddToSetlist = onAddToSetlist,
                    onDelete = onDelete,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                // Passing the active setlist along keeps pedal paging and the
                // jump strip working, exactly as if opened from the setlist.
                .clickable(onClick = onClick)
        )
        HorizontalDivider()
    }
}

/**
 * "Artist – Title" on one line, or just the title when no artist is set. Keeping both
 * parts on a single line lets the eye scan straight down the list.
 *
 * Composable rather than a plain property, because the separator is part of the
 * translatable resource rather than hard-coded punctuation.
 */
private val Song.listLabel: String
    @Composable get() = if (artist.isNotBlank()) {
        stringResource(R.string.msg_list_label, artist, displayTitle)
    } else {
        displayTitle
    }
