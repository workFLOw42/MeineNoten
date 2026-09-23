package de.workflow42.meinenoten.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * How much of the score view, measured from the bottom edge, reacts to page-turn taps.
 *
 * Restricting the zones to the lower part keeps the upper area free for zooming and panning,
 * which is where musicians most often touch the screen by accident.
 */
enum class TapZoneSize(val heightFraction: Float) {
    LOWER_THIRD(1f / 3f),
    LOWER_HALF(1f / 2f),
    FULL_HEIGHT(1f),
}

/** Which colour scheme the app uses; [SYSTEM] follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * All user-adjustable app preferences.
 *
 * The defaults describe the behaviour the app had before settings existed, so an upgrade
 * without any stored values changes nothing for the user.
 */
data class AppSettings(
    val showSongTitle: Boolean = true,
    val showSongPosition: Boolean = true,
    val showPageNumber: Boolean = true,
    val showPageButtons: Boolean = true,
    /** On the last page of a song, ▶ shows ⏭ to make clear the next tap changes the song. */
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
)

/**
 * Single app-wide DataStore instance.
 *
 * Declared as a top-level delegate because DataStore must not be instantiated more than once
 * per file within a process – doing so would throw on the second access.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists [AppSettings] in Preferences DataStore.
 *
 * DataStore is used instead of SharedPreferences because it is asynchronous and exposes
 * changes as a [Flow], so the UI recomposes automatically when a setting changes.
 */
class SettingsRepository(context: Context) {

    // Application context avoids leaking an Activity through the long-lived DataStore.
    private val dataStore = context.applicationContext.settingsDataStore

    /** Current settings; keys that were never written fall back to the [AppSettings] defaults. */
    val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toAppSettings() }

    /** Atomically reads the current settings, applies [transform] and writes the result back. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            prefs.write(transform(prefs.toAppSettings()))
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
    }

    /**
     * Enums are stored by name so reordering constants cannot silently change a stored value;
     * a name that no longer exists (e.g. after a downgrade) falls back to the default.
     */
    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    /** Key names are part of the persisted format – never rename them. */
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
    }
}
