package io.github.nicholas_sb.restart.data

import java.text.Normalizer

/**
 * Matching what you type against songs and playlists. Every word you type has to appear
 * somewhere in the text (in any order), ignoring upper/lower case and accents, so
 * "beyonce halo" finds "Halo" by "Beyoncé".
 */
object TextSearch {

    private val accents = Regex("\\p{Mn}+")  // the marks Normalizer splits off: é -> e + ´

    /** The words of a search, cleaned up. */
    fun words(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }

    /** Whether all [words] appear in [fields] together. */
    fun matches(words: List<String>, vararg fields: String): Boolean {
        val text = fields.joinToString(" ") { normalize(it) }
        return words.all { it in text }
    }

    /**
     * Songs matching [query], best first: titles that start with what you typed, then titles
     * that contain all of it, then everything else (artist or album matches), each A–Z.
     */
    fun songs(songs: List<Song>, query: String, limit: Int = 100): List<Song> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        val typed = words.joinToString(" ")
        return songs
            .filter { matches(words, it.title, it.artist, it.album) }
            .sortedWith(
                compareBy<Song> { song ->
                    val title = normalize(song.title)
                    when {
                        title.startsWith(typed) -> 0
                        matches(words, song.title) -> 1
                        else -> 2
                    }
                }.thenBy { normalize(it.title) }
            )
            .take(limit)
    }

    private fun normalize(text: String): String =
        accents.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
}
