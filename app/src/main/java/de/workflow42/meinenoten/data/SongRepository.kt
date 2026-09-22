package de.workflow42.meinenoten.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import androidx.core.net.toUri
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
        val extension = resolveExtension(uri)
        val sourceType = sourceTypeFor(extension)
        val id = UUID.randomUUID().toString()
        val destFile = copyToStorage(uri, id, extension, sourceType)

        return Song(
            id = id,
            title = title,
            fileUri = Uri.fromFile(destFile).toString(),
            sourceType = sourceType,
        )
    }

    /**
     * Best guess at the file extension.
     *
     * Prefers the display name, as many providers only report "application/octet-stream"
     * and would otherwise make every MusicXML file look like a PDF.
     */
    private fun resolveExtension(uri: Uri): String {
        val displayName = queryDisplayName(uri)
        return displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: context.contentResolver.getType(uri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: uri.path?.substringAfterLast('.', "pdf")
            ?: "pdf"
    }

    private fun sourceTypeFor(extension: String): SongSource =
        if (extension.lowercase() in MUSIC_XML_EXTENSIONS) {
            SongSource.MUSIC_XML
        } else {
            SongSource.PDF
        }

    /** Copies the picked document into app storage, named after the song's id. */
    private fun copyToStorage(
        uri: Uri,
        songId: String,
        extension: String,
        sourceType: SongSource,
    ): File {
        val folderName = if (sourceType == SongSource.PDF) "songs" else "musicxml"
        val songsDir = File(context.filesDir, folderName)
        if (!songsDir.exists()) {
            songsDir.mkdirs()
        }
        val destFile = File(songsDir, "$songId.$extension")

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException(context.getString(R.string.error_file_read_failed))

        return destFile
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if ((index >= 0) && cursor.moveToFirst()) cursor.getString(index) else null
        }

    /**
     * Attaches a score file to an existing song, replacing any file it already had.
     *
     * Unlike [importSong] this keeps the song's [Song.id], because setlists reference it
     * – a new id would silently drop the song out of every programme it appears in. The
     * previous file is deleted so repeated replacements cannot pile up orphans, and
     * [Song.lastPage] is reset since a page number from the old document is meaningless
     * in the new one.
     *
     * [Song.lyrics] is deliberately preserved: a song that started out as typed text
     * keeps that text once its score arrives.
     */
    fun attachFile(song: Song, uri: Uri): Song {
        val extension = resolveExtension(uri)
        val sourceType = sourceTypeFor(extension)
        val destFile = copyToStorage(uri, song.id, extension, sourceType)

        // Only after the copy succeeded, otherwise a failed import would leave the song
        // pointing at a file that is already gone.
        deleteSongFile(song)

        return song.copy(
            fileUri = Uri.fromFile(destFile).toString(),
            sourceType = sourceType,
            lastPage = 0,
            pageViews = emptyMap(),
        )
    }

    /**
     * Removes the score file from a song, turning it back into a text-only entry.
     *
     * Used when an import turned out to be the wrong document. The song itself and its
     * setlist memberships survive.
     */
    fun detachFile(song: Song): Song {
        deleteSongFile(song)
        return song.copy(
            fileUri = "",
            sourceType = SongSource.TEXT,
            lastPage = 0,
            pageViews = emptyMap(),
        )
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
            song.fileUri.toUri().path?.let { path ->
                File(path).takeIf { it.exists() }?.delete()
            }
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
            json.decodeFromString<List<Song>>(string).map(::migrateTextSong)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Moves the body of a hand-typed song from `notes` into `lyrics`.
     *
     * Before lyrics existed, manually added songs stored their whole text in `notes`,
     * which is also the field used for the short overlay memo next to a score. Without
     * this migration, attaching a PDF to such a song would squeeze its verses into a
     * 40%-wide corner box. Only songs with no file and no lyrics yet are touched, so the
     * conversion is idempotent.
     */
    private fun migrateTextSong(song: Song): Song =
        if (!song.hasFile && song.lyrics.isBlank() && song.notes.isNotBlank()) {
            song.copy(lyrics = song.notes, notes = "")
        } else {
            song
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
