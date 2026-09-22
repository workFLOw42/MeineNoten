package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.model.Song

/**
 * Muted green for the current position. Kept deliberately desaturated so the brighter
 * page-turn flash on top of it stays the element that catches the eye.
 */
private val HighlightGreen = Color(0xFF7BA07E)

/**
 * Vertical strip of setlist positions for jumping around inside a setlist.
 *
 * Running order changes constantly during a service. Without this, skipping a song
 * means leaving the score, finding the setlist, and reopening — this turns it into a
 * single tap.
 *
 * [flashAlpha] brightens the current position on every page turn, which replaces the
 * full-screen flash so the score itself is never obscured.
 */
@Composable
fun SetlistStrip(
    songs: List<Song>,
    currentSongId: String,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    flashAlpha: Float = 0f,
) {
    if (songs.size < 2) return

    val currentIndex = songs.indexOfFirst { it.id == currentSongId }
    val listState = rememberLazyListState()

    // Keep the current position in view; long setlists scroll past the screen.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.width(56.dp)
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(4.dp)
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                PositionTile(
                    position = index + 1,
                    isCurrent = song.id == currentSongId,
                    flashAlpha = flashAlpha,
                    onClick = { onSongClick(song) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }
        }
    }
}

@Composable
private fun PositionTile(
    position: Int,
    isCurrent: Boolean,
    flashAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    // The current position has a steady background, plus a white flash overlay
    // on every page turn to provide immediate visual feedback.
    val background = when {
        isCurrent -> HighlightGreen.copy(alpha = 0.85f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .background(background, MaterialTheme.shapes.small)
            .border(
                width = if (isCurrent) 0.dp else 1.dp,
                color = if (isCurrent) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isCurrent && (flashAlpha > 0f)) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = (flashAlpha * 3f).coerceAtMost(0.7f)), MaterialTheme.shapes.small)
            )
        }
        Text(
            // Two digits keep the column from shifting between 9 and 10.
            text = label ?: position.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = if (label != null) 10.dp else 0.dp)
        )
    }
}

/**
 * Horizontal variant for compact widths, where the navigation suite is a bottom bar and
 * there is no rail to host the vertical strip. Without this, a phone user has no way of
 * telling which song of the setlist is currently open — in either orientation.
 *
 * The current position is labelled "n/total" instead of just its number, so the running
 * order stays readable even when the row is scrolled.
 */
@Composable
fun SetlistStripHorizontal(
    songs: List<Song>,
    currentSongId: String,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    flashAlpha: Float = 0f
) {
    if (songs.size < 2) return

    val currentIndex = songs.indexOfFirst { it.id == currentSongId }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.height(48.dp)
    ) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(4.dp)
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                val isCurrent = song.id == currentSongId
                PositionTile(
                    position = index + 1,
                    isCurrent = isCurrent,
                    flashAlpha = flashAlpha,
                    onClick = { onSongClick(song) },
                    // The active position carries the count so "where am I" is answered
                    // without counting tiles.
                    label = if (isCurrent) "${index + 1}/${songs.size}" else null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 40.dp)
                )
            }
        }
    }
}
