package com.windrm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.windrm.app.settings.ThemeMode

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
 * One colour palette, as 0xRRGGBB values. The bars and buttons carry white text, so [primary] is the deeper
 * shade of the palette's blue, purple or orange (white stays readable on it); the palette's own bright
 * accent colours go to [secondary] and [tertiary].
 */
private class Palette(
    val dark: Boolean,
    val background: Long,
    val surface: Long,
    val surfaceHigh: Long,
    val surfaceHighest: Long,
    val onBackground: Long,
    val onVariant: Long,
    val primary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val tertiary: Long,
    val outline: Long,
    val outlineVariant: Long,
    val error: Long,
    val errorContainer: Long,
) {
    private fun c(rgb: Long) = Color(0xFF000000 or rgb)

    fun scheme(): ColorScheme {
        val onAccent = if (dark) c(background) else Color.White
        return if (dark) {
            darkColorScheme(
                primary = c(primary), onPrimary = Color.White,
                primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
                secondary = c(secondary), onSecondary = onAccent,
                secondaryContainer = c(surfaceHigh), onSecondaryContainer = c(onBackground),
                tertiary = c(tertiary), onTertiary = onAccent,
                background = c(background), onBackground = c(onBackground),
                surface = c(surface), onSurface = c(onBackground),
                surfaceVariant = c(surfaceHigh), onSurfaceVariant = c(onVariant),
                surfaceContainerLowest = c(background), surfaceContainerLow = c(surface), surfaceContainer = c(surface),
                surfaceContainerHigh = c(surfaceHigh), surfaceContainerHighest = c(surfaceHighest),
                outline = c(outline), outlineVariant = c(outlineVariant),
                error = c(error), onError = c(background),
                errorContainer = c(errorContainer), onErrorContainer = c(onBackground),
            )
        } else {
            lightColorScheme(
                primary = c(primary), onPrimary = Color.White,
                primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
                secondary = c(secondary), onSecondary = onAccent,
                secondaryContainer = c(surfaceHigh), onSecondaryContainer = c(onBackground),
                tertiary = c(tertiary), onTertiary = onAccent,
                background = c(background), onBackground = c(onBackground),
                surface = c(surface), onSurface = c(onBackground),
                surfaceVariant = c(surfaceHigh), onSurfaceVariant = c(onVariant),
                surfaceContainerLowest = c(background), surfaceContainerLow = c(surface), surfaceContainer = c(surface),
                surfaceContainerHigh = c(surfaceHigh), surfaceContainerHighest = c(surfaceHighest),
                outline = c(outline), outlineVariant = c(outlineVariant),
                error = c(error), onError = Color.White,
                errorContainer = c(errorContainer), onErrorContainer = c(onBackground),
            )
        }
    }

    /** Four colours that say what the palette looks like: page, bar, and the two accents. */
    fun swatches(): List<Color> = listOf(c(background), c(primary), c(secondary), c(tertiary))
}

