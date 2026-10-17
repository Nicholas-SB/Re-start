package io.github.nicholas_sb.restart.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plain JVM unit tests (no emulator needed).
 * Run them from Android Studio with the green arrow next to the class name.
 */
class FisherYatesShuffleOrderTest {

    /** Walks the shuffle order from first to last and returns the queue indices in play order. */
    private fun playOrder(shuffleOrder: ShuffleOrder): List<Int> {
        val result = mutableListOf<Int>()
        var index = shuffleOrder.firstIndex
        while (index != C.INDEX_UNSET) {
            result += index
            index = shuffleOrder.getNextIndex(index)
        }
        return result
    }

    @Test
    fun shuffleKeepsEveryElementExactlyOnce() {
        val list = (1..100).toMutableList()
        fisherYatesShuffle(list, Random(42))
        assertEquals((1..100).toList(), list.sorted())
    }

    @Test
    fun everyOrderOfThreeIsRoughlyEquallyLikely() {
        // 3 elements have 6 possible orders. Each should come up about 1/6 of the time.
        val counts = mutableMapOf<List<Int>, Int>()
        val random = Random(1)
        val runs = 60_000
        repeat(runs) {
            val list = mutableListOf(1, 2, 3)
            fisherYatesShuffle(list, random)
            counts[list] = (counts[list] ?: 0) + 1
        }
        assertEquals(6, counts.size)
        counts.values.forEach { count ->
            assertTrue("count $count too far from ${runs / 6}", count in 9_000..11_000)
        }
    }

    @Test
    fun currentSongPlaysFirstAndEverySongPlaysOnce() {
        val order = FisherYatesShuffleOrder(length = 50, firstIndex = 17, random = Random(7))
        val played = playOrder(order)
        assertEquals(17, played.first())
        assertEquals((0 until 50).toList(), played.sorted())
    }

    @Test
    fun previousIsTheReverseOfNext() {
        val order = FisherYatesShuffleOrder(length = 20, firstIndex = 0, random = Random(3))
        val played = playOrder(order)
        for (i in 1 until played.size) {
            assertEquals(played[i - 1], order.getPreviousIndex(played[i]))
        }
        assertEquals(C.INDEX_UNSET, order.getPreviousIndex(played.first()))
    }

    @Test
    fun insertAddsNewSongsAndShiftsLaterIndices() {
        val order = FisherYatesShuffleOrder(length = 5, firstIndex = 0, random = Random(5))
        val bigger = order.cloneAndInsert(insertionIndex = 2, insertionCount = 3)
        assertEquals((0 until 8).toList(), playOrder(bigger).sorted())
    }

    @Test
    fun removeDropsSongsAndShiftsLaterIndices() {
        val order = FisherYatesShuffleOrder(length = 10, firstIndex = 0, random = Random(9))
        val smaller = order.cloneAndRemove(indexFrom = 3, indexToExclusive = 6)
        assertEquals((0 until 7).toList(), playOrder(smaller).sorted())
    }

    @Test
    fun emptyQueueHasNoFirstOrLast() {
        val empty = FisherYatesShuffleOrder(length = 0, firstIndex = C.INDEX_UNSET)
        assertEquals(C.INDEX_UNSET, empty.firstIndex)
        assertEquals(C.INDEX_UNSET, empty.lastIndex)
    }
}
