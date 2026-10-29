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
 * The app-wide backdrop: a deep blue-black gradient with a few wide, soft pools of colour (indigo at the
 * top right, teal at the bottom left, a faint violet in the middle). The colours are kept low so the app
 * stays dark and calm but no longer reads as flat grey.
 *
 * It is painted once into a small picture and then only stretched across the screen. That keeps the look
 * smooth (the stretching blurs it) while costing the graphics chip one cheap draw per frame. It never
 * animates and is never drawn over fullscreen video.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    if (LocalAppearanceFlags.current.reducedBackground) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF10121A)))
        return
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val width = 360
                val height = (width * size.height / size.width).roundToInt().coerceAtLeast(1)
                val bitmap = ImageBitmap(width, height)
                val target = Size(width.toFloat(), height.toFloat())
                CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), target) {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFF151824), Color(0xFF10121B), Color(0xFF0B0C12)),
                        ),
                    )
                    // indigo, top right
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x5C3C44B8), Color.Transparent),
                            center = Offset(this.size.width * 0.88f, this.size.height * 0.06f),
                            radius = this.size.width * 0.66f,
                        ),
                    )
                    // teal, bottom left
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x401E8C94), Color.Transparent),
                            center = Offset(this.size.width * 0.06f, this.size.height * 0.94f),
                            radius = this.size.width * 0.6f,
                        ),
                    )
                    // a faint violet in the middle, for depth
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x2E7A3FA8), Color.Transparent),
                            center = Offset(this.size.width * 0.52f, this.size.height * 0.5f),
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
