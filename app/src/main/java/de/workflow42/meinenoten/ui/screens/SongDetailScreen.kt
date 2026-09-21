package de.workflow42.meinenoten.ui.screens

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.components.MusicXmlView
import de.workflow42.meinenoten.ui.components.PdfView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    song: Song,
    allSetlists: List<Setlist>,
    onBackClick: () -> Unit,
    onSongUpdated: (Song) -> Unit,
    onSongDeleted: (Song) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    /** Ordered songs of the setlist this song was opened from; empty otherwise. */
    setlistSongs: List<Song> = emptyList(),
    /** Opens another song. The flag requests its last page instead of its first. */
    onNavigateToSong: (Song, Boolean) -> Unit = { _, _ -> },
    /** Set when arriving by paging backwards, so the song opens on its final page. */
    openAtEnd: Boolean = false,
    /** If true, resumes at [Song.lastPage]; if false, starts at page 1. */
    resumeLastPage: Boolean = true,
    /** Called when a page turn occurs (for UI cues). */
    onPageTurn: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Start on the page the user last viewed, unless we paged in from the next song.
    var currentPage by remember(song.id) {
        mutableIntStateOf(
            when {
                openAtEnd -> -1
                resumeLastPage -> song.lastPage
                else -> 0
            }
        )
    }
    var pageCount by remember(song.id) { mutableIntStateOf(0) }
    var isUiVisible by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    val flashAlpha = remember { Animatable(0f) }

    val triggerFlash: suspend () -> Unit = {
        onPageTurn()
        // Fast attack, slightly longer decay to cover the PDF rendering time
        flashAlpha.animateTo(0.2f, animationSpec = tween(50))
        flashAlpha.animateTo(0f, animationSpec = tween(500))
    }

    val isPagedDocument = song.sourceType == SongSource.PDF

    // Position within the setlist, or -1 when opened from the song list.
    val setlistIndex = remember(song.id, setlistSongs) {
        setlistSongs.indexOfFirst { it.id == song.id }
    }
    val nextSong = setlistSongs.getOrNull(setlistIndex + 1).takeIf { setlistIndex >= 0 }
    val previousSong = if (setlistIndex > 0) setlistSongs[setlistIndex - 1] else null

    // Banner naming the song we just moved to, so an accidental extra pedal press is
    // noticed immediately rather than at the first wrong chord.
    var songChangeLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(songChangeLabel) {
        if (songChangeLabel != null) {
            delay(1500)
            songChangeLabel = null
        }
    }

    // A negative page means "open at the end", which can only be resolved once the PDF
    // has reported its page count. Resolving earlier would fall back to page 1 and lose
    // the intent of paging backwards into this song.
    LaunchedEffect(song.id, pageCount, isPagedDocument) {
        if (currentPage < 0) {
            if (pageCount > 0) {
                currentPage = pageCount - 1
            } else if (!isPagedDocument) {
                // Songs without pages never report a count.
                currentPage = 0
            }
        }
    }

    // Remember the current page so reopening the song resumes where it was left.
    LaunchedEffect(song.id, currentPage) {
        if (isPagedDocument && currentPage >= 0 && currentPage != song.lastPage) {
            onSongUpdated(song.copy(lastPage = currentPage))
        }
    }

    fun goToNextPage() {
        // While the target page is still unresolved a pedal press must do nothing,
        // otherwise it would skip straight into the following song.
        if (currentPage < 0) return

        if (isPagedDocument && currentPage < pageCount - 1) {
            currentPage++
            scope.launch { triggerFlash() }
        } else if (nextSong != null) {
            // Past the last page: continue with the next song of the setlist.
            songChangeLabel = nextSong.displayTitle
            onNavigateToSong(nextSong, false)
        }
    }

    fun goToPreviousPage() {
        if (currentPage < 0) return

        if (isPagedDocument && currentPage > 0) {
            currentPage--
            scope.launch { triggerFlash() }
        } else if (previousSong != null) {
            // Before the first page: land on the last page of the previous song.
            songChangeLabel = previousSong.displayTitle
            onNavigateToSong(previousSong, true)
        }
    }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editTitle by remember(song) { mutableStateOf(song.title) }
    var editArtist by remember(song) { mutableStateOf(song.artist) }
    var editVersion by remember(song) { mutableStateOf(song.version) }
    var editBpm by remember(song) { mutableStateOf(song.bpm.toString()) }
    var editTimeSignature by remember(song) { mutableStateOf(song.timeSignature) }
    var editTotalBars by remember(song) { mutableStateOf(song.totalBars.toString()) }
    var editNotes by remember(song) { mutableStateOf(song.notes) }
    
    val songSetlists = remember(song.id, allSetlists) {
        allSetlists.filter { it.songIds.contains(song.id) }
    }

    // Display Always On logic
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        focusRequester.requestFocus()
        
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text("Lied löschen?") },
            text = {
                Column {
                    Text("„${song.displayTitle}“ wird dauerhaft entfernt.")
                    if (songSetlists.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Das Lied wird auch aus diesen Setlisten entfernt:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        songSetlists.forEach { setlist ->
                            Text(
                                text = "• ${setlist.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        showEditDialog = false
                        onSongDeleted(song)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Metadata") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    TextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                    )
                    TextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artist") },
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                    )
                    TextField(
                        value = editVersion,
                        onValueChange = { editVersion = it },
                        label = { Text("Version") },
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                    )
                    TextField(
                        value = editBpm,
                        onValueChange = { editBpm = it },
                        label = { Text("BPM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                    )
                    TextField(
                        value = editTimeSignature,
                        onValueChange = { editTimeSignature = it },
                        label = { Text("Time Signature") },
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                    )
                    TextField(
                        value = editTotalBars,
                        onValueChange = { editTotalBars = it },
                        label = { Text("Total Bars") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                    )
                    TextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Notes (e.g. Capo, Tuning)") },
                        modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
                        minLines = 3
                    )
                    
                    Text(
                        text = "Included in Setlists:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (songSetlists.isEmpty()) {
                        Text(
                            text = "Not in any setlist yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        songSetlists.forEach { setlist ->
                            Text(
                                text = "• ${setlist.title}${if (setlist.date.isNotBlank()) " (${setlist.date})" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedSong = song.copy(
                        title = editTitle,
                        artist = editArtist,
                        version = editVersion,
                        bpm = editBpm.toIntOrNull() ?: song.bpm,
                        timeSignature = editTimeSignature,
                        totalBars = editTotalBars.toIntOrNull() ?: song.totalBars,
                        notes = editNotes
                    )
                    onSongUpdated(updatedSong)
                    showEditDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                // Delete sits here rather than in the list, where a stray swipe while
                // holding an instrument could trigger it by accident.
                Row {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Löschen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isUiVisible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                TopAppBar(
                    title = { Text(text = song.displayTitle) },
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
                        IconButton(onClick = { 
                            editTitle = song.title
                            editArtist = song.artist
                            editVersion = song.version
                            editBpm = song.bpm.toString()
                            editTimeSignature = song.timeSignature
                            editTotalBars = song.totalBars.toString()
                            editNotes = song.notes
                            showEditDialog = true 
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Metadata"
                            )
                        }
                        if (isPagedDocument && pageCount > 0) {
                            Text(
                                text = "${currentPage.coerceAtLeast(0) + 1} / $pageCount",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_PAGE_DOWN,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_SPACE,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            goToNextPage()
                            true
                        }
                        KeyEvent.KEYCODE_PAGE_UP,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            goToPreviousPage()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tap zones: left third = previous page, right third = next page,
                // centre = toggle the UI. Horizontal swipes also turn pages.
                .pointerInput(isPagedDocument, pageCount) {
                    val zone = size.width / 3f
                    detectTapGestures { offset ->
                        focusRequester.requestFocus()
                        when {
                            !isPagedDocument -> isUiVisible = !isUiVisible
                            offset.x < zone -> goToPreviousPage()
                            offset.x > size.width - zone -> goToNextPage()
                            else -> isUiVisible = !isUiVisible
                        }
                    }
                }
                .pointerInput(isPagedDocument, pageCount) {
                    if (!isPagedDocument) return@pointerInput
                    var dragAmount = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAmount = 0f },
                        onDragEnd = {
                            val threshold = size.width * 0.15f
                            if (abs(dragAmount) > threshold) {
                                if (dragAmount < 0) goToNextPage() else goToPreviousPage()
                            }
                        }
                    ) { _, delta -> dragAmount += delta }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                song.sourceType == SongSource.TEXT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(if (isUiVisible) innerPadding else PaddingValues(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (song.artist.isNotBlank()) {
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            text = song.notes,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                song.sourceType == SongSource.MUSIC_XML -> {
                    MusicXmlView(
                        fileUri = song.fileUri,
                        modifier = Modifier.padding(if (isUiVisible) innerPadding else PaddingValues(0.dp))
                    )
                }
                else -> {
                    PdfView(
                        fileUri = song.fileUri,
                        // -1 means "last page", not yet resolved – clamp until it is.
                        currentPage = currentPage.coerceAtLeast(0),
                        modifier = Modifier.padding(if (isUiVisible) innerPadding else PaddingValues(0.dp)),
                        onPageCountReady = { count ->
                            pageCount = count
                        }
                    )
                }
            }

            // The page-turn cue lives in the setlist strip instead of a full-screen
            // overlay, which used to wash out the score at the moment it was needed.

            // Crossing into another song is a bigger jump than a page turn, so it gets
            // named explicitly rather than relying on the flash alone.
            AnimatedVisibility(
                visible = songChangeLabel != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = songChangeLabel.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }

            // Notes Overlay (only for PDF/XML to show quick notes)
            if (isUiVisible && song.notes.isNotBlank() && song.sourceType != SongSource.TEXT) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        // Bottom right, since the jump strip now owns the left edge.
                        .align(Alignment.BottomEnd)
                        .padding(innerPadding)
                        .padding(16.dp)
                        .fillMaxWidth(0.4f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            // Page navigation bar, shown with the rest of the UI.
            if (isUiVisible && isPagedDocument && pageCount > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(innerPadding)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = { goToPreviousPage() },
                            enabled = currentPage > 0 || previousSong != null
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vorherige Seite")
                        }
                        Text(
                            text = "${currentPage.coerceAtLeast(0) + 1} / $pageCount",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { goToNextPage() },
                            enabled = currentPage < pageCount - 1 || nextSong != null
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Nächste Seite")
                        }
                    }
                }
            }
        }
    }
}
