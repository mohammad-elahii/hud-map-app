package com.example.hudmapapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Purple,
    secondary = SkyBlue,
    tertiary = Pink,

    background = Background,
    surface = White,

    onPrimary = White,
    onSecondary = White,
    onTertiary = White,

    onBackground = Black,
    onSurface = Black
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple,
    secondary = SkyBlue,
    tertiary = Pink,

    background = Black,
    surface = Black,

    onPrimary = White,
    onSecondary = White,
    onTertiary = White,

    onBackground = White,
    onSurface = White
)

@Composable
fun HudMapAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}