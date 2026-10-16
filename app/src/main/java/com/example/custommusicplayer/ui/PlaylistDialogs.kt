package com.example.custommusicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.R
import com.example.custommusicplayer.data.db.PlaylistSummary

/** Asks for a playlist name. Used for both "New playlist" and "Rename". */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    // Put the cursor in the text field when the dialog opens.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Lets the user pick which playlist to add a song to, or make a new one. */
@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistSummary>,
    onPlaylistChosen: (playlistId: Long) -> Unit,
    onNewPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    DialogRow(text = "New playlist…", iconRes = R.drawable.ic_add, onClick = onNewPlaylist)
                }
                items(playlists, key = { it.id }) { playlist ->
                    DialogRow(text = playlist.name, iconRes = R.drawable.ic_playlist, onClick = { onPlaylistChosen(playlist.id) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DialogRow(text: String, iconRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(iconRes), contentDescription = null)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "Are you sure?" before deleting a playlist. */
@Composable
fun DeletePlaylistDialog(playlistName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist?") },
        text = { Text("\"$playlistName\" will be deleted. The songs themselves stay on your phone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
