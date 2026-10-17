package io.github.nicholas_sb.restart.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import io.github.nicholas_sb.restart.data.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deletes a song from the phone, and takes it out of every playlist.
 *
 * Android only lets an app delete files it made itself (the songs Re-start downloaded). For any
 * other file, Android asks you first, in its own dialog: [delete] then returns what to show.
 */
class SongDeleter(private val context: Context) {

    private val playlistDao = MusicDatabase.getInstance(context).playlistDao()

    /**
     * Tries to delete [song]'s file. Returns null if it's gone, or an IntentSender for
     * Android's "Allow Re-start to delete this?" dialog; after you allow it, Android has
     * deleted the file and only [cleanUp] is left to do.
     */
    suspend fun delete(song: Song): IntentSender? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(song.uri, null, null)
            null
        } catch (e: SecurityException) {
            when {
                // Android 11+: ask for this one file.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri)).intentSender
                // Android 10: the error itself carries the question to ask.
                e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                else -> throw e
            }
        }
    }

    /** After the file is gone: its lyrics files, and its place in every playlist. Stats stay. */
    suspend fun cleanUp(song: Song) = withContext(Dispatchers.IO) {
        song.filePath?.let { path ->
            val lyrics = File(path).let { File(it.parentFile, "${it.nameWithoutExtension}.lrc") }
            lyrics.delete()  // the .lrc next to the song (if Android lets us; ignored otherwise)
            File(File(context.filesDir, "lyrics"), lyrics.name).delete()  // our own copy, if any
        }
        playlistDao.removeSongEverywhere(song.fileName)
    }
}
