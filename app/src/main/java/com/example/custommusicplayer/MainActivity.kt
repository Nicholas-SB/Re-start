package com.example.custommusicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.custommusicplayer.ui.MusicPlayerApp
import com.example.custommusicplayer.ui.theme.CustomMusicPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CustomMusicPlayerTheme {
                MusicPlayerApp()
            }
        }
    }
}
