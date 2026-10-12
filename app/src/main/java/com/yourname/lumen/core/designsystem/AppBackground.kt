package com.yourname.lumen.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The app-wide backdrop: a soft charcoal gradient with a few very faint, wide pools of lighter grey
 * that give it a blurred, out-of-focus depth. It is drawn once and never animates, so it costs
 * almost nothing. It is never drawn over fullscreen video.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1B1E23), Color(0xFF16181C), Color(0xFF121418)),
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x40303640), Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.08f),
                        radius = size.width * 0.62f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x30303640), Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.92f),
                        radius = size.width * 0.58f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x1E3A414B), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.width * 0.5f,
                    ),
                )
            },
    )
}
