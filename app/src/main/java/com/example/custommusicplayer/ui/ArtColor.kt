package com.example.custommusicplayer.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.palette.graphics.Palette
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.ui.theme.SurfaceMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A background color taken from the song's cover art, like YouTube Music's Now Playing screen.
 * It fades smoothly to the new color when the song changes.
 */
@Composable
fun rememberArtColor(song: Song, artLoader: AlbumArtLoader): Color {
    val extracted by produceState<Color?>(initialValue = null, song.id) {
        val bitmap = artLoader.load(song, AlbumArtLoader.LARGE_SIZE_PX)
        // Analysing pixels is CPU work: Dispatchers.Default is the thread pool meant for that.
        value = bitmap?.let { withContext(Dispatchers.Default) { colorFrom(it) } }
    }
    val color by animateColorAsState(
        targetValue = extracted ?: SurfaceMid,  // no cover art: plain dark grey
        animationSpec = tween(durationMillis = 600),
        label = "art color",
    )
    return color
}

private fun colorFrom(bitmap: Bitmap): Color {
    // Palette can't read pixels from "hardware" bitmaps (stored on the GPU), so copy those first.
    val readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val palette = Palette.from(readable).generate()
    // A "swatch" is one of the main colors Palette found. Prefer rich, darker ones.
    val swatch = palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.dominantSwatch ?: return SurfaceMid
    // Blend 35% towards black, so white text stays readable on bright covers.
    return lerp(Color(swatch.rgb), Color.Black, 0.35f)
}
