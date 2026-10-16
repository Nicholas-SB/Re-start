package com.example.custommusicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.R
import com.example.custommusicplayer.data.db.PlaylistSummary

/** The "Playlists" tab: all playlists, plus a button to make a new one. */
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    onPlaylistClick: (playlistId: Long) -> Unit,
    onNewPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Playlists", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onNewPlaylist) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New playlist")
                }
            }
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    "No playlists yet. Make one here, or use ⋮ next to any song.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_playlist), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
