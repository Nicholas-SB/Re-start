package com.example.custommusicplayer.data.mixes

import android.content.Context
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.db.MusicDatabase
import com.example.custommusicplayer.data.db.SongStatsEntity
import kotlin.random.Random

/** Gathers what SongMix needs from the database (playlists, listening history) and builds a mix. */
class SongMixMaker(context: Context) {

    private val database = MusicDatabase.getInstance(context)

    /** A mix starting with [seed], made from [library]. */
    suspend fun make(seed: Song, library: List<Song>, stats: Map<String, SongStatsEntity>): List<Song> {
        val byFileName = library.associateBy { it.fileName }
        val fileNames = SongMix.build(
            seed = seed.fileName,
            artists = library.associate { it.fileName to it.artist },
            // A song listed once per shared playlist (or per nearby play): count them.
            samePlaylist = database.playlistDao().songsSharingAPlaylist(seed.fileName).groupingBy { it }.eachCount(),
            playedAround = database.statsDao()
                .songsPlayedAround(seed.fileName, SongMix.PLAYED_AROUND_WINDOW_MS)
                .groupingBy { it }.eachCount(),
            stats = stats,
            random = Random.Default,
        )
        return fileNames.mapNotNull { byFileName[it] }
    }
}
