package io.github.nicholas_sb.restart.ui.device

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelMathTest {

    /** Moves a tracker through the given angles and adds up the steps. */
    private fun totalSteps(vararg angles: Float): Int {
        val tracker = WheelRotationTracker(degreesPerStep = 18f)
        tracker.start(angles.first())
        return angles.drop(1).sumOf { tracker.moveTo(it) }
    }

    @Test
    fun quarterTurnClockwiseIsFiveSteps() {
        assertEquals(5, totalSteps(0f, 30f, 60f, 90f))
    }

    @Test
    fun counterClockwiseIsNegative() {
        assertEquals(-5, totalSteps(90f, 45f, 0f))
    }

    @Test
    fun smallWigglesAddUpInsteadOfBeingLost() {
        // Ten 2° moves = 20° = one step, even though no single move reaches 18°.
        assertEquals(1, totalSteps(0f, 2f, 4f, 6f, 8f, 10f, 12f, 14f, 16f, 18f, 20f))
    }

    @Test
    fun crossingTheWrapAroundLineKeepsDirection() {
        // 162° -> 180° -> -162° is a 36° clockwise turn: 2 steps.
        assertEquals(2, totalSteps(162f, 180f, -162f))
        // And back the other way.
        assertEquals(-2, totalSteps(-162f, 180f, 162f))
    }

    @Test
    fun fullTurnIsTwentySteps() {
        val angles = (0..20).map { (it * 18f) - 180f }.map { if (it > 180f) it - 360f else it }
        assertEquals(20, totalSteps(*angles.toFloatArray()))
    }

    @Test
    fun angleOfPointsAroundTheCenter() {
        assertEquals(0f, WheelRotationTracker.angleOf(10f, 0f, 0f, 0f), 0.01f)    // right
        assertEquals(90f, WheelRotationTracker.angleOf(0f, 10f, 0f, 0f), 0.01f)   // below (y grows downward)
        assertEquals(-90f, WheelRotationTracker.angleOf(0f, -10f, 0f, 0f), 0.01f) // above
    }

    @Test
    fun slowTurningIsNotAccelerated() {
        val acceleration = WheelAcceleration()
        // One step every 200 ms.
        val moved = (0 until 10).sumOf { acceleration.apply(1, nowMs = it * 200L) }
        assertEquals(10, moved)
    }

    @Test
    fun fastSpinningMovesFurther() {
        val acceleration = WheelAcceleration()
        // 30 steps within 300 ms: later steps get multiplied.
        val moved = (0 until 30).sumOf { acceleration.apply(1, nowMs = it * 10L) }
        assertEquals(true, moved > 60)
    }

    @Test
    fun accelerationKeepsDirection() {
        val acceleration = WheelAcceleration()
        val moved = (0 until 30).sumOf { acceleration.apply(-1, nowMs = it * 10L) }
        assertEquals(true, moved < -60)
    }
}
