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

/** Order of the song list. Chosen by the user, never persisted. */
enum class SongSortMode(val label: String) {
    /** Default: artist, then title. */
    ARTIST("Künstler"),

    /** Title only – for songs known by their first line rather than their composer. */
    TITLE("Titel"),

    /** Grouped by genre, alphabetical within each group. */
    GENRE("Genre"),

    /**
     * The running order of the selected setlist. Only offered while a setlist filter is
     * active, because without one there is no order to follow.
     */
    SETLIST_ORDER("Reihenfolge")
}

/**
 * Sorts songs for display.
 *
 * [SongSortMode.SETLIST_ORDER] is not handled here – it needs the setlist's `songIds`
 * and is applied by the caller via [sortedBySetlistOrder].
 */
fun List<Song>.sortedForDisplay(mode: SongSortMode = SongSortMode.ARTIST): List<Song> =
    when (mode) {
        SongSortMode.ARTIST -> sortedWith(
            compareBy<Song, String>(germanCollator) { it.sortKey }
                .thenBy(germanCollator) { it.title }
        )

        SongSortMode.TITLE -> sortedWith(compareBy(germanCollator) { it.title })

        // Songs without a genre go last: an empty group heading at the top would be
        // the first thing the eye hits, which is the least useful place for it.
        SongSortMode.GENRE -> sortedWith(
            compareBy<Song> { it.genre.isBlank() }
                .thenBy(germanCollator) { it.genre }
                .thenBy(germanCollator) { it.sortKey }
                .thenBy(germanCollator) { it.title }
        )

        SongSortMode.SETLIST_ORDER -> this
    }

/**
 * Orders songs by their position in [songIds].
 *
 * Songs not part of the setlist are dropped rather than appended – this mode is only
 * reachable with a setlist filter active, where anything outside it is noise.
 */
fun List<Song>.sortedBySetlistOrder(songIds: List<String>): List<Song> {
    val positions = songIds.withIndex().associate { (index, id) -> id to index }
    return filter { positions.containsKey(it.id) }
        .sortedBy { positions[it.id] }
}

/**
 * Headings for [SongSortMode.GENRE]. Returns null for every other mode, so the list
 * stays a flat scan when no grouping was asked for.
 */
fun Song.groupHeading(mode: SongSortMode): String? =
    if (mode == SongSortMode.GENRE) genre.ifBlank { "Ohne Genre" } else null
