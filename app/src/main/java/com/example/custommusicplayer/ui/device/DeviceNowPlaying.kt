package com.example.custommusicplayer.ui.device

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.custommusicplayer.R
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.MusicScanner
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.lyrics.LyricsLoader
import com.example.custommusicplayer.ui.LyricsPanel
import com.example.custommusicplayer.ui.LyricsState
import com.example.custommusicplayer.ui.PlayerUiState
import com.example.custommusicplayer.ui.SongArt
import com.example.custommusicplayer.ui.formatDuration
import kotlin.math.abs

/**
 * The Now Playing page inside the player's screen: "2 of 10", cover and song info (or the
 * lyrics), and a progress bar that turns into a volume bar while you turn the wheel.
 */
@Composable
fun DeviceNowPlaying(
    song: Song?,
    state: PlayerUiState,
    positionMs: Long,
    artLoader: AlbumArtLoader,
    lyricsLoader: LyricsLoader,
    showLyrics: Boolean,
    volumeFraction: Float?,  // not null while the volume bar should show
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (song == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing playing", color = DeviceColors.TextSoft)
        }
        return
    }

    val lyricsState by produceState<LyricsState>(LyricsState.Loading, song.id, showLyrics) {
        if (!showLyrics) return@produceState
        value = LyricsState.Loading
        value = lyricsLoader.load(song)?.let { LyricsState.Found(it) } ?: LyricsState.NotFound
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "${state.queuePosition} of ${state.queueSize}",
            color = DeviceColors.TextSoft,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        val lyricsFound = showLyrics && lyricsState is LyricsState.Found
        if (lyricsFound) {
            // Small title line above the lyrics, so you still know what's playing.
            Text(
                "${song.title} · ${song.artist}",
                color = DeviceColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            LyricsPanel(
                state = lyricsState,
                positionMs = positionMs,
                onSeek = onSeek,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            SongInfo(
                song = song,
                artLoader = artLoader,
                note = if (showLyrics && lyricsState == LyricsState.NotFound) "No lyrics for this song" else null,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))
        if (volumeFraction != null) {
            VolumeBar(volumeFraction)
        } else {
            SongProgress(positionMs = positionMs, durationMs = song.durationMs)
        }
    }
}

/** Cover on the left, title / artist / album on the right, like the iPod's Now Playing. */
@Composable
private fun SongInfo(song: Song, artLoader: AlbumArtLoader, note: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SongArt(
            song = song,
            artLoader = artLoader,
            sizePx = AlbumArtLoader.LARGE_SIZE_PX,
            cornerRadius = 6.dp,
            modifier = Modifier.weight(1f).aspectRatio(1f),  // square
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = DeviceColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = DeviceColors.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (song.album != MusicScanner.UNKNOWN_ALBUM) {
                Text(song.album, color = DeviceColors.TextSoft, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (note != null) {
                Text(note, color = DeviceColors.TextSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Progress bar with elapsed time on the left and time remaining on the right. */
@Composable
private fun SongProgress(positionMs: Long, durationMs: Long) {
    val position = positionMs.coerceIn(0, durationMs)
    DeviceBar(fraction = if (durationMs > 0) position.toFloat() / durationMs else 0f)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(position), color = DeviceColors.Text, fontSize = 13.sp)
        Text("-" + formatDuration(durationMs - position), color = DeviceColors.Text, fontSize = 13.sp)
    }
}

@Composable
private fun VolumeBar(fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 21.dp)) {
        Icon(painterResource(R.drawable.ic_volume_down), contentDescription = null, tint = DeviceColors.Text, modifier = Modifier.size(18.dp))
        DeviceBar(fraction = fraction, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
        Icon(painterResource(R.drawable.ic_volume_up), contentDescription = "Volume", tint = DeviceColors.Text, modifier = Modifier.size(18.dp))
    }
}

/** A rounded bar filled with sage up to [fraction] (0..1). Used for progress and volume. */
@Composable
private fun DeviceBar(fraction: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(DeviceColors.Screen)
            .border(1.dp, DeviceColors.TextSoft, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Brush.verticalGradient(listOf(DeviceColors.Highlight, DeviceColors.HighlightDark))),
        )
    }
}

/**
 * The phone's media volume, changed by turning the wheel on Now Playing.
 * We draw our own volume bar, so Android's volume popup is not shown (flags = 0).
 */
class VolumeControl(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun change(steps: Int) {
        val direction = if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(abs(steps)) { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
    }

    /** Current volume, 0..1. */
    val fraction: Float
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
}
