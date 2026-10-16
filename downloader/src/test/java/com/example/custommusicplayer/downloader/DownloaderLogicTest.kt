package com.example.custommusicplayer.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloaderLogicTest {

    @Test
    fun youtubeMusicFieldsAreUsedAsIs() {
        val info = TrackInfoParser.parse(
            track = "Song I am.", artist = "Roselia", album = "Wahl",
            videoTitle = "Song I am.", uploader = "Roselia - Topic", durationSec = 243,
        )
        assertEquals(TrackInfo("Song I am.", "Roselia", "Wahl", 243), info)
    }

    @Test
    fun multipleArtistsKeepTheFirst() {
        val info = TrackInfoParser.parse("Song", "Main Artist, Guest", null, "x", null, null)
        assertEquals("Main Artist", info.artist)
    }

    @Test
    fun videoTitleIsSplitAndCleaned() {
        val info = TrackInfoParser.parse(
            track = null, artist = null, album = null,
            videoTitle = "Roselia - Song I am. (Official Music Video) [HD]", uploader = "BanG Dream!", durationSec = 250,
        )
        assertEquals("Roselia", info.artist)
        assertEquals("Song I am.", info.title)
    }

    @Test
    fun titleWithoutDashUsesChannelName() {
        val info = TrackInfoParser.parse(null, null, null, "Loverboy 【Lyrics】", "A-Wall - Topic", null)
        assertEquals(TrackInfo("Loverboy", "A-Wall", null, null), info)
    }

    @Test
    fun keepsBracketsThatArePartOfTheSongName() {
        val info = TrackInfoParser.parse(null, null, null, "Artist - Song (Remix)", null, null)
        assertEquals("Song (Remix)", info.title)
    }

    private fun record(duration: Double, synced: String? = null, plain: String? = null) =
        LrclibRecord("t", "a", duration, synced, plain)

    @Test
    fun prefersSyncedLyricsWithMatchingDuration() {
        val records = listOf(
            record(300.0, synced = "[00:01.00]wrong version"),
            record(200.0, plain = "plain"),
            record(201.0, synced = "[00:01.00]right"),
        )
        assertEquals("[00:01.00]right", LrclibClient.pickBest(records, durationSec = 200))
    }

    @Test
    fun fallsBackToPlainLyrics() {
        assertEquals("plain", LrclibClient.pickBest(listOf(record(200.0, plain = "plain")), 199))
    }

    @Test
    fun rejectsResultsWithTheWrongLength() {
        assertNull(LrclibClient.pickBest(listOf(record(260.0, synced = "x")), 200))
    }

    @Test
    fun unknownDurationAcceptsAnyResult() {
        assertEquals("x", LrclibClient.pickBest(listOf(record(260.0, synced = "x")), null))
    }
}
