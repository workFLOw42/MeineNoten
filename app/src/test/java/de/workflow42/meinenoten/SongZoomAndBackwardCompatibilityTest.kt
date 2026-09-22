package de.workflow42.meinenoten

import de.workflow42.meinenoten.model.PageView
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongZoomAndBackwardCompatibilityTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `legacy song json without pageViews deserializes successfully with default empty map`() {
        val legacyJson = """
            {
                "id": "test-id",
                "title": "Legacy Song",
                "artist": "Artist",
                "fileUri": "file://path/to/file.pdf",
                "sourceType": "PDF",
                "lastPage": 0
            }
        """.trimIndent()

        val song = json.decodeFromString<Song>(legacyJson)
        assertEquals("test-id", song.id)
        assertEquals("Legacy Song", song.title)
        assertTrue(song.pageViews.isEmpty())
    }

    @Test
    fun `song with pageViews serializes and deserializes correctly`() {
        val originalSong = Song(
            id = "zoom-id",
            title = "Zoom Song",
            fileUri = "file://path/to/file.pdf",
            sourceType = SongSource.PDF,
            pageViews = mapOf(
                0 to PageView(scale = 2.5f, offsetXRatio = 0.1f, offsetYRatio = -0.2f)
            )
        )

        val encoded = json.encodeToString(Song.serializer(), originalSong)
        val decoded = json.decodeFromString<Song>(encoded)

        assertEquals(originalSong.id, decoded.id)
        assertEquals(1, decoded.pageViews.size)
        val pageView = decoded.pageViews[0]
        assertEquals(2.5f, pageView?.scale ?: 0f, 0.001f)
        assertEquals(0.1f, pageView?.offsetXRatio ?: 0f, 0.001f)
        assertEquals(-0.2f, pageView?.offsetYRatio ?: 0f, 0.001f)
    }

    @Test
    fun `resetting zoom clears all stored page views`() {
        val songWithZoom = Song(
            id = "zoom-id",
            title = "Zoom Song",
            fileUri = "file://path/to/file.pdf",
            sourceType = SongSource.PDF,
            pageViews = mapOf(
                0 to PageView(scale = 2f),
                1 to PageView(scale = 1.5f)
            )
        )

        val resetSong = songWithZoom.copy(pageViews = emptyMap())
        assertTrue(resetSong.pageViews.isEmpty())
    }
}
