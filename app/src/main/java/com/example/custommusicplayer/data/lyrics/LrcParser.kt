package com.example.custommusicplayer.data.lyrics

/**
 * Reads the LRC lyrics format:
 *
 *     [ar:Artist]                 <- tags (ignored, except [offset:])
 *     [00:12.34]First line        <- minutes:seconds.hundredths, then the text
 *     [00:15.00][01:20.00]Chorus  <- one line can have several times
 *
 * An `object` is a singleton: one instance, created automatically. Call LrcParser.parse(...).
 */
object LrcParser {

    // [mm:ss], [mm:ss.x], [mm:ss.xx] or [mm:ss.xxx]; also accepts ':' before the fraction.
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    // [offset:+250] shifts every line by that many milliseconds (positive = earlier).
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    // "Enhanced LRC" puts per-word times like <00:12.50> inside the text; we drop them.
    private val wordTimeTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    /**
     * Parses [text]. Returns synced lyrics if it has time tags, plain lyrics if it's just
     * text, or null if it's empty.
     */
    fun parse(text: String): Lyrics? {
        val offsetMs = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()

        for (rawLine in text.lines()) {
            val times = timeTag.findAll(rawLine).toList()
            if (times.isEmpty()) continue

            // The text is whatever follows the last time tag.
            val lyricText = rawLine.substring(times.last().range.last + 1)
                .replace(wordTimeTag, "")
                .trim()

            for (match in times) {
                val timeMs = (toMillis(match) - offsetMs).coerceAtLeast(0)
                lines += LyricLine(timeMs, lyricText)
            }
        }

        return when {
            // sortedBy keeps lines with equal times in their original order.
            lines.isNotEmpty() -> Lyrics.Synced(lines.sortedBy { it.timeMs })
            text.isNotBlank() -> Lyrics.Plain(text.trim())
            else -> null
        }
    }

    private fun toMillis(match: MatchResult): Long {
        // Destructuring a MatchResult: the three capture groups, in order.
        val (minutes, seconds, fraction) = match.destructured
        // ".5" means 500 ms, ".05" means 50 ms, ".005" means 5 ms.
        val fractionMs = when (fraction.length) {
            0 -> 0
            1 -> fraction.toInt() * 100
            2 -> fraction.toInt() * 10
            else -> fraction.toInt()
        }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }
}
