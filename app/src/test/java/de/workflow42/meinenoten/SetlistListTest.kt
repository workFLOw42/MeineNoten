package de.workflow42.meinenoten

import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.ui.util.SetlistPeriod
import de.workflow42.meinenoten.ui.util.SetlistSortMode
import de.workflow42.meinenoten.ui.util.SongHeading
import de.workflow42.meinenoten.ui.util.groupHeading
import de.workflow42.meinenoten.ui.util.isIn
import de.workflow42.meinenoten.ui.util.matches
import de.workflow42.meinenoten.ui.util.nextUpcoming
import de.workflow42.meinenoten.ui.util.progressPosition
import de.workflow42.meinenoten.ui.util.sortedForDisplay
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Covers the pure logic behind the setlist list: search, period filter, sorting,
 * grouping and the small derived facts the rows show.
 *
 * "Today" is fixed throughout so the period tests do not depend on the day they run.
 */
class SetlistListTest {

    private val today = LocalDate.of(2025, 6, 15)

    private fun setlist(
        id: String,
        title: String = id,
        date: String = "",
        songIds: List<String> = emptyList(),
        notes: String = "",
        lastPlayedAt: Long = 0L,
        lastSongId: String? = null,
    ) = Setlist(
        id = id,
        title = title,
        date = date,
        songIds = songIds,
        notes = notes,
        lastPlayedAt = lastPlayedAt,
        lastSongId = lastSongId,
    )

    private val amazing = Song(id = "s1", title = "Amazing Grace", artist = "Newton", fileUri = "")
    private val songsById = mapOf(amazing.id to amazing)

    // --- Search ---------------------------------------------------------------------

    @Test
    fun `search finds a setlist by the title of a contained song`() {
        val s = setlist("a", title = "Sunday", songIds = listOf("s1"))
        assertTrue(s.matches("amazing", songsById))
        assertTrue(s.matches("Newton", songsById))
        assertFalse(setlist("b", title = "Sunday").matches("amazing", songsById))
    }

    @Test
    fun `search covers title, notes and the displayed date`() {
        val s = setlist("a", title = "Easter", notes = "bring capo", date = "2025-04-20")
        assertTrue(s.matches("east", songsById))
        assertTrue(s.matches("capo", songsById))
        // As displayed, not only as stored.
        assertTrue(s.matches("20.04.2025", songsById))
        assertTrue(s.matches("2025-04", songsById))
    }

    @Test
    fun `blank and padded queries behave like the song search`() {
        val s = setlist("a", title = "Easter")
        assertTrue(s.matches("   ", songsById))
        assertTrue(s.matches(" Easter ", songsById))
        assertFalse(s.matches("Christmas", songsById))
    }

    // --- Period ---------------------------------------------------------------------

    @Test
    fun `upcoming includes today and later`() {
        assertTrue(setlist("a", date = "2025-06-15").isIn(SetlistPeriod.UPCOMING, today))
        assertTrue(setlist("a", date = "2025-12-24").isIn(SetlistPeriod.UPCOMING, today))
        assertFalse(setlist("a", date = "2025-06-14").isIn(SetlistPeriod.UPCOMING, today))
    }

    @Test
    fun `past is strictly before today`() {
        assertTrue(setlist("a", date = "2025-06-14").isIn(SetlistPeriod.PAST, today))
        assertFalse(setlist("a", date = "2025-06-15").isIn(SetlistPeriod.PAST, today))
    }

    @Test
    fun `undated setlists only show under all`() {
        val draft = setlist("a", date = "")
        val labelled = setlist("b", date = "Ostern")
        assertTrue(draft.isIn(SetlistPeriod.ALL, today))
        assertTrue(labelled.isIn(SetlistPeriod.ALL, today))
        assertFalse(draft.isIn(SetlistPeriod.UPCOMING, today))
        assertFalse(draft.isIn(SetlistPeriod.PAST, today))
        assertFalse(labelled.isIn(SetlistPeriod.UPCOMING, today))
        assertFalse(labelled.isIn(SetlistPeriod.PAST, today))
    }

