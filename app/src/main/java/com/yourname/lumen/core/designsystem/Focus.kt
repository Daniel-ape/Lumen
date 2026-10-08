package com.yourname.lumen.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The one focus treatment used by every focusable thing in the app:
 * a short scale-up and a soft light outline. Click (OK / Enter) calls [onClick].
 */
@Composable
fun Modifier.tvFocusable(
    onClick: () -> Unit,
    shape: Shape,
    focusedScale: Float = 1.06f,
    onFocusChange: (Boolean) -> Unit = {},
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "focusScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Color.White.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "focusBorder",
    )
    return this
        .onFocusChanged {
            focused = it.isFocused
            onFocusChange(it.isFocused)
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(2.dp, borderColor, shape)
        .clip(shape)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
}
