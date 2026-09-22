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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import de.workflow42.meinenoten.model.PageView
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.components.MusicXmlView
import de.workflow42.meinenoten.ui.components.PdfView
import de.workflow42.meinenoten.ui.components.SetlistStripHorizontal
import de.workflow42.meinenoten.ui.components.DeleteSongDialog
import de.workflow42.meinenoten.ui.components.EditSongDialog
import de.workflow42.meinenoten.ui.components.SongOverflowMenu
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/**
 * Page-turn cue colour. Matches the setlist strip highlight so both cues read as the
 * same signal rather than two unrelated events.
 */
private val FlashGreen = Color(0xFF7BA07E)

/**
 * Deliberately narrow: wide enough to register in peripheral vision while playing,
 * narrow enough to never cover a stave.
 */
private val FlashBorderWidth = 6.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    song: Song,
    allSetlists: List<Setlist>,
    onBackClick: () -> Unit,
    onSongUpdated: (Song) -> Unit,
    onSongDeleted: (Song) -> Unit,
    /** Opens the "add to setlist" picker, which is owned by the host. */
    onAddToSetlist: (Song) -> Unit,
    /** Opens the file picker to attach or replace this song's score. */
    onAttachFile: (Song) -> Unit,
    /** Drops the score file, leaving a text-only song. */
    onRemoveFile: (Song) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    /**
     * Genres already in use, offered as chips in the edit dialog so the same category
     * is not re-typed with a different spelling.
     */
    knownGenres: List<String> = emptyList(),
    /** Ordered songs of the setlist this song was opened from; empty otherwise. */
    setlistSongs: List<Song> = emptyList(),
    /** Opens another song. The flag requests its last page instead of its first. */
    onNavigateToSong: (Song, Boolean) -> Unit = { _, _ -> },
    /** Set when arriving by paging backwards, so the song opens on its final page. */
    openAtEnd: Boolean = false,
    /** Zero-based page to open on. Ignored when [openAtEnd] is set. */
    startPage: Int = 0,
    /**
     * Reports the current position (song id and zero-based page) so the setlist being
     * played can be resumed there. Not called when opened outside a setlist.
     */
    onProgress: (String, Int) -> Unit = { _, _ -> },
    /**
     * Reports that the end of the setlist has been reached, so its stored position can
     * be cleared and the programme starts from the top next time.
     */
    onFinished: () -> Unit = {},
    /**
     * True on compact widths, where the navigation rail (and with it the vertical
     * [SetlistStripHorizontal] counterpart) is not shown. The screen then hosts its own
     * position indicator so the running order stays visible on a phone.
     */
    showSetlistStrip: Boolean = false,
    /** Called when a page turn occurs (for UI cues). */
    onPageTurn: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Start where the caller asked: a page carried over from a setlist's saved
    // position, or 0 when the song was opened from the library. -1 means "resolve to
    // the last page once the page count is known".
    var currentPage by remember(song.id) {
        mutableIntStateOf(if (openAtEnd) -1 else startPage)
    }
    var pageCount by remember(song.id) { mutableIntStateOf(0) }
    var isUiVisible by remember { mutableStateOf(value = true) }
    val focusRequester = remember { FocusRequester() }

    val flashAlpha = remember { Animatable(0f) }

    val triggerFlash: suspend () -> Unit = {
        onPageTurn()
        // Fast attack, slightly longer decay to cover the PDF rendering time
        flashAlpha.animateTo(0.2f, animationSpec = tween(50))
        flashAlpha.animateTo(0f, animationSpec = tween(500))
    }

    val isPagedDocument = song.sourceType == SongSource.PDF

    // A song can hold a score *and* its typed text. The text view is the only option
    // when there is no file, and an opt-in toggle once one has been attached.
    var showLyrics by remember(song.id) { mutableStateOf(!song.hasFile) }

    var localPageViews by remember(song.id, song.pageViews) { mutableStateOf(song.pageViews) }

    LaunchedEffect(localPageViews) {
        if (localPageViews == song.pageViews) return@LaunchedEffect
        delay(300)
        onSongUpdated(song.copy(pageViews = localPageViews))
    }
    val lyricsVisible = showLyrics || !song.hasFile

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
            delay(1.5.seconds)
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

    // Report the position back to the setlist that is being played, so it can be resumed
    // there. Deliberately *not* stored on the song: the same song can sit in several
    // programmes, and looking it up in the library must not move a service's position.
    //
    // Reaching the final page of the final song reports "finished" instead. Only this
    // screen can tell: the setlist screen has no way to know a PDF's page count.
    LaunchedEffect(song.id, currentPage, pageCount, setlistIndex) {
        if (currentPage < 0) return@LaunchedEffect

        val isLastSong = (setlistIndex >= 0) && (setlistIndex == setlistSongs.lastIndex)
        val atLastPage = if (isPagedDocument) {
            (pageCount > 0) && (currentPage >= pageCount - 1)
        } else {
            // Text and MusicXML are one continuous view, so being there is being at
            // its end.
            true
        }

        if (isLastSong && atLastPage) {
            onFinished()
        } else {
            onProgress(song.id, currentPage)
        }
    }

    fun goToNextPage() {
        // While the target page is still unresolved a pedal press must do nothing,
        // otherwise it would skip straight into the following song.
        if (currentPage < 0) return

        if (isPagedDocument && (currentPage < pageCount - 1)) {
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
    
    var showEditDialog by remember { mutableStateOf(value = false) }
    var showDeleteConfirm by remember { mutableStateOf(value = false) }

    val songSetlists = remember(song.id, allSetlists.toList()) {
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
        DeleteSongDialog(
            song = song,
            affectedSetlists = songSetlists,
            onConfirm = {
                showDeleteConfirm = false
                showEditDialog = false
                onSongDeleted(song)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showEditDialog) {
        EditSongDialog(
            song = song,
            songSetlists = songSetlists,
            knownGenres = knownGenres,
            onSave = { updatedSong ->
                onSongUpdated(updatedSong)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
            // The dialog stays open across the picker round trip, so the result is
            // visible right where it was requested.
            onAttachFile = { onAttachFile(song) },
            onRemoveFile = { onRemoveFile(song) },
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
                    title = {
                        // Prefixing the running-order position keeps "which song am I in"
                        // answerable even where the setlist strip has no room.
                        Text(
                            text = if (setlistIndex >= 0 && setlistSongs.size > 1) {
                                stringResource(
                                    R.string.msg_song_of_total,
                                    setlistIndex + 1,
                                    setlistSongs.size,
                                    song.displayTitle,
                                )
                            } else {
                                song.displayTitle
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back)
                                )
                            }
                        }
                    },
                    actions = {
                        // Only meaningful when both representations exist.
                        if (song.hasFile && song.lyrics.isNotBlank()) {
                            IconButton(onClick = { showLyrics = !showLyrics }) {
                                Icon(
                                    imageVector = if (showLyrics) {
                                        Icons.AutoMirrored.Filled.Article
                                    } else {
                                        Icons.AutoMirrored.Filled.Subject
                                    },
                                    contentDescription = if (showLyrics) {
                                        stringResource(R.string.cd_show_score)
                                    } else {
                                        stringResource(R.string.cd_show_lyrics)
                                    },
                                )
                            }
                        }
                        // Same three entries, same order as in the song list.
                        SongOverflowMenu(
                            onEdit = { showEditDialog = true },
                            onAddToSetlist = { onAddToSetlist(song) },
                            onDelete = { showDeleteConfirm = true },
                        )
                        if (isPagedDocument && pageCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.msg_page_of,
                                    currentPage.coerceAtLeast(0) + 1,
                                    pageCount,
                                ),
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
                // Tap zones: lower third: left half = back, right half = forward.
                // Everywhere else = toggle UI. Horizontal swipe turns pages when not zoomed.
                .pointerInput(isPagedDocument, pageCount) {
                    detectTapGestures { offset ->
                        focusRequester.requestFocus()
                        val lowerThirdHeight = size.height * 2f / 3f
                        if (offset.y > lowerThirdHeight) {
                            if (offset.x < size.width / 2f) {
                                goToPreviousPage()
                            } else {
                                goToNextPage()
                            }
                        } else {
                            isUiVisible = !isUiVisible
                        }
                    }
                }
                .pointerInput(isPagedDocument, pageCount, localPageViews, currentPage) {
                    if (!isPagedDocument) return@pointerInput
                    val currentScale = localPageViews[currentPage.coerceAtLeast(0)]?.scale ?: 1f
                    if (currentScale > 1.01f) return@pointerInput
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
                lyricsVisible -> {
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
                        if (song.lyrics.isNotBlank()) {
                            Text(
                                text = song.lyrics,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            // Reachable for a song that has neither file nor text yet.
                            Text(
                                text = stringResource(R.string.empty_no_content),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
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
                        currentPage = currentPage.coerceAtLeast(0),
                        pageView = localPageViews[currentPage.coerceAtLeast(0)] ?: PageView(),
                        onPageViewChange = { newView ->
                            localPageViews = localPageViews.toMutableMap().apply {
                                put(currentPage.coerceAtLeast(0), newView)
                            }
                        },
                        modifier = Modifier.padding(if (isUiVisible) innerPadding else PaddingValues(0.dp)),
                        onPageCountReady = { count ->
                            pageCount = count
                        }
                    )
                }
            }

            // Page-turn cue. A narrow band at the edges instead of the former
            // full-screen veil, which washed out the score at the exact moment it was
            // needed. Works without a setlist too, which the strip highlight cannot.
            if (flashAlpha.value > 0f) {
                val cueAlpha = (flashAlpha.value * 4f).coerceAtMost(0.8f)
                Box(
                    Modifier
                        .matchParentSize()
                        .border(FlashBorderWidth, FlashGreen.copy(alpha = cueAlpha))
                )
            }

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

            // Notes Overlay – a memo beside the score. Pointless over the text view,
            // which has room for everything anyway.
            if (isUiVisible && song.notes.isNotBlank() && !lyricsVisible) {
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
                            text = stringResource(R.string.label_notes),
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
            // Compact widths have no navigation rail, so the setlist strip lives here
            // instead. Without it there is no way to see the running order position on a
            // phone, in portrait or landscape.
            if (showSetlistStrip && setlistSongs.size > 1) {
                SetlistStripHorizontal(
                    songs = setlistSongs,
                    currentSongId = song.id,
                    flashAlpha = flashAlpha.value,
                    onSongClick = { target -> onNavigateToSong(target, false) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(innerPadding)
                        .padding(start = 12.dp, top = 8.dp, end = 12.dp)
                )
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_previous_page),
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.msg_page_of,
                                currentPage.coerceAtLeast(0) + 1,
                                pageCount,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { goToNextPage() },
                            enabled = currentPage < pageCount - 1 || nextSong != null
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.cd_next_page),
                            )
                        }
                    }
                }
            }
        }
    }
}
