package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.ScoreDarkMode

/**
 * The look actually applied to a score right now.
 *
 * The per-song [ScoreDarkMode] only takes effect while the app shows its dark design.
 * Read from the colour scheme rather than from the settings, so it follows whatever
 * decided the theme – the *Design* setting or, with *System*, the device's day/night
 * switch – without being told separately.
 */
@Composable
@ReadOnlyComposable
fun effectiveScoreMode(mode: ScoreDarkMode): ScoreDarkMode =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) mode else ScoreDarkMode.NORMAL

/**
 * Colour filter for a rendered PDF page.
 *
 * Applied at draw time instead of baked into the bitmap: the cached pages stay valid
 * when the device switches between day and night, and no page has to be re-rendered.
 * Offsets are in the 0–255 range, as in android.graphics.ColorMatrix.
 */
fun scoreColorFilter(mode: ScoreDarkMode): ColorFilter? = when (mode) {
    ScoreDarkMode.NORMAL -> null
    // White paper becomes a light grey that no longer glares; black stays black.
    ScoreDarkMode.SOFT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.72f, 0f, 0f, 0f, 0f,
                0f, 0.70f, 0f, 0f, 0f,
                0f, 0f, 0.64f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    // Not a hard invert to pure black/white: white paper maps to a near-black grey and
    // black notes to a soft light grey, which is easier on the eyes in a dark church.
    ScoreDarkMode.INVERTED -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.85f, 0f, 0f, 0f, 235f,
                0f, -0.85f, 0f, 0f, 235f,
                0f, 0f, -0.85f, 0f, 235f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

/**
 * CSS filter for the MusicXML WebView, matching [scoreColorFilter] as closely as CSS allows.
 * Same values as the web app, so a score looks alike on both.
 */
fun scoreCssFilter(mode: ScoreDarkMode): String = when (mode) {
    ScoreDarkMode.NORMAL -> "none"
    ScoreDarkMode.SOFT -> "brightness(0.72) sepia(0.12)"
    ScoreDarkMode.INVERTED -> "invert(0.92) hue-rotate(180deg)"
}

/**
 * Picker for the per-song [ScoreDarkMode], shown in the song editor.
 *
 * The hint spells out that it only matters in the dark design – otherwise choosing
 * *Invertiert* and seeing no change during the day looks like a bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreDarkModeChoice(
    selected: ScoreDarkMode,
    onSelect: (ScoreDarkMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ScoreDarkMode.NORMAL to R.string.score_dark_mode_normal,
        ScoreDarkMode.SOFT to R.string.score_dark_mode_soft,
        ScoreDarkMode.INVERTED to R.string.score_dark_mode_inverted,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.label_score_dark_mode),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(stringResource(label))
                }
            }
        }
        Text(
            text = stringResource(R.string.msg_score_dark_mode_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
