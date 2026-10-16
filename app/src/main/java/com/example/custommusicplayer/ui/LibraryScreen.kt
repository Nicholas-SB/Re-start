package com.example.custommusicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.Song

/**
 * The list of all songs. Pull down to rescan.
 *
 * This composable only *displays* data and reports clicks upward; it doesn't own any
 * state itself. That makes it easy to reuse and to preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    songs: List<Song>,
    isScanning: Boolean,
    currentSongId: Long?,
    artLoader: AlbumArtLoader,
    onRefresh: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (songs.isEmpty() && !isScanning) {
            EmptyLibraryMessage()
            return@PullToRefreshBox  // a "labeled return": leave only this lambda
        }

        // LazyColumn is like Android's RecyclerView: it only builds rows that are on screen.
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Library · ${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            // `key` lets Compose keep track of rows when the list changes.
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    artLoader = artLoader,
                    onClick = { onSongClick(song) },
                )
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    artLoader: AlbumArtLoader,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SongArt(song, artLoader, Modifier.size(52.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${song.artist} · ${formatDuration(song.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyLibraryMessage() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "No songs found.\nCopy some audio files to your phone, then pull down to rescan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 225000 ms -> "3:45" */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
