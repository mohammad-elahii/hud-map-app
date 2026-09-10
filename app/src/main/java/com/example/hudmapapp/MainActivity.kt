package com.example.hudmapapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.hudmapapp.ui.navigation.AppNavigation
import com.example.hudmapapp.ui.theme.HudMapAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            HudMapAppTheme {
                AppNavigation()
            }
        }
    }
}