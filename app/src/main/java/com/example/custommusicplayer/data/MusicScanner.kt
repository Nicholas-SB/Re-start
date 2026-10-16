package com.example.custommusicplayer.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds the audio files on the device.
 *
 * We don't walk the file system ourselves. Android already keeps an index of every media
 * file (MediaStore), including the title/artist tags, so we just query it like a database.
 */
class MusicScanner(private val context: Context) {

    // `suspend` marks a function that can pause without blocking a thread (a coroutine).
    // withContext(Dispatchers.IO) runs the block on a background thread meant for disk work,
    // so the UI never freezes while we query.
    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        // The columns we want, like SELECT _id, title, ... in SQL.
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        // Skip ringtones, notification sounds, recordings, etc.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()

        // `?.use { }`: only runs if query() didn't return null, and closes the cursor
        // afterwards, like Java's try-with-resources.
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val fileNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val fileName = cursor.getString(fileNameColumn) ?: "Unknown"

                songs += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = cursor.getString(titleColumn).orIfUnknown(fileName.substringBeforeLast('.')),
                    artist = cursor.getString(artistColumn).orIfUnknown("Unknown artist"),
                    album = cursor.getString(albumColumn).orIfUnknown("Unknown album"),
                    durationMs = cursor.getLong(durationColumn),
                )
            }
        }

        songs  // the last expression of the block is its result
    }

    /**
     * An "extension function": it adds a method to String? without subclassing it.
     * MediaStore uses null or the text "<unknown>" when a tag is missing.
     */
    private fun String?.orIfUnknown(fallback: String): String =
        if (isNullOrBlank() || this == MediaStore.UNKNOWN_STRING) fallback else this
}
