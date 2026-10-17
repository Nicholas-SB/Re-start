package io.github.nicholas_sb.restart.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Finds the audio files on the device.
 *
 * We don't walk the file system ourselves. Android already keeps an index of every media
 * file (MediaStore), including the title/artist tags, so we just query it like a database.
 */
class MusicScanner(private val context: Context) {

    /**
     * Asks Android's media scanner to index new or changed files in [folders] (paths from the
     * storage root, e.g. "YMusic"), and waits until it's done.
     *
     * Files copied onto the phone aren't always indexed right away. Until they are, MediaStore
     * doesn't know they're music and has no title/artist for them, so querySongs() skips them.
     * The scanner skips files it has already seen, so this is quick when nothing changed.
     */
    suspend fun indexNewFiles(folders: List<String>) {
        val paths = folders.map { "${storageRoot()}/$it" }.toTypedArray()
        if (paths.isEmpty()) return

        // Give up after a while rather than leave the refresh spinner going forever.
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            // suspendCancellableCoroutine turns a callback-style API into a suspend function:
            // the coroutine pauses here until we call continuation.resume().
            suspendCancellableCoroutine { continuation ->
                val foldersLeft = AtomicInteger(paths.size)  // thread-safe counter
                MediaScannerConnection.scanFile(context, paths, null) { _, _ ->
                    // Called once per folder, on a background thread.
                    if (foldersLeft.decrementAndGet() == 0) continuation.resume(Unit)
                }
            }
        }
    }

    // `suspend` marks a function that can pause without blocking a thread (a coroutine).
    // withContext(Dispatchers.IO) runs the block on a background thread meant for disk work,
    // so the UI never freezes while we query.
    @Suppress("DEPRECATION")  // MediaStore.Audio.Media.DATA
    suspend fun querySongs(): List<Song> = withContext(Dispatchers.IO) {
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
            MediaStore.Audio.Media.DATA,  // file path (deprecated, but still the only way to get it)
            MediaStore.Audio.Media.DATE_ADDED,  // seconds since 1970
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
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val fileName = cursor.getString(fileNameColumn) ?: "Unknown"
                val filePath: String? = cursor.getString(pathColumn)
                // "/storage/emulated/0/YMusic/Song.m4a" -> "YMusic"
                val relativeFolder = filePath?.removePrefix(storageRoot() + "/")?.substringBeforeLast('/', "").orEmpty()
                // Without an album tag, Android uses the folder name (e.g. "Download") as the album.
                val folderName = filePath?.substringBeforeLast('/')?.substringAfterLast('/')
                val album = cursor.getString(albumColumn)?.takeIf { it != folderName }

                songs += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = cursor.getString(titleColumn).orIfUnknown(fileName.substringBeforeLast('.')),
                    artist = cursor.getString(artistColumn).orIfUnknown("Unknown artist"),
                    album = album.orIfUnknown(UNKNOWN_ALBUM),
                    durationMs = cursor.getLong(durationColumn),
                    fileName = fileName,
                    filePath = filePath,
                    dateAddedMs = cursor.getLong(dateAddedColumn) * 1000,
                    folder = relativeFolder,
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

    @Suppress("DEPRECATION")  // still the simplest way to get the shared storage's path
    private fun storageRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    companion object {
        const val UNKNOWN_ALBUM = "Unknown album"

        /** The library folder until you pick another one in Settings. */
        const val DEFAULT_FOLDER = "Music"

        /**
         * Where the downloader saves songs. Always part of the library, whatever folder is
         * picked. (Same value as in the downloader module; the player doesn't depend on it.)
         */
        const val DOWNLOADS_FOLDER = "Music/Re-start"

        /** Is a song in [folder] part of a library that shows [chosen] (including subfolders)? */
        fun isInFolder(folder: String, chosen: String): Boolean =
            folder == chosen || folder.startsWith("$chosen/")

        /** The library: the chosen music folder, plus the downloads folder. */
        fun isInLibrary(song: Song, chosenFolder: String): Boolean =
            isInFolder(song.folder, chosenFolder) || isInFolder(song.folder, DOWNLOADS_FOLDER)

        /** The music folder picked in Settings (saved, so it survives closing the app). */
        fun chosenFolder(context: Context): String =
            prefs(context).getString(KEY_FOLDER, null) ?: DEFAULT_FOLDER

        fun saveChosenFolder(context: Context, folder: String) {
            prefs(context).edit().putString(KEY_FOLDER, folder).apply()
        }

        private fun prefs(context: Context) = context.getSharedPreferences("library", Context.MODE_PRIVATE)
        private const val KEY_FOLDER = "music_folder"

        private const val SCAN_TIMEOUT_MS = 30_000L
    }
}
