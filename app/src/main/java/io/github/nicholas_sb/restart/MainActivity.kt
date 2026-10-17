package io.github.nicholas_sb.restart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.nicholas_sb.restart.ui.device.RestartApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars (RestartApp picks the icon colors).
        enableEdgeToEdge()
        setContent {
            RestartApp()
        }
    }
}
