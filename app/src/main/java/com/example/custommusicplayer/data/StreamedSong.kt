package com.example.custommusicplayer.data

import android.net.Uri

/**
 * Songs in a mix that aren't on the phone are streamed from YouTube Music. They're still a
 * Song, so the rest of the app can show them, but with an address like
 * "restart-stream://Kohn9PRanoY" that PlaybackService turns into the real audio link
 * right before playing (those links expire, so they can't be stored).
 */
const val STREAM_SCHEME = "restart-stream"

/** What Now Playing shows instead of an album. */
const val STREAMING_ALBUM = "Streaming"

fun streamedSong(videoId: String, title: String, artist: String?, durationMs: Long): Song =
    Song(
        id = streamedSongId(videoId),
        uri = Uri.parse("$STREAM_SCHEME://$videoId"),
        title = title,
        artist = artist ?: "YouTube Music",
        album = STREAMING_ALBUM,
        durationMs = durationMs,
        fileName = "",  // no file: never used as a playlist or stats key
        filePath = null,
        streamVideoId = videoId,
    )

/** Negative, so it never clashes with the phone's own song ids (which are positive). */
private fun streamedSongId(videoId: String): Long = -1L - (videoId.hashCode().toLong() and 0x7fffffffL)
