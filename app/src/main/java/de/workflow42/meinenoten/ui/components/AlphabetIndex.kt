package de.workflow42.meinenoten.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Height of one entry in the strip.
 *
 * Fixed rather than wrapping the text: the touch position is mapped to a letter as an
 * equal fraction per entry, so every row has to be the same height – including the
 * highlighted one, which renders its letter larger. Growing that row would shift every
 * letter below it away from the fraction that points at it.
 */
private val RowHeight = 20.dp

/** Width of the strip itself; the bubble sits outside it. */
private val StripWidth = 28.dp

/** Size of the letter bubble shown next to the finger. */
private val BubbleSize = 64.dp

/** Room reserved left of the strip for the bubble, bubble plus a small gap. */
private val BubbleLane = 76.dp

/**
 * Letter strip down the right edge for jumping through a long list.
 *
 * Only shows the letters the list actually contains, so the strip stays short enough for
 * its entries to be reachable instead of listing a full alphabet of mostly dead targets.
 *
 * **Visible only while scrolling**, like the launcher's app drawer: it fades in when the
 * list moves or a finger rests on it, and fades out again shortly after. That is what
 * lets it sit above the floating action buttons without stealing their taps.
 *
 * The section currently at the top of the list is highlighted, so the strip also says
 * where you are, not just where you can go. While a finger is on the strip a large
 * bubble next to it repeats the letter under the finger – the finger itself covers the
 * strip, and without the bubble the drag is blind.
 *
 * Dragging along the strip scrolls continuously: with a dozen or more letters each target
 * falls below the recommended minimum touch size, so the gesture carries the interaction
 * rather than precise aim.
 *
 * This is a shortcut, never the only way through the list – the list itself remains fully
 * scrollable and readable by screen readers.
 */
@Composable
fun AlphabetIndex(
    /** Labels in display order, paired with the list index of their section header. */
    sections: List<Pair<String, Int>>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (sections.size < 2) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Kept separate from the scroll state: a finger resting on the strip must hold it
    // visible even between scroll events, or it would vanish mid-drag.
    var isTouched by remember { mutableStateOf(value = false) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var touchedIndex by remember { mutableIntStateOf(0) }

    // Derived, so scrolling only recomposes the strip when the section actually changes,
    // not on every pixel of movement.
    val currentIndex by remember(sections) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            sections.indexOfLast { it.second <= first }.coerceAtLeast(0)
        }
    }

    // Fade out a moment after everything settles. The delay keeps the strip from
    // flickering during the small pauses in a flick-scroll.
    var isVisible by remember { mutableStateOf(value = false) }
    LaunchedEffect(listState.isScrollInProgress, isTouched) {
        if (listState.isScrollInProgress || isTouched) {
            isVisible = true
        } else {
            delay(timeMillis = 900)
            isVisible = false
        }
    }

    val stripHeightPx = with(density) { (RowHeight * sections.size).toPx() }

    /** Scrolls to whichever section the given vertical offset points at. */
    fun jumpTo(offsetY: Float) {
        val clampedY = offsetY.coerceIn(0f, stripHeightPx)
        touchY = clampedY
        val fraction = clampedY / stripHeightPx
        val target = (fraction * sections.size).toInt().coerceIn(0, sections.lastIndex)
        touchedIndex = target
        scope.launch {
            // Not animated: during a drag the list has to track the finger.
            listState.scrollToItem(sections[target].second)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // The bubble gets its own lane left of the strip. It is purely drawn: nothing in
        // that lane handles pointer input, so touches there fall through to the list.
        Row(verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.width(BubbleLane).height(RowHeight * sections.size)) {
                if (isTouched) {
                    val bubbleHalf = with(density) { (BubbleSize / 2).toPx() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { IntOffset(0, (touchY - bubbleHalf).roundToInt()) }
                            .size(BubbleSize)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Text(
                            text = sections[touchedIndex].first,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(StripWidth)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
                    )
                    .pointerInput(sections) {
                        // One gesture loop handles press, drag and release together, so a
                        // press jumps immediately and any movement keeps steering.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isTouched = true
                            jumpTo(down.position.y)

                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        jumpTo(change.position.y)
                                        // Claim the movement so the list underneath does
                                        // not also scroll.
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            isTouched = false
                        }
                    },
            ) {
                sections.forEachIndexed { index, (label, _) ->
                    IndexEntry(label = label, isCurrent = index == currentIndex)
                }
            }
        }
    }
}

/**
 * One label in the strip.
 *
 * The highlight animates size and colour rather than snapping, so a fast scroll reads as
 * the marker sliding down the strip instead of flickering between letters. The row keeps
 * [RowHeight] whatever the text size, which keeps the touch mapping exact.
 */
@Composable
private fun IndexEntry(label: String, isCurrent: Boolean) {
    // Four-digit years get a step smaller than letters, so they still fit the strip.
    val isLong = label.length > 2
    val fontSize by animateFloatAsState(
        targetValue = when {
            isCurrent && isLong -> 11f
            isCurrent -> 14f
            isLong -> 9f
            else -> 11f
        },
        label = "indexFontSize",
    )
    val textColor by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "indexTextColor",
    )
    val circleColor by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        label = "indexCircleColor",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.width(StripWidth).height(RowHeight),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                // At least a circle, wider (a pill) for years; allowed past the parent's
                // width so a long label is never clipped mid-digit.
                .requiredWidthIn(min = RowHeight)
                .height(RowHeight)
                .background(circleColor, CircleShape)
                .padding(horizontal = 2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // Years are four digits; letting them shrink rather than wrap keeps the
                // row height fixed.
                softWrap = false,
            )
        }
    }
}
