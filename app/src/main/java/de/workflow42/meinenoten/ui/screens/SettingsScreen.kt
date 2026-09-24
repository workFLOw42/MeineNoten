package de.workflow42.meinenoten.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.data.AppSettings
import de.workflow42.meinenoten.data.TapZoneSize
import de.workflow42.meinenoten.data.ThemeMode
import de.workflow42.meinenoten.ui.components.VersionFooter
import de.workflow42.meinenoten.ui.theme.MeineNotenTheme

/**
 * App settings, grouped by the situation in which they matter.
 *
 * The screen is stateless: it renders [settings] and reports every change as a complete new
 * [AppSettings] via [onSettingsChange], so persistence stays in one place (the repository)
 * and the screen is trivially previewable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onMenuClick: () -> Unit,
    onExportBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.cd_open_menu))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader(R.string.settings_section_person)
            // A stored name is shown as plain, prominent text; the input line only appears
            // while the name is empty or being edited. Outside editing everything reads the
            // stored value directly, so a name set from elsewhere – above all by adopting the
            // identity from a backup, started from this very screen – appears at once.
            var editingName by rememberSaveable { mutableStateOf(false) }
            var nameDraft by rememberSaveable { mutableStateOf(settings.userName) }
            val nameFocus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            val saveName: () -> Unit = {
                val trimmed = nameDraft.trim()
                if (trimmed != settings.userName) onSettingsChange(settings.copy(userName = trimmed))
                editingName = false
                keyboard?.hide()
            }
            // Focus only after „Bearbeiten“: an empty field must not pop up the keyboard
            // every time the settings are opened.
            LaunchedEffect(editingName) {
                if (editingName) nameFocus.requestFocus()
            }
            val showNameField = editingName || settings.userName.isBlank()
            if (showNameField) {
                OutlinedTextField(
                    value = if (editingName) nameDraft else settings.userName,
                    onValueChange = {
                        // Typing into the empty field counts as editing from here on.
                        nameDraft = it
                        editingName = true
                    },
                    label = { Text(stringResource(R.string.settings_user_name)) },
                    supportingText = { Text(stringResource(R.string.settings_user_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { saveName() }),
                    trailingIcon = {
                        TextButton(onClick = saveName) {
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .focusRequester(nameFocus),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_user_name),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = settings.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(onClick = {
                        nameDraft = settings.userName
                        editingName = true
                    }) {
                        Text(stringResource(R.string.action_edit))
                    }
                }
            }

            SectionHeader(R.string.settings_section_status_bar)
            SwitchRow(R.string.settings_show_song_title, settings.showSongTitle) {
                onSettingsChange(settings.copy(showSongTitle = it))
            }
            SwitchRow(R.string.settings_show_song_position, settings.showSongPosition) {
                onSettingsChange(settings.copy(showSongPosition = it))
            }
            SwitchRow(R.string.settings_show_page_number, settings.showPageNumber) {
                onSettingsChange(settings.copy(showPageNumber = it))
            }
            SwitchRow(R.string.settings_show_page_buttons, settings.showPageButtons) {
                onSettingsChange(settings.copy(showPageButtons = it))
            }
            SwitchRow(R.string.settings_announce_song_change, settings.announceSongChange) {
                onSettingsChange(settings.copy(announceSongChange = it))
            }

            SectionHeader(R.string.settings_section_page_turning)
            SwitchRow(R.string.settings_tap_zones, settings.tapZonesEnabled) {
                onSettingsChange(settings.copy(tapZonesEnabled = it))
            }
            // Size and swapping only make sense with tap zones on; they stay visible but
            // disabled so users can see what enabling the zones would offer.
            ChoiceRow(
                label = R.string.settings_tap_zone_size,
                options = listOf(
                    TapZoneSize.LOWER_THIRD to R.string.settings_tap_zone_lower_third,
                    TapZoneSize.LOWER_HALF to R.string.settings_tap_zone_lower_half,
                    TapZoneSize.FULL_HEIGHT to R.string.settings_tap_zone_full_height,
                ),
                selected = settings.tapZoneSize,
                enabled = settings.tapZonesEnabled,
                onSelect = { onSettingsChange(settings.copy(tapZoneSize = it)) },
            )
            SwitchRow(
                R.string.settings_swap_tap_zones,
                settings.swapTapZones,
                enabled = settings.tapZonesEnabled,
            ) {
                onSettingsChange(settings.copy(swapTapZones = it))
            }
            SwitchRow(R.string.settings_page_turn_flash, settings.pageTurnFlash) {
                onSettingsChange(settings.copy(pageTurnFlash = it))
            }
            SwitchRow(R.string.settings_song_change_banner, settings.songChangeBanner) {
                onSettingsChange(settings.copy(songChangeBanner = it))
            }

            SectionHeader(R.string.settings_section_pedal)
            SwitchRow(R.string.settings_volume_keys, settings.volumeKeysTurnPages) {
                onSettingsChange(settings.copy(volumeKeysTurnPages = it))
            }
            SwitchRow(R.string.settings_reverse_direction, settings.reversePedalDirection) {
                onSettingsChange(settings.copy(reversePedalDirection = it))
            }

            SectionHeader(R.string.settings_section_display)
            SwitchRow(R.string.settings_keep_screen_on, settings.keepScreenOn) {
                onSettingsChange(settings.copy(keepScreenOn = it))
            }
            SwitchRow(R.string.settings_remember_zoom, settings.rememberZoom) {
                onSettingsChange(settings.copy(rememberZoom = it))
            }
            ChoiceRow(
                label = R.string.settings_theme,
                options = listOf(
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark,
                ),
                selected = settings.themeMode,
                onSelect = { onSettingsChange(settings.copy(themeMode = it)) },
            )

            SectionHeader(R.string.settings_section_backup)

            val lastBackupLabel = if (settings.lastBackupAt <= 0L) {
                stringResource(R.string.settings_last_backup_never)
            } else {
                val days = ((System.currentTimeMillis() - settings.lastBackupAt) / (1000 * 60 * 60 * 24)).toInt()
                when (days) {
                    0 -> stringResource(R.string.settings_last_backup_today)
                    1 -> stringResource(R.string.settings_last_backup_yesterday)
                    else -> stringResource(R.string.settings_last_backup, days)
                }
            }

            // 1. Alles sichern
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup_full)) },
                supportingContent = {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.settings_backup_full_hint) + "\n" + lastBackupLabel)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onExportBackup) {
                            Text(stringResource(R.string.settings_backup_full))
                        }
                    }
                },
            )

            // 2. Sicherung einlesen
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_restore)) },
                supportingContent = {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.settings_restore_hint))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onRestoreBackup) {
                            Text(stringResource(R.string.settings_restore))
                        }
                    }
                },
            )

            // 3. Notizen anderer
            SectionHeader(R.string.settings_section_other_notes)
            SwitchRow(R.string.settings_note_author_dot, settings.noteAuthorDot) {
                onSettingsChange(settings.copy(noteAuthorDot = it))
            }
            SwitchRow(R.string.settings_note_author_colored_name, settings.noteAuthorColoredName) {
                onSettingsChange(settings.copy(noteAuthorColoredName = it))
            }
            SwitchRow(R.string.settings_note_author_number, settings.noteAuthorNumber) {
                onSettingsChange(settings.copy(noteAuthorNumber = it))
            }

            // Kategorie Hinweise
            SectionHeader(R.string.settings_section_hinweise)

            // 4. Schalter für 30 Tage Backup
            SwitchRow(R.string.settings_show_backup_reminder, settings.showBackupReminder) {
                onSettingsChange(settings.copy(showBackupReminder = it))
            }

            // 5. Schalter für das Urheberrecht
            SwitchRow(R.string.settings_show_copyright_warning, settings.showCopyrightWarning) {
                onSettingsChange(settings.copy(showCopyrightWarning = it))
            }

            // 6. Text zum Datenschutz "Datenschutz-Hinweis"
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_privacy_notice_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_privacy_notice_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            VersionFooter(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

/**
 * A setting with an on/off switch.
 *
 * The whole row toggles, not just the switch, because a large target is far easier to hit
 * on a music stand than the small switch thumb.
 */
@Composable
private fun SwitchRow(
    @StringRes label: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        trailingContent = {
            // The row handles clicks; a null callback keeps the switch from consuming them twice.
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
    )
}

/** A single-choice setting rendered as a segmented button row below its label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    @StringRes label: Int,
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                options.forEachIndexed { index, (value, text) ->
                    SegmentedButton(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(stringResource(text), maxLines = 1) },
                    )
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MeineNotenTheme {
        SettingsScreen(
            settings = AppSettings(),
            onSettingsChange = {},
            onMenuClick = {},
        )
    }
}
