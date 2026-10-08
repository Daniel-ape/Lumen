package com.yourname.lumen.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class LumenColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val dock: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
)

@Immutable
data class LumenTypography(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
)

object Dimens {
    val ScreenPadding = 48.dp
    val CardRadius = 12.dp
    val RowGap = 20.dp
    val SectionGap = 32.dp
}

private val DarkColors = LumenColors(
    background = Color(0xFF0B0B0D),
    surface = Color(0x0FFFFFFF),
    surfaceHigh = Color(0x1FFFFFFF),
    dock = Color(0xE61E1E24),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF9A9AA2),
    textTertiary = Color(0xFF63636B),
    accent = Color(0xFF4C8DFF),
)

private val DefaultTypography = LumenTypography(
    display = TextStyle(fontSize = 54.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    heading = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    label = TextStyle(fontSize = 16.sp),
)

private val LocalLumenColors = staticCompositionLocalOf { DarkColors }
private val LocalLumenTypography = staticCompositionLocalOf { DefaultTypography }

object LumenTheme {
    val colors: LumenColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLumenColors.current

    val typography: LumenTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalLumenTypography.current
}

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLumenColors provides DarkColors,
        LocalLumenTypography provides DefaultTypography,
        content = content,
    )
}
