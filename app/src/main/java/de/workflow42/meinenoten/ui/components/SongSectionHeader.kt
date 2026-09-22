package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Section divider in the song list: the initial letter on a grey band spanning the width.
 *
 * Used as a sticky header, so while scrolling through a long library the current letter
 * stays visible at the top instead of disappearing upwards. That pairs with the
 * [AlphabetIndex]: the index says where you can go, this says where you are.
 *
 * Deliberately plain and short. It is a signpost passed at speed, not content.
 */
@Composable
fun SongSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
