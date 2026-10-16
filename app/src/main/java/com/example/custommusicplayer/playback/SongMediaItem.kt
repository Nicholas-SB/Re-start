package com.example.custommusicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.custommusicplayer.data.Song

/**
 * Converts our Song into Media3's MediaItem.
 *
 * The mediaId lets us find the Song again later (e.g. when the app reopens while music
 * is already playing). The metadata is what the notification and lock screen show;
 * ExoPlayer fills in the cover art from the file itself.
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
                .build()
        )
        .build()
