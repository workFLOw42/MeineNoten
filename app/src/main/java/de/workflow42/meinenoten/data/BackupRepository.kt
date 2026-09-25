package de.workflow42.meinenoten.data

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
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
        // Trailing separator: without it a sibling such as "backup_temp_<id>x/" would pass.
        val tempDirPrefix = tempDir.canonicalPath + File.separator

        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val destFile = File(tempDir, entry.name)
                    if (!destFile.canonicalPath.startsWith(tempDirPrefix)) {
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
            if (!BackupLogic.isFormatSupported(manifest)) {
                throw IllegalArgumentException(
                    context.getString(R.string.error_backup_format_too_new, manifest.formatVersion)
                )
            }

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
        // Ids taken by songs from this import, so two backup songs matched to the same
        // local song cannot end up under one id and replace each other.
        val takenIds = mutableSetOf<String>()

        // Files are no longer deleted up front, not even for "replace all": a score that
        // fails to copy from the backup falls back to the local file, which must still
        // exist. Unreferenced files are removed once the new list is saved.

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
                            val mergedNotes = local.notes.mergedWith(backupSong.notes)
                            finalSong = local.copy(notes = mergedNotes)
                            val idx = updatedSongs.indexOfFirst { it.id == local.id }
                            if (idx >= 0) updatedSongs[idx] = finalSong
                        }
                        backupToLocalSongIdMap[backupSong.id] = local.id
                    }
                }
                SongImportAction.TAKE_BACKUP -> {
                    val targetLocal = item.localSong?.takeIf { it.id !in takenIds }
                    val targetId = targetLocal?.id
                        ?: backupSong.id.takeIf { it !in takenIds }
                        ?: UUID.randomUUID().toString()
                    val importedFileUri = copyBackupScoreToStorage(analysis.tempDir, backupSong, overrideId = targetId)
                    val newSong = BackupLogic.takeBackupSong(backupSong, targetLocal, importedFileUri)
                        .copy(id = targetId)
                    val existingIdx = updatedSongs.indexOfFirst { it.id == targetId }
                    if (existingIdx >= 0) {
                        updatedSongs[existingIdx] = newSong
                    } else {
                        updatedSongs.add(newSong)
                    }
                    takenIds += targetId
                    backupToLocalSongIdMap[backupSong.id] = targetId
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
                    takenIds += newId
                    backupToLocalSongIdMap[backupSong.id] = newId
                }
                SongImportAction.SKIP -> {
                    if (item.localSong != null) {
                        backupToLocalSongIdMap[backupSong.id] = item.localSong.id
                    }
                }
            }
        }

        val availableSongIds = updatedSongs.map { it.id }.toSet()
        analysis.setlists.forEach { setlistItem ->
            if (setlistItem.importSetlist) {
                val setlist = setlistItem.backupSetlist
                val newSetlist = BackupLogic.rewireSetlist(setlist, backupToLocalSongIdMap, availableSongIds)

                // Same match as in the comparison screen, so a setlist shown there as
                // existing is replaced and not added a second time.
                val localId = setlistItem.localSetlist?.id
                val existingIdx = updatedSetlists.indexOfFirst { it.id == setlist.id || it.id == localId }
                if (existingIdx >= 0) {
                    updatedSetlists[existingIdx] = newSetlist
                } else {
                    updatedSetlists.add(newSetlist)
                }
            }
        }

        onSaveSongs(updatedSongs)
        onSaveSetlists(updatedSetlists)

        // Only after the new list is saved: a crash before this point leaves the old
        // files in place rather than songs pointing at deleted ones.
        BackupLogic.orphanedSongFiles(localSongs, updatedSongs).forEach { songRepository.deleteSongFile(it) }

        var newSettings = currentSettings
        if (analysis.importSettings && analysis.settingsInBackup != null) {
            newSettings = analysis.settingsInBackup.toAppSettings(currentSettings)
        }
        if (analysis.adoptAuthorIdentity && analysis.manifest.authorId.isNotBlank()) {
            // Notes written on this device so far move to the adopted id, so they stay
            // one's own; the name follows the id, as it belongs to the same person.
            val reassigned = BackupLogic.reassignNoteAuthor(
                songs = updatedSongs,
                oldUserId = currentSettings.userId,
                newUserId = analysis.manifest.authorId,
            )
            if (reassigned != updatedSongs) onSaveSongs(reassigned)
            newSettings = newSettings.copy(
                userId = analysis.manifest.authorId,
                userName = analysis.manifest.authorName.ifBlank { newSettings.userName },
            )
        }
        if (newSettings != currentSettings) {
            onSaveSettings(newSettings)
        }

        analysis.tempDir.deleteRecursively()
    }

    /**
     * First page, page count and file size of [song]'s score, for the side-by-side view
     * in the comparison screen. With [tempDir] the copy inside the backup is read,
     * otherwise the one on the device. Null for text songs and missing files; MusicXML
     * gets size only, as it has no pages to render.
     *
     * Blocking I/O: call off the main thread.
     */
    fun loadScorePreview(song: Song, tempDir: File?, targetWidth: Int): ScorePreview? {
        val file = scoreFileOf(song, tempDir) ?: return null
        val size = file.length()
        if (song.sourceType != SongSource.PDF) return ScorePreview(bitmap = null, pageCount = null, fileSize = size)

        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val bitmap = if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val scale = targetWidth.toFloat() / page.width
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            createBitmap(targetWidth, height).also { bmp ->
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    } else null
                    ScorePreview(bitmap = bitmap, pageCount = renderer.pageCount, fileSize = size)
                }
            }
        }.getOrElse { ScorePreview(bitmap = null, pageCount = null, fileSize = size) }
    }

    private fun scoreFileOf(song: Song, tempDir: File?): File? {
        if (!song.hasFile) return null
        return if (tempDir != null) {
            File(tempDir, song.fileUri).takeIf { it.isFile }
        } else {
            songRepository.fileOf(song)
        }
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
        // Copy next to the target first: the target may be the current local score
        // (same id, same extension), which must survive a copy that fails halfway.
        val partFile = File(targetDir, "$songId.$ext.part")
        return runCatching {
            srcFile.copyTo(partFile, overwrite = true)
            if (destFile.exists()) destFile.delete()
            check(partFile.renameTo(destFile)) { "rename failed" }
            destFile.toUri().toString()
        }.getOrElse {
            partFile.delete()
            ""
        }
    }

    private fun getAppVersion(): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.0"
        }.getOrDefault("1.5.0")
    }
}
