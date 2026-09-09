package ru.vzvod.konspekt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Палитра взята из предметной области: полевая зелень, бумага цвета хаки, латунь.
private val Field = Color(0xFF38492F)
private val FieldLight = Color(0xFFA9C08C)
private val Brass = Color(0xFF8A6A14)
private val BrassLight = Color(0xFFD9B451)
private val Paper = Color(0xFFF1ECDE)
private val PaperCard = Color(0xFFFBF8F0)
private val Ink = Color(0xFF1B1F1A)
private val Moss = Color(0xFF5E6B4F)

private val NightBg = Color(0xFF12150F)
private val NightCard = Color(0xFF1D2118)
private val NightInk = Color(0xFFE6E3D6)

private val Light = lightColorScheme(
    primary = Field,
    onPrimary = Color(0xFFF6F3E9),
    primaryContainer = Color(0xFFDCE4CE),
    onPrimaryContainer = Color(0xFF1B2415),
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E5D7),
    onSecondaryContainer = Color(0xFF232719),
    tertiary = Brass,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E4BE),
    onTertiaryContainer = Color(0xFF3B2C00),
    background = Paper,
    onBackground = Ink,
    surface = PaperCard,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE4DFCF),
    onSurfaceVariant = Color(0xFF4A4C42),
    outline = Color(0xFF8C8B7C),
    outlineVariant = Color(0xFFCFC9B6),
    error = Color(0xFF8C2F26)
)

private val Dark = darkColorScheme(
    primary = FieldLight,
    onPrimary = Color(0xFF1B2415),
    primaryContainer = Color(0xFF34452B),
    onPrimaryContainer = Color(0xFFD9E6C4),
    secondary = Color(0xFFB9C2A8),
    onSecondary = Color(0xFF232719),
    secondaryContainer = Color(0xFF3A4033),
    onSecondaryContainer = Color(0xFFDCE0D0),
    tertiary = BrassLight,
    onTertiary = Color(0xFF3B2C00),
    tertiaryContainer = Color(0xFF5A4508),
    onTertiaryContainer = Color(0xFFF7E3B4),
    background = NightBg,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF2A2F24),
    onSurfaceVariant = Color(0xFFBFC0B0),
    outline = Color(0xFF6E7263),
    outlineVariant = Color(0xFF3A3F33),
    error = Color(0xFFE0857B)
)

/** Заголовки — с плотным трекингом, тело документа — антиква, как в напечатанном конспекте. */
private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp, lineHeight = 33.sp, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp
    )
)

@Composable
fun KonspektTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        content = content
    )
}
