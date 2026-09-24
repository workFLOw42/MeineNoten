package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.data.AppSettings
import de.workflow42.meinenoten.model.SongNote
import de.workflow42.meinenoten.ui.util.personColor

/**
 * Name of a note's author, distinguished as chosen under *Notizen anderer*: coloured dot,
 * name in the person's colour, and "· 2" for a name that occurs more than once.
 *
 * [number] comes from [de.workflow42.meinenoten.model.authorNumbers]; null means the name
 * is unique.
 */
@Composable
fun NoteAuthorLabel(
    note: SongNote,
    settings: AppSettings,
    number: Int?,
    modifier: Modifier = Modifier,
) {
    // Follows the actual background, so "Dark" on a light device picks the dark variant.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = personColor(note.authorId).forTheme(darkTheme)
    val name = note.authorName.trim().ifEmpty { stringResource(R.string.note_author_unknown) }
    val label = if (settings.noteAuthorNumber && number != null) {
        stringResource(R.string.note_author_numbered, name, number)
    } else {
        name
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (settings.noteAuthorDot) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(10.dp)
                    .background(color, CircleShape),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (settings.noteAuthorColoredName) color else LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
