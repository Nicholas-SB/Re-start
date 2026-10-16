package com.example.custommusicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.R
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
    onSongClick: (index: Int) -> Unit,
    onShuffleAll: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
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
                LibraryHeader(songCount = songs.size, onShuffleAll = onShuffleAll)
            }
            // `key` lets Compose keep track of rows when the list changes.
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    artLoader = artLoader,
                    onClick = { onSongClick(index) },
                    isCurrent = song.id == currentSongId,
                    menuActions = listOf(MenuAction("Add to playlist") { onAddToPlaylist(song) }),
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(songCount: Int, onShuffleAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Library · $songCount ${if (songCount == 1) "song" else "songs"}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onShuffleAll) {
            Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shuffle all")
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
