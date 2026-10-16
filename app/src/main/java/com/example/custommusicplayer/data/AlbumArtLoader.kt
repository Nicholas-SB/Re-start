package com.example.custommusicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    /** Older Androids: read the picture bytes out of the file and shrink them ourselves. */
    private fun loadEmbeddedPicture(uri: Uri, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null

            // First read only the image size, then decode at roughly sizePx to save memory.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= sizePx) sampleSize *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            retriever.release()
        }
    }

    // `companion object` holds what Java would make `static`.
    companion object {
        const val SMALL_SIZE_PX = 256
        const val LARGE_SIZE_PX = 1024
    }
}
