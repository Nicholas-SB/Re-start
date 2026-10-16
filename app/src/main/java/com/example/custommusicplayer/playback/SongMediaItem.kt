package com.example.custommusicplayer.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.custommusicplayer.data.Song

private const val EXTRA_FILE_NAME = "fileName"
private const val EXTRA_DURATION_MS = "durationMs"

/**
 * Converts our Song into Media3's MediaItem.
 *
 * The mediaId lets us find the Song again later (e.g. when the app reopens while music
 * is already playing). The metadata is what the notification and lock screen show;
 * ExoPlayer fills in the cover art from the file itself. The extras carry what the
 * listening stats in PlaybackService need.
 */
fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setExtras(
                    Bundle().apply {
                        putString(EXTRA_FILE_NAME, fileName)
                        putLong(EXTRA_DURATION_MS, durationMs)
                    }
                )
                .build()
        )
        .build()

/** The song's file name (the key for stats), read back from the extras. */
val MediaItem.songFileName: String?
    get() = mediaMetadata.extras?.getString(EXTRA_FILE_NAME)

val MediaItem.songDurationMs: Long
    get() = mediaMetadata.extras?.getLong(EXTRA_DURATION_MS) ?: 0L
