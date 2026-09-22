package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.model.Setlist
import de.workflow42.meinenoten.model.Song
import de.workflow42.meinenoten.model.SongSource

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
            Icon(Icons.Default.MoreVert, contentDescription = "Weitere Aktionen")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Bearbeiten") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Zu Setlist hinzufügen") },
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
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
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
        title = { Text("Lied löschen?") },
        text = {
            Column {
                Text("„${song.displayTitle}“ wird dauerhaft entfernt.")
                if (affectedSetlists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Das Lied wird auch aus diesen Setlisten entfernt:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    affectedSetlists.forEach { setlist ->
                        Text(
                            text = "• ${setlist.title}",
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
                Text("Löschen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
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
) {
    var editTitle by remember(song.id) { mutableStateOf(song.title) }
    var editArtist by remember(song.id) { mutableStateOf(song.artist) }
    var editVersion by remember(song.id) { mutableStateOf(song.version) }
    var editGenre by remember(song.id) { mutableStateOf(song.genre) }
    var editBpm by remember(song.id) { mutableStateOf(song.bpm.toString()) }
    var editTimeSignature by remember(song.id) { mutableStateOf(song.timeSignature) }
    var editTotalBars by remember(song.id) { mutableStateOf(song.totalBars.toString()) }
    var editNotes by remember(song.id) { mutableStateOf(song.notes) }
    var editLyrics by remember(song.id) { mutableStateOf(song.lyrics) }

    val genreSuggestions = remember(knownGenres) {
        knownGenres.asSequence().filter { it.isNotBlank() }.distinct().sorted().toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Metadata") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            ) {
                TextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editArtist,
                    onValueChange = { editArtist = it },
                    label = { Text("Artist") },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editVersion,
                    onValueChange = { editVersion = it },
                    label = { Text("Version") },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editGenre,
                    onValueChange = { editGenre = it },
                    label = { Text("Genre") },
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
                    label = { Text("BPM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editTimeSignature,
                    onValueChange = { editTimeSignature = it },
                    label = { Text("Time Signature") },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editTotalBars,
                    onValueChange = { editTotalBars = it },
                    label = { Text("Total Bars") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                )
                TextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text("Notes (e.g. Capo, Tuning)") },
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                    minLines = 2,
                )
                TextField(
                    value = editLyrics,
                    onValueChange = { editLyrics = it },
                    label = { Text("Liedtext / Akkorde") },
                    // Shown for every song: a score can still have its text typed
                    // alongside, and the detail screen can switch between the two.
                    supportingText = {
                        Text(
                            if (song.hasFile) {
                                "Über die drei Punkte im Lied umschaltbar."
                            } else {
                                "Wird als Liedtext angezeigt, solange keine Noten hinterlegt sind."
                            }
                        )
                    },
                    modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
                    minLines = 4,
                )

                if (onAttachFile != null) {
                    Text(
                        text = "Noten:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Text(
                        text = if (song.hasFile) {
                            when (song.sourceType) {
                                SongSource.MUSIC_XML -> "MusicXML-Datei hinterlegt"
                                else -> "PDF hinterlegt"
                            }
                        } else {
                            "Keine Datei hinterlegt."
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
                            Text(if (song.hasFile) "Ersetzen" else "Hinzufügen")
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
                                    text = "Entfernen",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Included in Setlists:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (songSetlists.isEmpty()) {
                    Text(
                        text = "Not in any setlist yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    songSetlists.forEach { setlist ->
                        Text(
                            text = "• ${setlist.title}" +
                                if (setlist.date.isNotBlank()) " (${setlist.date})" else "",
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
                            notes = editNotes,
                            lyrics = editLyrics,
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
