package io.github.nicholas_sb.restart.data.discover

import android.content.Context
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.data.db.SongStatsEntity
import io.github.nicholas_sb.restart.data.mixes.RadioMixMaker
import io.github.nicholas_sb.restart.downloader.LibrarySong
import io.github.nicholas_sb.restart.downloader.PlaylistSong
import io.github.nicholas_sb.restart.downloader.YoutubeMusic
import io.github.nicholas_sb.restart.downloader.findInLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/** The day's For You list, and the day it was made for (days since 1970). */
data class ForYouList(val day: Long, val songs: List<PlaylistSong>)

/** Makes the For You list from YouTube Music, and keeps it in a file until the next day. */
class ForYouRepository(context: Context) {

    private val radioMixMaker = RadioMixMaker(context)
    private val youtube = YoutubeMusic(context)
    private val file = File(context.filesDir, "for_you.json")

    fun hasInternet() = radioMixMaker.hasInternet()

    /** The saved list, or null if there's none. */
    suspend fun load(): ForYouList? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(file.readText())
            val songs = json.getJSONArray("songs").let { array ->
                (0 until array.length()).map { index ->
                    val song = array.getJSONObject(index)
                    PlaylistSong(
                        videoId = song.getString("videoId"),
                        title = song.getString("title"),
                        artist = song.optString("artist").ifEmpty { null },
                        durationSec = song.optInt("durationSec", 0).takeIf { it > 0 },
                    )
                }
            }
            ForYouList(json.getLong("day"), songs)
        } catch (e: Exception) {
            null  // no file yet, or an unreadable one: make a new list
        }
    }

    /** Makes a new list for [day] and saves it. Throws if YouTube can't be reached. */
    suspend fun make(day: Long, library: List<Song>, stats: Map<String, SongStatsEntity>): ForYouList {
        check(hasInternet()) { "No internet" }
        val byFileName = library.associateBy { it.fileName }
        val seeds = ForYou.pickSeeds(
            songs = library.map { it.fileName to it.artist },
            playCounts = stats.mapValues { it.value.playCount },
            random = Random(day),
        ).mapNotNull { byFileName[it] }

        // Ask for the seeds' radios at the same time (async), then wait for all of them.
        val seedIds = mutableSetOf<String>()
        val radios = coroutineScope {
            seeds.map { seed ->
                async {
                    try {
                        val videoId = radioMixMaker.videoIdFor(seed) ?: return@async null
                        synchronized(seedIds) { seedIds += videoId }
                        youtube.radio(videoId, RADIO_SIZE)
                    } catch (e: Exception) {
                        null  // one seed failing still leaves the others
                    }
                }
            }.awaitAll().filterNotNull()
        }
        check(radios.isNotEmpty()) { "YouTube Music didn't answer" }

        val onPhone = library.map { LibrarySong(it.fileName, it.title, it.artist, it.durationMs) }
        val list = ForYouList(day, ForYou.blend(radios, isOwned = { findInLibrary(onPhone, it) != null }, seedIds = seedIds))
        save(list)
        return list
    }

    private suspend fun save(list: ForYouList) = withContext(Dispatchers.IO) {
        val songs = JSONArray()
        list.songs.forEach { song ->
            songs.put(
                JSONObject()
                    .put("videoId", song.videoId)
                    .put("title", song.title)
                    .put("artist", song.artist.orEmpty())
                    .put("durationSec", song.durationSec ?: 0)
            )
        }
        file.writeText(JSONObject().put("day", list.day).put("songs", songs).toString())
    }

    private companion object {
        const val RADIO_SIZE = 25
    }
}
