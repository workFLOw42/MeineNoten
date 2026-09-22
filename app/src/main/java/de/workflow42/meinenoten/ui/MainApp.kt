package de.workflow42.meinenoten.ui

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
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.data.SongRepository
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.components.DateField
import de.workflow42.meinenoten.ui.components.DeleteSongDialog
import de.workflow42.meinenoten.ui.components.EditSongDialog
import de.workflow42.meinenoten.ui.components.GenreChips
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
    var fileName = "New Song"
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
fun MainApp() {
    val context = LocalContext.current
    val repository = remember { SongRepository(context) }
    
    val navigationState = rememberNavigationState(
        startRoute = AppRoute.Songs,
        topLevelRoutes = setOf(AppRoute.Songs, AppRoute.Setlists),
    )
    val navigator = remember { Navigator(navigationState) }
    
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    
    val songs = remember {
        mutableStateListOf<Song>()
    }

    val setlists = remember {
        mutableStateListOf<Setlist>()
    }

    val flashAlpha = remember { Animatable(0f) }
    var lastPageTurnTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastPageTurnTime) {
        if (lastPageTurnTime > 0) {
            flashAlpha.animateTo(0.2f, animationSpec = tween(50))
            flashAlpha.animateTo(0f, animationSpec = tween(500))
        }
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
            title = { Text("Import Song (PDF/MusicXML)") },
            text = {
                Column {
                    TextField(
                        value = newSongTitle,
                        onValueChange = { newSongTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongArtist,
                        onValueChange = { newSongArtist = it },
                        label = { Text("Artist") },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongVersion,
                        onValueChange = { newSongVersion = it },
                        label = { Text("Version") },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongGenre,
                        onValueChange = { newSongGenre = it },
                        label = { Text("Genre") },
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
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAddManualSongDialog) {
        AlertDialog(
            onDismissRequest = { showAddManualSongDialog = false },
            title = { Text("Add Song (Manual)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = newSongTitle,
                        onValueChange = { newSongTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongArtist,
                        onValueChange = { newSongArtist = it },
                        label = { Text("Artist") },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongVersion,
                        onValueChange = { newSongVersion = it },
                        label = { Text("Version") },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    TextField(
                        value = newSongGenre,
                        onValueChange = { newSongGenre = it },
                        label = { Text("Genre") },
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
                        label = { Text("Liedtext / Akkorde") },
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
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualSongDialog = false }) {
                    Text("Cancel")
                }
            },
        )
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
            title = { Text("Add to Setlist") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Create new setlist...") },
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
                            text = "No setlists available yet.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToSetlistDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showCreateSetlistDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSetlistDialog = false },
            title = { Text("New Setlist") },
            text = {
                Column {
                    TextField(
                        value = newSetlistTitle,
                        onValueChange = { newSetlistTitle = it },
                        label = { Text("Setlist Title") },
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
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSetlistTitle.isNotBlank()) {
                            val newSetlist = Setlist(
                                id = UUID.randomUUID().toString(),
                                title = newSetlistTitle,
                                date = newSetlistDate,
                                songIds = emptyList(),
                                notes = newSetlistNotes,
                            )
                            setlists.add(newSetlist)
                            repository.saveSetlists(setlists)
                            newSetlistTitle = ""
                            newSetlistDate = ""
                            newSetlistNotes = ""
                            showCreateSetlistDialog = false
                        }
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSetlistDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<AppRoute.Songs>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a song")
                    }
                },
            ),
        ) {
            SongListScreen(
                songs = songs,
                setlists = setlists,
                onSongClick = { song, filterSetlistId ->
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
            )
        }
        entry<AppRoute.Setlists>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a setlist")
                    }
                }
            )
        ) {
            SetlistScreen(
                setlists = setlists,
                onSetlistClick = { navigator.navigate(AppRoute.SetlistDetail(it.id)) },
                onCreateSetlist = { showCreateSetlistDialog = true },
            )
        }
        entry<AppRoute.SongDetail>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) { key: AppRoute.SongDetail ->
            val song = songs.find { it.id == key.songId }
            if (song != null) {
                // Compact width means a single pane, so the detail view needs its own way
                // back; from the medium breakpoint up, list and detail sit side by side.
                val showBackButton = !currentWindowAdaptiveInfoV2().windowSizeClass
                    .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

                // Ordered songs of the originating setlist, empty when opened from the
                // song list. Drives both the jump strip and cross-song paging.
                val contextSetlist = key.setlistId?.let { id -> setlists.find { it.id == id } }
                val setlistSongs = remember(contextSetlist?.songIds, songs.toList()) {
                    contextSetlist?.songIds?.mapNotNull { id -> songs.find { it.id == id } }
                        ?: emptyList()
                }

                SongDetailScreen(
                    song = song,
                    allSetlists = setlists,
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
                    // On compact widths the strip cannot live in the rail, so the detail
                    // screen shows its own horizontal version.
                    showSetlistStrip = showBackButton,
                    knownGenres = knownGenres,
                    onBackClick = { navigator.goBack() },
                    showBackButton = showBackButton,
                    onPageTurn = { lastPageTurnTime = System.currentTimeMillis() },
                    onSongUpdated = { updatedSong ->
                        val idx = songs.indexOfFirst { it.id == updatedSong.id }
                        if (idx != -1) {
                            songs[idx] = updatedSong
                            repository.saveSongs(songs)
                        }
                    },
                    onSongDeleted = { deletedSong ->
                        // Leave the detail pane first – it would otherwise recompose
                        // against a song that no longer exists.
                        navigator.goBack()
                        deleteSong(deletedSong)
                    },
                    onAddToSetlist = { showAddToSetlistDialog = it },
                    onAttachFile = { target ->
                        songToAttachFileTo = target
                        attachFileLauncher.launch(documentMimeTypes)
                    },
                    onRemoveFile = { target ->
                        val updatedSong = repository.detachFile(target)
                        val idx = songs.indexOfFirst { it.id == updatedSong.id }
                        if (idx != -1) {
                            songs[idx] = updatedSong
                            repository.saveSongs(songs)
                        }
                    },
                )
            }
        }
        entry<AppRoute.SetlistDetail>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) { key: AppRoute.SetlistDetail ->
            val setlist = setlists.find { it.id == key.setlistId }
            if (setlist != null) {
                val showBackButton = !currentWindowAdaptiveInfoV2().windowSizeClass
                    .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
                SetlistDetailScreen(
                    setlist = setlist,
                    songs = songs,
                    onSongClick = {
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
                            setlists[idx] = setlists[idx].copy(lastSongId = null, lastPage = 0)
                            repository.saveSetlists(setlists)
                        }
                        navigator.navigate(
                            AppRoute.SongDetail(
                                songId = firstSong.id,
                                setlistId = setlist.id,
                                startPage = 0,
                            )
                        )
                    },
                    onBackClick = { navigator.goBack() },
                    showBackButton = showBackButton,
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
    val railSongs = remember(currentRoute, songs.toList(), setlists.toList()) {
        (currentRoute as? AppRoute.SongDetail)?.setlistId?.let { sid ->
            setlists.find { it.id == sid }?.songIds?.mapNotNull { id -> songs.find { it.id == id } }
        } ?: emptyList()
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)

    NavigationSuiteScaffoldLayout(
        navigationSuite = {
            NavigationSuite(
                navigationSuiteType = navigationSuiteType
            ) {
                val isVertical = (navigationSuiteType == NavigationSuiteType.WideNavigationRailCollapsed) ||
                                 (navigationSuiteType == NavigationSuiteType.WideNavigationRailExpanded) ||
                                 (navigationSuiteType == NavigationSuiteType.NavigationRail)

                if (isVertical) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NavigationSuiteItem(
                            selected = navigationState.topLevelRoute == AppRoute.Songs,
                            onClick = { navigator.navigate(AppRoute.Songs) },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                            label = { Text("Songs") },
                            navigationSuiteType = navigationSuiteType
                        )
                        NavigationSuiteItem(
                            selected = navigationState.topLevelRoute == AppRoute.Setlists,
                            onClick = { navigator.navigate(AppRoute.Setlists) },
                            icon = { Icon(Icons.Default.Menu, null) },
                            label = { Text("Setlists") },
                            navigationSuiteType = navigationSuiteType
                        )

                        if (railSongs.size > 1) {
                            Spacer(Modifier.weight(1f))
                            val songRoute = currentRoute as AppRoute.SongDetail
                            SetlistStrip(
                                songs = railSongs,
                                currentSongId = songRoute.songId,
                                flashAlpha = flashAlpha.value,
                                onSongClick = { target ->
                                    navigator.replace(
                                        AppRoute.SongDetail(
                                            songId = target.id,
                                            setlistId = songRoute.setlistId,
                                            startPage = 0,
                                        )
                                    )
                                },
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Preload neighbors for instant switching
                            val currentIndex = railSongs.indexOfFirst { it.id == songRoute.songId }
                            if (currentIndex != -1) {
                                railSongs.getOrNull(currentIndex + 1)?.let { PdfPreloader(it.fileUri) }
                                railSongs.getOrNull(currentIndex - 1)?.let { PdfPreloader(it.fileUri) }
                            }
                        }
                    }
                } else {
                    NavigationSuiteItem(
                        selected = navigationState.topLevelRoute == AppRoute.Songs,
                        onClick = { navigator.navigate(AppRoute.Songs) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        label = { Text("Songs") },
                        navigationSuiteType = navigationSuiteType
                    )
                    NavigationSuiteItem(
                        selected = navigationState.topLevelRoute == AppRoute.Setlists,
                        onClick = { navigator.navigate(AppRoute.Setlists) },
                        icon = { Icon(Icons.Default.Menu, null) },
                        label = { Text("Setlists") },
                        navigationSuiteType = navigationSuiteType
                    )
                }
            }
        },
        layoutType = navigationSuiteType
    ) {
        NavDisplay(
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            sceneStrategies = listOf(listDetailStrategy)
        )
    }
}
