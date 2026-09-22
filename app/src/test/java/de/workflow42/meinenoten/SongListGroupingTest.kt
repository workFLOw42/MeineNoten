package de.workflow42.meinenoten

import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.ui.util.SongHeading
import de.workflow42.meinenoten.ui.util.SongSortMode
import de.workflow42.meinenoten.ui.util.groupHeading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two pure functions behind the song list's sections and search.
 *
 * Both are worth pinning down in tests because their behaviour is a deliberate choice
 * rather than an obvious default: which characters get folded into which heading, and
 * that search stays accent-sensitive.
 */
class SongListGroupingTest {

    private fun song(
        title: String = "Titel",
        artist: String = "",
        version: String = "",
        genre: String = "",
    ) = Song(
        id = title + artist,
        title = title,
        artist = artist,
        version = version,
        genre = genre,
        fileUri = "",
    )

    /** Heading text, for the cases where one is expected. */
    private fun Song.headingText(mode: SongSortMode): String? =
        (groupHeading(mode) as? SongHeading.Text)?.value

    // --- Section headings -----------------------------------------------------------

    @Test
    fun `title sorting groups by initial letter`() {
        assertEquals("A", song(title = "Amazing Grace").headingText(SongSortMode.TITLE))
    }

    @Test
    fun `initial letter is upper case regardless of the title`() {
        assertEquals("K", song(title = "kleine Nachtmusik").headingText(SongSortMode.TITLE))
    }

    @Test
    fun `titles starting with a digit share one numeric section`() {
        // "15 Three Part Inventions" and "27 – The Truman Show" belong together rather
        // than forming a section per digit.
        assertEquals("#", song(title = "15 Three Part Inventions").headingText(SongSortMode.TITLE))
        assertEquals("#", song(title = "27 – The Truman Show").headingText(SongSortMode.TITLE))
    }

    @Test
    fun `umlauts are folded onto their base letter`() {
        // Without folding, "Über" would form its own section sitting nowhere near the U
        // the collator sorts it next to.
        assertEquals("U", song(title = "Über den Wolken").headingText(SongSortMode.TITLE))
        assertEquals("A", song(title = "Ärger").headingText(SongSortMode.TITLE))
        assertEquals("O", song(title = "Öffne mir die Augen").headingText(SongSortMode.TITLE))
    }

    @Test
    fun `accented latin letters are folded too`() {
        assertEquals("E", song(title = "Étude").headingText(SongSortMode.TITLE))
    }

    @Test
    fun `punctuation and empty titles land in the catch-all section`() {
        assertEquals("?", song(title = "(Intro)").headingText(SongSortMode.TITLE))
        assertEquals("?", song(title = "").headingText(SongSortMode.TITLE))
    }

    @Test
    fun `leading whitespace is ignored`() {
        assertEquals("B", song(title = "  Bourrée").headingText(SongSortMode.TITLE))
    }

    @Test
    fun `artist sorting groups by the artist, falling back to the title`() {
        assertEquals(
            "B",
            song(title = "Air", artist = "Bach").headingText(SongSortMode.ARTIST),
        )
        // No artist: sortKey falls back to the title, and the heading has to follow it or
        // the song would file under a letter it does not start with.
        assertEquals("A", song(title = "Air").headingText(SongSortMode.ARTIST))
    }

    @Test
    fun `genre sorting keeps naming the genre`() {
        assertEquals("Gospel", song(genre = "Gospel").headingText(SongSortMode.GENRE))
    }

    @Test
    fun `a blank genre gets its own heading rather than none`() {
        // Untitled, not None: genre-less songs form a real section whose wording is
        // localised and therefore supplied by the UI layer. Collapsing this onto the
        // "not grouped" case would drop the heading and merge them into the run above.
        assertEquals(SongHeading.Untitled, song(genre = "").groupHeading(SongSortMode.GENRE))
        assertEquals(SongHeading.Untitled, song(genre = "   ").groupHeading(SongSortMode.GENRE))
    }

    @Test
    fun `ungrouped modes have no headings`() {
        // Recency is chronological and a running order must not be broken up, so neither
        // gets dividers.
        assertEquals(SongHeading.None, song(title = "Air").groupHeading(SongSortMode.RECENT))
        assertEquals(
            SongHeading.None,
            song(title = "Air").groupHeading(SongSortMode.SETLIST_ORDER),
        )
    }

    // --- Search ---------------------------------------------------------------------

    @Test
    fun `search matches all four fields`() {
        val s = song(title = "Air", artist = "Bach", version = "Klavier", genre = "Barock")
        assertTrue(s.matches("Air"))
        assertTrue(s.matches("Bach"))
        assertTrue(s.matches("Klavier"))
        assertTrue(s.matches("Barock"))
    }

    @Test
    fun `search matches partial words anywhere in the field`() {
        assertTrue(song(title = "Amazing Grace").matches("Grace"))
        assertTrue(song(title = "Amazing Grace").matches("maz"))
    }

    @Test
    fun `search ignores case`() {
        assertTrue(song(title = "Amazing Grace").matches("AMAZING"))
        assertTrue(song(title = "Amazing Grace").matches("amazing"))
    }

    @Test
    fun `search is deliberately accent-sensitive`() {
        // A chosen trade-off, not an oversight: typing the umlaut costs nothing on a
        // German keyboard, and folding accents away would make unrelated titles collide.
        assertTrue(song(title = "Für Elise").matches("Für"))
        assertFalse(song(title = "Für Elise").matches("Fur"))
    }

    @Test
    fun `blank query matches everything`() {
        // Lets the screen pass the field straight through without a special case.
        assertTrue(song(title = "Air").matches(""))
        assertTrue(song(title = "Air").matches("   "))
    }

    @Test
    fun `query is trimmed before matching`() {
        // Trailing spaces arrive easily from autocorrect and would otherwise empty the
        // list for no visible reason.
        assertTrue(song(title = "Air").matches("Air "))
    }

    @Test
    fun `non-matching query is rejected`() {
        assertFalse(song(title = "Air", artist = "Bach").matches("Mozart"))
    }
}
