package de.workflow42.meinenoten

import de.workflow42.meinenoten.data.BackupLogic
import de.workflow42.meinenoten.model.BackupManifest
import de.workflow42.meinenoten.model.BackupType
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.SetlistImportItem
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongComparisonItem
import de.workflow42.meinenoten.model.SongImportAction
import de.workflow42.meinenoten.model.SongMatchCategory
import de.workflow42.meinenoten.model.SongNote
import de.workflow42.meinenoten.model.SongSource
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

        val rewired = BackupLogic.computeRewiredSetlistIds(
            map,
            setlistSongIds,
            availableSongIds = setOf("local-id-1", "local-id-2", "backup-id-3"),
        )

        assertEquals(listOf("local-id-1", "local-id-2", "backup-id-3"), rewired)
    }

    @Test
    fun `rewired setlist drops songs that do not exist after the import`() {
        val map = mapOf("backup-id-1" to "local-id-1")
        val setlist = Setlist(
            id = "s1",
            title = "Gottesdienst",
            songIds = listOf("backup-id-1", "skipped-new"),
            lastSongId = "skipped-new",
            lastPage = 3,
        )

        val rewired = BackupLogic.rewireSetlist(setlist, map, availableSongIds = setOf("local-id-1"))

        assertEquals(listOf("local-id-1"), rewired.songIds)
        assertEquals(null, rewired.lastSongId)
        assertEquals(0, rewired.lastPage)
    }

    @Test
    fun `newer backup format is not supported`() {
        assertEquals(true, BackupLogic.isFormatSupported(BackupManifest(formatVersion = 1)))
        assertEquals(false, BackupLogic.isFormatSupported(BackupManifest(formatVersion = 2)))
    }

    @Test
    fun `same id but different title and file is matched, not treated as new`() {
        val tempDir = Files.createTempDirectory("backup_test").toFile()
        val local = Song(id = "same", title = "Alt", fileUri = "", fileHash = "h1")
        val backup = Song(id = "same", title = "Neu", fileUri = "", fileHash = "h2")

        val (items, _) = BackupLogic.analyzeBackupSongs(listOf(backup), listOf(local), tempDir)

        assertEquals(SongMatchCategory.POSSIBLE_OTHER_VERSION, items.single().category)
        assertEquals("same", items.single().localSong?.id)
        assertEquals(SongImportAction.KEEP_OWN, items.single().action)
        tempDir.deleteRecursively()
    }

    @Test
    fun `take backup keeps the local id and merges notes`() {
        val local = Song(
            id = "local", title = "Lied", fileUri = "file:///local.pdf",
            notes = listOf(
                SongNote(authorId = "me", text = "neuer lokal", editedAt = 20),
                SongNote(authorId = "anna", text = "alt", editedAt = 1),
            ),
        )
        val backup = Song(
            id = "backup", title = "Lied", fileUri = "files/backup.pdf",
            notes = listOf(
                SongNote(authorId = "me", text = "älter", editedAt = 10),
                SongNote(authorId = "anna", text = "neu", editedAt = 5),
            ),
        )

        val result = BackupLogic.takeBackupSong(backup, local, importedFileUri = "file:///new.pdf")

        assertEquals("local", result.id)
        assertEquals("file:///new.pdf", result.fileUri)
        assertEquals("neuer lokal", result.notes.first { it.authorId == "me" }.text)
        assertEquals("neu", result.notes.first { it.authorId == "anna" }.text)
    }

    @Test
    fun `take backup without score in the backup keeps the local file`() {
        val local = Song(id = "local", title = "Lied", fileUri = "file:///local.pdf", fileHash = "h")
        val backup = Song(id = "backup", title = "Lied neu", fileUri = "files/missing.pdf", fileHash = "x")

        val result = BackupLogic.takeBackupSong(backup, local, importedFileUri = "")

        assertEquals("file:///local.pdf", result.fileUri)
        assertEquals("h", result.fileHash)
        assertEquals("Lied neu", result.title)
    }

    @Test
    fun `take backup without any score becomes a text song`() {
        val backup = Song(id = "backup", title = "Lied", fileUri = "files/missing.pdf", sourceType = SongSource.PDF)

        val result = BackupLogic.takeBackupSong(backup, localSong = null, importedFileUri = "")

        assertEquals("", result.fileUri)
        assertEquals(SongSource.TEXT, result.sourceType)
    }

    @Test
    fun `only unreferenced local files are orphaned`() {
        val kept = Song(id = "a", title = "A", fileUri = "file:///a.pdf")
        val replacedInPlace = Song(id = "b", title = "B", fileUri = "file:///b.pdf")
        val replacedByOther = Song(id = "c", title = "C", fileUri = "file:///c.pdf")
        val final = listOf(kept, replacedInPlace.copy(title = "B2"), replacedByOther.copy(fileUri = "file:///c.xml"))

        val orphans = BackupLogic.orphanedSongFiles(listOf(kept, replacedInPlace, replacedByOther), final)

        assertEquals(listOf("c"), orphans.map { it.id })
    }

    private fun song(id: String, title: String = id) = Song(id = id, title = title, fileUri = "")

    private fun item(
        backupId: String,
        category: SongMatchCategory,
        action: SongImportAction,
        hasLocal: Boolean,
    ) = SongComparisonItem(
        backupSong = song(backupId),
        localSong = if (hasLocal) song("local-$backupId") else null,
        category = category,
        scoreFileInBackup = true,
        action = action,
    )

    @Test
    fun `skipped new song is reported for every selected setlist that needs it`() {
        val songs = listOf(
            item("a", SongMatchCategory.NEW, SongImportAction.SKIP, hasLocal = false),
            item("b", SongMatchCategory.NEW, SongImportAction.TAKE_BACKUP, hasLocal = false),
            item("c", SongMatchCategory.IDENTICAL, SongImportAction.KEEP_OWN, hasLocal = true),
        )
        val setlists = listOf(
            SetlistImportItem(Setlist(id = "s1", title = "Erntedank", songIds = listOf("a", "b", "c")), null, importSetlist = true),
            SetlistImportItem(Setlist(id = "s2", title = "Advent", songIds = listOf("a")), null, importSetlist = true),
            SetlistImportItem(Setlist(id = "s3", title = "Ostern", songIds = listOf("a")), null, importSetlist = false),
        )

        val missing = BackupLogic.songsMissingFromSelectedSetlists(songs, setlists)

        assertEquals(mapOf("a" to listOf("Erntedank", "Advent")), missing)
    }

    @Test
    fun `no hint when skipped song exists locally or no setlist needs it`() {
        val songs = listOf(
            item("a", SongMatchCategory.IDENTICAL, SongImportAction.SKIP, hasLocal = true),
            item("b", SongMatchCategory.NEW, SongImportAction.SKIP, hasLocal = false),
        )
        val setlists = listOf(
            SetlistImportItem(Setlist(id = "s1", title = "Erntedank", songIds = listOf("a")), null, importSetlist = true),
        )

        assertTrue(BackupLogic.songsMissingFromSelectedSetlists(songs, setlists).isEmpty())
    }

    @Test
    fun `identity question only for complete backups with another id`() {
        val own = BackupManifest(type = BackupType.KOMPLETT, authorId = "me")
        val foreign = BackupManifest(type = BackupType.KOMPLETT, authorId = "old-device")
        val setlist = BackupManifest(type = BackupType.SETLIST, authorId = "old-device")
        val noId = BackupManifest(type = BackupType.KOMPLETT, authorId = "")

        assertFalse(BackupLogic.shouldAskForIdentity(own, "me"))
        assertTrue(BackupLogic.shouldAskForIdentity(foreign, "me"))
        assertFalse(BackupLogic.shouldAskForIdentity(setlist, "me"))
        assertFalse(BackupLogic.shouldAskForIdentity(noId, "me"))
    }

    @Test
    fun `reassigning notes moves own notes to adopted id and keeps the newer one`() {
        val songs = listOf(
            Song(
                id = "1", title = "A", fileUri = "",
                notes = listOf(
                    SongNote(authorId = "new-device", text = "Capo 2", editedAt = 200),
                    SongNote(authorId = "backup-id", text = "alt", editedAt = 100),
                    SongNote(authorId = "anna", text = "Anna", editedAt = 50),
                ),
            ),
            Song(id = "2", title = "B", fileUri = "", notes = listOf(SongNote(authorId = "anna", text = "x"))),
        )

        val result = BackupLogic.reassignNoteAuthor(songs, oldUserId = "new-device", newUserId = "backup-id")

        val notes1 = result[0].notes
        assertEquals(2, notes1.size)
        assertEquals("Capo 2", notes1.single { it.authorId == "backup-id" }.text)
        assertNull(notes1.firstOrNull { it.authorId == "new-device" })
        assertSame(songs[1], result[1])
    }
}
