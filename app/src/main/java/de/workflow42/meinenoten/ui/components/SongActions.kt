package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.data.AppSettings
import de.workflow42.meinenoten.model.otherNotes
import de.workflow42.meinenoten.model.ownNote
import de.workflow42.meinenoten.model.withOwnNote
import de.workflow42.meinenoten.model.ScoreDarkMode
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource
import de.workflow42.meinenoten.ui.util.formatSetlistDate

/**
 * The song actions that are offered in more than one place.
 *
 * Kept as one shared overflow menu plus its dialogs so that the song list and the song
 * detail screen cannot drift apart: a musician who learns "the three dots hold edit, add
 * and delete" should find the same three entries everywhere.
 *
 * Attaching or replacing a score is *not* here – it lives inside [EditSongDialog], so
 * that actions rewriting existing content sit behind "edit" rather than one tap away
 * from a row that is otherwise tapped to play.
 */
@Composable
fun SongOverflowMenu(
    onEdit: () -> Unit,
    onAddToSetlist: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(value = false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_add_to_setlist)) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onAddToSetlist()
                },
            )
            // Destructive action last and visually separated, so it is never the entry
            // the thumb lands on by reflex.
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * Confirmation for deleting a song.
 *
 * Deleting is the one song action that cannot be undone – the file goes with it – so it
 * keeps a dialog instead of the snackbar-undo used for removing a song from a setlist.
 * The affected setlists are listed because losing a song silently from a prepared
 * programme is the expensive part of the mistake.
 */
