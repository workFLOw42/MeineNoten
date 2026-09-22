package de.workflow42.meinenoten.ui.util

import androidx.annotation.StringRes
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Song
import java.text.Collator
import java.text.Normalizer
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
 * Order of the song list. Chosen by the user, never persisted.
 *
 * Each mode carries the resource id of its label rather than the text itself, so this
 * enum stays free of display strings and can be read in either language.
 */
enum class SongSortMode(@get:StringRes val labelRes: Int) {
    /** Default: artist, then title. */
    ARTIST(R.string.sort_artist),

    /** Title only – for songs known by their first line rather than their composer. */
    TITLE(R.string.sort_title),

    /** Most recently opened first – the pieces currently being worked on. */
    RECENT(R.string.sort_recent),

    /** Grouped by genre, alphabetical within each group. */
    GENRE(R.string.sort_genre),

    /**
     * The running order of the selected setlist. Only offered while a setlist filter is
     * active, because without one there is no order to follow.
     */
    SETLIST_ORDER(R.string.sort_setlist_order)
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
                .thenBy(germanCollator) { it.title },
        )

        SongSortMode.TITLE -> sortedWith(compareBy(germanCollator) { it.title })

        // Never-opened songs (lastOpenedAt == 0) land at the end by the same rule that
        // sorts everything else: descending by timestamp.
        SongSortMode.RECENT -> sortedWith(
            compareByDescending<Song> { it.lastOpenedAt }
                .thenBy(germanCollator) { it.sortKey }
                .thenBy(germanCollator) { it.title },
        )

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
    val positions = songIds.withIndex().associateBy({ it.value }, { it.index })
    return filter { positions.containsKey(it.id) }
        .sortedBy { positions[it.id] }
}

/**
 * Section heading for a song under a given sort mode.
 *
 * A separate type rather than a nullable string: "this mode has no sections at all" and
 * "this song belongs to the group of songs without a genre" are different outcomes, and
 * collapsing both onto null once made genre-less songs silently lose their heading.
 * [Untitled] also cannot carry its own text, because the wording is localised and this
 * layer holds no display strings.
 */
sealed interface SongHeading {
    /** The mode is not grouped; the list stays flat. */
    data object None : SongHeading

    /** Heading taken from the song's own data or its initial letter. */
    data class Text(val value: String) : SongHeading

    /** The song has no genre; the caller supplies the localised heading. */
    data object Untitled : SongHeading
}

/**
 * Section heading for [mode].
 *
 * [SongSortMode.ARTIST] and [SongSortMode.TITLE] group by initial letter, matching the
 * key each mode sorts by – otherwise the headings would contradict the order. The other
 * modes stay flat: [SongSortMode.RECENT] is chronological, and a running order must not
 * be broken up at all.
 */
fun Song.groupHeading(mode: SongSortMode): SongHeading = when (mode) {
    SongSortMode.GENRE ->
        if (genre.isBlank()) SongHeading.Untitled else SongHeading.Text(genre)

    SongSortMode.ARTIST -> SongHeading.Text(sortKey.initialLetter())
    SongSortMode.TITLE -> SongHeading.Text(title.initialLetter())
    SongSortMode.RECENT, SongSortMode.SETLIST_ORDER -> SongHeading.None
}

/**
 * First character of a heading: an upper-case letter, "#" for anything starting with a
 * digit, or "?" for everything else (punctuation, other scripts, empty strings).
 *
 * Accents are folded onto their base letter so "Über" files under U, next to where the
 * collator sorts it. Without that the list would grow single-entry sections for Ä, Ö and
 * Ü that sit nowhere near their letter in the order.
 */
private fun String.initialLetter(): String {
    val first = trimStart().firstOrNull() ?: return "?"
    if (first.isDigit()) return "#"
    if (!first.isLetter()) return "?"

    // Decompose, then drop the combining marks: "Ü" becomes "U" + diaeresis, and only the
    // "U" survives. Covers every accented letter without a lookup table.
    val folded = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
        .firstOrNull { it.isLetter() }
        ?: first

    return folded.uppercaseChar().toString()
}
