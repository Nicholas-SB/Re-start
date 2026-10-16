package com.example.custommusicplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningTrackerTest {

    // A fake recorder that just remembers what it was told, as readable strings.
    private val events = mutableListOf<String>()
    private val recorder = object : ListeningRecorder {
        override fun recordPlay(songFileName: String, listenedMs: Long) { events += "play $songFileName ${listenedMs / 1000}s" }
        override fun recordSkip(songFileName: String, listenedMs: Long) { events += "skip $songFileName ${listenedMs / 1000}s" }
        override fun recordListening(songFileName: String, listenedMs: Long) { events += "listen $songFileName ${listenedMs / 1000}s" }
    }

    // A fake clock we move forward by hand.
    private var now = 0L
    private val tracker = ListeningTracker(recorder) { now }
    private fun wait(seconds: Int) { now += seconds * 1000L }

    @Test
    fun listeningToHalfASongCountsAsAPlay() {
        tracker.songStarted("a.m4a", durationMs = 200_000, isPlaying = true)
        wait(100)
        tracker.songEnded(userSkipped = false)
        assertEquals(listOf("play a.m4a 100s"), events)
    }

    @Test
    fun fourMinutesIsEnoughForLongSongs() {
        tracker.songStarted("long.m4a", durationMs = 20 * 60_000, isPlaying = true)
        wait(240)
        tracker.songEnded(userSkipped = false)
        assertEquals(listOf("play long.m4a 240s"), events)
    }

    @Test
    fun movingOnEarlyIsASkip() {
        tracker.songStarted("a.m4a", durationMs = 200_000, isPlaying = true)
        wait(10)
        tracker.songEnded(userSkipped = true)
        assertEquals(listOf("skip a.m4a 10s"), events)
    }

    @Test
    fun skippingAfterItCountedIsNotASkip() {
        tracker.songStarted("a.m4a", durationMs = 200_000, isPlaying = true)
        wait(150)
        tracker.songEnded(userSkipped = true)
        assertEquals(listOf("play a.m4a 150s"), events)
    }

    @Test
    fun pausedTimeDoesNotCount() {
        tracker.songStarted("a.m4a", durationMs = 200_000, isPlaying = true)
        wait(60)
        tracker.playingChanged(isPlaying = false)
        wait(600)  // paused for 10 minutes
        tracker.playingChanged(isPlaying = true)
        wait(50)
        tracker.songEnded(userSkipped = false)
        // 60 s before the pause is just listening; at 110 s total it becomes a play.
        assertEquals(listOf("listen a.m4a 60s", "play a.m4a 50s"), events)
    }

    @Test
    fun aPlayIsSavedWhenPausingPastTheThreshold() {
        tracker.songStarted("a.m4a", durationMs = 200_000, isPlaying = true)
        wait(120)
        tracker.playingChanged(isPlaying = false)
        assertEquals(listOf("play a.m4a 120s"), events)
        // Listening more afterwards only adds time; it isn't a second play.
        tracker.playingChanged(isPlaying = true)
        wait(30)
        tracker.songEnded(userSkipped = false)
        assertEquals(listOf("play a.m4a 120s", "listen a.m4a 30s"), events)
    }

    @Test
    fun skippingAPausedSongYouNeverHeardRecordsNothing() {
        tracker.songStarted("a.m4a", durationMs = 200_000, isPlaying = false)
        tracker.songEnded(userSkipped = true)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun noSongMeansNothingToRecord() {
        tracker.songEnded(userSkipped = true)
        tracker.playingChanged(isPlaying = false)
        assertEquals(emptyList<String>(), events)
    }
}
