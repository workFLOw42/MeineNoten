package de.workflow42.meinenoten.ui

import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.height
import de.workflow42.meinenoten.ui.screens.SettingsScreen
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.data.AppSettings
import de.workflow42.meinenoten.data.SongRepository
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.components.DateField
import de.workflow42.meinenoten.ui.components.DeleteSongDialog
import de.workflow42.meinenoten.ui.components.EditSongDialog
import de.workflow42.meinenoten.ui.components.GenreChips
import de.workflow42.meinenoten.ui.components.InputDialogProperties
import de.workflow42.meinenoten.ui.components.PdfPreloader
import de.workflow42.meinenoten.ui.components.SetlistStrip
import de.workflow42.meinenoten.ui.screens.SetlistDetailScreen
import de.workflow42.meinenoten.ui.screens.SetlistScreen
import de.workflow42.meinenoten.ui.screens.SongDetailScreen
import de.workflow42.meinenoten.ui.screens.SongListScreen
import java.util.UUID

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.Alignment
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Songs : AppRoute
    @Serializable data object Setlists : AppRoute
    @Serializable data object Settings : AppRoute

    /**
     * [setlistId] is set when the song was opened from a setlist. It enables advancing
     * to the neighbouring song when paging past the end of the current one, and marks
     * the song's position as that setlist's progress.
     *
     * [startPage] is the zero-based page to open on. An explicit number rather than a
     * "resume" flag, because the page to resume at is now a property of the setlist
     * being played, not of the song: the same song sits in several programmes.
     *
     * [openAtEnd] makes the song open on its last page, which is what paging *backwards*
     * into the previous song should do. It wins over [startPage].
     */
    @Serializable data class SongDetail(
        val songId: String,
        val setlistId: String? = null,
        val startPage: Int = 0,
        val openAtEnd: Boolean = false,
    ) : AppRoute

    @Serializable data class SetlistDetail(val setlistId: String) : AppRoute
}

