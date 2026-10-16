package com.example.custommusicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * The order ExoPlayer plays the queue in when shuffle is on.
 *
 * The queue itself is never reordered. Instead, ExoPlayer asks this object "which song comes
 * after queue index X?". We answer from [order], a shuffled list of queue indices.
 *
 * Example with a 4-song queue: order = [2, 0, 3, 1] means play queue[2], then queue[0], ...
 *
 * `@OptIn(UnstableApi::class)`: Media3 marks ShuffleOrder as an advanced API that may change
 * between versions. This annotation says "yes, I know".
 */
@OptIn(UnstableApi::class)
class FisherYatesShuffleOrder private constructor(
    private val order: IntArray,
    private val random: Random,
) : ShuffleOrder {

    /**
     * A fresh shuffle of a queue with [length] songs. The song at [firstIndex] (the one
     * playing now) goes first, so every other song still gets played after it.
     */
    constructor(length: Int, firstIndex: Int, random: Random = Random.Default) :
        this(createOrder(length, firstIndex, random), random)

    // The reverse lookup: positionInOrder[queueIndex] = where that song sits in `order`.
    private val positionInOrder = IntArray(order.size).also { positions ->
        order.forEachIndexed { position, queueIndex -> positions[queueIndex] = position }
    }

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int {
        val nextPosition = positionInOrder[index] + 1
        return if (nextPosition < order.size) order[nextPosition] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val previousPosition = positionInOrder[index] - 1
        return if (previousPosition >= 0) order[previousPosition] else C.INDEX_UNSET
    }

    override fun getFirstIndex(): Int = order.firstOrNull() ?: C.INDEX_UNSET

    override fun getLastIndex(): Int = order.lastOrNull() ?: C.INDEX_UNSET

    /**
     * Songs were added to the queue at [insertionIndex]. Queue indices at or after that point
     * moved up by [insertionCount]; the new songs go to random positions in the order.
     */
    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val newOrder = order
            .map { if (it >= insertionIndex) it + insertionCount else it }
            .toMutableList()
        for (newQueueIndex in insertionIndex until insertionIndex + insertionCount) {
            newOrder.add(random.nextInt(newOrder.size + 1), newQueueIndex)
        }
        return FisherYatesShuffleOrder(newOrder.toIntArray(), random)
    }

    /** Queue indices [indexFrom, indexToExclusive) were removed; later indices move down. */
    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removedCount = indexToExclusive - indexFrom
        val newOrder = order
            .filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - removedCount else it }
        return FisherYatesShuffleOrder(newOrder.toIntArray(), random)
    }

    override fun cloneAndClear(): ShuffleOrder = FisherYatesShuffleOrder(IntArray(0), random)

    companion object {
        private fun createOrder(length: Int, firstIndex: Int, random: Random): IntArray {
            if (firstIndex !in 0 until length) {
                val all = (0 until length).toMutableList()
                fisherYatesShuffle(all, random)
                return all.toIntArray()
            }
            val others = (0 until length).filter { it != firstIndex }.toMutableList()
            fisherYatesShuffle(others, random)
            return (listOf(firstIndex) + others).toIntArray()
        }
    }
}
