package com.yourname.lumen.core.designsystem

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
        animationSpec = tween(durationMillis = 120),
        label = "focusScale",
    )
    // Not animated on purpose: animating this in composition re-composed every card each frame.
    val borderColor = if (focused) Color.White.copy(alpha = 0.6f) else Color.Transparent
    return this
        .onFocusChanged {
            focused = it.isFocused
            onFocusChange(it.isFocused)
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (focused) Modifier.border(2.dp, borderColor, shape) else Modifier)
        .clip(shape)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
}

/**
 * Jumps focus to [target] when [direction] is pressed. Used to connect the dock and the page
 * explicitly, because Android's automatic "nearest item" search can't find its way between them.
 * If [target] isn't on screen the key is left alone and normal navigation continues.
 */
fun Modifier.routeKey(direction: Key, target: FocusRequester): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == direction) {
            runCatching { target.requestFocus() }.isSuccess
        } else {
            false
        }
    }

/** Runs [action] and consumes the key when [direction] is pressed. */
fun Modifier.onDirection(direction: Key, action: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == direction) {
            action()
            true
        } else {
            false
        }
    }
