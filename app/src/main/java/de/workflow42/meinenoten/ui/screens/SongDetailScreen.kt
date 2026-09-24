package de.workflow42.meinenoten.ui.screens

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import de.workflow42.meinenoten.data.AppSettings
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.components.MusicXmlView
import de.workflow42.meinenoten.ui.components.NoteAuthorLabel
import de.workflow42.meinenoten.model.otherNotes
import de.workflow42.meinenoten.model.ownNote
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.workflow42.meinenoten.ui.components.PdfView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    /** Status bar, tap zones, keys and display behaviour, all user-adjustable. */
    settings: AppSettings,
    /** Opens the navigation drawer, which now holds the song actions as well. */
    onMenuClick: () -> Unit,
    onSongUpdated: (Song) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the typed lyrics are shown instead of the score. Hoisted because the
     * toggle lives in the navigation drawer, outside this screen. Ignored for songs
     * without a file, which can only show their text.
     */
    showLyrics: Boolean = false,
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
    /** Author numbers across the library, see [authorNumbers]. */
    authorNumbers: Map<String, Int> = emptyMap(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Start where the caller asked: a page carried over from a setlist's saved
    // position, or 0 when the song was opened from the library. -1 means "resolve to
    // the last page once the page count is known".
    var currentPage by remember(song.id) {
        mutableIntStateOf(if (openAtEnd) -1 else startPage)
    }
    var pageCount by remember(song.id) { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    var notesExpanded by remember(song.id) { mutableStateOf(value = false) }
    // Other people's notes are hidden by default and switched on per person. Not keyed on
    // the song: whoever is followed stays visible for the rest of the set.
    var shownAuthors by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val flashAlpha = remember { Animatable(0f) }

    val triggerFlash: suspend () -> Unit = {
        if (settings.pageTurnFlash) {
            // Fast attack, slightly longer decay to cover the PDF rendering time
            flashAlpha.animateTo(0.2f, animationSpec = tween(50))
            flashAlpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    val isPagedDocument = song.sourceType == SongSource.PDF

    // With "remember zoom" off, stored zoom levels are neither applied nor written:
    // the view still zooms, but only for as long as the song stays open.
    var localPageViews by remember(song.id, song.pageViews, settings.rememberZoom) {
        mutableStateOf(if (settings.rememberZoom) song.pageViews else emptyMap())
    }

    LaunchedEffect(localPageViews, settings.rememberZoom) {
        if (!settings.rememberZoom) return@LaunchedEffect
        if (localPageViews == song.pageViews) return@LaunchedEffect
        delay(300)
        onSongUpdated(song.copy(pageViews = localPageViews))
    }
    // A song can hold a score *and* its typed text. The text view is the only option
    // when there is no file, and an opt-in toggle once one has been attached.
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
            if (settings.songChangeBanner) songChangeLabel = nextSong.displayTitle
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
            if (settings.songChangeBanner) songChangeLabel = previousSong.displayTitle
            onNavigateToSong(previousSong, true)
        }
    }
    
    // What the status bar buttons do next. Computed once so the icon, the enabled state
    // and the actual action cannot disagree about whether a song change is coming.
    val resolvedPage = currentPage.coerceAtLeast(0)
    val hasNextPage = isPagedDocument && (resolvedPage < pageCount - 1)
    val hasPreviousPage = isPagedDocument && (resolvedPage > 0)
    val canGoNext = hasNextPage || (nextSong != null)
    val canGoPrevious = hasPreviousPage || (previousSong != null)
    // ⏭ / ⏮ instead of the plain arrow warns that the next tap leaves this song. Only
    // once the page count is known, so a PDF still loading does not flash the wrong icon.
    val pagesKnown = !isPagedDocument || (pageCount > 0)
    val nextIsSongChange = settings.announceSongChange && pagesKnown &&
        !hasNextPage && (nextSong != null)
    val previousIsSongChange = settings.announceSongChange && pagesKnown &&
        !hasPreviousPage && (previousSong != null)

    // Keyed on the setting so switching it in the drawer's settings screen and coming
    // back takes effect without reopening the song.
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        focusRequester.requestFocus()

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        topBar = {
            // Always visible: it is the status bar of the performance (where am I, what
            // comes next), so hiding it on a tap would hide exactly what is needed.
            CenterAlignedTopAppBar(
                title = {
                    SongStatusTitle(
                        song = song,
                        settings = settings,
                        setlistPosition = setlistIndex.takeIf { it >= 0 && setlistSongs.size > 1 }
                            ?.let { it + 1 to setlistSongs.size },
                        pagePosition = if (isPagedDocument && pageCount > 0) {
                            resolvedPage + 1 to pageCount
                        } else {
                            null
                        },
                    )
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // No back arrow: system back still works, and the menu is the one
                        // way into everything else, wherever the song was opened from.
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.cd_open_menu),
                            )
                        }
                        if (settings.showPageButtons) {
                            PageTurnButton(
                                onClick = { goToPreviousPage() },
                                enabled = canGoPrevious,
                                isSongChange = previousIsSongChange,
                                forward = false,
                            )
                        }
                    }
                },
                actions = {
                    // A memo beside the score (capo, tuning). Behind a button rather than
                    // an overlay, so it never covers a stave.
                    val ownNote = song.notes.ownNote(settings.userId)?.takeIf { it.text.isNotBlank() }
                    val othersNotes = song.notes.otherNotes(settings.userId)
                    if (ownNote != null || othersNotes.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { notesExpanded = !notesExpanded }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.StickyNote2,
                                    contentDescription = stringResource(R.string.label_notes),
                                )
                            }
                            DropdownMenu(
                                expanded = notesExpanded,
                                onDismissRequest = { notesExpanded = false },
                            ) {
                                if (ownNote != null) {
                                    Text(
                                        text = ownNote.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .widthIn(max = 320.dp)
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                if (othersNotes.isNotEmpty()) {
                                    if (ownNote != null) HorizontalDivider()
                                    othersNotes.forEach { note ->
                                        val shown = note.authorId in shownAuthors
                                        val authorName = note.authorName.ifBlank {
                                            stringResource(R.string.note_author_unknown)
                                        }
                                        val showNoteLabel = stringResource(R.string.cd_show_note, authorName)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .widthIn(max = 320.dp)
                                                .padding(start = 16.dp, end = 4.dp),
                                        ) {
                                            NoteAuthorLabel(
                                                note = note,
                                                settings = settings,
                                                number = authorNumbers[note.authorId],
                                                modifier = Modifier.weight(1f),
                                            )
                                            Switch(
                                                checked = shown,
                                                onCheckedChange = { on ->
                                                    shownAuthors = if (on) {
                                                        shownAuthors + note.authorId
                                                    } else {
                                                        shownAuthors - note.authorId
                                                    }
                                                },
                                                modifier = Modifier.semantics {
                                                    contentDescription = showNoteLabel
                                                },
                                            )
                                        }
                                        if (shown) {
                                            Text(
                                                text = note.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier
                                                    .widthIn(max = 320.dp)
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (settings.showPageButtons) {
                        PageTurnButton(
                            onClick = { goToNextPage() },
                            enabled = canGoNext,
                            isSongChange = nextIsSongChange,
                            forward = true,
                        )
                    }
                },
            )
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                val isVolumeKey = (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) ||
                    (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                // Not consumed when switched off, so the system still changes the volume.
                if (isVolumeKey && !settings.volumeKeysTurnPages) return@onKeyEvent false

                val forward = when (keyCode) {
                    KeyEvent.KEYCODE_PAGE_DOWN,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_VOLUME_DOWN -> true
                    KeyEvent.KEYCODE_PAGE_UP,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    KeyEvent.KEYCODE_VOLUME_UP -> false
                    else -> return@onKeyEvent false
                }
                // Some pedals are wired the other way round, or mounted for the other
                // foot; reversing here covers every key at once.
                if (forward != settings.reversePedalDirection) goToNextPage() else goToPreviousPage()
                true
            }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tap zones cover only the bottom part of the screen: the upper area
                // stays free for zooming and panning, where accidental touches happen.
                // No swipe-to-turn on purpose, since it clashed with panning a zoomed page.
                .pointerInput(
                    settings.tapZonesEnabled,
                    settings.tapZoneSize,
                    settings.swapTapZones,
                ) {
                    detectTapGestures { offset ->
                        focusRequester.requestFocus()
                        if (!settings.tapZonesEnabled) return@detectTapGestures
                        val zoneTop = size.height * (1f - settings.tapZoneSize.heightFraction)
                        if (offset.y < zoneTop) return@detectTapGestures
                        val leftHalf = offset.x < size.width / 2f
                        if (leftHalf != settings.swapTapZones) goToPreviousPage() else goToNextPage()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                lyricsVisible -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(innerPadding)
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
                        modifier = Modifier.padding(innerPadding)
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
                        modifier = Modifier.padding(innerPadding),
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
        }
    }
}

/**
 * Centre of the status bar: position on the first line, song title on the second.
 *
 * [setlistPosition] and [pagePosition] are (current, total) pairs, null when they do not
 * apply. When neither is shown the title moves up to become the only, larger line, so
 * the bar never shows an empty row above a small title.
 */
@Composable
private fun SongStatusTitle(
    song: Song,
    settings: AppSettings,
    setlistPosition: Pair<Int, Int>?,
    pagePosition: Pair<Int, Int>?,
) {
    val songPart = setlistPosition?.takeIf { settings.showSongPosition }?.let { (current, total) ->
        stringResource(R.string.msg_status_song, current, total)
    }
    val pagePart = pagePosition?.takeIf { settings.showPageNumber }?.let { (current, total) ->
        stringResource(R.string.msg_status_page, current, total)
    }
    val firstLine = when {
        (songPart != null) && (pagePart != null) ->
            stringResource(R.string.msg_status_joined, songPart, pagePart)
        else -> songPart ?: pagePart
    }

    if (firstLine == null) {
        Text(
            text = song.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = firstLine,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (settings.showSongTitle) {
            Text(
                text = song.displayTitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * ◀ / ▶ in the status bar. With [isSongChange] set it shows ⏮ / ⏭ instead, so the tap
 * that leaves the current song is recognisable before it happens.
 *
 * At least 56 dp, larger than the default icon button: it is hit mid-performance,
 * often without looking.
 */
@Composable
private fun PageTurnButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isSongChange: Boolean,
    forward: Boolean,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp),
    ) {
        when {
            forward && isSongChange -> Icon(
                Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.cd_next_song),
            )
            forward -> Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.cd_next_page),
            )
            isSongChange -> Icon(
                Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous_song),
            )
            else -> Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_previous_page),
            )
        }
    }
}
