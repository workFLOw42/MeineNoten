package de.workflow42.meinenoten.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.ui.components.DateField
import de.workflow42.meinenoten.ui.components.AlphabetIndex
import de.workflow42.meinenoten.ui.components.InputDialogProperties
import de.workflow42.meinenoten.ui.components.SetlistFilterBar
import de.workflow42.meinenoten.ui.components.SongSectionHeader
import de.workflow42.meinenoten.ui.util.SetlistPeriod
import de.workflow42.meinenoten.ui.util.SetlistSortMode
import de.workflow42.meinenoten.ui.util.SongHeading
import de.workflow42.meinenoten.ui.util.SongSortMode
import de.workflow42.meinenoten.ui.util.formatSetlistDate
import de.workflow42.meinenoten.ui.util.groupHeading
import de.workflow42.meinenoten.ui.util.isIn
import de.workflow42.meinenoten.ui.util.matches
import de.workflow42.meinenoten.ui.util.nextUpcoming
import de.workflow42.meinenoten.ui.util.parseSetlistDate
import de.workflow42.meinenoten.ui.util.progressPosition
import de.workflow42.meinenoten.ui.util.sortedForDisplay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A run of setlists under one heading, or a single unheaded run ([label] null).
 *
 * Built once and used both for rendering and for the jump index, like the song list's
 * sections, so the index cannot disagree with the headings about where a group begins.
 */
