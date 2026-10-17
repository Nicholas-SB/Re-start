package io.github.nicholas_sb.restart.ui.device

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.animation.Crossfade
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
    positionMs: StateFlow<Long>,  // collected only where it's shown, so ticking redraws just that
    artLoader: AlbumArtLoader,
    lyricsLoader: LyricsLoader,
    showLyrics: Boolean,
    volumeBar: VolumeBarState,
    coverStyle: CoverStyle,
    discAngle: () -> Float,  // from rememberDiscAngle, kept by the whole app
    nextSong: Song?,  // the song after this one, to load its cover ahead of time
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
    // The last cover loaded is kept until the next one is ready, so the background can fade
    // from one to the other instead of going blank in between.
    var backdrop by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(song.id) { backdrop = artLoader.loadBackdrop(song) }

    // The next song's cover, loaded ahead of time: its disc then slides in with the picture
    // already there, instead of it popping in halfway through the slide.
    LaunchedEffect(nextSong?.id) {
        nextSong?.let { upcoming ->
            artLoader.load(upcoming, AlbumArtLoader.LARGE_SIZE_PX)
            artLoader.loadBackdrop(upcoming)
        }
    }
    val lyricsFound = showLyrics && lyricsState is LyricsState.Found

    Box(modifier = modifier.fillMaxSize()) {
        // A soft wash of the cover's colours: a tiny pre-blurred copy (see loadBackdrop), faded.
        if (!lyricsFound) {
            // Crossfade: the new song's background fades in while the old one fades out.
            Crossfade(targetState = backdrop, animationSpec = motionSpec(BACKDROP_FADE_MS), modifier = Modifier.matchParentSize(), label = "backdrop") { art ->
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,  // stretches the tiny picture smoothly
                        modifier = Modifier.fillMaxSize().alpha(0.35f),
                    )
                }
            }
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
                val position by positionMs.collectAsStateWithLifecycle()
                LyricsPanel(
                    state = lyricsState,
                    positionMs = position,
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                SongInfo(
                    song = song,
                    queuePosition = state.queuePosition,
                    queueSize = state.queueSize,
                    artLoader = artLoader,
                    coverStyle = coverStyle,
                    discAngle = discAngle,
                    note = if (showLyrics && lyricsState == LyricsState.NotFound) "No lyrics for this song" else null,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))
            ProgressOrVolume(song, positionMs, volumeBar)
        }
    }
}

/** A song on Now Playing and where it is in the play order (1 = first), to know which way to slide. */
private data class ShownSong(val song: Song, val position: Int)

/**
 * The cover as the star of the page (a spinning disc, or the plain square), with the song
 * centered underneath.
 *
 * When the song changes, it works like a record changer: going forward (the song ended, or ⏭),
 * the new disc slides in from the right while the old one slides out to the left; going back
 * (⏮), the other way round. The discs disappear at the edge of the screen's frame.
 */
@Composable
private fun SongInfo(
    song: Song,
    queuePosition: Int,
    queueSize: Int,
    artLoader: AlbumArtLoader,
    coverStyle: CoverStyle,
    discAngle: () -> Float,  // shared by the old and new song's disc, so both keep turning
    note: String?,
    modifier: Modifier = Modifier,
) {
    val slide = motionSpec<IntOffset>(SONG_SLIDE_MS)
    AnimatedContent(
        targetState = ShownSong(song, queuePosition),
        contentKey = { it.song.id },
        transitionSpec = {
            val from = initialState.position
            val to = targetState.position
            // Forward, unless we moved back in the queue. Repeat-all going from the last song
            // to the first counts as forward too.
            val wrappedAround = from == queueSize && to == 1
            val direction = if (to >= from || wrappedAround) 1 else -1
            // A bit more than one width: this area sits inside the screen's padding, so sliding
            // exactly its width left a sliver of the old disc (and its shadow) in view.
            val distance = { width: Int -> width + width / 4 }
            (slideInHorizontally(slide) { width -> direction * distance(width) } togetherWith
                slideOutHorizontally(slide) { width -> -direction * distance(width) })
                // Don't cut the discs off at this area's edge: they slide on until the
                // screen's frame, which clips them.
                .using(SizeTransform(clip = false))
        },
        // One description for the whole area. Accessibility services (TalkBack, but also
        // password managers' autofill) otherwise get told about every text moving, on every
        // frame of the slide, which kept the main thread busy and made the slide stutter.
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "${song.title}, ${song.artist}"
        },
        label = "song",
    ) { shown ->
        SongDetails(shown.song, artLoader, coverStyle, discAngle, note)
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

/** How long the blurred background takes to fade to the next song's. */
private const val BACKDROP_FADE_MS = 700

/** How long a new song's disc takes to slide in. */
private const val SONG_SLIDE_MS = 450

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

/**
 * The bottom of Now Playing: the song's progress, or the volume while the wheel is turning.
 * It's its own small composable on purpose: the position (5 times a second) and the volume
 * (every wheel step) are read only here, so only this bar redraws, not the disc above it.
 */
@Composable
private fun ProgressOrVolume(song: Song, positionMs: StateFlow<Long>, volumeBar: VolumeBarState) {
    // A new change restarts this, so the volume bar stays while you keep turning.
    LaunchedEffect(volumeBar.changedAt) {
        if (volumeBar.fraction == null) return@LaunchedEffect
        delay(1_500)
        volumeBar.hide()
    }
    val volume = volumeBar.fraction
    if (volume != null) {
        VolumeBar(volume)
    } else {
        val position by positionMs.collectAsStateWithLifecycle()
        SongProgress(positionMs = position, durationMs = song.durationMs)
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
    // Glide to the new level instead of jumping a whole step.
    val shown by animateFloatAsState(fraction, motionSpec(150), label = "volume")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 21.dp)) {
        Icon(painterResource(R.drawable.ic_volume_down), contentDescription = null, tint = DeviceColors.Text, modifier = Modifier.size(18.dp))
        DeviceBar(fraction = shown, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
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
 *
 * It keeps its own idea of the level while you turn: Android applies a volume change a moment
 * later, so reading it back right away could still give the old level (the bar jumped back and
 * forth). The change itself is sent from a background thread, so the screen never waits on it.
 */
class VolumeControl(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val background = Executors.newSingleThreadExecutor()  // changes go out in order
    private var level: Int? = null  // the level we set, while the volume bar is showing

    /** Moves the volume by [steps] (+ louder, - quieter) and returns the new level, 0..1. */
    fun change(steps: Int): Float {
        val current = level ?: audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = (current + steps).coerceIn(0, max)
        level = target
        background.execute { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
        return target.toFloat() / max
    }

    /** The bar went away: next time, start from the phone's real volume (the buttons may have changed it). */
    fun forget() {
        level = null
    }
}

/** The volume bar on Now Playing: its level while it shows (null = hidden), and when it last changed. */
class VolumeBarState(private val control: VolumeControl) {
    var fraction by mutableStateOf<Float?>(null)
        private set
    var changedAt by mutableLongStateOf(0L)
        private set

    /** A turn of the wheel: change the volume and show it. */
    fun turn(steps: Int) {
        fraction = control.change(steps)
        changedAt = SystemClock.uptimeMillis()
    }

    fun hide() {
        fraction = null
        control.forget()
    }
}
