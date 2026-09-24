package de.workflow42.meinenoten.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.workflow42.meinenoten.model.SerializableAppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class TapZoneSize(val heightFraction: Float) {
    LOWER_THIRD(1f / 3f),
    LOWER_HALF(1f / 2f),
    FULL_HEIGHT(1f),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val showSongTitle: Boolean = true,
    val showSongPosition: Boolean = true,
    val showPageNumber: Boolean = true,
    val showPageButtons: Boolean = true,
    val announceSongChange: Boolean = true,
    val tapZonesEnabled: Boolean = true,
    val tapZoneSize: TapZoneSize = TapZoneSize.LOWER_THIRD,
    val swapTapZones: Boolean = false,
    val pageTurnFlash: Boolean = true,
    val songChangeBanner: Boolean = true,
    val volumeKeysTurnPages: Boolean = true,
    val reversePedalDirection: Boolean = false,
    val keepScreenOn: Boolean = true,
    val rememberZoom: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val userName: String = "",
    val userId: String = "",
    val lastBackupAt: Long = 0L,
    val noteAuthorDot: Boolean = false,
    val noteAuthorColoredName: Boolean = true,
    val noteAuthorNumber: Boolean = false,
    val showBackupReminder: Boolean = true,
    val showCopyrightWarning: Boolean = true,
) {
    val displayName: String
        get() = userName.trim()

    fun toSerializable(): SerializableAppSettings =
        SerializableAppSettings(
            showSongTitle = showSongTitle,
            showSongPosition = showSongPosition,
            showPageNumber = showPageNumber,
            showPageButtons = showPageButtons,
            announceSongChange = announceSongChange,
            tapZonesEnabled = tapZonesEnabled,
            tapZoneSize = tapZoneSize.name,
            swapTapZones = swapTapZones,
            pageTurnFlash = pageTurnFlash,
            songChangeBanner = songChangeBanner,
            volumeKeysTurnPages = volumeKeysTurnPages,
            reversePedalDirection = reversePedalDirection,
            keepScreenOn = keepScreenOn,
            rememberZoom = rememberZoom,
            themeMode = themeMode.name,
            userName = userName,
            noteAuthorDot = noteAuthorDot,
            noteAuthorColoredName = noteAuthorColoredName,
            noteAuthorNumber = noteAuthorNumber,
            showBackupReminder = showBackupReminder,
            showCopyrightWarning = showCopyrightWarning,
        )
}