private data class SetlistSection(val label: String?, val setlists: List<Setlist>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistScreen(
    setlists: List<Setlist>,
    /** Used for searching by song, the "contains song" and genre filters. */
    songs: List<Song>,
    onSetlistClick: (Setlist) -> Unit,
    onCreateSetlist: () -> Unit,
    /** Opens the app's navigation drawer, which replaced the navigation rail. */
    onMenuClick: () -> Unit,
    onEditSetlist: (Setlist) -> Unit,
    onDuplicateSetlist: (Setlist) -> Unit,
    /** Asks for confirmation before deleting; the caller owns the actual removal. */
    onDeleteSetlist: (Setlist) -> Unit,
    onShareSetlist: (Setlist) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // View state, not data: survives rotation, deliberately not persisted.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SetlistSortMode.DATE) }
    var period by rememberSaveable { mutableStateOf(SetlistPeriod.ALL) }
    var selectedSongId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Snapshots of the contents: the caller passes SnapshotStateLists whose identity never
    // changes, so keying on the instances would never recompute.
    val setlistList = setlists.toList()
    val songList = songs.toList()

    val songsById = remember(songList) { songList.associateBy { it.id } }
    val songsForMenu = remember(songList) { songList.sortedForDisplay(SongSortMode.TITLE) }
    val genres = remember(songList) {
        songList.asSequence().map { it.genre }.filter { it.isNotBlank() }.distinct().sorted()
            .toList()
    }

    // A filter can outlive what it points at – a deleted song or a genre renamed on its
    // last song would otherwise leave an empty list with no visible way back.
    val activeSongId = selectedSongId?.takeIf { songsById.containsKey(it) }
    val activeGenre = selectedGenre?.takeIf { genres.contains(it) }

    val visibleSetlists = remember(
        setlistList, songsById, searchQuery, period, activeSongId, activeGenre, sortMode,
    ) {
        setlistList
            .filter { it.isIn(period) }
            .filter { (activeSongId == null) || it.songIds.contains(activeSongId) }
            .filter { setlist ->
                activeGenre == null || setlist.songIds.any { id ->
                    songsById[id]?.genre.equals(activeGenre, ignoreCase = true)
                }
            }
            .filter { it.matches(searchQuery, songsById) }
            .sortedForDisplay(sortMode)
    }

    // Judged over all setlists, not the filtered ones: "next" is a fact about the
    // calendar, and it must not move just because a filter hides the real next one.
    val nextSetlistId = remember(setlistList) { setlistList.nextUpcoming()?.id }

    // The "no date" heading is resolved here: stringResource needs a composable scope, and
    // SetlistUtils holds no display text.
    val noDateHeading = stringResource(R.string.empty_no_date)
    val sections = remember(visibleSetlists, sortMode, noDateHeading) {
        visibleSetlists
            .groupBy { it.groupHeading(sortMode) }
            .map { (heading, entries) ->
                val label = when (heading) {
                    is SongHeading.Text -> heading.value
                    SongHeading.Untitled -> noDateHeading
                    SongHeading.None -> null
                }
                SetlistSection(label, entries)
            }
    }

    // Same walk as the song list: one item per header plus one per row, so each label maps
    // to the list index of its own header.
    val indexSections = remember(sections) {
        var itemIndex = 0
        sections.mapNotNull { section ->
            val label = section.label
            val headerIndex = itemIndex
            if (label != null) itemIndex++
            itemIndex += section.setlists.size
            label?.let { it to headerIndex }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            R.string.msg_nav_with_count,
                            stringResource(R.string.nav_setlists),
                            setlists.size,
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
            FloatingActionButton(onClick = onCreateSetlist) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_create_setlist))
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Hidden while there is nothing at all: filtering an empty list is pointless,
            // and the empty state should be the only thing on screen.
            if (setlistList.isNotEmpty()) {
                SetlistFilterBar(
                    songs = songsForMenu,
                    genres = genres,
                    searchQuery = searchQuery,
                    sortMode = sortMode,
                    period = period,
                    selectedSongId = activeSongId,
                    selectedGenre = activeGenre,
                    onSearchQueryChange = { searchQuery = it },
                    onSortModeSelected = { sortMode = it },
                    onPeriodSelected = { period = it },
                    onSongSelected = { selectedSongId = it },
                    onGenreSelected = { selectedGenre = it },
                )
            }

            if (visibleSetlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        // Three dead ends, three remedies: create one, clear the search,
                        // or clear the filter.
                        text = when {
                            setlistList.isEmpty() -> stringResource(R.string.empty_no_setlists)
                            searchQuery.isNotBlank() ->
                                stringResource(R.string.empty_no_search_results, searchQuery)

                            else -> stringResource(R.string.empty_no_setlist_filter_results)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                return@Column
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    sections.forEach { section ->
                        if (section.label != null) {
                            stickyHeader(key = "header-${sortMode.name}-${section.label}") {
                                SongSectionHeader(label = section.label)
                            }
                        }
                        items(items = section.setlists, key = { it.id }) { setlist ->
                            SetlistRow(
                                setlist = setlist,
                                isNext = setlist.id == nextSetlistId,
                                onClick = { onSetlistClick(setlist) },
                                onEdit = { onEditSetlist(setlist) },
                                onDuplicate = { onDuplicateSetlist(setlist) },
                                onDelete = { onDeleteSetlist(setlist) },
                                onShare = { onShareSetlist(setlist) },
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
 * One setlist in the list.
 *
 * The date sits in a small calendar leaf at the start rather than in the text: when
 * looking for a programme, the date is what the eye scans for, and a fixed-width block
 * lines the days up down the list.
 *
 * The next upcoming programme is tinted and labelled, because "what are we playing on
 * Sunday" is the most common reason to open this list.
 */
@Composable
private fun SetlistRow(
    setlist: Setlist,
    isNext: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val parsedDate = remember(setlist.date) { parseSetlistDate(setlist.date) }
    val songCount = setlist.songIds.size
    val progress = setlist.progressPosition

    Column {
        ListItem(
            leadingContent = { CalendarLeaf(date = parsedDate) },
            headlineContent = { Text(text = setlist.title) },
            supportingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNext) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.label_next_setlist),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        text = pluralStringResource(R.plurals.song_count, songCount, songCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (progress != null) {
                        // Coloured, so a half-played service stands out from the rest.
                        Text(
                            text = " · ▶ " +
                                stringResource(R.string.msg_setlist_in_progress, progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            trailingContent = {
                SetlistOverflowMenu(
                    onEdit = onEdit,
                    onDuplicate = onDuplicate,
                    onShare = onShare,
                    onDelete = onDelete,
                )
            },
            colors = if (isNext) {
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                )
            } else {
                ListItemDefaults.colors()
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
        HorizontalDivider()
    }
}

/**
 * Day over short month, like a tear-off calendar. Month names come from the device
 * locale, so the leaf reads "DEZ" or "DEC" to match the rest of the UI.
 *
 * Without a usable date an icon stands in, keeping the titles aligned.
 */
@Composable
private fun CalendarLeaf(date: LocalDate?) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(48.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (date != null) {
                val month = remember(date) {
                    date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                        .trimEnd('.')
                        .uppercase(Locale.getDefault())
                }
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = month, style = MaterialTheme.typography.labelSmall)
            } else {
                Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null)
            }
        }
    }
}

/**
 * Edit, duplicate, delete – the same visual pattern as the song overflow menu, with the
 * destructive entry last, separated and in the error colour.
 */
@Composable
private fun SetlistOverflowMenu(
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.cd_more_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_duplicate)) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share_setlist)) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = {
                    expanded = false
                    onShare()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
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
    /** Opens the stored position: song id and zero-based page. */
    onResume: (String, Int) -> Unit = { _, _ -> },
    /** Opens the first song of the programme and clears the stored position. */
    onStartFromBeginning: (Song) -> Unit = {},
    onShareSetlist: (Setlist) -> Unit = {},
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

    // The snackbar is raised from a callback, outside any composable scope, so its texts
    // are resolved here and captured. The message keeps its placeholder until the song is
    // known; reading it through stringResource rather than a Context keeps it
    // configuration-aware, so a language change while the screen is open is picked up.
    val removedTemplate = stringResource(R.string.msg_song_removed)
    val undoLabel = stringResource(R.string.action_undo)

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
                message = removedTemplate.format(song.displayTitle),
                actionLabel = undoLabel,
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
            modifier = Modifier.imePadding().padding(horizontal = 16.dp, vertical = 24.dp),
            properties = InputDialogProperties,
            title = { Text(stringResource(R.string.dialog_edit_setlist_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.label_title)) },
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
                        label = { Text(stringResource(R.string.label_notes)) },
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
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
                    // Always shown: without the split view there is no list pane beside
                    // this screen to go back to.
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onShareSetlist(setlist) }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share_setlist),
                        )
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.cd_edit_setlist),
                        )
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
                                    text = if (setlist.lastPage > 0) {
                                        stringResource(
                                            R.string.action_resume_with_page,
                                            resumeTarget.displayTitle,
                                            setlist.lastPage + 1,
                                        )
                                    } else {
                                        stringResource(
                                            R.string.action_resume,
                                            resumeTarget.displayTitle,
                                        )
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
                            Text(stringResource(R.string.action_start_from_beginning))
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
                    text = stringResource(R.string.msg_song_position, number),
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
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_move_down),
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_remove_from_setlist)) },
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
