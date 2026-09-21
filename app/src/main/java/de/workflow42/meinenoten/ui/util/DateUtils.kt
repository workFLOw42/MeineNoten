package de.workflow42.meinenoten.ui.util

import de.workflow42.meinenoten.model.Setlist
import java.text.Collator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Canonical storage format. Sorts correctly as a plain string. */
private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** Display format for German users. */
private val GERMAN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

private val germanCollator: Collator = Collator.getInstance(Locale.GERMAN).apply {
    strength = Collator.SECONDARY
}

/**
 * Interprets a stored date string.
 *
 * The date field was free text before, so existing files may hold anything the user
 * typed. Recognised forms are the canonical `yyyy-MM-dd`, German `dd.MM.yyyy`, and a
 * day/month without year such as `24.12.` — the latter is assumed to mean the current
 * year, which is the reading a musician would give it.
 *
 * Returns `null` for anything else. Callers treat that as "no date" while still
 * showing the original text, so no user input is silently discarded.
 */
fun parseSetlistDate(raw: String): LocalDate? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    runCatching { return LocalDate.parse(value, ISO) }
    runCatching { return LocalDate.parse(value, GERMAN) }

    // "24.12." or "24.12" — day and month only.
    val dayMonth = Regex("""^(\d{1,2})\.(\d{1,2})\.?$""").find(value)
    if (dayMonth != null) {
        val (day, month) = dayMonth.destructured
        runCatching {
            return LocalDate.of(LocalDate.now().year, month.toInt(), day.toInt())
        }
    }

    return null
}

/** Formats a date for storage. */
fun LocalDate.toStorageString(): String = format(ISO)

/**
 * Renders a stored date for display. Values that cannot be interpreted are echoed
 * unchanged, preserving labels such as "Ostern" that predate the date picker.
 */
fun formatSetlistDate(raw: String): String =
    parseSetlistDate(raw)?.format(GERMAN) ?: raw.trim()

/**
 * Orders setlists by concert date, newest first.
 *
 * Setlists without a usable date go last, ordered alphabetically among themselves —
 * they are typically drafts, and burying them below the dated ones keeps the top of
 * the list useful.
 */
fun List<Setlist>.sortedForDisplay(): List<Setlist> {
    val (dated, undated) = map { it to parseSetlistDate(it.date) }
        .partition { it.second != null }

    val newestFirst = dated
        .sortedWith(
            compareByDescending<Pair<Setlist, LocalDate?>> { it.second }
                .thenBy(germanCollator) { it.first.title }
        )
        .map { it.first }

    val rest = undated
        .map { it.first }
        .sortedWith(compareBy(germanCollator) { it.title })

    return newestFirst + rest
}
