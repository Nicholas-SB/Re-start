package com.example.custommusicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.R
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.lyrics.LyricsLoader

/**
 * Full-screen player: big cover (or lyrics), seek bar, all the controls, and the upcoming queue.
 * Like the other screens, it reports button presses upward instead of changing things itself.
 */
@Composable
fun NowPlayingScreen(
    song: Song,
    state: PlayerUiState,
    positionMs: Long,
    songsById: Map<Long, Song>,
    artLoader: AlbumArtLoader,
    lyricsLoader: LyricsLoader,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onQueueItemClick: (queueIndex: Int) -> Unit,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    // Load lyrics only while they're shown; reload when the song changes.
    val lyricsState by produceState<LyricsState>(LyricsState.Loading, song.id, showLyrics) {
        if (!showLyrics) return@produceState
        value = LyricsState.Loading
        value = lyricsLoader.load(song)?.let { LyricsState.Found(it) } ?: LyricsState.NotFound
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_expand_more), contentDescription = "Close", modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            painterResource(R.drawable.ic_lyrics),
                            contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                // Cover and lyrics take the same square spot, so the controls don't jump around.
                val squareModifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .aspectRatio(1f)
                if (showLyrics) {
                    LyricsPanel(
                        state = lyricsState,
                        positionMs = positionMs,
                        onSeek = onSeek,
                        modifier = squareModifier,
                    )
                } else {
                    SongArt(song = song, artLoader = artLoader, sizePx = AlbumArtLoader.LARGE_SIZE_PX, modifier = squareModifier)
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                SeekBar(positionMs = positionMs, durationMs = song.durationMs, onSeek = onSeek)
            }
            item {
                PlaybackControls(
                    state = state,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                )
            }
            item {
                Text(
                    text = if (state.upNext.isEmpty()) "Nothing up next" else "Up next",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            items(state.upNext, key = { it.queueIndex }) { entry ->
                // The queue only stores ids; skip any song that disappeared from the library.
                val queuedSong = songsById[entry.songId] ?: return@items
                SongRow(song = queuedSong, artLoader = artLoader, onClick = { onQueueItemClick(entry.queueIndex) })
            }
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    // While the user drags, show where their finger is instead of the real position.
    // Only seek when they let go, so we don't send dozens of seeks per second.
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val shownPositionMs = dragPositionMs ?: positionMs.coerceIn(0, durationMs).toFloat()

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Slider(
            value = shownPositionMs,
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
            onValueChange = { dragPositionMs = it },
            onValueChangeFinished = {
                dragPositionMs?.let { onSeek(it.toLong()) }
                dragPositionMs = null
            },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownPositionMs.toLong()), style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlaybackControls(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    // Highlight shuffle/repeat when they're on.
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                painterResource(R.drawable.ic_shuffle),
                contentDescription = if (state.shuffleEnabled) "Shuffle on" else "Shuffle off",
                tint = if (state.shuffleEnabled) activeColor else inactiveColor,
            )
        }
        IconButton(onClick = onSkipPrevious) {
            Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous", modifier = Modifier.size(36.dp))
        }
        FilledIconButton(onClick = onTogglePlayPause, modifier = Modifier.size(72.dp)) {
            Icon(
                painter = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = onSkipNext) {
            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next", modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = onCycleRepeat) {
            // `when` on an enum: the compiler checks that every case is covered.
            val (icon: Int, description: String, tint: Color) = when (state.repeatMode) {
                RepeatMode.OFF -> Triple(R.drawable.ic_repeat, "Repeat off", inactiveColor)
                RepeatMode.ALL -> Triple(R.drawable.ic_repeat, "Repeat all", activeColor)
                RepeatMode.ONE -> Triple(R.drawable.ic_repeat_one, "Repeat one", activeColor)
            }
            Icon(painterResource(icon), contentDescription = description, tint = tint)
        }
    }
}
