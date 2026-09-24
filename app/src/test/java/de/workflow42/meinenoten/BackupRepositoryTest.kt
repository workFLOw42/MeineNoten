package de.workflow42.meinenoten

import de.workflow42.meinenoten.data.AppSettings
import de.workflow42.meinenoten.data.TapZoneSize
import de.workflow42.meinenoten.data.ThemeMode
import de.workflow42.meinenoten.data.toAppSettings
import org.junit.Assert.*
import org.junit.Test

class BackupRepositoryTest {

    @Test
    fun `filename generation sanitizes special characters`() {
        val filenameClean = sanitizeFilename("Erntedank 2026 / Special")
        assertEquals("Erntedank-2026-Special", filenameClean)

        val nameClean = sanitizeFilename("Anna S. <Vocal>")
        assertEquals("Anna-S.-Vocal", nameClean)

        assertEquals("Ostern-2026", sanitizeFilename("  Ostern / 2026 "))
    }

    private fun sanitizeFilename(part: String): String {
        return part.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    @Test
    fun `serializable app settings roundtrip`() {
        val original = AppSettings(
            showSongTitle = false,
            showSongPosition = true,
            showPageNumber = false,
            showPageButtons = true,
            announceSongChange = false,
            tapZonesEnabled = false,
            tapZoneSize = TapZoneSize.FULL_HEIGHT,
            swapTapZones = true,
            pageTurnFlash = false,
            songChangeBanner = false,
            volumeKeysTurnPages = false,
            reversePedalDirection = true,
            keepScreenOn = false,
            rememberZoom = false,
            themeMode = ThemeMode.DARK,
            userName = "Florian Test",
            userId = "user-12345",
            lastBackupAt = 123456789L,
            noteAuthorDot = false,
            noteAuthorColoredName = true,
            noteAuthorNumber = false,
        )

        val serializable = original.toSerializable()

        assertEquals(false, serializable.showSongTitle)
        assertEquals("FULL_HEIGHT", serializable.tapZoneSize)
        assertEquals("DARK", serializable.themeMode)
        assertEquals("Florian Test", serializable.userName)

        val restored = serializable.toAppSettings(AppSettings(userId = "user-12345", lastBackupAt = 123456789L))

        assertEquals(original.showSongTitle, restored.showSongTitle)
        assertEquals(original.tapZoneSize, restored.tapZoneSize)
        assertEquals(original.themeMode, restored.themeMode)
        assertEquals(original.userName, restored.userName)
        assertEquals(original.userId, restored.userId)
        assertEquals(original.lastBackupAt, restored.lastBackupAt)
        assertEquals(original.noteAuthorDot, restored.noteAuthorDot)
        assertEquals(original.noteAuthorColoredName, restored.noteAuthorColoredName)
        assertEquals(original.noteAuthorNumber, restored.noteAuthorNumber)
    }
}
