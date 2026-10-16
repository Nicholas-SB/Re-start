package com.example.custommusicplayer.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's SQLite database. Room generates the real implementation (MusicDatabase_Impl).
 *
 * `version` must go up whenever the tables change, together with a migration that tells Room
 * how to update the existing database on the phone (so your playlists aren't lost).
 *
 * Version history:
 *  1. playlists
 *  2. + listening stats (song_stats, play_events)
 *
 * Adding new tables is simple enough that Room can write the migration itself
 * (AutoMigration), by comparing the saved schema files in app/schemas/.
 */
@Database(
    entities = [PlaylistEntity::class, PlaylistSongEntity::class, SongStatsEntity::class, PlayEventEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun statsDao(): StatsDao

    companion object {
        // @Volatile: writes to this field are immediately visible to other threads.
        @Volatile
        private var instance: MusicDatabase? = null

        /** The one shared database instance (opening a database is expensive). */
        fun getInstance(context: Context): MusicDatabase =
            instance ?: synchronized(this) {
                // Check again inside the lock, in case another thread created it meanwhile.
                instance ?: Room.databaseBuilder(context.applicationContext, MusicDatabase::class.java, "music.db")
                    .build()
                    .also { instance = it }
            }
    }
}
