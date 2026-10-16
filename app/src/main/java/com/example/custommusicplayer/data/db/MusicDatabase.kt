package com.example.custommusicplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's SQLite database. Room generates the real implementation (MusicDatabase_Impl).
 *
 * `version` must go up whenever the tables change (e.g. adding stats in milestone 7),
 * together with a migration that tells Room how to update existing users' data.
 */
@Database(
    entities = [PlaylistEntity::class, PlaylistSongEntity::class],
    version = 1,
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

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