@Composable
fun DeleteSongDialog(
    song: Song,
    affectedSetlists: List<Setlist>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.dialog_delete_song_title)) },
        text = {
            Column {
                Text(stringResource(R.string.msg_delete_song_confirm, song.displayTitle))
                if (affectedSetlists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.msg_delete_song_setlists),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    affectedSetlists.forEach { setlist ->
                        Text(
                            text = stringResource(R.string.msg_bullet, setlist.title),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Metadata editor for a song, including its score file.
 *
 * The draft values live here rather than in the calling screen. They are keyed on
 * [Song.id] rather than on the whole [song]: attaching or removing a score updates the
 * stored song while this dialog is open, and keying on the instance would reset every
 * field the user had already typed.
 *
 * Attaching, replacing and removing the score sit here because they rewrite existing
 * content. Deleting the song is *not* offered here – it sits in the overflow menu next
 * to the entry that opens this dialog, so the destructive action does not hide one level
 * deep behind "edit".
 */
@Composable
fun EditSongDialog(
    song: Song,
    songSetlists: List<Setlist>,
    knownGenres: List<String>,
    onSave: (Song) -> Unit,
    onDismiss: () -> Unit,
    /** Opens the file picker to attach or replace the score. Null hides both entries. */
    onAttachFile: (() -> Unit)? = null,
    /** Drops the score file, leaving a text-only song. */
    onRemoveFile: (() -> Unit)? = null,
    /** Own identity and the *Notizen anderer* switches. */
    settings: AppSettings = AppSettings(),
    /** Author numbers across the library, see [authorNumbers]. */
    authorNumbers: Map<String, Int> = emptyMap(),
) {
    var editTitle by remember(song.id) { mutableStateOf(song.title) }
    var editArtist by remember(song.id) { mutableStateOf(song.artist) }
    var editVersion by remember(song.id) { mutableStateOf(song.version) }
    var editGenre by remember(song.id) { mutableStateOf(song.genre) }
    var editBpm by remember(song.id) { mutableStateOf(song.bpm.toString()) }
    var editTimeSignature by remember(song.id) { mutableStateOf(song.timeSignature) }
    var editTotalBars by remember(song.id) { mutableStateOf(song.totalBars.toString()) }
    var editNotes by remember(song.id) {
        mutableStateOf(song.notes.ownNote(settings.userId)?.text.orEmpty())
    }
    // Other people's notes can be deleted here, but not edited.
    var otherNotes by remember(song.id) { mutableStateOf(song.notes.otherNotes(settings.userId)) }
    var editLyrics by remember(song.id) { mutableStateOf(song.lyrics) }
    var editPageViews by remember(song.id) { mutableStateOf(song.pageViews) }
    var editDarkMode by remember(song.id) { mutableStateOf(song.scoreDarkMode) }

    val genreSuggestions = remember(knownGenres) {
        knownGenres.asSequence().filter { it.isNotBlank() }.distinct().sorted().toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Shrinks the dialog to the space left by the keyboard instead of letting it
        // cover the lower fields and the save button.
        modifier = Modifier.imePadding().padding(horizontal = 16.dp, vertical = 24.dp),
        properties = InputDialogProperties,
        title = { Text(stringResource(R.string.dialog_edit_song_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            ) {
                TextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text(stringResource(R.string.label_title)) },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editArtist,
                    onValueChange = { editArtist = it },
                    label = { Text(stringResource(R.string.label_artist)) },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editVersion,
                    onValueChange = { editVersion = it },
                    label = { Text(stringResource(R.string.label_version)) },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editGenre,
                    onValueChange = { editGenre = it },
                    label = { Text(stringResource(R.string.label_genre)) },
                    singleLine = true,
                    modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth(),
                )
                GenreChips(
                    suggestions = genreSuggestions,
                    selected = editGenre,
                    onSelect = { editGenre = it },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TextField(
                    value = editBpm,
                    onValueChange = { editBpm = it },
                    label = { Text(stringResource(R.string.label_bpm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editTimeSignature,
                    onValueChange = { editTimeSignature = it },
                    label = { Text(stringResource(R.string.label_time_signature)) },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editTotalBars,
                    onValueChange = { editTotalBars = it },
                    label = { Text(stringResource(R.string.label_total_bars)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text(stringResource(R.string.label_own_note)) },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                    minLines = 2,
                )
                if (otherNotes.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.label_other_notes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                    otherNotes.forEach { note ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                                NoteAuthorLabel(
                                    note = note,
                                    settings = settings,
                                    number = authorNumbers[note.authorId],
                                )
                                Text(text = note.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            val authorName = note.authorName.ifBlank {
                                stringResource(R.string.note_author_unknown)
                            }
                            IconButton(onClick = { otherNotes = otherNotes - note }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete_note, authorName),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TextField(
                    value = editLyrics,
                    onValueChange = { editLyrics = it },
                    label = { Text(stringResource(R.string.label_lyrics)) },
                    // Shown for every song: a score can still have its text typed
                    // alongside, and the detail screen can switch between the two.
                    supportingText = {
                        Text(
                            if (song.hasFile) {
                                stringResource(R.string.msg_lyrics_toggle_hint)
                            } else {
                                stringResource(R.string.msg_lyrics_fallback_hint)
                            }
                        )
                    },
                    modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
                    minLines = 4,
                )

                if (onAttachFile != null) {
                    Text(
                        text = stringResource(R.string.label_score),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Text(
                        text = if (song.hasFile) {
                            when (song.sourceType) {
                                SongSource.MUSIC_XML -> stringResource(R.string.msg_file_musicxml)
                                else -> stringResource(R.string.msg_file_pdf)
                            }
                        } else {
                            stringResource(R.string.msg_file_none)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp),
                    ) {
                        TextButton(onClick = onAttachFile) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text(
                                stringResource(
                                    if (song.hasFile) R.string.action_replace else R.string.action_add
                                )
                            )
                        }
                        if (song.hasFile && (onRemoveFile != null)) {
                            TextButton(onClick = onRemoveFile) {
                                Icon(
                                    imageVector = Icons.Default.LinkOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text(
                                    text = stringResource(R.string.action_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (song.sourceType == SongSource.PDF && editPageViews.isNotEmpty()) {
                            TextButton(onClick = { editPageViews = emptyMap() }) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text(text = stringResource(R.string.action_reset_zoom))
                            }
                        }
                    }

                    if (song.hasFile) {
                        ScoreDarkModeChoice(
                            selected = editDarkMode,
                            onSelect = { editDarkMode = it },
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.msg_included_in_setlists),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (songSetlists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_not_in_any_setlist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    songSetlists.forEach { setlist ->
                        Text(
                            text = if (setlist.date.isNotBlank()) {
                                stringResource(
                                    R.string.msg_bullet_with_date,
                                    setlist.title,
                                    formatSetlistDate(setlist.date),
                                )
                            } else {
                                stringResource(R.string.msg_bullet, setlist.title)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        song.copy(
                            title = editTitle,
                            artist = editArtist,
                            version = editVersion,
                            // Trimmed, because a stray space would create a second
                            // category that looks identical in the list.
                            genre = editGenre.trim(),
                            bpm = editBpm.toIntOrNull() ?: song.bpm,
                            timeSignature = editTimeSignature,
                            totalBars = editTotalBars.toIntOrNull() ?: song.totalBars,
                            // The original own note goes in too, so an unchanged text
                            // keeps its timestamp.
                            notes = (otherNotes + listOfNotNull(song.notes.ownNote(settings.userId)))
                                .withOwnNote(
                                    userId = settings.userId,
                                    userName = settings.displayName,
                                    text = editNotes,
                                    now = System.currentTimeMillis(),
                                ),
                            lyrics = editLyrics,
                            pageViews = editPageViews,
                            darkMode = editDarkMode.name,
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
