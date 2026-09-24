package de.workflow42.meinenoten

import de.workflow42.meinenoten.data.BackupLogic
import de.workflow42.meinenoten.model.BackupType
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongMatchCategory
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*

class BackupLogicTest {

    @Test
    fun `filename generation sanitizes special characters and formatting`() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        val filenameKomplett = BackupLogic.generateBackupFilename(
            type = BackupType.KOMPLETT,
            userName = "Anna S. <Vocal>",
            deviceName = "Pixel 8 / Pro"
        )
        assertTrue(filenameKomplett.startsWith("${today}_Komplett_Pixel-8-Pro_Anna-S.-Vocal"))
        assertTrue(filenameKomplett.endsWith(".zip"))

        val filenameSetlist = BackupLogic.generateBackupFilename(
            type = BackupType.SETLIST,
            setlistTitle = "Erntedank / Gottesdienst",
            userName = "Florian",
            deviceName = "Tablet"
        )
        assertTrue(filenameSetlist.startsWith("${today}_Setlist-Erntedank-Gottesdienst_Tablet_Florian"))
    }

    @Test
    fun `analyze backup songs categorizes correctly`() {
        val tempDir = Files.createTempDirectory("backup_test").toFile()

        val identicalLocal = Song(id = "local-1", title = "Amazing Grace", artist = "Traditional", genre = "Gospel", lyrics = "Lyrics text", fileUri = "", fileHash = "hash123")
        val backupIdentical = Song(id = "backup-1", title = "Amazing Grace", artist = "Traditional", genre = "Gospel", lyrics = "Lyrics text", fileUri = "", fileHash = "hash123")

        val diffMetaLocal = Song(id = "local-2", title = "Nearer, My God", artist = "Mason", genre = "Hymn", fileUri = "", fileHash = "hash456")
        val backupDiffMeta = Song(id = "backup-2", title = "Nearer, My God to Thee", artist = "Mason", genre = "Hymn", fileUri = "", fileHash = "hash456")

        val otherVersionLocal = Song(id = "local-3", title = "Holy, Holy, Holy", artist = "Dykes", genre = "Hymn", fileUri = "", fileHash = "hashOld")
        val backupOtherVersion = Song(id = "backup-3", title = "Holy, Holy, Holy", artist = "Dykes", genre = "Hymn", fileUri = "", fileHash = "hashNew")

        val backupNew = Song(id = "backup-4", title = "New Song", artist = "Unknown", fileUri = "", fileHash = "hashNewSong")

        val backupSongs = listOf(backupIdentical, backupDiffMeta, backupOtherVersion, backupNew)
        val localSongs = listOf(identicalLocal, diffMetaLocal, otherVersionLocal)

        val (items, missing) = BackupLogic.analyzeBackupSongs(backupSongs, localSongs, tempDir)

        assertEquals(4, items.size)
        assertEquals(0, missing.size)

        val item1 = items.find { it.backupSong.id == "backup-1" }!!
        assertEquals(SongMatchCategory.IDENTICAL, item1.category)

        val item2 = items.find { it.backupSong.id == "backup-2" }!!
        assertEquals(SongMatchCategory.SAME_FILE_DIFFERENT_METADATA, item2.category)

        val item3 = items.find { it.backupSong.id == "backup-3" }!!
        assertEquals(SongMatchCategory.POSSIBLE_OTHER_VERSION, item3.category)

        val item4 = items.find { it.backupSong.id == "backup-4" }!!
        assertEquals(SongMatchCategory.NEW, item4.category)

        tempDir.deleteRecursively()
    }

    @Test
    fun `compute rewired setlist IDs correctly maps backup to local IDs`() {
        val map = mapOf(
            "backup-id-1" to "local-id-1",
            "backup-id-2" to "local-id-2"
        )
        val setlistSongIds = listOf("backup-id-1", "backup-id-2", "backup-id-3")

        val rewired = BackupLogic.computeRewiredSetlistIds(map, setlistSongIds)

        assertEquals(listOf("local-id-1", "local-id-2", "backup-id-3"), rewired)
    }
}
