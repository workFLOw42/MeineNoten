package de.workflow42.meinenoten.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class BackupType {
    KOMPLETT,
    SETLIST,
}

@Serializable
data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersion: String = "",
    val type: BackupType = BackupType.KOMPLETT,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceName: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    /** Relative zip entry path -> sha256 checksum hex string */
    val fileHashes: Map<String, String> = emptyMap(),
)

@Serializable
data class SerializableAppSettings(
    val showSongTitle: Boolean = true,
    val showSongPosition: Boolean = true,
    val showPageNumber: Boolean = true,
    val showPageButtons: Boolean = true,
    val announceSongChange: Boolean = true,
    val tapZonesEnabled: Boolean = true,
    val tapZoneSize: String = "LOWER_THIRD",
    val swapTapZones: Boolean = false,
    val pageTurnFlash: Boolean = true,
    val songChangeBanner: Boolean = true,
    val volumeKeysTurnPages: Boolean = true,
    val reversePedalDirection: Boolean = false,
    val keepScreenOn: Boolean = true,
    val rememberZoom: Boolean = true,
    val themeMode: String = "SYSTEM",
    val userName: String = "",
    val noteAuthorDot: Boolean = false,
    val noteAuthorColoredName: Boolean = true,
    val noteAuthorNumber: Boolean = false,
    val showBackupReminder: Boolean = true,
    val showCopyrightWarning: Boolean = true,
)

/** Decision for an individual song from a backup archive during import. */
enum class SongImportAction {
    /** Keep local song as-is. Fremde Notizen can optionally be merged. */
    KEEP_OWN,
    /** Overwrite local metadata and score file with the version from backup. */
    TAKE_BACKUP,
    /** Import backup song as a new song with a new UUID and modified title. */
    KEEP_BOTH,
    /** Do not import or touch this song. */
    SKIP,
}

enum class SongMatchCategory {
    /** Identical score file hash and metadata/content. */
    IDENTICAL,
    /** Same score file hash, but metadata (title, genre, notes, etc.) differs. */
    SAME_FILE_DIFFERENT_METADATA,
    /** Same title and artist, but different score file hash. */
    POSSIBLE_OTHER_VERSION,
    /** Unique song not existing locally. */
    NEW,
}

class SongComparisonItem(
    val backupSong: Song,
    val localSong: Song?,
    val category: SongMatchCategory,
    val scoreFileInBackup: Boolean,
    action: SongImportAction,
    mergeForeignNotes: Boolean = true,
) {
    var action by mutableStateOf(action)
    var mergeForeignNotes by mutableStateOf(mergeForeignNotes)
}

class SetlistImportItem(
    val backupSetlist: Setlist,
    val localSetlist: Setlist?,
    importSetlist: Boolean = true,
) {
    var importSetlist by mutableStateOf(importSetlist)
}

class BackupAnalysisResult(
    val manifest: BackupManifest,
    val tempDir: File,
    val songs: List<SongComparisonItem>,
    val setlists: List<SetlistImportItem>,
    val settingsInBackup: SerializableAppSettings?,
    importSettings: Boolean = false,
    val missingFiles: List<String> = emptyList(),
) {
    var importSettings by mutableStateOf(importSettings)
}
