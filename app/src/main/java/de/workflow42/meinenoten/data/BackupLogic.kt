package de.workflow42.meinenoten.data

import de.workflow42.meinenoten.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object BackupLogic {

    fun generateBackupFilename(
        type: BackupType,
        setlistTitle: String? = null,
        userName: String = "",
        deviceName: String = "Tablet",
    ): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val typePart = if (type == BackupType.SETLIST && !setlistTitle.isNullOrBlank()) {
            "Setlist-${sanitizeFilenamePart(setlistTitle)}"
        } else {
            "Komplett"
        }

        val devicePart = sanitizeFilenamePart(deviceName.ifBlank { "Tablet" })
        val namePart = sanitizeFilenamePart(userName.ifBlank { "Meine-Noten" })

        return "${dateStr}_${typePart}_${devicePart}_${namePart}.zip"
    }

    fun sanitizeFilenamePart(part: String): String {
        return part.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    fun analyzeBackupSongs(
        backupSongs: List<Song>,
        localSongs: List<Song>,
        tempDir: File,
    ): Pair<List<SongComparisonItem>, List<String>> {
        val missingFiles = mutableListOf<String>()
        val localSongsByHash = localSongs.filter { it.fileHash.isNotBlank() }.associateBy { it.fileHash }
        val localSongsByKey = localSongs.associateBy { "${it.artist.lowercase()}|${it.title.lowercase()}" }

        val songItems = backupSongs.map { backupSong ->
            val scoreFile = if (backupSong.hasFile && backupSong.fileUri.isNotBlank()) {
                File(tempDir, backupSong.fileUri).takeIf { it.isFile }
            } else null

            val hasScoreFile = scoreFile != null
            val computedHash = scoreFile?.let { FileHash.of(it) } ?: backupSong.fileHash
            val songWithHash = backupSong.copy(fileHash = computedHash)

            if (backupSong.hasFile && scoreFile == null) {
                missingFiles.add(backupSong.displayTitle)
            }

            val matchedByHash = if (computedHash.isNotBlank()) localSongsByHash[computedHash] else null
            val songKey = "${backupSong.artist.lowercase()}|${backupSong.title.lowercase()}"
            val matchedByKey = localSongsByKey[songKey]

            val (category, localSong, action) = when {
                matchedByHash != null -> {
                    val isIdentical = matchedByHash.title == backupSong.title &&
                            matchedByHash.artist == backupSong.artist &&
                            matchedByHash.genre == backupSong.genre &&
                            matchedByHash.lyrics == backupSong.lyrics
                    if (isIdentical) {
                        Triple(SongMatchCategory.IDENTICAL, matchedByHash, SongImportAction.KEEP_OWN)
                    } else {
                        Triple(SongMatchCategory.SAME_FILE_DIFFERENT_METADATA, matchedByHash, SongImportAction.KEEP_OWN)
                    }
                }
                matchedByKey != null -> {
                    Triple(SongMatchCategory.POSSIBLE_OTHER_VERSION, matchedByKey, SongImportAction.KEEP_OWN)
                }
                else -> {
                    Triple(SongMatchCategory.NEW, null, SongImportAction.TAKE_BACKUP)
                }
            }

            SongComparisonItem(
                backupSong = songWithHash,
                localSong = localSong,
                category = category,
                scoreFileInBackup = hasScoreFile,
                action = action,
                mergeForeignNotes = true,
            )
        }

        return Pair(songItems, missingFiles)
    }

    fun analyzeBackupSetlists(
        backupSetlists: List<Setlist>,
        localSetlists: List<Setlist>,
    ): List<SetlistImportItem> {
        val localSetlistsMap = localSetlists.associateBy { "${it.title.lowercase()}|${it.date}" }
        return backupSetlists.map { setlist ->
            val key = "${setlist.title.lowercase()}|${setlist.date}"
            val localMatch = localSetlistsMap[key]
            SetlistImportItem(
                backupSetlist = setlist,
                localSetlist = localMatch,
                importSetlist = true,
            )
        }
    }

    fun computeRewiredSetlistIds(
        backupToLocalSongIdMap: Map<String, String>,
        setlistSongIds: List<String>,
    ): List<String> {
        return setlistSongIds.mapNotNull { backupToLocalSongIdMap[it] ?: it }
    }
}
