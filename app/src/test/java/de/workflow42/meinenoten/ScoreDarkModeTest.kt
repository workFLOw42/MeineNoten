package de.workflow42.meinenoten

import de.workflow42.meinenoten.model.ScoreDarkMode
import de.workflow42.meinenoten.model.Song
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreDarkModeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `song without darkMode field reads as normal`() {
        val song = json.decodeFromString<Song>("""{"id":"a","title":"T","fileUri":""}""")
        assertEquals(ScoreDarkMode.NORMAL, song.scoreDarkMode)
    }

    @Test
    fun `unknown darkMode value falls back to normal instead of failing`() {
        val song = json.decodeFromString<Song>(
            """{"id":"a","title":"T","fileUri":"","darkMode":"SEPIA"}"""
        )
        assertEquals(ScoreDarkMode.NORMAL, song.scoreDarkMode)
    }

    @Test
    fun `darkMode survives a round trip`() {
        val song = Song(id = "a", title = "T", fileUri = "", darkMode = ScoreDarkMode.INVERTED.name)
        val decoded = json.decodeFromString<Song>(json.encodeToString(Song.serializer(), song))
        assertEquals(ScoreDarkMode.INVERTED, decoded.scoreDarkMode)
    }
}
