package com.yourname.lumen.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
    background = Color(0xFF0D0F16),
    surface = Color(0x0FFFFFFF),
    surfaceHigh = Color(0x1FFFFFFF),
    dock = Color(0xE6181B26),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0A6BC),
    textTertiary = Color(0xFF686E84),
    accent = Color(0xFF7C9BFF),
)

private val DefaultTypography = LumenTypography(
    display = TextStyle(fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    heading = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    label = TextStyle(fontSize = 14.sp),
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

/** Settings that change how things are drawn. They are fixed for a whole session. */
@Immutable
data class AppearanceFlags(val reducedMotion: Boolean = false, val reducedBackground: Boolean = false)

val LocalAppearanceFlags = staticCompositionLocalOf { AppearanceFlags() }

/** Animation length: 0 when the user chose Reduced Motion. */
@Composable
@ReadOnlyComposable
fun motionMs(ms: Int): Int = if (LocalAppearanceFlags.current.reducedMotion) 0 else ms

/**
 * [density] scales every dp and sp in the app at once (0.9 compact, 1.0 comfortable, 1.1 spacious).
 * [textScale] scales text only.
 */
@Composable
fun LumenTheme(
    density: Float = 0.9f,
    textScale: Float = 1f,
    flags: AppearanceFlags = AppearanceFlags(),
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = base.density * density, fontScale = base.fontScale * textScale),
        LocalAppearanceFlags provides flags,
        LocalLumenColors provides DarkColors,
        LocalLumenTypography provides DefaultTypography,
        content = content,
    )
}
