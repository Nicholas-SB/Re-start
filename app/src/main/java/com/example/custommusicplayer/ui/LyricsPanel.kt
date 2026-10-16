package com.example.custommusicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.data.lyrics.LoadedLyrics
import com.example.custommusicplayer.data.lyrics.LyricLine
import com.example.custommusicplayer.data.lyrics.Lyrics
import com.example.custommusicplayer.data.lyrics.LyricsSource
import com.example.custommusicplayer.data.lyrics.currentLineIndex

/** What the lyrics panel is showing. */
sealed interface LyricsState {
    // `data object`: a singleton with a readable toString(). Used for cases with no data.
    data object Loading : LyricsState
    data object NotFound : LyricsState
    data class Found(val loaded: LoadedLyrics) : LyricsState
}

/** Shows lyrics (synced or plain), a spinner, or a "no lyrics" message. */
@Composable
fun LyricsPanel(
    state: LyricsState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (state) {
            LyricsState.Loading -> CircularProgressIndicator()
            LyricsState.NotFound -> Text(
                "No lyrics found.\nAdd a .lrc file with the same name next to the song.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            is LyricsState.Found -> Column(modifier = Modifier.fillMaxSize()) {
                val lyricsModifier = Modifier.weight(1f).fillMaxWidth()
                when (val lyrics = state.loaded.lyrics) {
                    is Lyrics.Synced -> SyncedLyrics(lyrics.lines, positionMs, onSeek, lyricsModifier)
                    is Lyrics.Plain -> PlainLyrics(lyrics.text, lyricsModifier)
                }
                Text(
                    text = when (state.loaded.source) {
                        LyricsSource.LRC_FILE -> "From .lrc file"
                        LyricsSource.EMBEDDED -> "Embedded in song"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Synced lyrics: the current line is highlighted and scrolled to about a third of the way
 * down. Tap any line to jump there in the song.
 */
@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = lines.currentLineIndex(positionMs)
    val listState = rememberLazyListState()

    // Runs again each time the current line changes.
    LaunchedEffect(currentIndex) {
        // Don't fight the user if they're scrolling through the lyrics themselves.
        if (currentIndex < 0 || listState.isScrollInProgress) return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportSize.height
        // A negative offset leaves some space above the line, so earlier lines stay visible.
        listState.animateScrollToItem(currentIndex, scrollOffset = -viewportHeight / 3)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 48.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            Text(
                text = line.text.ifEmpty { "♪" },  // show a note during instrumental breaks
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.verticalScroll(rememberScrollState()),
    )
}
