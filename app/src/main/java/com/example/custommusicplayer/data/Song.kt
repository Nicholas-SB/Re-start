package com.example.custommusicplayer.data

import android.net.Uri

/**
 * One audio file on the device.
 *
 * A `data class` is like a Java class where the compiler writes the constructor, getters,
 * equals(), hashCode(), toString() and copy() for you. `val` = read-only (like `final`).
 */
data class Song(
    val id: Long,          // MediaStore's id for this file
    val uri: Uri,          // content:// address used to play the file and read its cover art
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)
