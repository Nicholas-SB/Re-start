package io.github.nicholas_sb.restart.ui.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nicholas_sb.restart.R
import io.github.nicholas_sb.restart.data.AlbumArtLoader
import io.github.nicholas_sb.restart.data.MusicScanner
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.data.lyrics.LyricsLoader
import io.github.nicholas_sb.restart.ui.LyricsPanel
import io.github.nicholas_sb.restart.ui.LyricsState
import io.github.nicholas_sb.restart.ui.PlayerUiState
import io.github.nicholas_sb.restart.ui.SongArt
import io.github.nicholas_sb.restart.ui.formatDuration
import kotlin.math.abs

/**
 * The Now Playing page inside the player's screen: a big cover with the song below it (or the
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
    coverStyle: CoverStyle,
    discAngle: () -> Float,  // from rememberDiscAngle, kept by the whole app
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

    // A soft wash of the cover's colors behind everything (blurred and faded).
    val backdrop by produceState<Bitmap?>(initialValue = null, song.id) {
        value = artLoader.load(song)
    }
    val lyricsFound = showLyrics && lyricsState is LyricsState.Found

    Box(modifier = modifier.fillMaxSize()) {
        // Modifier.blur only works on Android 12+; without it the picture would be sharp
        // and distracting, so older phones simply keep the plain cream screen.
        val art = backdrop
        if (art != null && !lyricsFound && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(40.dp).alpha(0.35f),
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
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
                    coverStyle = coverStyle,
                    discAngle = discAngle,
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
}

/**
 * The cover as the star of the page (a spinning disc, or the plain square), with the song
 * centered underneath. When the song changes, the new one fades in over the old one.
 */
@Composable
private fun SongInfo(
    song: Song,
    artLoader: AlbumArtLoader,
    coverStyle: CoverStyle,
    discAngle: () -> Float,  // shared by the old and new song's disc, so a new song doesn't make it jump
    note: String?,
    modifier: Modifier = Modifier,
) {
    val fade = motionSpec<Float>(300)
    AnimatedContent(
        targetState = song,
        contentKey = { it.id },
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        modifier = modifier.fillMaxWidth(),
        label = "song",
    ) { shownSong ->
        SongDetails(shownSong, artLoader, coverStyle, discAngle, note)
    }
}

@Composable
private fun SongDetails(song: Song, artLoader: AlbumArtLoader, coverStyle: CoverStyle, discAngle: () -> Float, note: String?) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // matchHeightConstraintsFirst: try "as tall as the space allows" first, so the
            // square never overflows; if that's too wide, it uses the width instead.
            val coverModifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true)
            when (coverStyle) {
                CoverStyle.DISC -> Disc(song, artLoader, discAngle, coverModifier)
                CoverStyle.SQUARE -> {
                    val shape = RoundedCornerShape(10.dp)
                    SongArt(
                        song = song,
                        artLoader = artLoader,
                        sizePx = AlbumArtLoader.LARGE_SIZE_PX,
                        cornerRadius = 10.dp,
                        modifier = coverModifier.shadow(elevation = 10.dp, shape = shape),
                    )
                }
            }
        }
        Text(
            song.title,
            color = DeviceColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // "Artist · Album", leaving out the album when there isn't one.
        val details = listOfNotNull(song.artist, song.album.takeIf { it != MusicScanner.UNKNOWN_ALBUM })
        Text(
            details.joinToString(" · "),
            color = DeviceColors.TextSoft,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (note != null) {
            Text(note, color = DeviceColors.TextSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** One full turn of the disc takes this long (a slow, relaxed pace). */
private const val DISC_TURN_MS = 16_000

/**
 * The disc's angle, which keeps turning while [spinning]. Pausing lets it coast to a stop,
 * and playing again speeds it back up, like a real turntable. Without [animate] it doesn't
 * move at all.
 *
 * RestartApp keeps this above all the pages, so the disc keeps turning while you're in other
 * menus and is where you'd expect when you come back to Now Playing.
 *
 * Returns a function rather than the number: whoever draws reads it only in the drawing
 * phase (graphicsLayer), so the 60 updates a second redraw the disc without recomposing.
 */
@Composable
fun rememberDiscAngle(spinning: Boolean, animate: Boolean): () -> Float {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(spinning, animate) {
        if (!animate) return@LaunchedEffect
        // The speed while spinning, in degrees per millisecond.
        val speed = 360f / DISC_TURN_MS
        if (spinning) {
            // Speed up over 0.6 s. Starting from rest and accelerating evenly to `speed`
            // covers half the distance full speed would: speed * 600 / 2.
            angle.animateTo(angle.value + speed * 300, tween(600, easing = FastOutLinearInEasing))
            while (true) {
                angle.animateTo(angle.value + 360f, tween(DISC_TURN_MS, easing = LinearEasing))
                angle.snapTo(angle.value % 360f)  // keep the number small; looks the same
            }
        } else {
            // Coast to a stop over 0.8 s (again half of what full speed would cover).
            angle.animateTo(angle.value + speed * 400, tween(800, easing = LinearOutSlowInEasing))
        }
    }
    return { angle.value }
}

/**
 * The cover as a record: round, with faint grooves, a still reflection of light, and a
 * spindle hole in the middle. Only the picture turns; the grooves are circles (they look the
 * same at any angle) and the reflection stays put, which is what makes it look like it spins.
 */
@Composable
private fun Disc(song: Song, artLoader: AlbumArtLoader, angle: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(elevation = 12.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(DeviceColors.Disc),
    ) {
        SongArt(
            song = song,
            artLoader = artLoader,
            sizePx = AlbumArtLoader.LARGE_SIZE_PX,
            cornerRadius = 0.dp,
            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = angle() },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            // Grooves: thin dark rings over the outer part of the picture.
            for (ring in 0 until 12) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.07f),
                    radius = radius * (0.42f + ring * 0.048f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            // Light catching the vinyl: two soft bright wedges that don't turn with the disc.
            drawCircle(
                Brush.sweepGradient(
                    0.00f to Color.Transparent,
                    0.10f to Color.White.copy(alpha = 0.16f),
                    0.20f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.62f to Color.White.copy(alpha = 0.10f),
                    0.72f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
                radius = radius,
            )
            // The center: a dark ring around the spindle hole, which shows the screen behind.
            drawCircle(DeviceColors.Disc, radius = radius * 0.12f)
            drawCircle(Color.White.copy(alpha = 0.12f), radius = radius * 0.12f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(DeviceColors.Screen, radius = radius * 0.035f)
            // The rim.
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
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
