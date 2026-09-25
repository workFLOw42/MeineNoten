package de.workflow42.meinenoten

import de.workflow42.meinenoten.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GenerateSampleBackupsTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun generateSampleBackups() {
        val samplesDir = File("../format/samples").apply { mkdirs() }
        if (!samplesDir.exists()) return

        // 1. komplett_minimal.zip
        createSampleZip(
            file = File(samplesDir, "komplett_minimal.zip"),
            manifest = BackupManifest(
                formatVersion = 1,
                appVersion = "1.6.2",
                type = BackupType.KOMPLETT,
                createdAt = 1740000000000L,
                deviceName = "TestDevice",
                authorId = "author-1",
                authorName = "Florian",
                title = "Komplett Minimal",
                fileHashes = mapOf("files/song-1.pdf" to "da39a3ee5e6b4b0d3255bfef95601890afd80709")
            ),
            songs = listOf(
                Song(
                    id = "song-1",
                    title = "Beispiel-Lied",
                    artist = "Test Artist",
                    fileUri = "files/song-1.pdf",
                    sourceType = SongSource.PDF,
                    fileHash = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
                )
            ),
            setlists = listOf(
                Setlist(
                    id = "setlist-1",
                    title = "Sonntag",
                    date = "2026-03-05",
                    songIds = listOf("song-1")
                )
            ),
            settings = SerializableAppSettings(userName = "Florian"),
            includeSettings = true,
            samplePdfContent = "%PDF-1.4 minimal dummy pdf content"
        )

        // 2. setlist.zip
        createSampleZip(
            file = File(samplesDir, "setlist.zip"),
            manifest = BackupManifest(
                formatVersion = 1,
                appVersion = "1.6.2",
                type = BackupType.SETLIST,
                createdAt = 1740000000000L,
                deviceName = "TestDevice",
                authorId = "author-1",
                authorName = "Florian",
                title = "Sonntag",
                fileHashes = mapOf("files/song-1.pdf" to "da39a3ee5e6b4b0d3255bfef95601890afd80709")
            ),
            songs = listOf(
                Song(
                    id = "song-1",
                    title = "Lied in Setlist",
                    fileUri = "files/song-1.pdf",
                    sourceType = SongSource.PDF,
                    fileHash = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
                )
            ),
            setlists = listOf(
                Setlist(
                    id = "setlist-1",
                    title = "Sonntag",
                    songIds = listOf("song-1")
                )
            ),
            settings = null,
            includeSettings = false,
            samplePdfContent = "%PDF-1.4 dummy"
        )

        // 3. notizen_personen.zip
        createSampleZip(
            file = File(samplesDir, "notizen_personen.zip"),
            manifest = BackupManifest(
                formatVersion = 1,
                appVersion = "1.6.2",
                type = BackupType.KOMPLETT,
                createdAt = 1740000000000L,
                deviceName = "TestDevice",
                authorId = "author-1",
                authorName = "Anna",
                title = "Notizen Personen",
                fileHashes = emptyMap()
            ),
            songs = listOf(
                Song(
                    id = "song-2",
                    title = "Mehrpersonen-Lied",
                    fileUri = "",
                    sourceType = SongSource.TEXT,
                    lyrics = "Verse 1\nChorus",
                    notes = listOf(
                        SongNote(authorId = "anna-1", authorName = "Anna", text = "Capo 1", editedAt = 1000L),
                        SongNote(authorId = "anna-2", authorName = "Anna", text = "Solo ab Takt 8", editedAt = 2000L),
                        SongNote(authorId = "peter-1", authorName = "Peter", text = "Langsam spielen", editedAt = 1500L)
                    )
                )
            ),
            setlists = emptyList(),
            settings = SerializableAppSettings(userName = "Anna"),
            includeSettings = true,
            samplePdfContent = null
        )

        // 4. alt_formatVersion1_einzelnotiz.zip
        val altFile = File(samplesDir, "alt_formatVersion1_einzelnotiz.zip")
        ZipOutputStream(FileOutputStream(altFile)).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(json.encodeToString(BackupManifest(formatVersion = 1, title = "Alt")).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("songs.json"))
            val legacySongJson = """
                [
                    {
                        "id": "legacy-1",
                        "title": "Altes Lied",
                        "fileUri": "",
                        "sourceType": "TEXT",
                        "notes": "Alte Einzelnotiz im Textfeld"
                    }
                ]
            """.trimIndent()
            zipOut.write(legacySongJson.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("setlists.json"))
            zipOut.write("[]".toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("settings.json"))
            zipOut.write(json.encodeToString(SerializableAppSettings()).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }
    }

    private fun createSampleZip(
        file: File,
        manifest: BackupManifest,
        songs: List<Song>,
        setlists: List<Setlist>,
        settings: SerializableAppSettings?,
        includeSettings: Boolean,
        samplePdfContent: String?
    ) {
        ZipOutputStream(FileOutputStream(file)).use { zipOut ->
            // manifest.json
            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // songs.json
            zipOut.putNextEntry(ZipEntry("songs.json"))
            zipOut.write(json.encodeToString(songs).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // setlists.json
            zipOut.putNextEntry(ZipEntry("setlists.json"))
            zipOut.write(json.encodeToString(setlists).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // settings.json
            if (includeSettings && settings != null) {
                zipOut.putNextEntry(ZipEntry("settings.json"))
                zipOut.write(json.encodeToString(settings).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            // sample files
            if (samplePdfContent != null) {
                songs.forEach { song ->
                    if (song.hasFile && song.fileUri.startsWith("files/")) {
                        val bytes = samplePdfContent.toByteArray(Charsets.UTF_8)
                        val entry = ZipEntry(song.fileUri).apply {
                            method = ZipOutputStream.STORED
                            size = bytes.size.toLong()
                            val crc = CRC32().apply { update(bytes) }
                            setCrc(crc.value)
                        }
                        zipOut.putNextEntry(entry)
                        zipOut.write(bytes)
                        zipOut.closeEntry()
                    }
                }
            }
        }
    }
}
