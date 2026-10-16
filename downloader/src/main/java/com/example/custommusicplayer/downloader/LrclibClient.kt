package com.example.custommusicplayer.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** One result from LRCLIB. Either lyrics field can be missing. */
data class LrclibRecord(
    val trackName: String,
    val artistName: String,
    val durationSec: Double,
    val syncedLyrics: String?,
    val plainLyrics: String?,
)

/**
 * Looks up lyrics on LRCLIB (https://lrclib.net), a free, open lyrics database.
 * No account or API key needed.
 */
class LrclibClient {

    /** Returns synced (LRC) lyrics if available, otherwise plain lyrics, otherwise null. */
    suspend fun findLyrics(track: TrackInfo): String? = withContext(Dispatchers.IO) {
        // 1. Exact lookup. It needs the album and duration, which only YouTube Music gives us.
        if (track.album != null && track.durationSec != null) {
            val exact = getJson(
                "/api/get",
                "track_name" to track.title,
                "artist_name" to track.artist,
                "album_name" to track.album,
                "duration" to track.durationSec.toString(),
            )?.let { parseRecord(JSONObject(it)) }
            if (exact != null) pickBest(listOf(exact), track.durationSec)?.let { return@withContext it }
        }

        // 2. Search by title + artist, then 3. a looser free-text search.
        val searches = listOf(
            arrayOf("track_name" to track.title, "artist_name" to track.artist),
            arrayOf("q" to "${track.artist} ${track.title}"),
        )
        for (params in searches) {
            val results = getJson("/api/search", *params)?.let { parseRecords(JSONArray(it)) } ?: continue
            pickBest(results, track.durationSec)?.let { return@withContext it }
        }
        null
    }

    /** GETs an LRCLIB endpoint; returns the body, or null on 404/network errors. */
    private fun getJson(path: String, vararg params: Pair<String, String>): String? {
        val query = params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        val connection = URL("$BASE_URL$path?$query").openConnection() as HttpURLConnection
        return try {
            // LRCLIB asks apps to identify themselves.
            connection.setRequestProperty("User-Agent", "CustomMusicPlayer/1.0 (personal project)")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRecords(array: JSONArray): List<LrclibRecord> =
        (0 until array.length()).map { parseRecord(array.getJSONObject(it)) }

    private fun parseRecord(json: JSONObject) = LrclibRecord(
        trackName = json.optString("trackName"),
        artistName = json.optString("artistName"),
        durationSec = json.optDouble("duration", 0.0),
        // optString returns "null" for JSON null, so check isNull first.
        syncedLyrics = if (json.isNull("syncedLyrics")) null else json.optString("syncedLyrics").ifBlank { null },
        plainLyrics = if (json.isNull("plainLyrics")) null else json.optString("plainLyrics").ifBlank { null },
    )

    companion object {
        private const val BASE_URL = "https://lrclib.net"
        private const val MAX_DURATION_DIFFERENCE_SEC = 3.0

        /**
         * Chooses the lyrics to use. If we know the song's length, only results within a few
         * seconds count (so a live version or remix doesn't get the wrong timings).
         * Synced lyrics beat plain ones.
         */
        fun pickBest(records: List<LrclibRecord>, durationSec: Int?): String? {
            val candidates = if (durationSec == null) {
                records
            } else {
                records.filter { abs(it.durationSec - durationSec) <= MAX_DURATION_DIFFERENCE_SEC }
            }
            return candidates.firstNotNullOfOrNull { it.syncedLyrics }
                ?: candidates.firstNotNullOfOrNull { it.plainLyrics }
        }
    }
}
