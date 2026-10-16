package com.example.custommusicplayer.ui.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/** How full the phone's battery is (0..1), and whether it's charging. */
data class BatteryLevel(val fraction: Float, val charging: Boolean)

/** The phone's battery, re-read once a minute (it changes slowly, so that's plenty). */
@Composable
fun rememberBatteryLevel(): BatteryLevel? {
    val context = LocalContext.current
    val level by produceState<BatteryLevel?>(initialValue = null) {
        while (true) {
            value = readBattery(context)
            delay(60_000)
        }
    }
    return level
}

/**
 * Android keeps the latest battery info in a "sticky" broadcast. Registering with a null
 * receiver doesn't listen for changes; it just hands back that latest message.
 */
private fun readBattery(context: Context): BatteryLevel? {
    val intent = ContextCompat.registerReceiver(
        context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
    ) ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryLevel(level.toFloat() / scale, charging)
}

/** A little battery like the one in the iPod's title bar. */
@Composable
fun BatteryIcon(battery: BatteryLevel, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 26.dp, height = 13.dp)) {
        val nubWidth = size.width * 0.08f
        val bodyWidth = size.width - nubWidth
        val stroke = 1.5.dp.toPx()
        val corner = CornerRadius(2.dp.toPx())

        // Outline and the little nub on the right.
        drawRoundRect(DeviceColors.Text, topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyWidth - stroke, size.height - stroke), cornerRadius = corner, style = Stroke(stroke))
        drawRect(DeviceColors.Text, topLeft = Offset(bodyWidth, size.height * 0.3f), size = Size(nubWidth, size.height * 0.4f))

        // The charge: sage normally, warm red when low.
        val inset = stroke * 2
        val fillColor = if (battery.fraction < 0.15f && !battery.charging) LowBattery else DeviceColors.HighlightDark
        drawRect(fillColor, topLeft = Offset(inset, inset),
            size = Size((bodyWidth - inset * 2) * battery.fraction.coerceIn(0f, 1f), size.height - inset * 2))

        // A lightning bolt while charging.
        if (battery.charging) {
            val w = bodyWidth
            val h = size.height
            drawPath(Path().apply {
                moveTo(w * 0.55f, h * 0.12f)
                lineTo(w * 0.32f, h * 0.55f)
                lineTo(w * 0.48f, h * 0.55f)
                lineTo(w * 0.42f, h * 0.88f)
                lineTo(w * 0.68f, h * 0.42f)
                lineTo(w * 0.52f, h * 0.42f)
                close()
            }, DeviceColors.Text)
        }
    }
}

private val LowBattery = Color(0xFFB5543C)
