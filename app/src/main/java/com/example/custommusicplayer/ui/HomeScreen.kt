package com.example.custommusicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.db.SongStatsEntity
import com.example.custommusicplayer.data.mixes.Mix
import com.example.custommusicplayer.data.mixes.MixGenerator
import java.time.LocalDate
import kotlin.random.Random

/** The Home tab: mixes made from your habits, and a summary of your listening. */
@Composable
fun HomeScreen(
    songsByFileName: Map<String, Song>,
    stats: Map<String, SongStatsEntity>,
    playsLast30Days: Map<String, Int>,
    currentSongId: Long?,
    artLoader: AlbumArtLoader,
    onPlaySongs: (songs: List<Song>, startIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Seeded by today's date, so the random mixes stay the same all day and change tomorrow.
    val today = LocalDate.now().toEpochDay()
    val mixes = remember(songsByFileName, stats, playsLast30Days, today) {
        MixGenerator.generate(
            songFileNames = songsByFileName.keys.toList(),
            stats = stats,
            playsLast30Days = playsLast30Days,
            now = System.currentTimeMillis(),
            random = Random(today),
        )
    }
    // Only songs that are still on the phone, most played first.
    val mostPlayed = remember(songsByFileName, stats) {
        stats.values
            .filter { it.playCount > 0 && it.songFileName in songsByFileName }
            .sortedByDescending { it.playCount }
            .take(5)
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text("Home", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        }

        item { SectionTitle("Your mixes") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(mixes, key = { it.id }) { mix ->
                    // mapNotNull: skip any file that disappeared since the stats were saved.
                    val songs = mix.songFileNames.mapNotNull { songsByFileName[it] }
                    MixCard(mix = mix, songs = songs, artLoader = artLoader, onClick = { onPlaySongs(songs, 0) })
                }
            }
        }

        item { SectionTitle("Your listening") }
        item { StatsSummary(stats.values) }

        item { SectionTitle("Most played") }
        if (mostPlayed.isEmpty()) {
            item {
                Text(
                    "Nothing yet. A song counts as played once you've heard half of it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(mostPlayed, key = { it.songFileName }) { songStats ->
            val song = songsByFileName.getValue(songStats.songFileName)
            val songs = mostPlayed.map { songsByFileName.getValue(it.songFileName) }
            SongRow(
                song = song,
                artLoader = artLoader,
                onClick = { onPlaySongs(songs, songs.indexOf(song)) },
                isCurrent = song.id == currentSongId,
                subtitle = "${song.artist} · ${plural(songStats.playCount, "play")} · ${lastPlayedText(songStats.lastPlayedAt)}",
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun MixCard(mix: Mix, songs: List<Song>, artLoader: AlbumArtLoader, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp).clickable(onClick = onClick)) {
        val cover = songs.firstOrNull()
        if (cover != null) {
            SongArt(cover, artLoader, Modifier.size(150.dp))
        }
        Text(mix.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        Text(
            mix.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(plural(songs.size, "song"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Three tiles: total plays, listening time, skips. */
@Composable
private fun StatsSummary(allStats: Collection<SongStatsEntity>) {
    val plays = allStats.sumOf { it.playCount }
    val skips = allStats.sumOf { it.skipCount }
    val listenedMs = allStats.sumOf { it.listenedMs }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile("Plays", plays.toString(), Modifier.weight(1f))
        StatTile("Listened", formatListeningTime(listenedMs), Modifier.weight(1f))
        StatTile("Skips", skips.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 32 s -> "32s", 12 min -> "12m", 125 min -> "2h 5m" */
private fun formatListeningTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    return when {
        minutes == 0L -> "${totalSeconds}s"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

private fun plural(count: Int, word: String) = "$count ${if (count == 1) word else word + "s"}"

/** "today", "yesterday", "5 days ago" */
private fun lastPlayedText(lastPlayedAt: Long?): String {
    if (lastPlayedAt == null) return "never"
    val days = (System.currentTimeMillis() - lastPlayedAt) / (24 * 60 * 60 * 1000L)
    return when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }
}
