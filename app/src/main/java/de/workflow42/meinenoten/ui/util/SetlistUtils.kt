package de.workflow42.meinenoten.ui.util

import androidx.annotation.StringRes
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/**
 * Same collator settings as the song list, so titles in both lists sort by one rule:
 * umlauts next to their base letter, case ignored.
 */
private val setlistCollator: Collator = Collator.getInstance(Locale.GERMAN).apply {
    strength = Collator.SECONDARY
}

/**
 * Order of the setlist list. Chosen by the user, never persisted.
 *
 * Carries label resource ids rather than text, so this layer holds no display strings.
 */
enum class SetlistSortMode(@get:StringRes val labelRes: Int) {
    /** Default: concert date, newest first, undated drafts last. */
    DATE(R.string.sort_date),

    /** Alphabetical – for programmes known by name ("Christmas Eve") rather than date. */
    TITLE(R.string.sort_title),

    /** Most recently played first – the programme currently in rehearsal. */
    RECENT(R.string.sort_last_played),
}

/**
 * Time window filter.
 *
 * Undated setlists only appear under [ALL]: without a date there is no way to say whether
 * a draft lies ahead or behind, and guessing either way would be wrong half the time.
 */
enum class SetlistPeriod(@get:StringRes val labelRes: Int) {
    ALL(R.string.label_period),
    UPCOMING(R.string.period_upcoming),
    PAST(R.string.period_past),
}

/**
 * True when [query] appears in the title, the notes, the displayed date, or the title or
 * artist of any contained song.
 *
 * Searching the songs too is the point: "where did we play Amazing Grace?" is the
 * question people actually ask of an archive of programmes. [songsById] is passed in
 * rather than looked up per call so a long list does not rebuild the map per row.
 *
 * Same rules as [Song.matches]: trimmed, case-insensitive, blank matches everything.
 */
fun Setlist.matches(query: String, songsById: Map<String, Song>): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    if (title.contains(needle, ignoreCase = true)) return true
    if (notes.contains(needle, ignoreCase = true)) return true
    // Both the raw and the formatted date: people type "24.12." as they read it.
    if (date.contains(needle, ignoreCase = true)) return true
    if (formatSetlistDate(date).contains(needle, ignoreCase = true)) return true
    return songIds.any { id ->
        val song = songsById[id] ?: return@any false
        song.title.contains(needle, ignoreCase = true) ||
            song.artist.contains(needle, ignoreCase = true)
    }
}

/**
 * True when this setlist falls into [period] relative to [today].
 *
 * [today] is a parameter so tests do not depend on the day they run.
 */
fun Setlist.isIn(period: SetlistPeriod, today: LocalDate = LocalDate.now()): Boolean {
    val parsed = parseSetlistDate(date)
    return when (period) {
        SetlistPeriod.ALL -> true
        SetlistPeriod.UPCOMING -> parsed != null && !parsed.isBefore(today)
        SetlistPeriod.PAST -> parsed != null && parsed.isBefore(today)
    }
}

/** Sorts setlists for display under [mode]. */
fun List<Setlist>.sortedForDisplay(mode: SetlistSortMode): List<Setlist> = when (mode) {
    SetlistSortMode.DATE -> sortedForDisplay()
    SetlistSortMode.TITLE -> sortedWith(compareBy(setlistCollator) { it.title })
    // Never-played setlists (lastPlayedAt == 0) land at the end by the same descending
    // rule; among them the date order keeps the list predictable.
    SetlistSortMode.RECENT -> {
        val dateOrder = sortedForDisplay().withIndex().associate { it.value.id to it.index }
        sortedWith(
            compareByDescending<Setlist> { it.lastPlayedAt }
                .thenBy { dateOrder[it.id] ?: Int.MAX_VALUE },
        )
    }
}

/**
 * Section heading for a setlist under [mode].
 *
 * Reuses [SongHeading] because the three outcomes are the same: a real heading, the
 * localised "no date" group the caller has to name, or no sections at all.
 *
 * Grouped by year rather than month under [SetlistSortMode.DATE]: a church or band
 * archive has a handful of programmes per month, and monthly headings would outnumber
 * the rows between them.
 */
fun Setlist.groupHeading(mode: SetlistSortMode): SongHeading = when (mode) {
    SetlistSortMode.DATE -> parseSetlistDate(date)
        ?.let { SongHeading.Text(it.year.toString()) }
        ?: SongHeading.Untitled

    SetlistSortMode.TITLE -> SongHeading.Text(title.initialLetter())
    SetlistSortMode.RECENT -> SongHeading.None
}

/**
 * The next programme coming up: the earliest date on or after [today], or null.
 *
 * Highlighted in the list because "what are we playing on Sunday" is the most common
 * reason to open it.
 */
fun List<Setlist>.nextUpcoming(today: LocalDate = LocalDate.now()): Setlist? =
    mapNotNull { setlist -> parseSetlistDate(setlist.date)?.let { setlist to it } }
        .filter { (_, date) -> !date.isBefore(today) }
        .minWithOrNull(
            compareBy<Pair<Setlist, LocalDate>> { it.second }
                .thenBy(setlistCollator) { it.first.title },
        )
        ?.first

/**
 * 1-based position of the song this setlist was left at, or null without progress.
 *
 * Counted in songIds so it matches the numbers shown in the running order.
 */
val Setlist.progressPosition: Int?
    get() {
        val lastId = lastSongId ?: return null
        val index = songIds.indexOf(lastId)
        return if (index == -1) null else index + 1
    }
