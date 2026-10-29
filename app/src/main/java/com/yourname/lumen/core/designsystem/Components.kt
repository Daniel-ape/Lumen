package com.yourname.lumen.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/**
 * Glass button. Every button shares the same heights (44dp, or 34dp when [compact]), keeps its text on
 * one line, and lights up brighter while focused, so focus is obvious on a TV from across the room.
 * [primary] is a slightly brighter glass for the main action on a screen.
 */
@Composable
fun LumenButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 34.dp else 44.dp)
            .tvFocusable(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.04f,
                onFocusChange = {
                    focused = it
                    onFocusChange(it)
                },
            )
            .glass(
                shape,
                when {
                    focused -> GlassLevel.Selected
                    primary -> GlassLevel.Primary
                    else -> GlassLevel.Control
                },
            )
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        LumenText(
            text = text,
            style = LumenTheme.typography.body.copy(
                fontSize = if (compact) 13.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Small round glass button holding a single character, such as the info "i". Same height as a normal button. */
@Composable
fun LumenCircleButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(44.dp)
            .tvFocusable(onClick = onClick, shape = CircleShape, focusedScale = 1.06f, onFocusChange = { focused = it })
            .glass(CircleShape, if (focused) GlassLevel.Selected else GlassLevel.Control),
        contentAlignment = Alignment.Center,
    ) {
        LumenText(
            text = text,
            style = LumenTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
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

/** A stable color hue for a title, used for placeholder tiles while artwork loads. */
fun hueOf(text: String): Float = (kotlin.math.abs(text.hashCode()) % 360).toFloat()