fun SerializableAppSettings.toAppSettings(currentSettings: AppSettings): AppSettings =
    currentSettings.copy(
        showSongTitle = showSongTitle,
        showSongPosition = showSongPosition,
        showPageNumber = showPageNumber,
        showPageButtons = showPageButtons,
        announceSongChange = announceSongChange,
        tapZonesEnabled = tapZonesEnabled,
        tapZoneSize = runCatching { TapZoneSize.valueOf(tapZoneSize) }.getOrDefault(currentSettings.tapZoneSize),
        swapTapZones = swapTapZones,
        pageTurnFlash = pageTurnFlash,
        songChangeBanner = songChangeBanner,
        volumeKeysTurnPages = volumeKeysTurnPages,
        reversePedalDirection = reversePedalDirection,
        keepScreenOn = keepScreenOn,
        rememberZoom = rememberZoom,
        themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(currentSettings.themeMode),
        userName = userName,
        noteAuthorDot = noteAuthorDot,
        noteAuthorColoredName = noteAuthorColoredName,
        noteAuthorNumber = noteAuthorNumber,
        showBackupReminder = showBackupReminder,
        showCopyrightWarning = showCopyrightWarning,
    )

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            prefs.write(transform(prefs.toAppSettings()))
        }
    }

    suspend fun ensureUserId() {
        dataStore.edit { prefs ->
            if (prefs[Keys.USER_ID].isNullOrBlank()) {
                prefs[Keys.USER_ID] = UUID.randomUUID().toString()
            }
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            showSongTitle = this[Keys.SHOW_SONG_TITLE] ?: defaults.showSongTitle,
            showSongPosition = this[Keys.SHOW_SONG_POSITION] ?: defaults.showSongPosition,
            showPageNumber = this[Keys.SHOW_PAGE_NUMBER] ?: defaults.showPageNumber,
            showPageButtons = this[Keys.SHOW_PAGE_BUTTONS] ?: defaults.showPageButtons,
            announceSongChange = this[Keys.ANNOUNCE_SONG_CHANGE] ?: defaults.announceSongChange,
            tapZonesEnabled = this[Keys.TAP_ZONES_ENABLED] ?: defaults.tapZonesEnabled,
            tapZoneSize = enumOrDefault(this[Keys.TAP_ZONE_SIZE], defaults.tapZoneSize),
            swapTapZones = this[Keys.SWAP_TAP_ZONES] ?: defaults.swapTapZones,
            pageTurnFlash = this[Keys.PAGE_TURN_FLASH] ?: defaults.pageTurnFlash,
            songChangeBanner = this[Keys.SONG_CHANGE_BANNER] ?: defaults.songChangeBanner,
            volumeKeysTurnPages = this[Keys.VOLUME_KEYS_TURN_PAGES] ?: defaults.volumeKeysTurnPages,
            reversePedalDirection = this[Keys.REVERSE_PEDAL_DIRECTION] ?: defaults.reversePedalDirection,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            rememberZoom = this[Keys.REMEMBER_ZOOM] ?: defaults.rememberZoom,
            themeMode = enumOrDefault(this[Keys.THEME_MODE], defaults.themeMode),
            userName = this[Keys.USER_NAME] ?: defaults.userName,
            userId = this[Keys.USER_ID] ?: defaults.userId,
            lastBackupAt = this[Keys.LAST_BACKUP_AT] ?: defaults.lastBackupAt,
            noteAuthorDot = this[Keys.NOTE_AUTHOR_DOT] ?: defaults.noteAuthorDot,
            noteAuthorColoredName = this[Keys.NOTE_AUTHOR_COLORED_NAME] ?: defaults.noteAuthorColoredName,
            noteAuthorNumber = this[Keys.NOTE_AUTHOR_NUMBER] ?: defaults.noteAuthorNumber,
            showBackupReminder = this[Keys.SHOW_BACKUP_REMINDER] ?: defaults.showBackupReminder,
            showCopyrightWarning = this[Keys.SHOW_COPYRIGHT_WARNING] ?: defaults.showCopyrightWarning,
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.SHOW_SONG_TITLE] = settings.showSongTitle
        this[Keys.SHOW_SONG_POSITION] = settings.showSongPosition
        this[Keys.SHOW_PAGE_NUMBER] = settings.showPageNumber
        this[Keys.SHOW_PAGE_BUTTONS] = settings.showPageButtons
        this[Keys.ANNOUNCE_SONG_CHANGE] = settings.announceSongChange
        this[Keys.TAP_ZONES_ENABLED] = settings.tapZonesEnabled
        this[Keys.TAP_ZONE_SIZE] = settings.tapZoneSize.name
        this[Keys.SWAP_TAP_ZONES] = settings.swapTapZones
        this[Keys.PAGE_TURN_FLASH] = settings.pageTurnFlash
        this[Keys.SONG_CHANGE_BANNER] = settings.songChangeBanner
        this[Keys.VOLUME_KEYS_TURN_PAGES] = settings.volumeKeysTurnPages
        this[Keys.REVERSE_PEDAL_DIRECTION] = settings.reversePedalDirection
        this[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
        this[Keys.REMEMBER_ZOOM] = settings.rememberZoom
        this[Keys.THEME_MODE] = settings.themeMode.name
        this[Keys.USER_NAME] = settings.userName
        if (settings.userId.isNotBlank()) {
            this[Keys.USER_ID] = settings.userId
        }
        this[Keys.LAST_BACKUP_AT] = settings.lastBackupAt
        this[Keys.NOTE_AUTHOR_DOT] = settings.noteAuthorDot
        this[Keys.NOTE_AUTHOR_COLORED_NAME] = settings.noteAuthorColoredName
        this[Keys.NOTE_AUTHOR_NUMBER] = settings.noteAuthorNumber
        this[Keys.SHOW_BACKUP_REMINDER] = settings.showBackupReminder
        this[Keys.SHOW_COPYRIGHT_WARNING] = settings.showCopyrightWarning
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private object Keys {
        val SHOW_SONG_TITLE = booleanPreferencesKey("show_song_title")
        val SHOW_SONG_POSITION = booleanPreferencesKey("show_song_position")
        val SHOW_PAGE_NUMBER = booleanPreferencesKey("show_page_number")
        val SHOW_PAGE_BUTTONS = booleanPreferencesKey("show_page_buttons")
        val ANNOUNCE_SONG_CHANGE = booleanPreferencesKey("announce_song_change")
        val TAP_ZONES_ENABLED = booleanPreferencesKey("tap_zones_enabled")
        val TAP_ZONE_SIZE = stringPreferencesKey("tap_zone_size")
        val SWAP_TAP_ZONES = booleanPreferencesKey("swap_tap_zones")
        val PAGE_TURN_FLASH = booleanPreferencesKey("page_turn_flash")
        val SONG_CHANGE_BANNER = booleanPreferencesKey("song_change_banner")
        val VOLUME_KEYS_TURN_PAGES = booleanPreferencesKey("volume_keys_turn_pages")
        val REVERSE_PEDAL_DIRECTION = booleanPreferencesKey("reverse_pedal_direction")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val REMEMBER_ZOOM = booleanPreferencesKey("remember_zoom")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_ID = stringPreferencesKey("user_id")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val NOTE_AUTHOR_DOT = booleanPreferencesKey("note_author_dot")
        val NOTE_AUTHOR_COLORED_NAME = booleanPreferencesKey("note_author_colored_name")
        val NOTE_AUTHOR_NUMBER = booleanPreferencesKey("note_author_number")
        val SHOW_BACKUP_REMINDER = booleanPreferencesKey("show_backup_reminder")
        val SHOW_COPYRIGHT_WARNING = booleanPreferencesKey("show_copyright_warning")
    }
}
