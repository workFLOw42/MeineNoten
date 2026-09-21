package de.workflow42.meinenoten.ui.util

import de.workflow42.meinenoten.model.Song
import java.text.Collator
import java.util.Locale

/**
 * Locale-aware collator for German.
 *
 * A plain `String.compareTo` orders by Unicode code point, which places "Über" after
 * "Zusammen" and makes capitalisation affect the order. The collator sorts umlauts
 * next to their base letter and ignores case differences, which is what a reader
 * expects when scanning a list of song titles.
 */
private val germanCollator: Collator = Collator.getInstance(Locale.GERMAN).apply {
    strength = Collator.SECONDARY
}

/**
 * Sorts songs alphabetically by artist, then by title.
 *
 * Songs without an artist are keyed by their title (see [Song.sortKey]), so they take
 * their natural alphabetical position rather than being grouped separately.
 */
fun List<Song>.sortedForDisplay(): List<Song> =
    sortedWith(
        compareBy<Song, String>(germanCollator) { it.sortKey }
            .thenBy(germanCollator) { it.title }
    )
