package com.example.custommusicplayer.data.discover

import com.example.custommusicplayer.downloader.PlaylistSong
import kotlin.random.Random

/**
 * "For You": new songs (ones you don't have) picked from the YouTube Music radios of songs you
 * play a lot. Pure Kotlin, so it's easy to test; ForYouRepository does the fetching.
 */
object ForYou {

    const val SEED_COUNT = 3
    const val SIZE = 30

    /**
     * Which of your songs to start from: [count] songs from your 10 most played, by different
     * artists where possible. [random] is seeded with the day, so the picks change daily.
     * Without any plays yet, any songs will do.
     *
     * @param songs every song in the library: file name and artist
     * @return file names
     */
    fun pickSeeds(songs: List<Pair<String, String>>, playCounts: Map<String, Int>, random: Random, count: Int = SEED_COUNT): List<String> {
        val favorites = songs
            .filter { (fileName, _) -> (playCounts[fileName] ?: 0) > 0 }
            .sortedByDescending { (fileName, _) -> playCounts[fileName] }
            .take(10)
            .shuffled(random)
        val others = songs.shuffled(random)
        val candidates = (favorites + others).distinctBy { (fileName, _) -> fileName }

        val differentArtists = candidates.distinctBy { (_, artist) -> artist }.take(count)
        // Not enough different artists: fill up with more songs (same artists allowed).
        val seeds = (differentArtists + candidates).distinctBy { (fileName, _) -> fileName }.take(count)
        return seeds.map { (fileName, _) -> fileName }
    }

    /**
     * Mixes the radios together, taking one song from each in turn, so the list isn't all
     * one seed's style. Skips songs you already have ([isOwned]), the seeds themselves, and
     * repeats.
     */
    fun blend(radios: List<List<PlaylistSong>>, isOwned: (PlaylistSong) -> Boolean, seedIds: Set<String>, size: Int = SIZE): List<PlaylistSong> {
        val picked = LinkedHashMap<String, PlaylistSong>()  // keeps the order they were added in
        val longest = radios.maxOfOrNull { it.size } ?: 0
        for (position in 0 until longest) {
            for (radio in radios) {
                val song = radio.getOrNull(position) ?: continue
                if (picked.size == size) return picked.values.toList()
                if (song.videoId in seedIds || song.videoId in picked || isOwned(song)) continue
                picked[song.videoId] = song
            }
        }
        return picked.values.toList()
    }
}