fun getCleanFileName(context: Context, uri: Uri): String {
    var fileName = context.getString(R.string.default_song_title)
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst()) {
            fileName = cursor.getString(nameIndex)
        }
    }
    // Remove extension
    return fileName.substringBeforeLast(".")
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainApp(
    // Hoisted to MainActivity, which also needs them for the theme; defaults keep previews simple.
    settings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { SongRepository(context) }
    
    val navigationState = rememberNavigationState(
        startRoute = AppRoute.Songs,
        topLevelRoutes = setOf(AppRoute.Songs, AppRoute.Setlists, AppRoute.Settings),
    )
    val navigator = remember { Navigator(navigationState) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { drawerScope.launch { drawerState.open() } }
    // Lyrics/score toggle of the open song. Lives here because the switch sits in the
    // drawer; keyed per song so each one opens in its default view.
    var lyricsSongId by remember { mutableStateOf<String?>(null) }
    
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    
    val songs = remember {
        mutableStateListOf<Song>()
    }

    val setlists = remember {
        mutableStateListOf<Setlist>()
    }

    LaunchedEffect(Unit) {
        songs.clear()
        songs.addAll(repository.loadSongs())
        setlists.clear()
        setlists.addAll(repository.loadSetlists())
    }

    var showImportDialog by remember { mutableStateOf(value = false) }
    var showAddManualSongDialog by remember { mutableStateOf(value = false) }
    var selectedUri by remember { mutableStateOf<String?>(null) }
    var newSongTitle by remember { mutableStateOf("") }
    var newSongArtist by remember { mutableStateOf("") }
    var newSongVersion by remember { mutableStateOf("") }
    var newSongGenre by remember { mutableStateOf("") }
    // Separate from the setlist notes state, which this used to borrow – cancelling the
    // dialog then left the typed text behind in the "new setlist" notes field.
    var newSongLyrics by remember { mutableStateOf("") }

    // Genres already in use, offered as chips so categories stay consistent.
    val knownGenres = remember(songs.toList()) {
        songs.asSequence().map { it.genre }.filter { it.isNotBlank() }.distinct().sorted().toList()
    }

    var showAddToSetlistDialog by remember { mutableStateOf<Song?>(null) }
    var showCreateSetlistDialog by remember { mutableStateOf(value = false) }
    var newSetlistTitle by remember { mutableStateOf("") }
    var newSetlistDate by remember { mutableStateOf("") }
    var newSetlistNotes by remember { mutableStateOf("") }
    // Set while the setlist dialog edits an existing programme instead of creating one.
    // The dialog is shared so both paths offer exactly the same fields.
    var setlistToEdit by remember { mutableStateOf<Setlist?>(null) }
    var setlistToDelete by remember { mutableStateOf<Setlist?>(null) }

    // Edit and delete are reachable from the song list as well, so the dialogs live here
    // next to the repository instead of inside either screen.
    var songToEdit by remember { mutableStateOf<Song?>(null) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    // Song awaiting a score file. Held across the picker round trip, which leaves and
    // re-enters the composition.
    var songToAttachFileTo by remember { mutableStateOf<Song?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            selectedUri = it.toString()
            newSongTitle = getCleanFileName(context, it)
            showImportDialog = true
        }
    }

    // Separate from [pdfLauncher]: that one creates a new song, this one fills in the
    // score of an existing one and must not open the import dialog.
    val attachFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val target = songToAttachFileTo
        songToAttachFileTo = null
        if ((uri != null) && (target != null)) {
            runCatching { repository.attachFile(target, uri) }
                .onSuccess { updatedSong ->
                    val idx = songs.indexOfFirst { it.id == updatedSong.id }
                    if (idx != -1) {
                        songs[idx] = updatedSong
                        repository.saveSongs(songs)
                    }
                }
                .onFailure { it.printStackTrace() }
        }
    }

    val documentMimeTypes = arrayOf(
        "application/pdf",
        "application/vnd.recordare.musicxml",
        "application/vnd.recordare.musicxml+xml",
        "text/xml",
        "application/xml",
        "application/octet-stream",
    )

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            modifier = Modifier.imePadding().padding(horizontal = 16.dp, vertical = 24.dp),
            properties = InputDialogProperties,
            title = { Text(stringResource(R.string.dialog_import_song_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = newSongTitle,
                        onValueChange = { newSongTitle = it },
                        label = { Text(stringResource(R.string.label_title)) },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongArtist,
                        onValueChange = { newSongArtist = it },
                        label = { Text(stringResource(R.string.label_artist)) },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongVersion,
                        onValueChange = { newSongVersion = it },
                        label = { Text(stringResource(R.string.label_version)) },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongGenre,
                        onValueChange = { newSongGenre = it },
                        label = { Text(stringResource(R.string.label_genre)) },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    GenreChips(
                        suggestions = knownGenres,
                        selected = newSongGenre,
                        onSelect = { newSongGenre = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedUri?.let { uriString ->
                            try {
                                val uri = uriString.toUri()
                                val importedSong = repository.importSong(uri, newSongTitle)
                                val songWithMetadata = importedSong.copy(
                                    artist = newSongArtist,
                                    version = newSongVersion,
                                    genre = newSongGenre.trim(),
                                )
                                songs.add(songWithMetadata)
                                repository.saveSongs(songs)

                                newSongArtist = ""
                                newSongVersion = ""
                                newSongGenre = ""
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        showImportDialog = false
                    },
                ) {
                    Text(stringResource(R.string.action_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showAddManualSongDialog) {
        AlertDialog(
            onDismissRequest = { showAddManualSongDialog = false },
            modifier = Modifier.imePadding().padding(horizontal = 16.dp, vertical = 24.dp),
            properties = InputDialogProperties,
            title = { Text(stringResource(R.string.dialog_add_song_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = newSongTitle,
                        onValueChange = { newSongTitle = it },
                        label = { Text(stringResource(R.string.label_title)) },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongArtist,
                        onValueChange = { newSongArtist = it },
                        label = { Text(stringResource(R.string.label_artist)) },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongVersion,
                        onValueChange = { newSongVersion = it },
                        label = { Text(stringResource(R.string.label_version)) },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongGenre,
                        onValueChange = { newSongGenre = it },
                        label = { Text(stringResource(R.string.label_genre)) },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    GenreChips(
                        suggestions = knownGenres,
                        selected = newSongGenre,
                        onSelect = { newSongGenre = it },
                    )
                    TextField(
                        value = newSongLyrics,
                        onValueChange = { newSongLyrics = it },
                        label = { Text(stringResource(R.string.label_lyrics)) },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        minLines = 5,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSongTitle.isNotBlank()) {
                            val newSong = Song(
                                id = UUID.randomUUID().toString(),
                                title = newSongTitle,
                                artist = newSongArtist,
                                version = newSongVersion,
                                genre = newSongGenre.trim(),
                                fileUri = "", // No file yet; can be attached later.
                                sourceType = SongSource.TEXT,
                                lyrics = newSongLyrics,
                            )
                            songs.add(newSong)
                            repository.saveSongs(songs)

                            newSongTitle = ""
                            newSongArtist = ""
                            newSongVersion = ""
                            newSongGenre = ""
                            newSongLyrics = ""
                            showAddManualSongDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualSongDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    /**
     * Records that [songId] was just opened, for the "recently opened" ordering.
     *
     * Called from every route that opens a song – library, setlist, and the jump strip –
     * because someone looking for the song they just played does not think about how they
     * got to it.
     *
     * Unlike the setlist's resume position this belongs to the song itself, so it is saved
     * here rather than on the setlist.
     */
    fun markSongOpened(songId: String) {
        val idx = songs.indexOfFirst { it.id == songId }
        if (idx != -1) {
            songs[idx] = songs[idx].copy(lastOpenedAt = System.currentTimeMillis())
            repository.saveSongs(songs)
        }
    }

    /**
     * Records that [setlistId] was just played from, for the "last played" ordering.
     *
     * Called wherever a song is opened in the context of a setlist – its rows, resume,
     * start from the beginning, and the drawer's running order.
     */
    fun markSetlistPlayed(setlistId: String?) {
        val idx = setlists.indexOfFirst { it.id == setlistId }
        if (idx != -1) {
            setlists[idx] = setlists[idx].copy(lastPlayedAt = System.currentTimeMillis())
            repository.saveSetlists(setlists)
        }
    }

    // Resolved here rather than in the callback: stringResource needs a composable scope,
    // and reading it this way keeps the suffix configuration-aware.
    val copyTitleTemplate = stringResource(R.string.msg_setlist_copy_title)

    /**
     * Copies a programme for reuse – next year's Christmas service starts as last year's.
     *
     * The copy starts fresh: no resume position and never played, because the progress of
     * the original says nothing about a run that has not happened yet.
     */
    fun duplicateSetlist(setlist: Setlist) {
        setlists.add(
            setlist.copy(
                id = UUID.randomUUID().toString(),
                title = copyTitleTemplate.format(setlist.title),
                lastSongId = null,
                lastPage = 0,
                lastPlayedAt = 0L,
            )
        )
        repository.saveSetlists(setlists)
    }

    /**
     * Removes a setlist. The songs are untouched – they belong to the library.
     *
     * If the setlist or one of its songs is open further up the setlists stack, the stack
     * is reset to the list: those screens would otherwise point at a programme that no
     * longer exists.
     */
    fun deleteSetlist(setlist: Setlist) {
        setlists.removeAll { it.id == setlist.id }
        repository.saveSetlists(setlists)

        val stack = navigationState.backStacks[AppRoute.Setlists]
        val showsDeleted = stack?.any { route ->
            ((route as? AppRoute.SetlistDetail)?.setlistId == setlist.id) ||
                ((route as? AppRoute.SongDetail)?.setlistId == setlist.id)
        } == true
        if (showsDeleted) {
            navigator.navigate(AppRoute.Setlists)
        }
    }

    // Shared by the list and the detail screen so a song always disappears from disk,
    // the library and every setlist in the same step.
    fun deleteSong(song: Song) {
        repository.deleteSongFile(song)
        songs.removeAll { it.id == song.id }
        repository.saveSongs(songs)

        var setlistsChanged = false
        setlists.forEachIndexed { index, setlist ->
            if (setlist.songIds.contains(song.id)) {
                setlists[index] = setlist.copy(
                    songIds = setlist.songIds.filterNot { it == song.id }
                )
                setlistsChanged = true
            }
        }
        if (setlistsChanged) {
            repository.saveSetlists(setlists)
        }
    }

    songToEdit?.let { editing ->
        // Re-read from the list: attaching or removing a score writes a new instance,
        // and the dialog has to show the result rather than the song as it was opened.
        val song = songs.firstOrNull { it.id == editing.id } ?: editing
        EditSongDialog(
            song = song,
            songSetlists = setlists.filter { it.songIds.contains(song.id) },
            knownGenres = knownGenres,
            onSave = { updatedSong ->
                val idx = songs.indexOfFirst { it.id == updatedSong.id }
                if (idx != -1) {
                    songs[idx] = updatedSong
                    repository.saveSongs(songs)
                }
                songToEdit = null
            },
            onDismiss = { songToEdit = null },
            onAttachFile = {
                songToAttachFileTo = song
                attachFileLauncher.launch(documentMimeTypes)
            },
            onRemoveFile = {
                val updatedSong = repository.detachFile(song)
                val idx = songs.indexOfFirst { it.id == updatedSong.id }
                if (idx != -1) {
                    songs[idx] = updatedSong
                    repository.saveSongs(songs)
                }
            },
        )
    }

    songToDelete?.let { song ->
        DeleteSongDialog(
            song = song,
            affectedSetlists = setlists.filter { it.songIds.contains(song.id) },
            onConfirm = {
                songToDelete = null
                deleteSong(song)
            },
            onDismiss = { songToDelete = null },
        )
    }

    if (showAddToSetlistDialog != null) {
        val songToAdd = showAddToSetlistDialog!!
        AlertDialog(
            onDismissRequest = { showAddToSetlistDialog = null },
            title = { Text(stringResource(R.string.dialog_add_to_setlist_title)) },
            text = {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.action_create_new_setlist))
                        },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showAddToSetlistDialog = null
                            showCreateSetlistDialog = true
                        },
                    )
                    
                    if (setlists.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        LazyColumn {
                            itemsIndexed(setlists) { index, setlist ->
                                ListItem(
                                    headlineContent = { Text(setlist.title) },
                                    modifier = Modifier.clickable {
                                        val updatedSetlist = setlist.copy(
                                            songIds = setlist.songIds + songToAdd.id,
                                        )
                                        setlists[index] = updatedSetlist
                                        repository.saveSetlists(setlists)
                                        showAddToSetlistDialog = null
                                    },
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.empty_no_setlists_available),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToSetlistDialog = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showCreateSetlistDialog) {
        val editing = setlistToEdit
        val closeSetlistDialog = {
            showCreateSetlistDialog = false
            setlistToEdit = null
            newSetlistTitle = ""
            newSetlistDate = ""
            newSetlistNotes = ""
        }
        AlertDialog(
            onDismissRequest = closeSetlistDialog,
            modifier = Modifier.imePadding().padding(horizontal = 16.dp, vertical = 24.dp),
            properties = InputDialogProperties,
            title = {
                Text(
                    stringResource(
                        if (editing != null) {
                            R.string.dialog_edit_setlist_title
                        } else {
                            R.string.dialog_new_setlist_title
                        }
                    )
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = newSetlistTitle,
                        onValueChange = { newSetlistTitle = it },
                        label = { Text(stringResource(R.string.label_setlist_title)) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    DateField(
                        value = newSetlistDate,
                        onValueChange = { newSetlistDate = it },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TextField(
                        value = newSetlistNotes,
                        onValueChange = { newSetlistNotes = it },
                        label = { Text(stringResource(R.string.label_notes)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSetlistTitle.isNotBlank()) {
                            if (editing != null) {
                                val idx = setlists.indexOfFirst { it.id == editing.id }
                                if (idx != -1) {
                                    setlists[idx] = setlists[idx].copy(
                                        title = newSetlistTitle,
                                        date = newSetlistDate,
                                        notes = newSetlistNotes,
                                    )
                                    repository.saveSetlists(setlists)
                                }
                            } else {
                                val newSetlist = Setlist(
                                    id = UUID.randomUUID().toString(),
                                    title = newSetlistTitle,
                                    date = newSetlistDate,
                                    songIds = emptyList(),
                                    notes = newSetlistNotes,
                                )
                                setlists.add(newSetlist)
                                repository.saveSetlists(setlists)
                            }
                            closeSetlistDialog()
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (editing != null) R.string.action_save else R.string.action_create
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = closeSetlistDialog) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    setlistToDelete?.let { setlist ->
        // A dialog rather than snackbar-undo: a setlist carries a prepared order and a
        // resume position, and losing that by a stray tap in a menu would be costly.
        AlertDialog(
            onDismissRequest = { setlistToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_delete_setlist_title)) },
            text = { Text(stringResource(R.string.msg_delete_setlist_confirm, setlist.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        setlistToDelete = null
                        deleteSetlist(setlist)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { setlistToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<AppRoute.Songs>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.empty_select_song))
                    }
                },
            ),
        ) {
            SongListScreen(
                songs = songs,
                setlists = setlists,
                onSongClick = { song, filterSetlistId ->
                    markSongOpened(song.id)
                    navigator.navigate(
                        AppRoute.SongDetail(
                            songId = song.id,
                            setlistId = filterSetlistId,
                            // Always page 1: opening a song from the library is looking
                            // it up, not resuming a performance. Where a saved page is
                            // wanted is the setlist, which tracks its own position.
                            startPage = 0,
                        )
                    )
                },
                onImportPdf = {
                    pdfLauncher.launch(documentMimeTypes)
                },
                onAddManual = { showAddManualSongDialog = true },
                onAddToSetlist = { showAddToSetlistDialog = it },
                onEditSong = { songToEdit = it },
                onDeleteSong = { songToDelete = it },
                onMenuClick = openDrawer,
            )
        }
        entry<AppRoute.Setlists>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.empty_select_setlist))
                    }
                }
            )
        ) {
            SetlistScreen(
                setlists = setlists,
                songs = songs,
                onSetlistClick = { navigator.navigate(AppRoute.SetlistDetail(it.id)) },
                onCreateSetlist = {
                    setlistToEdit = null
                    showCreateSetlistDialog = true
                },
                onMenuClick = openDrawer,
                onEditSetlist = { setlist ->
                    // Prefill the shared dialog with the programme as it is now.
                    setlistToEdit = setlist
                    newSetlistTitle = setlist.title
                    newSetlistDate = setlist.date
                    newSetlistNotes = setlist.notes
                    showCreateSetlistDialog = true
                },
                onDuplicateSetlist = { duplicateSetlist(it) },
                onDeleteSetlist = { setlistToDelete = it },
            )
        }
        entry<AppRoute.Settings> {
            SettingsScreen(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onMenuClick = openDrawer,
            )
        }
        entry<AppRoute.SongDetail>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) { key: AppRoute.SongDetail ->
            val song = songs.find { it.id == key.songId }
            if (song != null) {
                // Ordered songs of the originating setlist, empty when opened from the
                // song list. Drives both the drawer's running order and cross-song paging.
                val contextSetlist = key.setlistId?.let { id -> setlists.find { it.id == id } }
                val setlistSongs = remember(contextSetlist?.songIds, songs.toList()) {
                    contextSetlist?.songIds?.mapNotNull { id -> songs.find { it.id == id } }
                        ?: emptyList()
                }

                SongDetailScreen(
                    song = song,
                    settings = settings,
                    onMenuClick = openDrawer,
                    showLyrics = lyricsSongId == song.id,
                    setlistSongs = setlistSongs,
                    onNavigateToSong = { target, openAtEnd ->
                        navigator.replace(
                            AppRoute.SongDetail(
                                songId = target.id,
                                setlistId = key.setlistId,
                                startPage = 0,
                                openAtEnd = openAtEnd,
                            )
                        )
                    },
                    openAtEnd = key.openAtEnd,
                    startPage = key.startPage,
                    onProgress = { songId, page ->
                        // Only a song opened from a setlist moves that setlist's
                        // position; browsing the library leaves it untouched.
                        key.setlistId?.let { sid ->
                            val idx = setlists.indexOfFirst { it.id == sid }
                            if (idx != -1) {
                                val current = setlists[idx]
                                val changed = (current.lastSongId != songId) ||
                                    (current.lastPage != page)
                                if (changed) {
                                    setlists[idx] = current.copy(
                                        lastSongId = songId,
                                        lastPage = page,
                                    )
                                    repository.saveSetlists(setlists)
                                }
                            }
                        }
                    },
                    onFinished = {
                        // Programme played through: drop the position so it opens at the
                        // top next time instead of on the closing page.
                        key.setlistId?.let { sid ->
                            val idx = setlists.indexOfFirst { it.id == sid }
                            if ((idx != -1) && setlists[idx].hasProgress) {
                                setlists[idx] = setlists[idx].copy(
                                    lastSongId = null,
                                    lastPage = 0,
                                )
                                repository.saveSetlists(setlists)
                            }
                        }
                    },
                    onSongUpdated = { updatedSong ->
                        val idx = songs.indexOfFirst { it.id == updatedSong.id }
                        if (idx != -1) {
                            songs[idx] = updatedSong
                            repository.saveSongs(songs)
                        }
                    },
                )

                // Keep the neighbours rendered ahead, so a page turn across the song
                // boundary is instant. Used to sit in the rail, which no longer exists.
                if (setlistSongs.size > 1) {
                    val currentIndex = setlistSongs.indexOfFirst { it.id == song.id }
                    if (currentIndex != -1) {
                        setlistSongs.getOrNull(currentIndex + 1)?.let { PdfPreloader(it.fileUri) }
                        setlistSongs.getOrNull(currentIndex - 1)?.let { PdfPreloader(it.fileUri) }
                    }
                }
            }
        }
        entry<AppRoute.SetlistDetail>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) { key: AppRoute.SetlistDetail ->
            val setlist = setlists.find { it.id == key.setlistId }
            if (setlist != null) {
                SetlistDetailScreen(
                    setlist = setlist,
                    songs = songs,
                    onSongClick = {
                        markSongOpened(it.id)
                        markSetlistPlayed(setlist.id)
                        // Pass the setlist along so paging can cross song boundaries.
                        // Tapping a specific song starts it from the top: the song was
                        // just announced, so its first page is what is wanted.
                        navigator.navigate(
                            AppRoute.SongDetail(
                                songId = it.id,
                                setlistId = setlist.id,
                                startPage = 0,
                            )
                        )
                    },
                    onResume = { songId, page ->
                        markSongOpened(songId)
                        markSetlistPlayed(setlist.id)
                        navigator.navigate(
                            AppRoute.SongDetail(
                                songId = songId,
                                setlistId = setlist.id,
                                startPage = page,
                            )
                        )
                    },
                    onStartFromBeginning = { firstSong ->
                        // Clear the stored position as well, so "resume" afterwards
                        // refers to this run rather than the abandoned one.
                        val idx = setlists.indexOfFirst { it.id == setlist.id }
                        if (idx != -1) {
                            setlists[idx] = setlists[idx].copy(
                                lastSongId = null,
                                lastPage = 0,
                                lastPlayedAt = System.currentTimeMillis(),
                            )
                            repository.saveSetlists(setlists)
                        }
                        markSongOpened(firstSong.id)
                        navigator.navigate(
                            AppRoute.SongDetail(
                                songId = firstSong.id,
                                setlistId = setlist.id,
                                startPage = 0,
                            )
                        )
                    },
                    onBackClick = { navigator.goBack() },
                    onOrderChanged = { updatedSongIds ->
                        val idx = setlists.indexOfFirst { it.id == setlist.id }
                        if (idx != -1) {
                            setlists[idx] = setlists[idx].copy(songIds = updatedSongIds)
                            repository.saveSetlists(setlists)
                        }
                    },
                    onSetlistUpdated = { updatedSetlist ->
                        val idx = setlists.indexOfFirst { it.id == updatedSetlist.id }
                        if (idx != -1) {
                            setlists[idx] = updatedSetlist
                            repository.saveSetlists(setlists)
                        }
                    },
                )
            }
        }
    }

    val currentRoute = navigationState.backStacks[navigationState.topLevelRoute]?.lastOrNull()
    val songRoute = currentRoute as? AppRoute.SongDetail
    val currentSong = songRoute?.let { r -> songs.find { it.id == r.songId } }
    val runningOrder = remember(songRoute?.setlistId, songs.toList(), setlists.toList()) {
        songRoute?.setlistId?.let { sid ->
            setlists.find { it.id == sid }?.songIds?.mapNotNull { id -> songs.find { it.id == id } }
        } ?: emptyList()
    }
    val closeDrawerThen: (() -> Unit) -> Unit = { action ->
        drawerScope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // No edge swipe in the score view: it would fight with panning a zoomed page.
        gesturesEnabled = drawerState.isOpen || (songRoute == null),
        drawerContent = {
            AppDrawerContent(
                songCount = songs.size,
                setlistCount = setlists.size,
                selectedTopLevel = navigationState.topLevelRoute,
                onNavigate = { route -> closeDrawerThen { navigator.navigate(route) } },
                currentSong = currentSong,
                showingLyrics = (currentSong != null) && (lyricsSongId == currentSong.id),
                onEditSong = { closeDrawerThen { songToEdit = currentSong } },
                onAddToSetlist = { closeDrawerThen { showAddToSetlistDialog = currentSong } },
                onToggleLyrics = {
                    closeDrawerThen {
                        lyricsSongId = if (lyricsSongId == currentSong?.id) null else currentSong?.id
                    }
                },
                onDeleteSong = { closeDrawerThen { songToDelete = currentSong } },
                runningOrder = runningOrder,
                onSongClick = { target ->
                    closeDrawerThen {
                        markSongOpened(target.id)
                        markSetlistPlayed(songRoute?.setlistId)
                        navigator.replace(
                            AppRoute.SongDetail(
                                songId = target.id,
                                setlistId = songRoute?.setlistId,
                                startPage = 0,
                            )
                        )
                    }
                },
            )
        },
    ) {
        NavDisplay(
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            sceneStrategies = listOf(listDetailStrategy)
        )
    }
}
