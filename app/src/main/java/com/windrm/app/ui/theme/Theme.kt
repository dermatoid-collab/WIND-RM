package com.windrm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = WindOrange,
    onPrimary = Color.White,
    secondary = WindOrangeDark,
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
)

private val DarkColors = darkColorScheme(
    primary = WindOrangeLight,
    onPrimary = Color.Black,
    secondary = WindOrange,
    surface = Color(0xFF1E1E1E),
    background = Color(0xFF121212),
)

@Composable
fun WindRmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
