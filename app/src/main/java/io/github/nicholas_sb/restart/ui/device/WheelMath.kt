package io.github.nicholas_sb.restart.ui.device

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Turns a finger moving around the click wheel into whole "steps" (one tick each).
 *
 * Angles are in degrees, measured from the wheel's center. On screen the y axis points down,
 * so going clockwise makes the angle *increase*. One step = [degreesPerStep]: the default of
 * 24° gives 15 ticks per full turn. (Lower = lighter wheel, higher = heavier.)
 */
class WheelRotationTracker(private val degreesPerStep: Float = 24f) {

    private var lastAngle: Float? = null
    private var leftover = 0f  // rotation that hasn't added up to a full step yet

    /** A finger touched down on the ring at [angle]. */
    fun start(angle: Float) {
        lastAngle = angle
        leftover = 0f
    }

    /** The finger moved to [angle]. Returns the whole steps since last time: + clockwise, − counter-clockwise. */
    fun moveTo(angle: Float): Int {
        val previous = lastAngle ?: run { start(angle); return 0 }
        var delta = angle - previous
        // Crossing the line where angles wrap around: going from 170° to -170° is a 20° turn
        // clockwise, not 340° counter-clockwise.
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        lastAngle = angle

        leftover += delta
        val steps = (leftover / degreesPerStep).toInt()  // toInt() cuts toward zero, for both directions
        leftover -= steps * degreesPerStep
        return steps
    }

    companion object {
        /** The angle (degrees, -180..180) of point (x, y) as seen from the center (cx, cy). */
        fun angleOf(x: Float, y: Float, cx: Float, cy: Float): Float =
            Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
    }
}

/**
 * Spinning fast moves further, so long lists don't take forever: the more steps in the last
 * moment, the bigger the multiplier. Slow, careful turning still moves one item per tick.
 */
class WheelAcceleration(private val windowMs: Long = 400) {

    private val recentSteps = ArrayDeque<Long>()  // when each recent step happened

    fun apply(steps: Int, nowMs: Long): Int {
        repeat(abs(steps)) { recentSteps.addLast(nowMs) }
        while (recentSteps.isNotEmpty() && nowMs - recentSteps.first() > windowMs) {
            recentSteps.removeFirst()
        }
        val multiplier = when {
            recentSteps.size >= 14 -> 4  // about a full turn in under half a second
            recentSteps.size >= 7 -> 2
            else -> 1
        }
        return steps * multiplier
    }
}
