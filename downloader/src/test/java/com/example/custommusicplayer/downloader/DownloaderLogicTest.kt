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

    @Test
    fun acceptsRealYoutubeLinks() {
        listOf(
            "https://youtu.be/Kohn9PRanoY",
            "https://www.youtube.com/watch?v=Kohn9PRanoY&list=RD123",
            "https://music.youtube.com/watch?v=Kohn9PRanoY",
            "https://m.youtube.com/watch?v=Kohn9PRanoY",
            "  HTTPS://YOUTUBE.COM/watch?v=abc  ",
        ).forEach { assertEquals(it, LinkKind.YOUTUBE, linkKind(it)) }
    }

    @Test
    fun recognisesSpotifyLinks() {
        assertEquals(LinkKind.SPOTIFY, linkKind("https://open.spotify.com/track/abc123"))
    }

    @Test
    fun rejectsTextThatOnlyMentionsYoutube() {
        listOf(
            "--exec 'rm -rf x' https://youtube.com/watch?v=a",  // option injection attempt
            "-o /sdcard/x https://youtu.be/a",
            "https://evil.example/youtube.com/watch?v=a",          // YouTube only in the path
            "https://youtube.com.evil.example/watch?v=a",          // look-alike host
            "file:///sdcard/youtube.com/x",                         // not a web link
            "youtube.com/watch?v=a",                                // no https://
            "",
        ).forEach { assertEquals(it, LinkKind.UNKNOWN, linkKind(it)) }
    }

    @Test
    fun readsPlaylistLinks() {
        val music = playlistLink("https://music.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG&si=AbC")
        assertEquals("https://music.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG", music?.url)
        assertEquals("https://music.youtube.com/watch?v=Kohn9PRanoY", music?.songUrl("Kohn9PRanoY"))

        val youtube = playlistLink("https://www.youtube.com/playlist?list=OLAK5uy_abc-123")
        assertEquals("https://www.youtube.com/playlist?list=OLAK5uy_abc-123", youtube?.url)

        // A song played from a mix: keep both ids, the mix only works that way.
        val mix = playlistLink("https://www.youtube.com/watch?v=Kohn9PRanoY&list=RDKohn9PRanoY")
        assertEquals("https://www.youtube.com/watch?v=Kohn9PRanoY&list=RDKohn9PRanoY", mix?.url)
        assertEquals(false, isWholePlaylist("https://www.youtube.com/watch?v=Kohn9PRanoY&list=RDKohn9PRanoY"))
        assertEquals(true, isWholePlaylist("https://www.youtube.com/playlist?list=PL123"))
    }

    @Test
    fun rejectsLinksWithoutAProperPlaylist() {
        listOf(
            "https://youtu.be/Kohn9PRanoY",                              // just a song
            "https://www.youtube.com/playlist?list=",                    // empty id
            "https://www.youtube.com/playlist?list=PL1%20--exec%20x",    // odd characters
            "https://evil.example/playlist?list=PL123",                  // not YouTube
        ).forEach { assertEquals(it, null, playlistLink(it)) }
    }
}
