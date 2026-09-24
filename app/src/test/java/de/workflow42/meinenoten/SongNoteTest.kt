package de.workflow42.meinenoten

import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongNote
import de.workflow42.meinenoten.model.authorNumbers
import de.workflow42.meinenoten.model.mergedWith
import de.workflow42.meinenoten.model.otherNotes
import de.workflow42.meinenoten.model.ownNote
import de.workflow42.meinenoten.model.withOwnNote
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongNoteTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun song(notes: List<SongNote> = emptyList(), legacy: String = "") =
        Song(id = "s", title = "T", fileUri = "", legacyNotes = legacy, notes = notes)

    @Test
    fun `old json with notes string still reads`() {
        val s = json.decodeFromString<Song>("""{"id":"s","title":"T","fileUri":"x","notes":"Capo 2"}""")
        assertEquals("Capo 2", s.legacyNotes)
        assertTrue(s.notes.isEmpty())
    }

    @Test
    fun `legacy note becomes own note and is not written again`() {
        val migrated = song(legacy = "Capo 2").migrateLegacyNote("me", "Florian")
        assertEquals("", migrated.legacyNotes)
        assertEquals("Capo 2", migrated.notes.ownNote("me")?.text)
        assertEquals("Florian", migrated.notes.single().authorName)
        // Idempotent.
        assertEquals(migrated, migrated.migrateLegacyNote("me", "Florian"))
        val encoded = json.encodeToString(Song.serializer(), migrated)
        val back = json.decodeFromString<Song>(encoded)
        assertEquals(migrated.notes, back.notes)
        assertEquals("", back.legacyNotes)
    }

    @Test
    fun `migration waits for a user id`() {
        val s = song(legacy = "Capo 2")
        assertEquals(s, s.migrateLegacyNote("", "Florian"))
    }

    @Test
    fun `editing own note leaves others untouched`() {
        val anna = SongNote("anna", "Anna", "Tief einsetzen", 5)
        val result = listOf(anna).withOwnNote("me", "Florian", "Capo 2", now = 10)
        assertEquals(anna, result.otherNotes("me").single())
        assertEquals("Capo 2", result.ownNote("me")?.text)
        assertEquals(10L, result.ownNote("me")?.editedAt)
    }

    @Test
    fun `unchanged own note keeps its timestamp`() {
        val mine = SongNote("me", "Florian", "Capo 2", 3)
        val result = listOf(mine).withOwnNote("me", "Florian", "Capo 2", now = 99)
        assertEquals(3L, result.ownNote("me")?.editedAt)
    }

    @Test
    fun `blank own note removes it`() {
        val mine = SongNote("me", "Florian", "Capo 2", 3)
        assertNull(listOf(mine).withOwnNote("me", "Florian", "  ", now = 9).ownNote("me"))
    }

    @Test
    fun `merge keeps newer version per author and adds new authors`() {
        val mineOld = SongNote("me", "F", "alt", 1)
        val annaNew = SongNote("anna", "Anna", "neu", 7)
        val mineNewer = SongNote("me", "F", "neu", 5)
        val annaOld = SongNote("anna", "Anna", "alt", 2)
        val merged = listOf(mineOld, annaNew).mergedWith(listOf(mineNewer, annaOld))
        assertEquals("neu", merged.ownNote("me")?.text)
        assertEquals("neu", merged.ownNote("anna")?.text)
        assertEquals(2, merged.size)
    }

    @Test
    fun `numbers only for shared names and stable by first appearance`() {
        val songs = listOf(
            song(listOf(SongNote("a2", "Anna", "x", 20), SongNote("bob", "Bob", "y", 1))),
            song(listOf(SongNote("a1", "anna ", "z", 10))),
        )
        val numbers = authorNumbers(songs)
        assertEquals(1, numbers["a1"])
        assertEquals(2, numbers["a2"])
        assertNull(numbers["bob"])
    }
}
