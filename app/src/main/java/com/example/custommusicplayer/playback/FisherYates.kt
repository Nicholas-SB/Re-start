package com.example.custommusicplayer.playback

import kotlin.random.Random

/**
 * Shuffles [list] in place using the Fisher-Yates algorithm.
 *
 * Walk from the last position down to the second one. At each position i, pick a random
 * position j from 0..i (inclusive) and swap the two. Every possible order is equally
 * likely, and it runs in O(n).
 *
 * `<T>` makes this a generic function, like `static <T> void shuffle(List<T> list)` in Java.
 * [random] is a parameter so tests can pass a seeded Random and get a repeatable result.
 */
fun <T> fisherYatesShuffle(list: MutableList<T>, random: Random = Random.Default) {
    for (i in list.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)  // nextInt(n) returns 0..n-1, so this is 0..i
        val temp = list[i]
        list[i] = list[j]
        list[j] = temp
    }
}
