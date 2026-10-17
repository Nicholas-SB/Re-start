package io.github.nicholas_sb.restart.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.net.Uri
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.data.streamedSong

private const val EXTRA_FILE_NAME = "fileName"
private const val EXTRA_DURATION_MS = "durationMs"
private const val EXTRA_VIDEO_ID = "videoId"

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
                // Streamed songs have no file to take the cover from; the notification loads this.
                .setArtworkUri(streamVideoId?.let { Uri.parse("https://i.ytimg.com/vi/$it/hqdefault.jpg") })
                .setExtras(
                    Bundle().apply {
                        // No file name for streamed songs: they aren't counted in the stats.
                        if (streamVideoId == null) putString(EXTRA_FILE_NAME, fileName) else putString(EXTRA_VIDEO_ID, streamVideoId)
                        putLong(EXTRA_DURATION_MS, durationMs)
                    }
                )
                .build()
        )
        .build()

/** The song's file name (the key for stats), read back from the extras. */
val MediaItem.songFileName: String?
    get() = mediaMetadata.extras?.getString(EXTRA_FILE_NAME)

/** For a streamed song: the Song again (it isn't in the library, so it can't be looked up). */
fun MediaItem.toStreamedSong(): Song? {
    val videoId = mediaMetadata.extras?.getString(EXTRA_VIDEO_ID) ?: return null
    return streamedSong(
        videoId = videoId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString(),
        durationMs = songDurationMs,
    )
}

val MediaItem.songDurationMs: Long
    get() = mediaMetadata.extras?.getLong(EXTRA_DURATION_MS) ?: 0L
