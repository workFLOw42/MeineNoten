package de.workflow42.meinenoten.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.ui.components.appTitle

/**
 * Content of the app's navigation drawer, which replaced the navigation rail.
 *
 * One menu for everything keeps the score view free of any permanent chrome besides its
 * status bar. The song section only appears while a song is open, and the running order
 * only while it was opened from a setlist, so the drawer never offers actions without
 * a target.
 */
@Composable
fun AppDrawerContent(
    songCount: Int,
    setlistCount: Int,
    selectedTopLevel: NavKey,
    onNavigate: (AppRoute) -> Unit,
    currentSong: Song?,
    showingLyrics: Boolean,
    onEditSong: () -> Unit,
    onAddToSetlist: () -> Unit,
    onToggleLyrics: () -> Unit,
    onDeleteSong: () -> Unit,
    runningOrder: List<Song>,
    onSongClick: (Song) -> Unit,
    /** Entered name; turns the heading into "‹Name›s Noten". */
    userName: String = "",
) {
    ModalDrawerSheet {
        LazyColumn {
            item {
                Text(
                    text = appTitle(userName),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                )
                DrawerItem(
                    label = stringResource(R.string.msg_nav_with_count, stringResource(R.string.nav_songs), songCount),
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    selected = selectedTopLevel == AppRoute.Songs,
                    onClick = { onNavigate(AppRoute.Songs) },
                )
                DrawerItem(
                    label = stringResource(R.string.msg_nav_with_count, stringResource(R.string.nav_setlists), setlistCount),
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    selected = selectedTopLevel == AppRoute.Setlists,
                    onClick = { onNavigate(AppRoute.Setlists) },
                )
                DrawerItem(
                    label = stringResource(R.string.settings_title),
                    icon = { Icon(Icons.Default.Settings, null) },
                    selected = selectedTopLevel == AppRoute.Settings,
                    onClick = { onNavigate(AppRoute.Settings) },
                )
            }

            if (currentSong != null) {
                item {
                    SectionDivider(stringResource(R.string.drawer_section_this_song))
                    DrawerItem(
                        label = stringResource(R.string.action_edit),
                        icon = { Icon(Icons.Default.Edit, null) },
                        onClick = onEditSong,
                    )
                    DrawerItem(
                        label = stringResource(R.string.action_add_to_setlist),
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        onClick = onAddToSetlist,
                    )
                    // Only meaningful when both representations exist.
                    if (currentSong.hasFile && currentSong.lyrics.isNotBlank()) {
                        DrawerItem(
                            label = stringResource(
                                if (showingLyrics) R.string.cd_show_score else R.string.cd_show_lyrics
                            ),
                            icon = {
                                Icon(
                                    if (showingLyrics) Icons.AutoMirrored.Filled.Article
                                    else Icons.AutoMirrored.Filled.Subject,
                                    null,
                                )
                            },
                            onClick = onToggleLyrics,
                        )
                    }
                    DrawerItem(
                        label = stringResource(R.string.action_delete),
                        icon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = onDeleteSong,
                    )
                }
            }

            if (runningOrder.size > 1) {
                item { SectionDivider(stringResource(R.string.label_setlist)) }
                itemsIndexed(runningOrder, key = { _, song -> song.id }) { index, song ->
                    DrawerItem(
                        label = stringResource(R.string.msg_drawer_song, index + 1, song.displayTitle),
                        selected = song.id == currentSong?.id,
                        onClick = { onSongClick(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = icon,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun SectionDivider(title: String) {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
    )
}