private val Palettes: Map<ThemeMode, Palette> = mapOf(
    // folke/tokyonight.nvim "night"
    ThemeMode.TOKYO_NIGHT to Palette(
        dark = true, background = 0x1A1B26, surface = 0x1F2335, surfaceHigh = 0x292E42, surfaceHighest = 0x2F3549,
        onBackground = 0xC0CAF5, onVariant = 0xA9B1D6, primary = 0x5A7BD0, primaryContainer = 0x3D59A1, onPrimaryContainer = 0xC0CAF5,
        secondary = 0xBB9AF7, tertiary = 0x7DCFFF, outline = 0x565F89, outlineVariant = 0x3B4261, error = 0xF7768E, errorContainer = 0x51283A,
    ),
    // Arctic Ice Studio's Nord
    ThemeMode.NORD to Palette(
        dark = true, background = 0x2E3440, surface = 0x3B4252, surfaceHigh = 0x434C5E, surfaceHighest = 0x4C566A,
        onBackground = 0xECEFF4, onVariant = 0xD8DEE9, primary = 0x5E81AC, primaryContainer = 0x4C6A8F, onPrimaryContainer = 0xECEFF4,
        secondary = 0x88C0D0, tertiary = 0xA3BE8C, outline = 0x616E88, outlineVariant = 0x4C566A, error = 0xBF616A, errorContainer = 0x5B3438,
    ),
    ThemeMode.DRACULA to Palette(
        dark = true, background = 0x282A36, surface = 0x2F3142, surfaceHigh = 0x44475A, surfaceHighest = 0x51556B,
        onBackground = 0xF8F8F2, onVariant = 0xC9CBE0, primary = 0x7E5FC9, primaryContainer = 0x5B4A99, onPrimaryContainer = 0xF1E9FF,
        secondary = 0xFF79C6, tertiary = 0x8BE9FD, outline = 0x6272A4, outlineVariant = 0x44475A, error = 0xFF5555, errorContainer = 0x5A2A2F,
    ),
    ThemeMode.GRUVBOX_DARK to Palette(
        dark = true, background = 0x282828, surface = 0x32302F, surfaceHigh = 0x3C3836, surfaceHighest = 0x504945,
        onBackground = 0xEBDBB2, onVariant = 0xD5C4A1, primary = 0xC4560A, primaryContainer = 0x8F3F08, onPrimaryContainer = 0xFBE9CF,
        secondary = 0xB8BB26, tertiary = 0x83A598, outline = 0x928374, outlineVariant = 0x504945, error = 0xFB4934, errorContainer = 0x5C2420,
    ),
    ThemeMode.CATPPUCCIN_MOCHA to Palette(
        dark = true, background = 0x1E1E2E, surface = 0x262637, surfaceHigh = 0x313244, surfaceHighest = 0x45475A,
        onBackground = 0xCDD6F4, onVariant = 0xBAC2DE, primary = 0x7B68D9, primaryContainer = 0x5B4DA8, onPrimaryContainer = 0xE0D8FF,
        secondary = 0xF5C2E7, tertiary = 0x89DCEB, outline = 0x6C7086, outlineVariant = 0x45475A, error = 0xF38BA8, errorContainer = 0x5A2D3A,
    ),
    ThemeMode.ONE_DARK to Palette(
        dark = true, background = 0x282C34, surface = 0x2C313A, surfaceHigh = 0x353B45, surfaceHighest = 0x3E4451,
        onBackground = 0xABB2BF, onVariant = 0x9DA5B4, primary = 0x4D78CC, primaryContainer = 0x3A5A9B, onPrimaryContainer = 0xDCE8FF,
        secondary = 0xC678DD, tertiary = 0x56B6C2, outline = 0x5C6370, outlineVariant = 0x3E4451, error = 0xE06C75, errorContainer = 0x58323A,
    ),
    ThemeMode.SOLARIZED_DARK to Palette(
        dark = true, background = 0x002B36, surface = 0x073642, surfaceHigh = 0x0E4350, surfaceHighest = 0x15505E,
        onBackground = 0x93A1A1, onVariant = 0x839496, primary = 0x1F7BBF, primaryContainer = 0x14557F, onPrimaryContainer = 0xD6ECFA,
        secondary = 0x2AA198, tertiary = 0xB58900, outline = 0x586E75, outlineVariant = 0x0E4350, error = 0xDC322F, errorContainer = 0x4A1F1E,
    ),
    // True black for OLED screens, with the app's own orange
    ThemeMode.AMOLED to Palette(
        dark = true, background = 0x000000, surface = 0x0A0A0A, surfaceHigh = 0x161616, surfaceHighest = 0x222222,
        onBackground = 0xE6E1E5, onVariant = 0xCAC4D0, primary = 0xD84315, primaryContainer = 0x8A2C0C, onPrimaryContainer = 0xFFDBCF,
        secondary = 0xFF8A65, tertiary = 0x4285F4, outline = 0x6A6A6A, outlineVariant = 0x2E2E2E, error = 0xFF6B6B, errorContainer = 0x5A1E1E,
    ),
    ThemeMode.SOLARIZED_LIGHT to Palette(
        dark = false, background = 0xFDF6E3, surface = 0xF5EEDA, surfaceHigh = 0xEEE8D5, surfaceHighest = 0xE6DFC8,
        onBackground = 0x073642, onVariant = 0x586E75, primary = 0x1F7BBF, primaryContainer = 0xBBDDF3, onPrimaryContainer = 0x073642,
        secondary = 0x2AA198, tertiary = 0xB58900, outline = 0x93A1A1, outlineVariant = 0xD9D2BA, error = 0xDC322F, errorContainer = 0xF8D3CF,
    ),
    ThemeMode.CATPPUCCIN_LATTE to Palette(
        dark = false, background = 0xEFF1F5, surface = 0xE6E9EF, surfaceHigh = 0xDCE0E8, surfaceHighest = 0xCCD0DA,
        onBackground = 0x4C4F69, onVariant = 0x6C6F85, primary = 0x8839EF, primaryContainer = 0xE2CFFB, onPrimaryContainer = 0x2E1A52,
        secondary = 0x1E66F5, tertiary = 0x179299, outline = 0x9CA0B0, outlineVariant = 0xBCC0CC, error = 0xD20F39, errorContainer = 0xF8CCD4,
    ),
    ThemeMode.GRUVBOX_LIGHT to Palette(
        dark = false, background = 0xFBF1C7, surface = 0xF2E5BC, surfaceHigh = 0xEBDBB2, surfaceHighest = 0xD5C4A1,
        onBackground = 0x3C3836, onVariant = 0x504945, primary = 0xAF3A03, primaryContainer = 0xFAD7B5, onPrimaryContainer = 0x5A1F00,
        secondary = 0x79740E, tertiary = 0x427B58, outline = 0x928374, outlineVariant = 0xD5C4A1, error = 0x9D0006, errorContainer = 0xF4C7C3,
    ),
)

/** The colours that sum up [mode], for the little preview next to its name in Settings. */
fun themeSwatches(mode: ThemeMode): List<Color> = Palettes[mode]?.swatches() ?: when (mode) {
    ThemeMode.LIGHT -> listOf(Color(0xFFFAFAFA), WindOrange, WindOrangeDark, WindOrangeLight)
    ThemeMode.DARK -> listOf(Color(0xFF121212), WindOrangeLight, WindOrange, WindBlue)
    else -> listOf(Color(0xFFFAFAFA), Color(0xFF121212), WindOrange, WindOrangeLight)
}

@Composable
fun WindRmTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val colors = Palettes[mode]?.scheme() ?: if (mode.dark ?: isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
