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

/**
 * The Tokyo Night palette (folke/tokyonight.nvim "night"). The blue that carries the top bars and buttons
 * is the deeper "blue0" shade, so the white text on it stays readable; the bright blue is kept for accents.
 */
private val TokyoNightColors = darkColorScheme(
    primary = Color(0xFF5A7BD0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D59A1),
    onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFFBB9AF7),
    onSecondary = Color(0xFF1A1B26),
    secondaryContainer = Color(0xFF3B3A63),
    onSecondaryContainer = Color(0xFFC0CAF5),
    tertiary = Color(0xFF7DCFFF),
    onTertiary = Color(0xFF1A1B26),
    background = Color(0xFF1A1B26),
    onBackground = Color(0xFFC0CAF5),
    surface = Color(0xFF1F2335),
    onSurface = Color(0xFFC0CAF5),
    surfaceVariant = Color(0xFF292E42),
    onSurfaceVariant = Color(0xFFA9B1D6),
    surfaceContainer = Color(0xFF24283B),
    surfaceContainerHigh = Color(0xFF292E42),
    surfaceContainerHighest = Color(0xFF2F3549),
    outline = Color(0xFF565F89),
    outlineVariant = Color(0xFF3B4261),
    error = Color(0xFFF7768E),
    onError = Color(0xFF1A1B26),
    errorContainer = Color(0xFF51283A),
    onErrorContainer = Color(0xFFFFC7D1),
)

@Composable
fun WindRmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    tokyoNight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        tokyoNight -> TokyoNightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
