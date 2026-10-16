package com.example.custommusicplayer.ui.device

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Re-start's little fox. Awake (eyes open, tail wagging) while music plays; curled up asleep
 * (eyes shut, slow breathing, floating z's) when it's paused.
 *
 * Drawn with shapes on a Canvas instead of an image, so it stays sharp at any size.
 */
@Composable
fun FoxMascot(awake: Boolean, modifier: Modifier = Modifier) {
    // An infinite transition drives animations that loop forever.
    val loop = rememberInfiniteTransition(label = "fox")
    val tailSway by loop.animateFloat(
        initialValue = -12f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tail",
    )
    val breath by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val zRise by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "z",
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFox(awake = awake, tailAngle = if (awake) tailSway else 0f, breath = if (awake) 0f else breath)
        }
        if (!awake) {
            // Two z's, half a cycle apart, drifting up and fading out.
            for (phase in listOf(0f, 0.5f)) {
                val t = (zRise + phase) % 1f
                Text(
                    "z",
                    color = DeviceColors.TextSoft,
                    fontSize = (12 + 6 * t).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-14 + 8 * t).dp, y = (26 - 30 * t).dp)
                        .alpha(1f - t),
                )
            }
        }
    }
}

private val Caramel = Color(0xFFC98B5A)
private val CaramelDark = Color(0xFF8E5D3C)
private val FoxCream = Color(0xFFFBF5EC)
private val Blush = Color(0x99E8A595)
private val Ink = Color(0xFF3F3129)

/**
 * Draws the fox in a 100 × 100 grid scaled to the canvas, so the numbers below are like
 * percentages: (50, 50) is the middle.
 */
private fun DrawScope.drawFox(awake: Boolean, tailAngle: Float, breath: Float) {
    val u = size.minDimension / 100f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun path(build: Path.() -> Unit) = Path().apply(build)

    // Breathing: grow very slightly, anchored at the bottom so the fox doesn't float.
    scale(scaleX = 1f, scaleY = 1f + 0.03f * breath, pivot = p(48f, 96f)) {
        // Tail, swinging around where it meets the body.
        rotate(degrees = tailAngle, pivot = p(62f, 84f)) {
            drawPath(path {
                moveTo(60 * u, 88 * u)
                cubicTo(80 * u, 93 * u, 99 * u, 78 * u, 92 * u, 55 * u)
                cubicTo(86 * u, 62 * u, 80 * u, 74 * u, 62 * u, 79 * u)
                close()
            }, Caramel)
            drawCircle(FoxCream, radius = 6 * u, center = p(91f, 58f))  // white tip
        }

        // Body, chest and feet.
        drawOval(Caramel, topLeft = p(28f, 59f), size = Size(40 * u, 34 * u))
        drawOval(FoxCream, topLeft = p(38f, 62f), size = Size(20 * u, 27 * u))
        drawOval(FoxCream, topLeft = p(35f, 88f), size = Size(10 * u, 6 * u))
        drawOval(FoxCream, topLeft = p(51f, 88f), size = Size(10 * u, 6 * u))

        // Ears (outer caramel, inner dark).
        drawPath(path { moveTo(27 * u, 33 * u); lineTo(31 * u, 7 * u); lineTo(45 * u, 26 * u); close() }, Caramel)
        drawPath(path { moveTo(51 * u, 26 * u); lineTo(65 * u, 7 * u); lineTo(69 * u, 33 * u); close() }, Caramel)
        drawPath(path { moveTo(31 * u, 28 * u); lineTo(33 * u, 14 * u); lineTo(41 * u, 26 * u); close() }, CaramelDark)
        drawPath(path { moveTo(55 * u, 26 * u); lineTo(63 * u, 14 * u); lineTo(65 * u, 28 * u); close() }, CaramelDark)

        // Head: wide at the top, pointed chin.
        drawPath(path {
            moveTo(25 * u, 36 * u)
            quadraticTo(27 * u, 22 * u, 48 * u, 22 * u)
            quadraticTo(69 * u, 22 * u, 71 * u, 36 * u)
            quadraticTo(71 * u, 50 * u, 48 * u, 61 * u)
            quadraticTo(25 * u, 50 * u, 25 * u, 36 * u)
            close()
        }, Caramel)
        // Cream cheeks and muzzle.
        drawPath(path {
            moveTo(29 * u, 40 * u)
            quadraticTo(38 * u, 45 * u, 48 * u, 46 * u)
            quadraticTo(58 * u, 45 * u, 67 * u, 40 * u)
            quadraticTo(65 * u, 51 * u, 48 * u, 59 * u)
            quadraticTo(31 * u, 51 * u, 29 * u, 40 * u)
            close()
        }, FoxCream)

        // Eyes: open ovals when awake, sleepy arcs when not.
        if (awake) {
            drawOval(Ink, topLeft = p(38f, 34f), size = Size(4 * u, 5.5f * u))
            drawOval(Ink, topLeft = p(54f, 34f), size = Size(4 * u, 5.5f * u))
        } else {
            val closedEye = Stroke(width = 1.8f * u)
            drawArc(Ink, 20f, 140f, useCenter = false, topLeft = p(36.5f, 33f), size = Size(7 * u, 6 * u), style = closedEye)
            drawArc(Ink, 20f, 140f, useCenter = false, topLeft = p(52.5f, 33f), size = Size(7 * u, 6 * u), style = closedEye)
        }
        drawCircle(Blush, radius = 3 * u, center = p(34f, 46f))
        drawCircle(Blush, radius = 3 * u, center = p(62f, 46f))
        drawCircle(Ink, radius = 2.3f * u, center = p(48f, 50f))  // nose
    }
}
