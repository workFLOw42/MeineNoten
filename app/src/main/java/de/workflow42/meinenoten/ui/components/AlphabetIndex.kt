package de.workflow42.meinenoten.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Letter strip down the right edge for jumping through a long song list.
 *
 * Only shows the letters the list actually contains, so the strip stays short enough for
 * its entries to be reachable instead of listing a full alphabet of mostly dead targets.
 *
 * **Visible only while scrolling**, like the launcher's app drawer: it fades in when the
 * list moves or a finger rests on it, and fades out again shortly after. That is what
 * lets it sit above the floating action buttons without stealing their taps – while
 * scrolling nobody is pressing a button, and while pressing a button nobody is scrolling.
 *
 * Dragging along the strip scrolls continuously: with a dozen or more letters each target
 * falls below the recommended minimum touch size, so the gesture carries the interaction
 * rather than precise aim. Tapping still works for the larger strips.
 *
 * This is a shortcut, never the only way through the list – the list itself remains fully
 * scrollable and readable by screen readers.
 */
@Composable
fun AlphabetIndex(
    /** Letters in display order, paired with the list index of their section header. */
    sections: List<Pair<String, Int>>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (sections.size < 2) return

    val scope = rememberCoroutineScope()

    // Kept separate from the scroll state: a finger resting on the strip must hold it
    // visible even between scroll events, or it would vanish mid-drag.
    var isTouched by remember { mutableStateOf(value = false) }
    var stripHeight by remember { mutableIntStateOf(0) }

    // Fade out a moment after everything settles. The delay keeps the strip from
    // flickering during the small pauses in a flick-scroll, and leaves it readable just
    // long enough to aim at after the list stops.
    var isVisible by remember { mutableStateOf(value = false) }
    LaunchedEffect(listState.isScrollInProgress, isTouched) {
        if (listState.isScrollInProgress || isTouched) {
            isVisible = true
        } else {
            delay(timeMillis = 900)
            isVisible = false
        }
    }

    /** Scrolls to whichever section the given vertical offset points at. */
    fun jumpTo(offsetY: Float) {
        if (stripHeight <= 0) return
        val fraction = (offsetY / stripHeight).coerceIn(0f, 1f)
        val target = (fraction * sections.size)
            .toInt()
            .coerceIn(0, sections.lastIndex)
        scope.launch {
            // Not animated: during a drag the list has to track the finger, and an
            // animation per step would lag behind and feel rubbery.
            listState.scrollToItem(sections[target].second)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                // Deliberately wrapping its height instead of filling it: the touch
                // position is mapped to a letter as a fraction of this column, so the
                // column has to end where the letters end. Filling the full height would
                // leave most of the strip empty and shift every tap onto a letter well
                // above the one being aimed at.
                .wrapContentHeight()
                .width(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
                )
                .onSizeChanged { stripHeight = it.height }
                .pointerInput(sections) {
                    // One gesture loop handles press, drag and release together. Two
                    // separate detectors (one for taps, one for drags) would both receive
                    // every event and fight over it; this way a press jumps immediately
                    // and any movement keeps steering from there.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isTouched = true
                        jumpTo(down.position.y)

                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    jumpTo(change.position.y)
                                    // Claim the movement so the list underneath does not
                                    // also scroll it.
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        isTouched = false
                    }
                },
        ) {
            sections.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(28.dp)
                        .padding(vertical = 1.dp),
                )
            }
        }
    }
}
