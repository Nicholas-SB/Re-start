package io.github.nicholas_sb.restart.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nicholas_sb.restart.R
import io.github.nicholas_sb.restart.data.AlbumArtLoader
import io.github.nicholas_sb.restart.data.Song

/** A song's cover art, or a music-note placeholder if it has none. */
@Composable
fun SongArt(
    song: Song,
    artLoader: AlbumArtLoader,
    modifier: Modifier = Modifier,
    sizePx: Int = AlbumArtLoader.SMALL_SIZE_PX,
    cornerRadius: Dp = 4.dp,
) {
    // produceState runs the block in a coroutine and turns its result into UI state.
    // Keyed on song.id: if this slot is reused for another song, it loads again.
    // It starts with the cover if that's already loaded, so it's there from the first frame
    // (instead of the placeholder for a frame, then everything drawn again with the cover).
    val art by produceState(initialValue = artLoader.cached(song, sizePx), song.id, sizePx) {
        value = artLoader.load(song, sizePx)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // `art?.let { }` runs only when art isn't null; `?:` handles the null case.
        art?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Icon(
            painter = painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.5f),
        )
    }
}
