package ru.franh.modemmon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Палитра взята из настроек темы footstrap на роутере (uci show footstrap):
// tint #161009, card #241a12, control #1a120b, line #372b21,
// accent #d99a4d, good #7fb88f, warn #e3b341, danger #e5634f.
val Bg = Color(0xFF161009)
val Surface = Color(0xFF241A12)
val SurfaceHigh = Color(0xFF1A120B)
val Outline = Color(0xFF372B21)
val OnSurface = Color(0xFFF0E6DA)
val Muted = Color(0xFFA08B74)
val Accent = Color(0xFFD99A4D)
val Good = Color(0xFF7FB88F)
val Warn = Color(0xFFE3B341)
val Bad = Color(0xFFE5634F)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF241A12),
    secondary = Good,
    onSecondary = Color(0xFF141A15),
    background = Bg,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Muted,
    outline = Outline,
    error = Bad
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        letterSpacing = (-2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.6.sp)
)

@Composable
fun ModemMonTheme(content: @Composable () -> Unit) {
    // Интерфейс всегда тёмный: так цифры читаются на улице.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        content = content
    )
}
