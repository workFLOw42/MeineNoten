package de.workflow42.meinenoten.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import de.workflow42.meinenoten.R

/**
 * Search field above the song list, filtering title, artist, version and genre.
 *
 * Filters as you type – with a personal library the result is immediate, and there is
 * nothing to be gained by making the reader press a button first.
 *
 * Deliberately not Material 3's `SearchBar`: that expands into a full-screen overlay with
 * its own suggestion list, covering the very list being filtered. Here the point is to
 * watch entries disappear as the query narrows.
 */
@Composable
fun SongSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.label_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            // Only while there is something to clear, so the field stays quiet at rest.
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.cd_clear_search),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The list is already filtered, so "Search" only has to get the keyboard out of
        // the way of the results.
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        // Outer spacing is the caller's business – this field sits in a filter bar that
        // already owns the screen's margins.
        modifier = modifier.fillMaxWidth(),
    )
}
