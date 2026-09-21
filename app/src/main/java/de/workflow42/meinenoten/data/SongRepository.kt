package de.workflow42.meinenoten.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.UUID

private val MUSIC_XML_EXTENSIONS = setOf("xml", "musicxml", "mxl")

class SongRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    private val songsFile = File(context.filesDir, "songs.json")
    private val setlistsFile = File(context.filesDir, "setlists.json")

    fun importSong(uri: Uri, title: String): Song {
        // Prefer the display name, as many providers only report "application/octet-stream".
        val displayName = queryDisplayName(uri)
        val extension = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: context.contentResolver.getType(uri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: uri.path?.substringAfterLast('.', "pdf")
            ?: "pdf"

        val sourceType = if (extension.lowercase() in MUSIC_XML_EXTENSIONS) {
            SongSource.MUSIC_XML
        } else {
            SongSource.PDF
        }

        val folderName = if (sourceType == SongSource.PDF) "songs" else "musicxml"
        val songsDir = File(context.filesDir, folderName)
        if (!songsDir.exists()) {
            songsDir.mkdirs()
        }
        val id = UUID.randomUUID().toString()
        val destFile = File(songsDir, "$id.$extension")

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("Datei konnte nicht gelesen werden")

        return Song(
            id = id,
            title = title,
            fileUri = Uri.fromFile(destFile).toString(),
            sourceType = sourceType
        )
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    /**
     * Deletes the imported file belonging to a song.
     *
     * Only touches storage – removing the song from the in-memory lists and stripping it
     * from setlists is the caller's job, since those lists are the source of truth while
     * the app is running. Manually entered songs have no file and are a no-op here.
     */
    fun deleteSongFile(song: Song) {
        if (song.fileUri.isBlank()) return
        runCatching {
            Uri.parse(song.fileUri).path?.let { path ->
                File(path).takeIf { it.exists() }?.delete()
            }
        }
    }

    fun updateSong(updatedSong: Song) {
        val songs = loadSongs().toMutableList()
        val index = songs.indexOfFirst { it.id == updatedSong.id }
        if (index != -1) {
            songs[index] = updatedSong
            saveSongs(songs)
        }
    }

    fun saveSongs(songs: List<Song>) {
        try {
            val string = json.encodeToString(songs)
            songsFile.writeText(string)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSongs(): List<Song> {
        if (!songsFile.exists()) return emptyList()
        return try {
            val string = songsFile.readText()
            json.decodeFromString<List<Song>>(string)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveSetlists(setlists: List<Setlist>) {
        try {
            val string = json.encodeToString(setlists)
            setlistsFile.writeText(string)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSetlists(): List<Setlist> {
        if (!setlistsFile.exists()) return emptyList()
        return try {
            val string = setlistsFile.readText()
            json.decodeFromString<List<Setlist>>(string)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
