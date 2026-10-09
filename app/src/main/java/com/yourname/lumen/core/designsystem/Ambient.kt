package com.yourname.lumen.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Soft, blurred-looking backdrop: two large glows of color over the dark base.
 * Works on every Android version and costs almost nothing to draw.
 * The glow follows [hue] and fades smoothly when it changes.
 */
@Composable
fun AmbientBackground(hue: Float, modifier: Modifier = Modifier) {
    val base = LumenTheme.colors.background
    val glowA by animateColorAsState(
        targetValue = hueColor(hue, 0.60f, 0.55f),
        animationSpec = tween(900),
        label = "ambientA",
    )
    val glowB by animateColorAsState(
        targetValue = hueColor(hue + 70f, 0.55f, 0.45f),
        animationSpec = tween(900),
        label = "ambientB",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowA.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.05f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowB.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(size.width * 0.10f, size.height * 0.95f),
                        radius = size.width * 0.6f,
                    ),
                )
            },
    )
}
