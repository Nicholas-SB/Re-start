package io.github.nicholas_sb.restart.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun everyWordHasToBeSomewhere() {
        val words = TextSearch.words("halo beyonce")
        assertTrue(TextSearch.matches(words, "Halo", "Beyoncé", "I Am... Sasha Fierce"))
        assertFalse(TextSearch.matches(words, "Halo", "Walk the Moon", "Talking Is Hard"))
    }

    @Test
    fun ignoresCaseAccentsAndExtraSpaces() {
        assertEquals(listOf("pokemon", "theme"), TextSearch.words("  Pokémon   THEME "))
        assertTrue(TextSearch.matches(TextSearch.words("POKEMON"), "Pokémon Theme"))
    }

    @Test
    fun nothingTypedFindsNothing() {
        assertEquals(emptyList<String>(), TextSearch.words("   "))
    }

    @Test
    fun partsOfWordsMatchToo() {
        assertTrue(TextSearch.matches(TextSearch.words("rosel"), "Song I am.", "Roselia"))
    }
}
