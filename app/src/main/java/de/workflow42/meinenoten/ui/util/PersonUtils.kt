package de.workflow42.meinenoten.ui.util

import androidx.compose.ui.graphics.Color

/**
 * Longest name that still makes a readable personal title. Beyond this the title falls
 * back to "Meine Noten" instead of being cut off mid-name.
 */
const val MAX_TITLE_NAME_LENGTH = 20

/** Grammar used to build "‹Name›s Noten"; chosen per language via a string resource. */
enum class PossessiveRule {
    /** "Annas", but "Hans’", "Max’", "Fritz’" for names ending in an s sound. */
    GERMAN,

    /** "Anna's" – always apostrophe-s, which is correct in modern English usage. */
    ENGLISH;

    companion object {
        fun fromTag(tag: String): PossessiveRule =
            if (tag.equals("en", ignoreCase = true)) ENGLISH else GERMAN
    }
}

/**
 * German names ending in these take only an apostrophe in the genitive. The dot covers
 * abbreviated names like "Anna S.", where "Anna S.s" would be unreadable.
 */
private val GERMAN_S_SOUND_ENDINGS = listOf("s", "ß", "x", "z", "ce", ".")

/** Possessive form of [name], e.g. "Annas", "Hans’", "Anna's". */
fun possessive(name: String, rule: PossessiveRule): String = when (rule) {
    PossessiveRule.GERMAN ->
        if (GERMAN_S_SOUND_ENDINGS.any { name.endsWith(it, ignoreCase = true) }) {
            "$name’"
        } else {
            "${name}s"
        }
    PossessiveRule.ENGLISH -> "$name's"
}

/**
 * The app title as shown on the launch screen and in the menu.
 *
 * [template] is the localised "%1$s Noten" and [fallback] the plain app name. A blank or
 * overly long name yields [fallback]; the home screen label is unaffected either way, as
 * an app cannot change that at runtime.
 */
fun personalTitle(
    name: String,
    rule: PossessiveRule,
    template: String,
    fallback: String,
): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_TITLE_NAME_LENGTH) return fallback
    return template.format(possessive(trimmed, rule))
}

/**
 * One tone of the person palette, with a variant for each theme.
 *
 * Both variants reach at least 4.5 : 1 against the respective Material background
 * (near-white in light, near-black in dark), so a coloured name stays readable.
 */
data class PersonColor(val light: Color, val dark: Color) {
    fun forTheme(darkTheme: Boolean): Color = if (darkTheme) dark else light
}

/** Fixed palette. Order is part of the format: reordering would recolour everyone. */
val PersonPalette: List<PersonColor> = listOf(
    PersonColor(light = Color(0xFFB3261E), dark = Color(0xFFFFB4AB)), // red
    PersonColor(light = Color(0xFF1B5E20), dark = Color(0xFF81C784)), // green
    PersonColor(light = Color(0xFF0D47A1), dark = Color(0xFF90CAF9)), // blue
    PersonColor(light = Color(0xFF8A4A00), dark = Color(0xFFFFB870)), // orange
    PersonColor(light = Color(0xFF6A1B9A), dark = Color(0xFFCE93D8)), // purple
    PersonColor(light = Color(0xFF006064), dark = Color(0xFF80DEEA)), // teal
    PersonColor(light = Color(0xFFAD1457), dark = Color(0xFFF48FB1)), // pink
    PersonColor(light = Color(0xFF5D4037), dark = Color(0xFFD7B8A8)), // brown
)

/**
 * Palette slot for a person id.
 *
 * Derived from the id, never the name, so two "Anna"s differ and one person has the same
 * colour on every device. [String.hashCode] is specified by the JVM and therefore stable
 * across devices and app versions.
 */
fun personColorIndex(userId: String): Int = Math.floorMod(userId.hashCode(), PersonPalette.size)

fun personColor(userId: String): PersonColor = PersonPalette[personColorIndex(userId)]
