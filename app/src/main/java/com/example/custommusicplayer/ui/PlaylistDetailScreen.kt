package com.example.custommusicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.R
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.db.PlaylistSongEntity

/** One playlist: play/shuffle buttons, rename/delete, and its songs with move/remove actions. */
@Composable
fun PlaylistDetailScreen(
    detail: PlaylistDetail,
    songsByFileName: Map<String, Song>,
    currentSongId: Long?,
    artLoader: AlbumArtLoader,
    onBack: () -> Unit,
    onPlay: (songs: List<Song>, startIndex: Int) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemoveEntry: (PlaylistSongEntity) -> Unit,
    onMoveEntry: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pair each entry with its Song. If the file is gone from the phone, the song is null.
    val rows: List<Pair<PlaylistSongEntity, Song?>> = detail.entries.map { it to songsByFileName[it.songFileName] }
    val playableSongs = rows.mapNotNull { it.second }

    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
                Text(
                    detail.playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OverflowMenu(
                    listOf(
                        MenuAction("Rename") { showRenameDialog = true },
                        MenuAction("Delete playlist") { showDeleteDialog = true },
                    )
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { onPlay(playableSongs, 0) }, enabled = playableSongs.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play")
                }
                FilledTonalButton(onClick = { onShuffle(playableSongs) }, enabled = playableSongs.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
                Text(
                    "${rows.size} ${if (rows.size == 1) "song" else "songs"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    "This playlist is empty. Add songs with ⋮ in your library.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        itemsIndexed(rows, key = { _, row -> row.first.id }) { index, (entry, song) ->
            val actions = buildList {
                if (index > 0) add(MenuAction("Move up") { onMoveEntry(index, index - 1) })
                if (index < rows.lastIndex) add(MenuAction("Move down") { onMoveEntry(index, index + 1) })
                add(MenuAction("Remove from playlist") { onRemoveEntry(entry) })
            }
            if (song != null) {
                SongRow(
                    song = song,
                    artLoader = artLoader,
                    onClick = { onPlay(playableSongs, playableSongs.indexOf(song)) },
                    isCurrent = song.id == currentSongId,
                    menuActions = actions,
                )
            } else {
                MissingSongRow(fileName = entry.songFileName, menuActions = actions)
            }
        }
    }

    if (showRenameDialog) {
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Rename",
            initialName = detail.playlist.name,
            onConfirm = { name ->
                onRename(name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
    if (showDeleteDialog) {
        DeletePlaylistDialog(
            playlistName = detail.playlist.name,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/** A playlist entry whose file isn't on the phone anymore (deleted or renamed). */
@Composable
private fun MissingSongRow(fileName: String, menuActions: List<MenuAction>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Missing file: $fileName",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OverflowMenu(menuActions)
    }
}
