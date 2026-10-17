package io.github.nicholas_sb.restart.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    private fun synced(text: String): List<LyricLine> {
        val result = LrcParser.parse(text)
        assertTrue("expected synced lyrics, got $result", result is Lyrics.Synced)
        return (result as Lyrics.Synced).lines
    }

    @Test
    fun parsesBasicLines() {
        val lines = synced(
            """
            [ar:Someone]
            [ti:Some Song]
            [00:01.00]First
            [00:03.50]Second
            """.trimIndent()
        )
        assertEquals(listOf(LyricLine(1_000, "First"), LyricLine(3_500, "Second")), lines)
    }

    @Test
    fun handlesDifferentFractionLengths() {
        val lines = synced("[00:01]a\n[00:02.5]b\n[00:03.25]c\n[01:04.125]d")
        assertEquals(listOf(1_000L, 2_500L, 3_250L, 64_125L), lines.map { it.timeMs })
    }

    @Test
    fun repeatedTimeTagsBecomeSeparateLinesInTimeOrder() {
        val lines = synced("[00:10.00][00:30.00]Chorus\n[00:20.00]Verse")
        assertEquals(
            listOf(LyricLine(10_000, "Chorus"), LyricLine(20_000, "Verse"), LyricLine(30_000, "Chorus")),
            lines,
        )
    }

    @Test
    fun appliesOffset() {
        val lines = synced("[offset:+500]\n[00:02.00]Line")
        assertEquals(1_500L, lines.single().timeMs)
    }

    @Test
    fun stripsWordTimestamps() {
        val lines = synced("[00:01.00]<00:01.00>Hello <00:01.50>world")
        assertEquals("Hello world", lines.single().text)
    }

    @Test
    fun keepsEmptyLinesAsInstrumentalBreaks() {
        val lines = synced("[00:01.00]Sing\n[00:05.00]\n[00:09.00]Again")
        assertEquals("", lines[1].text)
    }

    @Test
    fun textWithoutTimesIsPlainLyrics() {
        assertEquals(Lyrics.Plain("Just some\nwords"), LrcParser.parse("Just some\nwords\n"))
    }

    @Test
    fun blankTextIsNoLyrics() {
        assertNull(LrcParser.parse("  \n "))
    }

    @Test
    fun findsCurrentLine() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))
        assertEquals(-1, lines.currentLineIndex(500))
        assertEquals(0, lines.currentLineIndex(1_000))
        assertEquals(0, lines.currentLineIndex(4_999))
        assertEquals(1, lines.currentLineIndex(5_000))
        assertEquals(2, lines.currentLineIndex(60_000))
    }
}
