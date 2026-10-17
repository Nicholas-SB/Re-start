package io.github.nicholas_sb.restart.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import io.github.nicholas_sb.restart.downloader.YoutubeMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads the cover art embedded in each song file, and keeps recent ones in memory
 * so scrolling back up the list doesn't reload them.
 */
class AlbumArtLoader(private val context: Context) {

    // Use up to 1/8 of the app's memory for cover art. Sizes are counted in kilobytes.
    // Keyed by "songId@size", since the same cover can be loaded small (list) and big (Now Playing).
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Returns the song's cover art about [sizePx] pixels wide, or null if the file has none. */
    suspend fun load(song: Song, sizePx: Int = SMALL_SIZE_PX): Bitmap? {
        val key = "${song.id}@$sizePx"
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            try {
                if (song.streamVideoId != null) {
                    loadFromYoutube(song.streamVideoId, sizePx)
                } else if (sizePx > SMALL_SIZE_PX) {
                    // The big cover (Now Playing): straight from the file at full quality.
                    // Android's thumbnails fit the whole picture into sizePx, so a 1280x720
                    // YouTube cover came out 1024x576, and its middle square was only 576 px.
                    loadEmbeddedPicture(song.uri, sizePx)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ can make (and cache) a thumbnail from the embedded art for us.
                    context.contentResolver.loadThumbnail(song.uri, Size(sizePx, sizePx), null)
                } else {
                    loadEmbeddedPicture(song.uri, sizePx)
                }
            } catch (e: Exception) {
                null  // no cover art, or the file couldn't be read
            }
        }

        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /**
     * Reads the picture bytes out of the file and shrinks them ourselves (also used on older
     * Androids, which can't make thumbnails for us).
     */
    private fun loadEmbeddedPicture(uri: Uri, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null

            // First read only the image size, then decode at roughly sizePx to save memory.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            // Shrink by halves only while the *shorter* side stays at least sizePx: covers are
            // shown cropped to a square, so that side is what ends up on screen.
            val shorterSide = minOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (shorterSide / (sampleSize * 2) >= sizePx) sampleSize *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            retriever.release()
        }
    }

    /**
     * A streamed song's cover: YouTube's picture for the video (the biggest that exists),
     * cut to the middle square. On YouTube Music songs that's the album cover.
     */
    private fun loadFromYoutube(videoId: String, sizePx: Int): Bitmap? {
        for (url in YoutubeMusic.coverUrls(videoId)) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                if (connection.responseCode != HttpURLConnection.HTTP_OK) continue  // that size doesn't exist
                val picture = connection.inputStream.use { BitmapFactory.decodeStream(it) } ?: continue
                val side = minOf(picture.width, picture.height)
                val square = Bitmap.createBitmap(picture, (picture.width - side) / 2, (picture.height - side) / 2, side, side)
                return Bitmap.createScaledBitmap(square, minOf(sizePx, side), minOf(sizePx, side), true)
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    // `companion object` holds what Java would make `static`.
    companion object {
        const val SMALL_SIZE_PX = 256
        const val LARGE_SIZE_PX = 1024
    }
}
