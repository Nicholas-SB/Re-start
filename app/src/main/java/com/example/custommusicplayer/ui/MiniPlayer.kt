package com.example.custommusicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.R
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.Song

/** The bar at the bottom showing what's playing. Tap it to open the Now Playing screen. */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    artLoader: AlbumArtLoader,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(onClick = onClick, tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SongArt(song, artLoader, Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next", modifier = Modifier.size(28.dp))
            }
        }
    }
}
