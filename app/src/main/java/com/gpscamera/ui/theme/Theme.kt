package com.gpscamera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00BFA5)
private val TealDark = Color(0xFF00897B)
private val Amber = Color(0xFFFFB300)
private val DarkBg = Color(0xFF0E1116)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color.Black,
    secondary = Amber,
    onSecondary = Color.Black,
    background = DarkBg,
    surface = Color(0xFF161B22),
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColors = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color.Black
)

@Composable
fun GpsCameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
