package com.example.custommusicplayer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room turns each `@Entity` class into a database table, and each property into a column.
 * You never write CREATE TABLE yourself.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    // autoGenerate: the database picks the id. Pass 0 when inserting a new playlist.
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    // Shown on the main menu. The default value is for playlists made before this column
    // existed (the migration needs it to fill them in).
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
)

/**
 * One song in one playlist. The same song can appear in many playlists (or twice in one).
 *
 * Songs are identified by their file name (e.g. "Song I am. [Kohn9PRanoY].m4a") instead of
 * MediaStore's id: Android may give a file a new id when it's moved or re-indexed, but the
 * name stays the same, so playlists survive that.
 */
@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,  // deleting a playlist deletes its entries too
        ),
    ],
    indices = [Index("playlistId")],  // makes "all songs of playlist X" lookups fast
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songFileName: String,
    val position: Int,  // sort order within the playlist
)

/** Not a table: the result shape of the "playlists with song counts" query in PlaylistDao. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val songCount: Int,
    val pinned: Boolean,
)
