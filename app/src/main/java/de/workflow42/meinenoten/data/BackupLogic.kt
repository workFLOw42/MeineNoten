package de.workflow42.meinenoten.data

import de.workflow42.meinenoten.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object BackupLogic {

    /**
     * Newest backup format this version can read. A newer backup is refused rather than
     * read half: `ignoreUnknownKeys` would otherwise drop whatever the new format added
     * without a word.
     */
    const val SUPPORTED_FORMAT_VERSION = 1

    fun isFormatSupported(manifest: BackupManifest): Boolean =
        manifest.formatVersion <= SUPPORTED_FORMAT_VERSION

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
        val localSongsById = localSongs.associateBy { it.id }

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
            // Same id as a local song, but neither file nor title match any more (e.g. a
            // shared setlist edited on both sides): still the same song. Treating it as
            // new would overwrite the local one without asking.
            val matchedByKey = localSongsByKey[songKey] ?: localSongsById[backupSong.id]

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

    /**
     * The song that „Sicherung übernehmen“ leaves in the collection.
     *
     * - Keeps the id of the matched [localSong], so the local song is replaced instead of
     *   duplicated and every setlist pointing at it stays intact.
     * - Merges the notes: per author the newer one wins, as everywhere else. Local notes
     *   written after the backup was made are not lost.
     * - [importedFileUri] is empty when the score could not be copied from the backup;
     *   then the local file stays in place instead of the song ending up without one.
     */
    fun takeBackupSong(backupSong: Song, localSong: Song?, importedFileUri: String): Song {
        val base = backupSong.copy(
            id = localSong?.id ?: backupSong.id,
            notes = localSong?.notes.orEmpty().mergedWith(backupSong.notes),
        )
        return when {
            importedFileUri.isNotBlank() -> base.copy(fileUri = importedFileUri)
            localSong != null && localSong.hasFile -> base.copy(
                fileUri = localSong.fileUri,
                sourceType = localSong.sourceType,
                fileHash = localSong.fileHash,
                // Zoom and pan belong to the file that is actually shown.
                pageViews = localSong.pageViews,
            )
            // No score anywhere: what is left is the text, so show it as a text song.
            backupSong.sourceType == SongSource.PDF || backupSong.sourceType == SongSource.MUSIC_XML ->
                base.copy(fileUri = "", sourceType = SongSource.TEXT, fileHash = "")
            else -> base.copy(fileUri = "")
        }
    }

    /**
     * Local songs whose score file nobody references after the import, so it can go.
     *
     * Deleting only at the end, and only what is unreferenced, means a failed copy or a
     * file that was overwritten in place (same id, same extension) never loses a score.
     */
    fun orphanedSongFiles(localSongs: List<Song>, finalSongs: List<Song>): List<Song> {
        val stillUsed = finalSongs.filter { it.hasFile }.map { it.fileUri }.toSet()
        return localSongs.filter { it.hasFile && it.fileUri !in stillUsed }
    }

    /**
     * Points a setlist from the backup at the songs as they are after the import.
     *
     * Ids of songs that end up nowhere (a new song that was skipped) are dropped, so the
     * setlist does not carry references to songs that do not exist.
     */
    fun computeRewiredSetlistIds(
        backupToLocalSongIdMap: Map<String, String>,
        setlistSongIds: List<String>,
        availableSongIds: Set<String>,
    ): List<String> {
        return setlistSongIds.mapNotNull { id ->
            (backupToLocalSongIdMap[id] ?: id).takeIf { it in availableSongIds }
        }
    }

    /**
     * [setlist] with its songs and its resume position rewired like
     * [computeRewiredSetlistIds]. A position on a song that is gone is reset.
     */
    fun rewireSetlist(
        setlist: Setlist,
        backupToLocalSongIdMap: Map<String, String>,
        availableSongIds: Set<String>,
    ): Setlist {
        val songIds = computeRewiredSetlistIds(backupToLocalSongIdMap, setlist.songIds, availableSongIds)
        val lastSongId = setlist.lastSongId
            ?.let { backupToLocalSongIdMap[it] ?: it }
            ?.takeIf { it in songIds }
        return setlist.copy(
            songIds = songIds,
            lastSongId = lastSongId,
            lastPage = if (lastSongId == null) 0 else setlist.lastPage,
        )
    }

    /**
     * Whether a song ends up in the collection after the import, so that a setlist
     * pointing at it stays complete.
     *
     * Skipping a *new* song leaves a gap: nothing local can stand in for it. Every other
     * choice either imports the song or points the setlist at the own copy.
     */
    fun isSongAvailableAfterImport(item: SongComparisonItem): Boolean = when (item.action) {
        SongImportAction.SKIP -> item.localSong != null
        SongImportAction.KEEP_OWN -> item.localSong != null
        SongImportAction.TAKE_BACKUP, SongImportAction.KEEP_BOTH -> true
    }

    /**
     * Backup song id -> titles of the *selected* setlists that need it but would miss it
     * with the current choices. Empty when every selected setlist stays complete.
     */
    fun songsMissingFromSelectedSetlists(
        songs: List<SongComparisonItem>,
        setlists: List<SetlistImportItem>,
    ): Map<String, List<String>> {
        val unavailable = songs.filterNot { isSongAvailableAfterImport(it) }
            .map { it.backupSong.id }
            .toSet()
        if (unavailable.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, MutableList<String>>()
        setlists.filter { it.importSetlist }.forEach { setlistItem ->
            setlistItem.backupSetlist.songIds.distinct()
                .filter { it in unavailable }
                .forEach { songId ->
                    result.getOrPut(songId) { mutableListOf() }.add(setlistItem.backupSetlist.title)
                }
        }
        return result
    }

    /**
     * Whether to ask „Bist du diese Person?“: only for a complete backup made by someone
     * else's id – the typical case being one's own backup read in on a new device, which
     * has created a fresh id on first start.
     */
    fun shouldAskForIdentity(manifest: BackupManifest, currentUserId: String): Boolean =
        manifest.type == BackupType.KOMPLETT &&
            manifest.authorId.isNotBlank() &&
            manifest.authorId != currentUserId

    /**
     * Rewrites notes so that the previous device id [oldUserId] becomes [newUserId].
     *
     * Used when this device adopts the identity from a backup: notes written here before
     * (usually none on a new device) stay the own notes. If both ids have a note on the
     * same song, the newer one wins, as in [mergedWith].
     */
    fun reassignNoteAuthor(songs: List<Song>, oldUserId: String, newUserId: String): List<Song> {
        if (oldUserId.isBlank() || oldUserId == newUserId) return songs
        return songs.map { song ->
            if (song.notes.none { it.authorId == oldUserId }) {
                song
            } else {
                val others = song.notes.filter { it.authorId != oldUserId }
                val moved = song.notes.filter { it.authorId == oldUserId }
                    .map { it.copy(authorId = newUserId) }
                song.copy(notes = others.mergedWith(moved))
            }
        }
    }
}