    // --- Sorting --------------------------------------------------------------------

    private val sample = listOf(
        setlist("old", title = "Zebra", date = "2024-03-01", lastPlayedAt = 300),
        setlist("new", title = "Apfel", date = "2025-05-01", lastPlayedAt = 0),
        setlist("draft", title = "Über", date = "", lastPlayedAt = 100),
        setlist("mid", title = "Mitte", date = "2024-11-01", lastPlayedAt = 0),
    )

    @Test
    fun `date sort is newest first with undated last`() {
        assertEquals(
            listOf("new", "mid", "old", "draft"),
            sample.sortedForDisplay(SetlistSortMode.DATE).map { it.id },
        )
    }

    @Test
    fun `title sort uses the collator`() {
        // "Über" sorts next to U, before "Zebra", rather than after it by code point.
        assertEquals(
            listOf("new", "mid", "draft", "old"),
            sample.sortedForDisplay(SetlistSortMode.TITLE).map { it.id },
        )
    }

    @Test
    fun `recent sort puts never-played last, in date order`() {
        assertEquals(
            listOf("old", "draft", "new", "mid"),
            sample.sortedForDisplay(SetlistSortMode.RECENT).map { it.id },
        )
    }

    // --- Grouping -------------------------------------------------------------------

    @Test
    fun `date sort groups by year`() {
        assertEquals(
            SongHeading.Text("2024"),
            setlist("a", date = "2024-11-01").groupHeading(SetlistSortMode.DATE),
        )
        assertEquals(
            SongHeading.Text("2025"),
            setlist("a", date = "01.02.2025").groupHeading(SetlistSortMode.DATE),
        )
    }

    @Test
    fun `undated setlists get the localised no-date group`() {
        assertEquals(SongHeading.Untitled, setlist("a").groupHeading(SetlistSortMode.DATE))
        assertEquals(
            SongHeading.Untitled,
            setlist("a", date = "Ostern").groupHeading(SetlistSortMode.DATE),
        )
    }

    @Test
    fun `title sort groups by folded initial letter`() {
        assertEquals(
            SongHeading.Text("U"),
            setlist("a", title = "Über allen").groupHeading(SetlistSortMode.TITLE),
        )
        assertEquals(
            SongHeading.Text("#"),
            setlist("a", title = "1. Advent").groupHeading(SetlistSortMode.TITLE),
        )
    }

    @Test
    fun `recent sort has no headings`() {
        assertEquals(SongHeading.None, setlist("a").groupHeading(SetlistSortMode.RECENT))
    }

    // --- Derived row facts ----------------------------------------------------------

    @Test
    fun `next upcoming is the earliest date from today on`() {
        val list = listOf(
            setlist("past", date = "2025-06-01"),
            setlist("later", date = "2025-12-24"),
            setlist("soon", date = "2025-06-20"),
            setlist("draft"),
        )
        assertEquals("soon", list.nextUpcoming(today)?.id)
        assertNull(listOf(setlist("past", date = "2025-06-01")).nextUpcoming(today))
    }

    @Test
    fun `progress position is 1-based in the running order`() {
        val s = setlist("a", songIds = listOf("x", "y", "z"), lastSongId = "z")
        assertEquals(3, s.progressPosition)
        assertNull(setlist("a", songIds = listOf("x")).progressPosition)
        // The song was removed from the programme meanwhile.
        assertNull(setlist("a", songIds = listOf("x"), lastSongId = "gone").progressPosition)
    }

    @Test
    fun `setlist json without lastPlayedAt still deserialises`() {
        val legacy = """{"id":"a","title":"Old","songIds":["s1"]}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<Setlist>(legacy)
        assertEquals(0L, decoded.lastPlayedAt)
    }
}
