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

/** White text on a primary colour needs at least this contrast (WCAG large text is 3, normal text 4.5); under it the text turns black. */
private const val MIN_WHITE_ON_PRIMARY = 4.0

private fun channel(v: Int): Double {
    val c = v / 255.0
    return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}

private fun luminance(rgb: Long): Double =
    0.2126 * channel(((rgb shr 16) and 0xFF).toInt()) + 0.7152 * channel(((rgb shr 8) and 0xFF).toInt()) + 0.0722 * channel((rgb and 0xFF).toInt())

/** WCAG contrast ratio between two 0xRRGGBB colours, 1 to 21. */
internal fun contrastRatio(a: Long, b: Long): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * One colour palette, as 0xRRGGBB values. Text on the bars and buttons is white on a deep [primary] and black on a light
 * one (see [MIN_WHITE_ON_PRIMARY]); the palette's own bright accent colours go to [secondary] and [tertiary]. [error] reads
 * at 4.5 or more on the background.
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
        // Text and icons on the bars and buttons: white on a deep primary, black on a light one (gold, grey, near white).
        val onPrimary = if (contrastRatio(0xFFFFFF, primary) >= MIN_WHITE_ON_PRIMARY) Color.White else Color.Black
        return if (dark) {
            darkColorScheme(
                primary = c(primary), onPrimary = onPrimary,
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
                primary = c(primary), onPrimary = onPrimary,
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
        onBackground = 0xC0CAF5, onVariant = 0xA9B1D6, primary = 0x4E71CD, primaryContainer = 0x3D59A1, onPrimaryContainer = 0xC0CAF5,
        secondary = 0xBB9AF7, tertiary = 0x7DCFFF, outline = 0x565F89, outlineVariant = 0x3B4261, error = 0xF7768E, errorContainer = 0x51283A,
    ),
    // Arctic Ice Studio's Nord
    ThemeMode.NORD to Palette(
        dark = true, background = 0x2E3440, surface = 0x3B4252, surfaceHigh = 0x434C5E, surfaceHighest = 0x4C566A,
        onBackground = 0xECEFF4, onVariant = 0xD8DEE9, primary = 0x5578A4, primaryContainer = 0x4C6A8F, onPrimaryContainer = 0xECEFF4,
        secondary = 0x88C0D0, tertiary = 0xA3BE8C, outline = 0x616E88, outlineVariant = 0x4C566A, error = 0xCF8A90, errorContainer = 0x5B3438,
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
        onBackground = 0xCDD6F4, onVariant = 0xBAC2DE, primary = 0x7764D8, primaryContainer = 0x5B4DA8, onPrimaryContainer = 0xE0D8FF,
        secondary = 0xF5C2E7, tertiary = 0x89DCEB, outline = 0x6C7086, outlineVariant = 0x45475A, error = 0xF38BA8, errorContainer = 0x5A2D3A,
    ),
    ThemeMode.ONE_DARK to Palette(
        dark = true, background = 0x282C34, surface = 0x2C313A, surfaceHigh = 0x353B45, surfaceHighest = 0x3E4451,
        onBackground = 0xABB2BF, onVariant = 0x9DA5B4, primary = 0x4773CA, primaryContainer = 0x3A5A9B, onPrimaryContainer = 0xDCE8FF,
        secondary = 0xC678DD, tertiary = 0x56B6C2, outline = 0x5C6370, outlineVariant = 0x3E4451, error = 0xE06C75, errorContainer = 0x58323A,
    ),
    ThemeMode.SOLARIZED_DARK to Palette(
        dark = true, background = 0x002B36, surface = 0x073642, surfaceHigh = 0x0E4350, surfaceHighest = 0x15505E,
        onBackground = 0x93A1A1, onVariant = 0x839496, primary = 0x1F7BBF, primaryContainer = 0x14557F, onPrimaryContainer = 0xD6ECFA,
        secondary = 0x2AA198, tertiary = 0xB58900, outline = 0x586E75, outlineVariant = 0x0E4350, error = 0xE56563, errorContainer = 0x4A1F1E,
    ),
    // True black for OLED screens, with the app's own orange
    ThemeMode.AMOLED to Palette(
        dark = true, background = 0x000000, surface = 0x0A0A0A, surfaceHigh = 0x161616, surfaceHighest = 0x222222,
        onBackground = 0xE6E1E5, onVariant = 0xCAC4D0, primary = 0xD44215, primaryContainer = 0x8A2C0C, onPrimaryContainer = 0xFFDBCF,
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
    // WIND-RM palettes (the same ones as in SUNW-RM and ERG-RM) and the four minimalist families. The error colours and,
    // on three of the light minimalist families, the secondary text colour are a little deeper than in the apps they
    // come from, so they read at 4.5 or more on their background.
    ThemeMode.ROSE_PINE to Palette(dark = true, background = 0x12101B, surface = 0x1F1D2E, surfaceHigh = 0x252334, surfaceHighest = 0x2C2A3B, onBackground = 0xE0DEF4, onVariant = 0x908CAA, primary = 0xF6C177, primaryContainer = 0x9F7E54, onPrimaryContainer = 0xE0DEF4, secondary = 0x9CCFD8, tertiary = 0xEB6F92, outline = 0x6E6A86, outlineVariant = 0x46445A, error = 0xEB6F92, errorContainer = 0x743B51),
    ThemeMode.EVERFOREST to Palette(dark = true, background = 0x272E33, surface = 0x343F44, surfaceHigh = 0x394347, surfaceHighest = 0x3E484B, onBackground = 0xD3C6AA, onVariant = 0xB6AF9A, primary = 0xDBBC7F, primaryContainer = 0x978662, onPrimaryContainer = 0xD3C6AA, secondary = 0x7FBBB3, tertiary = 0xE69875, outline = 0x859289, outlineVariant = 0x5C6866, error = 0xE67E80, errorContainer = 0x7D5256),
    ThemeMode.NIGHT_OWL to Palette(dark = true, background = 0x01111D, surface = 0x0B2942, surfaceHigh = 0x112E47, surfaceHighest = 0x18354D, onBackground = 0xD6DEEB, onVariant = 0x8AA3BF, primary = 0xECC48D, primaryContainer = 0x938062, onPrimaryContainer = 0xD6DEEB, secondary = 0x82AAFF, tertiary = 0xF78C6C, outline = 0x5F7E97, outlineVariant = 0x35546C, error = 0xEF5350, errorContainer = 0x6C2F34),
    ThemeMode.PALENIGHT to Palette(dark = true, background = 0x232635, surface = 0x32364A, surfaceHigh = 0x363A4F, surfaceHighest = 0x3B3F54, onBackground = 0xBFC7E8, onVariant = 0x9AA3CC, primary = 0xFFCB6B, primaryContainer = 0xAB8C56, onPrimaryContainer = 0xBFC7E8, secondary = 0x82AAFF, tertiary = 0xF78C6C, outline = 0x676E95, outlineVariant = 0x4C5270, error = 0xF07178, errorContainer = 0x7F4853),
    ThemeMode.AYU_MIRAGE to Palette(dark = true, background = 0x171B24, surface = 0x2A3142, surfaceHigh = 0x2F3646, surfaceHighest = 0x353B4A, onBackground = 0xCCCAC2, onVariant = 0x9C9A93, primary = 0xFFD173, primaryContainer = 0xA78C55, onPrimaryContainer = 0xCCCAC2, secondary = 0x73D0FF, tertiary = 0xFFAD66, outline = 0x707A8C, outlineVariant = 0x4D5667, error = 0xF28779, errorContainer = 0x7A4C4A),
    ThemeMode.VINO to Palette(dark = true, background = 0x1F0F13, surface = 0x3A1D25, surfaceHigh = 0x3F232A, surfaceHighest = 0x462931, onBackground = 0xF1DADA, onVariant = 0xC9A5A8, primary = 0x9C8E92, primaryContainer = 0x6C5E62, onPrimaryContainer = 0xF1DADA, secondary = 0x7A9CE0, tertiary = 0xF29A5E, outline = 0x8A6469, outlineVariant = 0x624047, error = 0xE8665F, errorContainer = 0x793635),
    ThemeMode.METEOBLUE to Palette(dark = true, background = 0x11264D, surface = 0x1B3F78, surfaceHigh = 0x22457C, surfaceHighest = 0x2A4B81, onBackground = 0xFFFFFF, onVariant = 0xBCD0F0, primary = 0x8D9FBC, primaryContainer = 0x5E7192, onPrimaryContainer = 0xFFFFFF, secondary = 0x4DA3FF, tertiary = 0xFFA94D, outline = 0x7F9BCB, outlineVariant = 0x4D6DA2, error = 0xFF6B8A, errorContainer = 0x7C4568),
    ThemeMode.TURCHESE to Palette(dark = true, background = 0x00535B, surface = 0x006F79, surfaceHigh = 0x08737D, surfaceHighest = 0x117882, onBackground = 0xFFFFFF, onVariant = 0xCFF3F6, primary = 0x80B7BC, primaryContainer = 0x4F9197, onPrimaryContainer = 0xFFFFFF, secondary = 0x38C6D9, tertiary = 0xFFB74D, outline = 0x6FB5BC, outlineVariant = 0x38929A, error = 0xFF9FB6, errorContainer = 0x736073),
    ThemeMode.VIOLA to Palette(dark = true, background = 0x3A157F, surface = 0x5527B8, surfaceHigh = 0x5A2DBA, surfaceHighest = 0x6035BD, onBackground = 0xFFFFFF, onVariant = 0xDCCCFF, primary = 0xAA93DC, primaryContainer = 0x7F63B9, onPrimaryContainer = 0xFFFFFF, secondary = 0x5CC8FF, tertiary = 0xFF9F5A, outline = 0x9C86E0, outlineVariant = 0x7856CC, error = 0xFF5FA2, errorContainer = 0x93368F),
    ThemeMode.CORALLO to Palette(dark = true, background = 0x85201B, surface = 0xA82A24, surfaceHigh = 0xAB302A, surfaceHighest = 0xAE3731, onBackground = 0xFFF1EF, onVariant = 0xFFCFC9, primary = 0xD49492, primaryContainer = 0xB66865, onPrimaryContainer = 0xFFF1EF, secondary = 0x7FB6FF, tertiary = 0xFFC857, outline = 0xE08E86, outlineVariant = 0xC45C55, error = 0xFFB3A7, errorContainer = 0xBC625A),
    ThemeMode.LIME to Palette(dark = true, background = 0x0B3D16, surface = 0x14602A, surfaceHigh = 0x1B6530, surfaceHighest = 0x226A36, onBackground = 0xF0FFE8, onVariant = 0xBDEBB0, primary = 0x8AB094, primaryContainer = 0x5A8464, onPrimaryContainer = 0xF0FFE8, secondary = 0x4DD0E1, tertiary = 0xFFB74D, outline = 0x6DB760, outlineVariant = 0x408C45, error = 0xFF8A80, errorContainer = 0x796046),
    ThemeMode.ARDESIA to Palette(dark = false, background = 0xDDE3EA, surface = 0xCBD3DD, surfaceHigh = 0xC6CED8, surfaceHighest = 0xC0C8D2, onBackground = 0x1F2937, onVariant = 0x475569, primary = 0x98A3B2, primaryContainer = 0xE2E5E9, onPrimaryContainer = 0x1F2937, secondary = 0x2563EB, tertiary = 0xEA580C, outline = 0x8793A3, outlineVariant = 0xA9B3C0, error = 0xC31F1F, errorContainer = 0xF9D8D8),
    ThemeMode.SALVIA to Palette(dark = false, background = 0xDCE4D6, surface = 0xC9D6C1, surfaceHigh = 0xC4D1BC, surfaceHighest = 0xBECBB6, onBackground = 0x22301F, onVariant = 0x425A3C, primary = 0x9AA993, primaryContainer = 0xE3E7E1, onPrimaryContainer = 0x22301F, secondary = 0x2F6F8F, tertiary = 0xC2571A, outline = 0x8A9A84, outlineVariant = 0xAAB8A2, error = 0xB03947, errorContainer = 0xF1DCDE),
    ThemeMode.DUNA to Palette(dark = false, background = 0xE8DDC8, surface = 0xD9CBB0, surfaceHigh = 0xD4C6AC, surfaceHighest = 0xCFC1A7, onBackground = 0x3A2F1E, onVariant = 0x5E4F37, primary = 0xAA9B80, primaryContainer = 0xE7E3DB, onPrimaryContainer = 0x3A2F1E, secondary = 0x2F6B8A, tertiary = 0xC0561A, outline = 0x9A8B70, outlineVariant = 0xBAAB90, error = 0xAE392E, errorContainer = 0xF1DCD9),
    ThemeMode.ILMETEO to Palette(dark = false, background = 0xFFD79A, surface = 0xFFC066, surfaceHigh = 0xF9BB63, surfaceHighest = 0xF1B55F, onBackground = 0x2B1500, onVariant = 0x5C3A10, primary = 0xCA9451, primaryContainer = 0xF0E1CE, onPrimaryContainer = 0x2B1500, secondary = 0x1E6FB8, tertiary = 0xE65100, outline = 0xB8864A, outlineVariant = 0xDCA358, error = 0xB92727, errorContainer = 0xF7DADA),
    ThemeMode.AZZURRO to Palette(dark = false, background = 0xCDEBFF, surface = 0x9ED4FF, surfaceHigh = 0x99CFF9, surfaceHighest = 0x94C8F2, onBackground = 0x06203D, onVariant = 0x1F4A75, primary = 0x78A9D6, primaryContainer = 0xD9E7F4, onPrimaryContainer = 0x06203D, secondary = 0x0A6CE0, tertiary = 0xE85D04, outline = 0x6C9BC8, outlineVariant = 0x85B8E4, error = 0xC52639, errorContainer = 0xF8D8DC),
    ThemeMode.GIALLO to Palette(dark = false, background = 0xFFF0A8, surface = 0xFFE066, surfaceHigh = 0xF9DA63, surfaceHighest = 0xF1D45F, onBackground = 0x2A2000, onVariant = 0x5C4A00, primary = 0xC6AC3C, primaryContainer = 0xEFE8C8, onPrimaryContainer = 0x2A2000, secondary = 0x1E70C8, tertiary = 0xE65100, outline = 0xB39B2E, outlineVariant = 0xD9BE4A, error = 0xCD2B2B, errorContainer = 0xF7DADA),
    ThemeMode.NEUTRAL_DARK to Palette(dark = true, background = 0x101010, surface = 0x1C1C1C, surfaceHigh = 0x2B2B2B, surfaceHighest = 0x474747, onBackground = 0xF5F5F5, onVariant = 0xBDBDBD, primary = 0xF5F5F5, primaryContainer = 0x2B2B2B, onPrimaryContainer = 0xF5F5F5, secondary = 0x777777, tertiary = 0x777777, outline = 0x777777, outlineVariant = 0x2B2B2B, error = 0xD9776E, errorContainer = 0x382523),
    ThemeMode.NEUTRAL_LIGHT to Palette(dark = false, background = 0xF0F0F0, surface = 0xFAFAFA, surfaceHigh = 0xDCDCDC, surfaceHighest = 0xC3C3C3, onBackground = 0x282828, onVariant = 0x6C6C6C, primary = 0x282828, primaryContainer = 0xDCDCDC, onPrimaryContainer = 0x282828, secondary = 0xA8A8A8, tertiary = 0xA8A8A8, outline = 0xA8A8A8, outlineVariant = 0xDCDCDC, error = 0xA6433B, errorContainer = 0xE3D1CF),
    ThemeMode.SEPIA_DARK to Palette(dark = true, background = 0x181512, surface = 0x28231F, surfaceHigh = 0x39322B, surfaceHighest = 0x534D46, onBackground = 0xF5F0E9, onVariant = 0xC8BCAC, primary = 0xF5F0E9, primaryContainer = 0x39322B, onPrimaryContainer = 0xF5F0E9, secondary = 0x887B6E, tertiary = 0x887B6E, outline = 0x887B6E, outlineVariant = 0x39322B, error = 0xD9776E, errorContainer = 0x3F2924),
    ThemeMode.SEPIA_LIGHT to Palette(dark = false, background = 0xF3F0EA, surface = 0xFCFAF6, surfaceHigh = 0xE4DDD3, surfaceHighest = 0xCBC4BB, onBackground = 0x302A25, onVariant = 0x756B60, primary = 0x302A25, primaryContainer = 0xE4DDD3, onPrimaryContainer = 0x302A25, secondary = 0xB3A99B, tertiary = 0xB3A99B, outline = 0xB3A99B, outlineVariant = 0xE4DDD3, error = 0xA6433B, errorContainer = 0xE5D1CA),
    ThemeMode.SAGE_DARK to Palette(dark = true, background = 0x151A16, surface = 0x242C25, surfaceHigh = 0x354037, surfaceHighest = 0x4F5951, onBackground = 0xF1F5F0, onVariant = 0xC0CDC2, primary = 0xF1F5F0, primaryContainer = 0x354037, onPrimaryContainer = 0xF1F5F0, secondary = 0x819084, tertiary = 0x819084, outline = 0x819084, outlineVariant = 0x354037, error = 0xD9776E, errorContainer = 0x3C2D28),
    ThemeMode.SAGE_LIGHT to Palette(dark = false, background = 0xF0F3EF, surface = 0xFAFCF9, surfaceHigh = 0xDFE6DC, surfaceHighest = 0xC6CEC4, onBackground = 0x2C382D, onVariant = 0x647262, primary = 0x2C382D, primaryContainer = 0xDFE6DC, onPrimaryContainer = 0x2C382D, secondary = 0xACB9A9, tertiary = 0xACB9A9, outline = 0xACB9A9, outlineVariant = 0xDFE6DC, error = 0xA6433B, errorContainer = 0xE3D3CF),
    ThemeMode.LAVENDER_DARK to Palette(dark = true, background = 0x17151D, surface = 0x25232E, surfaceHigh = 0x373442, surfaceHighest = 0x524F5C, onBackground = 0xF5F2F9, onVariant = 0xC8C3D3, primary = 0xF5F2F9, primaryContainer = 0x373442, onPrimaryContainer = 0xF5F2F9, secondary = 0x898397, tertiary = 0x898397, outline = 0x898397, outlineVariant = 0x373442, error = 0xD9776E, errorContainer = 0x3E292D),
    ThemeMode.LAVENDER_LIGHT to Palette(dark = false, background = 0xF2F0F6, surface = 0xFCFBFD, surfaceHigh = 0xE4DFEB, surfaceHighest = 0xCBC6D2, onBackground = 0x302B3B, onVariant = 0x716A81, primary = 0x302B3B, primaryContainer = 0xE4DFEB, onPrimaryContainer = 0x302B3B, secondary = 0xB2A9C0, tertiary = 0xB2A9C0, outline = 0xB2A9C0, outlineVariant = 0xE4DFEB, error = 0xA6433B, errorContainer = 0xE4D1D4),
)

/** The colours that sum up [mode], for the little preview next to its name in Settings. */
fun themeSwatches(mode: ThemeMode): List<Color> = Palettes[mode]?.swatches() ?: when (mode) {
    ThemeMode.STOCK_LIGHT -> lightColorScheme().let { listOf(it.background, it.primary, it.secondary, it.tertiary) }
    ThemeMode.STOCK_DARK -> darkColorScheme().let { listOf(it.background, it.primary, it.secondary, it.tertiary) }
    ThemeMode.STOCK_SYSTEM -> listOf(Color(0xFFFFFBFE), Color(0xFF1C1B1F), lightColorScheme().primary, darkColorScheme().primary)
    // WIND-RM Legacy: the orange look, light and dark.
    else -> listOf(Color(0xFFFAFAFA), Color(0xFF121212), WindOrange, WindOrangeLight)
}

@Composable
fun WindRmTheme(
    mode: ThemeMode = ThemeMode.WIND_RM_LEGACY,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = Palettes[mode]?.scheme() ?: when (mode) {
        ThemeMode.STOCK_SYSTEM -> if (systemDark) darkColorScheme() else lightColorScheme()
        ThemeMode.STOCK_LIGHT -> lightColorScheme()
        ThemeMode.STOCK_DARK -> darkColorScheme()
        // WIND-RM Legacy: the original orange look, following the phone.
        else -> if (systemDark) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
