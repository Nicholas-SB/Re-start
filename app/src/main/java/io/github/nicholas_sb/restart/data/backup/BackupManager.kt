package io.github.nicholas_sb.restart.data.backup

import android.content.Context
import io.github.nicholas_sb.restart.data.MusicScanner
import io.github.nicholas_sb.restart.data.db.ListeningDayEntity
import io.github.nicholas_sb.restart.data.db.MusicDatabase
import io.github.nicholas_sb.restart.data.db.PlayEventEntity
import io.github.nicholas_sb.restart.data.db.PlaylistEntity
import io.github.nicholas_sb.restart.data.db.SongStatsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** Everything a backup holds. Songs are named by file name, like everywhere else. */
data class Backup(
    val createdAt: Long,
    val playlists: List<Pair<PlaylistEntity, List<String>>>,
    val stats: List<SongStatsEntity>,
    val playEvents: List<PlayEventEntity>,
    val listeningDays: List<ListeningDayEntity>,
    val settings: Map<String, Any>,  // the Extras settings: Booleans and Strings
    val musicFolder: String?,
) {
    /** e.g. "2 playlists · 351 songs played" */
    val summary: String
        get() = "${playlists.size} playlist${if (playlists.size == 1) "" else "s"} · ${stats.count { it.playCount > 0 }} songs played"
}

/**
 * Saves playlists, stats and settings to a JSON file, and puts them back from one.
 *
 * JSON is plain text, so a backup can be opened and read in any text editor. The music files
 * themselves aren't in it: a backup is small, and the songs stay where they are.
 */
class BackupManager(private val context: Context) {

    private val dao = MusicDatabase.getInstance(context).backupDao()
    private val settingsPrefs = context.getSharedPreferences("device_settings", Context.MODE_PRIVATE)

    /** Everything as it is right now. */
    suspend fun current(): Backup {
        val songsByPlaylist = dao.playlistSongs().groupBy({ it.playlistId }, { it.songFileName })
        return Backup(
            createdAt = System.currentTimeMillis(),
            playlists = dao.playlists().map { it to songsByPlaylist[it.id].orEmpty() },
            stats = dao.stats(),
            playEvents = dao.playEvents(),
            listeningDays = dao.listeningDays(),
            // Only on/off switches and choices (all the Extras settings are one or the other).
            settings = settingsPrefs.all.filterValues { it is Boolean || it is String }.mapValues { it.value!! },
            musicFolder = MusicScanner.chosenFolder(context),
        )
    }

    suspend fun write(backup: Backup, out: OutputStream) = withContext(Dispatchers.IO) {
        val json = JSONObject()
            .put("app", "Re-start")
            .put("version", FORMAT_VERSION)
            .put("createdAt", backup.createdAt)
            .put("playlists", JSONArray().apply {
                backup.playlists.forEach { (playlist, songs) ->
                    put(
                        JSONObject()
                            .put("name", playlist.name)
                            .put("createdAt", playlist.createdAt)
                            .put("pinned", playlist.pinned)
                            .put("songs", JSONArray(songs))
                    )
                }
            })
            .put("stats", JSONArray().apply {
                backup.stats.forEach { stat ->
                    put(
                        JSONObject()
                            .put("song", stat.songFileName)
                            .put("plays", stat.playCount)
                            .put("skips", stat.skipCount)
                            .put("lastPlayedAt", stat.lastPlayedAt ?: JSONObject.NULL)
                            .put("listenedMs", stat.listenedMs)
                    )
                }
            })
            .put("plays", JSONArray().apply {
                backup.playEvents.forEach { play ->
                    put(
                        JSONObject().put("song", play.songFileName).put("at", play.playedAt).apply {
                            // Streamed songs carry their name (they have no file to look it up from).
                            play.title?.let { put("title", it) }
                            play.artist?.let { put("artist", it) }
                        }
                    )
                }
            })
            .put("listeningDays", JSONArray().apply {
                backup.listeningDays.forEach { put(JSONObject().put("day", it.day).put("ms", it.listenedMs)) }
            })
            .put("settings", JSONObject(backup.settings))
            .put("musicFolder", backup.musicFolder ?: JSONObject.NULL)
        out.writer().use { it.write(json.toString(2)) }  // indented, so it's readable
    }

    /** Reads a backup file. Throws (with a message to show) if it isn't one. */
    suspend fun read(input: InputStream): Backup = withContext(Dispatchers.IO) {
        val json = try {
            JSONObject(input.reader().use { it.readText() })
        } catch (e: Exception) {
            throw IllegalArgumentException("That file isn't a Re-start backup")
        }
        require(json.optString("app") == "Re-start") { "That file isn't a Re-start backup" }
        require(json.optInt("version") <= FORMAT_VERSION) { "That backup is from a newer Re-start" }

        val playlists = json.getJSONArray("playlists").objects().map { playlist ->
            PlaylistEntity(
                name = playlist.getString("name"),
                createdAt = playlist.optLong("createdAt", System.currentTimeMillis()),
                pinned = playlist.optBoolean("pinned"),
            ) to playlist.getJSONArray("songs").strings()
        }
        val stats = json.optJSONArray("stats")?.objects().orEmpty().map { stat ->
            SongStatsEntity(
                songFileName = stat.getString("song"),
                playCount = stat.optInt("plays"),
                skipCount = stat.optInt("skips"),
                lastPlayedAt = if (stat.isNull("lastPlayedAt")) null else stat.optLong("lastPlayedAt"),
                listenedMs = stat.optLong("listenedMs"),
            )
        }
        val plays = json.optJSONArray("plays")?.objects().orEmpty().map { play ->
            PlayEventEntity(
                songFileName = play.getString("song"),
                playedAt = play.getLong("at"),
                title = play.optString("title").ifEmpty { null },
                artist = play.optString("artist").ifEmpty { null },
            )
        }
        // Not in backups from before the monthly history: then there's simply none.
        val listeningDays = json.optJSONArray("listeningDays")?.objects().orEmpty().map { day ->
            ListeningDayEntity(day = day.getLong("day"), listenedMs = day.getLong("ms"))
        }
        val settings = json.optJSONObject("settings")?.let { saved ->
            saved.keys().asSequence().mapNotNull { key ->
                when (val value = saved.get(key)) {
                    is Boolean, is String -> key to value
                    else -> null
                }
            }.toMap()
        }.orEmpty()

        Backup(
            createdAt = json.optLong("createdAt"),
            playlists = playlists,
            stats = stats,
            playEvents = plays,
            listeningDays = listeningDays,
            settings = settings,
            musicFolder = if (json.isNull("musicFolder")) null else json.optString("musicFolder").ifEmpty { null },
        )
    }

    /** Replaces the current playlists, stats and settings with [backup]'s. */
    suspend fun restore(backup: Backup) {
        dao.replaceAll(backup.playlists, backup.stats, backup.playEvents, backup.listeningDays)
        val editor = settingsPrefs.edit().clear()
        backup.settings.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()  // saves in the background
        backup.musicFolder?.let { MusicScanner.saveChosenFolder(context, it) }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private companion object {
        /** Goes up if the format ever changes, so an older app can refuse a newer backup. */
        const val FORMAT_VERSION = 1
    }
}
