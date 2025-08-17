package com.example.svommeapp.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColors(
    primary = Color(0xFF5B9BD5),
    primaryVariant = Color(0xFF004999),
    secondary = Color(0xFF03DAC5)
)

private val AppTypography = Typography()
private val AppShapes = Shapes()

@Composable
fun SvommeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = DarkColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
