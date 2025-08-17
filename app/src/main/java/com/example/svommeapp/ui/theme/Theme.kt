package com.example.svommeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.svommeapp.ThemeMode

private val LightColors = lightColors(
    primary = Color(0xFF0066CC),
    primaryVariant = Color(0xFF004999),
    secondary = Color(0xFF03DAC5),
    background = Color(0xFFFDFDFD),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

private val DarkColors = darkColors(
    primary = Color(0xFF5B9BD5),
    primaryVariant = Color(0xFF004999),
    secondary = Color(0xFF03DAC5)
)

private val AppTypography = Typography()
private val AppShapes = Shapes()

@Composable
fun SvommeTheme(mode: ThemeMode = ThemeMode.AUTO, content: @Composable () -> Unit) {
    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colors = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
