package com.yourname.lumen.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LumenText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LumenTheme.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun LumenButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
) {
    val colors = LumenTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .tvFocusable(onClick = onClick, shape = shape, onFocusChange = onFocusChange)
            .background(if (primary) Color.White else colors.surfaceHigh)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        LumenText(
            text = text,
            style = LumenTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (primary) Color.Black else colors.textPrimary,
        )
    }
}

@Composable
fun LumenProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(LumenTheme.colors.accent),
        )
    }
}

@Composable
fun LogoTile(initial: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B1B21)),
        contentAlignment = Alignment.Center,
    ) {
        LumenText(
            text = initial,
            style = LumenTheme.typography.heading,
            color = LumenTheme.colors.textSecondary,
        )
    }
}

fun hueColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue % 360f, saturation, value)))
