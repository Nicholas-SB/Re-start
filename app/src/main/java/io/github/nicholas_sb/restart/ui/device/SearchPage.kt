package io.github.nicholas_sb.restart.ui.device

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Search: a box to type in, with the results (a normal menu list) underneath. They update as
 * you type. The keyboard's search key closes the keyboard, and then the wheel scrolls the
 * results like any other list.
 */
@Composable
fun SearchPage(
    query: String,
    onQueryChange: (String) -> Unit,
    results: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Songs, artists, albums, playlists") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(8.dp).focusRequester(focusRequester),
        )
        Box(modifier = Modifier.weight(1f)) { results() }
    }

    // A new search: put the cursor in the box (which opens the keyboard). Coming back to an
    // earlier search keeps the keyboard closed, so the wheel works on its results right away.
    LaunchedEffect(Unit) {
        if (query.isEmpty()) focusRequester.requestFocus()
    }
}
