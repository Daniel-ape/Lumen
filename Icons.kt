package com.yourname.lumen.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SearchIcon(color: Color, modifier: Modifier = Modifier, iconSize: Dp = 16.dp) {
    Canvas(modifier.size(iconSize)) {
        val w = size.minDimension
        val stroke = w * 0.12f
        drawCircle(
            color = color,
            radius = w * 0.32f,
            center = Offset(w * 0.42f, w * 0.42f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(w * 0.66f, w * 0.66f),
            end = Offset(w * 0.92f, w * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun GearIcon(color: Color, modifier: Modifier = Modifier, iconSize: Dp = 16.dp) {
    Canvas(modifier.size(iconSize)) {
        val r = size.minDimension / 2f
        val c = center
        val stroke = r * 0.24f
        drawCircle(
            color = color,
            radius = r * 0.5f,
            center = c,
            style = Stroke(width = stroke),
        )
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color = color,
                start = Offset(c.x + dx * r * 0.62f, c.y + dy * r * 0.62f),
                end = Offset(c.x + dx * r * 0.95f, c.y + dy * r * 0.95f),
                strokeWidth = stroke * 1.2f,
                cap = StrokeCap.Round,
            )
        }
    }
}
