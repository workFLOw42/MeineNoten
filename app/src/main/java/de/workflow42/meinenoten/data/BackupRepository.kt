package de.workflow42.meinenoten.data

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.*

class BackupRepository(private val context: Context, private val songRepository: SongRepository) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun generateBackupFilename(
        type: BackupType,
        setlistTitle: String? = null,
        userName: String = "",
    ): String {
        return BackupLogic.generateBackupFilename(
            type = type,
            setlistTitle = setlistTitle,
            userName = userName,
            deviceName = Build.MODEL,
        )
    }

    fun createFullBackupZip(
        outputStream: OutputStream,
        songs: List<Song>,
        setlists: List<Setlist>,
        appSettings: AppSettings,
        onProgress: (current: Int, total: Int, songTitle: String) -> Unit = { _, _, _ -> },
    ) {
        val missingFiles = songs.filter { it.hasFile && songRepository.fileOf(it) == null }
            .map { it.displayTitle }
        if (missingFiles.isNotEmpty()) {
            val errorMsg = context.getString(R.string.error_missing_song_file, missingFiles.first())
            throw IllegalStateException(errorMsg)
        }

        val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))
        val fileHashes = mutableMapOf<String, String>()

        val totalSongs = songs.size
        var currentIndex = 0

        // 1. Copy score files into ZIP under files/
        val relativeSongs = songs.map { song ->
            currentIndex++
            onProgress(currentIndex, totalSongs, song.displayTitle)
            if (song.hasFile) {
                val file = songRepository.fileOf(song)
                if (file != null && file.exists()) {
                    val ext = file.extension
                    val zipPath = "files/${song.id}.$ext"
                    writeScoreFileToZip(zipOut, zipPath, file)
                    val hash = song.fileHash.ifBlank { FileHash.of(file) }
                    fileHashes[zipPath] = hash
                    song.copy(fileUri = zipPath, fileHash = hash)
                } else {
                    song.copy(fileUri = "")
                }
            } else {
                song
            }
        }

        // 2. Write songs.json
        writeJsonToZip(zipOut, "songs.json", json.encodeToString(relativeSongs))

        // 3. Write setlists.json
        writeJsonToZip(zipOut, "setlists.json", json.encodeToString(setlists))

        // 4. Write settings.json
        writeJsonToZip(zipOut, "settings.json", json.encodeToString(appSettings.toSerializable()))

        // 5. Write manifest.json
        val manifest = BackupManifest(
            formatVersion = 1,
            appVersion = getAppVersion(),
            type = BackupType.KOMPLETT,
            createdAt = System.currentTimeMillis(),
            deviceName = Build.MODEL,
            authorId = appSettings.userId,
            authorName = appSettings.displayName,
            title = "Komplett",
            fileHashes = fileHashes,
        )
        writeJsonToZip(zipOut, "manifest.json", json.encodeToString(manifest))

        zipOut.finish()
        zipOut.flush()
    }

    fun createSetlistBackupZip(
        outputStream: OutputStream,
        setlist: Setlist,
        allSongs: List<Song>,
        appSettings: AppSettings,
        onProgress: (current: Int, total: Int, songTitle: String) -> Unit = { _, _, _ -> },
    ) {
        val setlistSongsMap = allSongs.associateBy { it.id }
        val setlistSongs = setlist.songIds.mapNotNull { setlistSongsMap[it] }

        val missingFiles = setlistSongs.filter { it.hasFile && songRepository.fileOf(it) == null }
            .map { it.displayTitle }
        if (missingFiles.isNotEmpty()) {
            val errorMsg = context.getString(R.string.error_missing_song_file, missingFiles.first())
            throw IllegalStateException(errorMsg)
        }

        val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))
        val fileHashes = mutableMapOf<String, String>()

        val totalSongs = setlistSongs.size
        var currentIndex = 0

        val relativeSongs = setlistSongs.map { song ->
            currentIndex++
            onProgress(currentIndex, totalSongs, song.displayTitle)
            if (song.hasFile) {
                val file = songRepository.fileOf(song)
                if (file != null && file.exists()) {
                    val ext = file.extension
                    val zipPath = "files/${song.id}.$ext"
                    writeScoreFileToZip(zipOut, zipPath, file)
                    val hash = song.fileHash.ifBlank { FileHash.of(file) }
                    fileHashes[zipPath] = hash
                    song.copy(fileUri = zipPath, fileHash = hash)
                } else {
                    song.copy(fileUri = "")
                }
            } else {
                song
            }
        }

        writeJsonToZip(zipOut, "songs.json", json.encodeToString(relativeSongs))
        writeJsonToZip(zipOut, "setlists.json", json.encodeToString(listOf(setlist)))

        val manifest = BackupManifest(
            formatVersion = 1,
            appVersion = getAppVersion(),
            type = BackupType.SETLIST,
            createdAt = System.currentTimeMillis(),
            deviceName = Build.MODEL,
            authorId = appSettings.userId,
            authorName = appSettings.displayName,
            title = setlist.title,
            fileHashes = fileHashes,
        )
        writeJsonToZip(zipOut, "manifest.json", json.encodeToString(manifest))

        zipOut.finish()
        zipOut.flush()
    }

    private fun writeScoreFileToZip(zipOut: ZipOutputStream, zipPath: String, file: File) {
        val entry = ZipEntry(zipPath)
        zipOut.putNextEntry(entry)
        file.inputStream().use { input -> input.copyTo(zipOut) }
        zipOut.closeEntry()
    }

    private fun writeJsonToZip(zipOut: ZipOutputStream, zipPath: String, jsonString: String) {
        val entry = ZipEntry(zipPath)
        zipOut.putNextEntry(entry)
        zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }

    fun readAndAnalyzeBackupArchive(
        inputStream: InputStream,
        localSongs: List<Song>,
        localSetlists: List<Setlist>,
    ): BackupAnalysisResult {
        val tempDir = File(context.cacheDir, "backup_temp_${UUID.randomUUID()}")
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val destFile = File(tempDir, entry.name)
                    if (!destFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
                        throw SecurityException("ZIP Entry outside target directory")
                    }
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { out -> zipIn.copyTo(out) }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw IllegalArgumentException(context.getString(R.string.error_backup_read_failed))
            }

            val manifest = json.decodeFromString<BackupManifest>(manifestFile.readText())

            val backupSongsFile = File(tempDir, "songs.json")
            val backupSongs = if (backupSongsFile.exists()) {
                json.decodeFromString<List<Song>>(backupSongsFile.readText())
            } else {
                emptyList()
            }

            val backupSetlistsFile = File(tempDir, "setlists.json")
            val backupSetlists = if (backupSetlistsFile.exists()) {
                json.decodeFromString<List<Setlist>>(backupSetlistsFile.readText())
            } else {
                emptyList()
            }

            val backupSettingsFile = File(tempDir, "settings.json")
            val settingsInBackup = if (backupSettingsFile.exists()) {
                runCatching { json.decodeFromString<SerializableAppSettings>(backupSettingsFile.readText()) }.getOrNull()
            } else {
                null
            }

            val (songItems, missingFiles) = BackupLogic.analyzeBackupSongs(backupSongs, localSongs, tempDir)
            val setlistItems = BackupLogic.analyzeBackupSetlists(backupSetlists, localSetlists)

            return BackupAnalysisResult(
                manifest = manifest,
                tempDir = tempDir,
                songs = songItems,
                setlists = setlistItems,
                settingsInBackup = settingsInBackup,
                importSettings = false,
                missingFiles = missingFiles,
            )
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        }
    }

    fun applyBackupImport(
        analysis: BackupAnalysisResult,
        localSongs: List<Song>,
        localSetlists: List<Setlist>,
        currentSettings: AppSettings,
        onSaveSongs: (List<Song>) -> Unit,
        onSaveSetlists: (List<Setlist>) -> Unit,
        onSaveSettings: (AppSettings) -> Unit,
        replaceAll: Boolean = false,
        onProgress: (current: Int, total: Int, songTitle: String) -> Unit = { _, _, _ -> },
    ) {
        val updatedSongs = if (replaceAll) mutableListOf() else localSongs.toMutableList()
        val updatedSetlists = if (replaceAll) mutableListOf() else localSetlists.toMutableList()
        val backupToLocalSongIdMap = mutableMapOf<String, String>()

        if (replaceAll) {
            localSongs.forEach { songRepository.deleteSongFile(it) }
        }

        val totalSongs = analysis.songs.size
        var currentIndex = 0

        analysis.songs.forEach { item ->
            currentIndex++
            onProgress(currentIndex, totalSongs, item.backupSong.displayTitle)
            val backupSong = item.backupSong
            when (item.action) {
                SongImportAction.KEEP_OWN -> {
                    val local = item.localSong
                    if (local != null) {
                        var finalSong = local
                        if (item.mergeForeignNotes) {
                            val mergedNotes = mergeNotesList(local.notes, backupSong.notes)
                            finalSong = local.copy(notes = mergedNotes)
                            val idx = updatedSongs.indexOfFirst { it.id == local.id }
                            if (idx >= 0) updatedSongs[idx] = finalSong
                        }
                        backupToLocalSongIdMap[backupSong.id] = local.id
                    }
                }
                SongImportAction.TAKE_BACKUP -> {
                    val importedFileUri = copyBackupScoreToStorage(analysis.tempDir, backupSong)
                    val newSong = backupSong.copy(
                        fileUri = importedFileUri,
                    )
                    val existingIdx = updatedSongs.indexOfFirst { it.id == backupSong.id }
                    if (existingIdx >= 0) {
                        songRepository.deleteSongFile(updatedSongs[existingIdx])
                        updatedSongs[existingIdx] = newSong
                    } else {
                        updatedSongs.add(newSong)
                    }
                    backupToLocalSongIdMap[backupSong.id] = newSong.id
                }
                SongImportAction.KEEP_BOTH -> {
                    val newId = UUID.randomUUID().toString()
                    val versionSuffix = if (analysis.manifest.authorName.isNotBlank()) {
                        "Sicherung ${analysis.manifest.authorName}"
                    } else {
                        "Sicherung"
                    }
                    val newVersion = if (backupSong.version.isBlank()) versionSuffix else "${backupSong.version} ($versionSuffix)"
                    val importedFileUri = copyBackupScoreToStorage(analysis.tempDir, backupSong, overrideId = newId)

                    val newSong = backupSong.copy(
                        id = newId,
                        version = newVersion,
                        fileUri = importedFileUri,
                    )
                    updatedSongs.add(newSong)
                    backupToLocalSongIdMap[backupSong.id] = newId
                }
                SongImportAction.SKIP -> {
                    if (item.localSong != null) {
                        backupToLocalSongIdMap[backupSong.id] = item.localSong.id
                    }
                }
            }
        }

        analysis.setlists.forEach { setlistItem ->
            if (setlistItem.importSetlist) {
                val setlist = setlistItem.backupSetlist
                val rewiredSongIds = BackupLogic.computeRewiredSetlistIds(backupToLocalSongIdMap, setlist.songIds)
                val newSetlist = setlist.copy(songIds = rewiredSongIds)

                val existingIdx = updatedSetlists.indexOfFirst { it.id == setlist.id || (it.title == setlist.title && it.date == setlist.date) }
                if (existingIdx >= 0) {
                    updatedSetlists[existingIdx] = newSetlist
                } else {
                    updatedSetlists.add(newSetlist)
                }
            }
        }

        onSaveSongs(updatedSongs)
        onSaveSetlists(updatedSetlists)

        if (analysis.importSettings && analysis.settingsInBackup != null) {
            onSaveSettings(analysis.settingsInBackup.toAppSettings(currentSettings))
        }

        analysis.tempDir.deleteRecursively()
    }

    private fun copyBackupScoreToStorage(tempDir: File, backupSong: Song, overrideId: String? = null): String {
        if (!backupSong.hasFile || backupSong.fileUri.isBlank()) return ""
        val srcFile = File(tempDir, backupSong.fileUri)
        if (!srcFile.isFile) return ""

        val songId = overrideId ?: backupSong.id
        val ext = srcFile.extension.lowercase()
        val folderName = if (backupSong.sourceType == SongSource.PDF) "songs" else "musicxml"
        val targetDir = File(context.filesDir, folderName)
        if (!targetDir.exists()) targetDir.mkdirs()

        val destFile = File(targetDir, "$songId.$ext")
        srcFile.copyTo(destFile, overwrite = true)
        return destFile.toUri().toString()
    }

    private fun mergeNotesList(localNotes: List<SongNote>, foreignNotes: List<SongNote>): List<SongNote> {
        val result = localNotes.toMutableList()
        foreignNotes.forEach { foreign ->
            val idx = result.indexOfFirst { it.authorId == foreign.authorId }
            if (idx >= 0) {
                if (foreign.editedAt > result[idx].editedAt) {
                    result[idx] = foreign
                }
            } else {
                result.add(foreign)
            }
        }
        return result
    }

    private fun getAppVersion(): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.0"
        }.getOrDefault("1.5.0")
    }
}
