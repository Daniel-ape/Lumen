package com.yourname.lumen.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * The app-wide backdrop: a soft charcoal gradient with a few very faint, wide pools of lighter grey
 * that give it a blurred, out-of-focus depth.
 *
 * It is painted once into a small picture and then simply stretched over the screen, so every frame
 * costs one textured rectangle instead of several full-screen gradients. The softness hides the
 * enlargement completely. It is never drawn over fullscreen video.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    if (LocalAppearanceFlags.current.reducedBackground) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF16181C)))
        return
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val width = 320
                val height = (width * size.height / size.width).roundToInt().coerceAtLeast(1)
                val bitmap = ImageBitmap(width, height)
                CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1B1E23), Color(0xFF16181C), Color(0xFF121418)),
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x40303640), Color.Transparent),
                            center = Offset(this.size.width * 0.86f, this.size.height * 0.08f),
                            radius = this.size.width * 0.62f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x1E303640), Color.Transparent),
                            center = Offset(this.size.width * 0.08f, this.size.height * 0.92f),
                            radius = this.size.width * 0.58f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x1E3A414B), Color.Transparent),
                            center = Offset(this.size.width * 0.5f, this.size.height * 0.5f),
                            radius = this.size.width * 0.5f,
                        ),
                    )
                }
                onDrawBehind {
                    drawImage(
                        image = bitmap,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                }
            },
    )
}
