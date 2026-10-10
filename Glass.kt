package com.yourname.lumen.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass look, kept cheap enough for Android TV: a translucent fill that is a little
 * brighter at the top, plus a thin edge that catches light. No live backdrop blur, which would
 * be expensive on TV hardware.
 */
object GlassBrush {
    val Soft = Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF)))
    val Strong = Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x24FFFFFF)))
    val Edge = Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF)))
}

fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier = this
    .background(if (strong) GlassBrush.Strong else GlassBrush.Soft, shape)
    .border(1.dp, GlassBrush.Edge, shape)
