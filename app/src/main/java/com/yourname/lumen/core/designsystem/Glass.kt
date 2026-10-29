package com.yourname.lumen.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Surface levels, from most to least prominent. Each level is built from three layers:
 * a translucent body that is lighter at the top, a soft diagonal sheen that reads as a reflection,
 * and a thin rim that catches light on its top-left edge.
 *
 * There is no live backdrop blur: it would be costly on TV hardware. The app background is already
 * a soft gradient, so the translucent layers read as glass floating over it.
 */
enum class GlassLevel(
    internal val body: Brush,
    internal val sheen: Brush,
    internal val rim: Brush,
) {
    /** Large panels: the quietest level, so content on top stays readable. */
    Panel(
        body = Brush.verticalGradient(listOf(Color(0x24E2E8FF), Color(0x0EE2E8FF))),
        sheen = Brush.linearGradient(0f to Color(0x18FFFFFF), 0.45f to Color.Transparent),
        rim = Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF), Color(0x2EFFFFFF))),
    ),

    /** Secondary buttons and list rows. */
    Control(
        body = Brush.verticalGradient(listOf(Color(0x2AE2E8FF), Color(0x12E2E8FF))),
        sheen = Brush.linearGradient(0f to Color(0x1FFFFFFF), 0.5f to Color.Transparent),
        rim = Brush.linearGradient(listOf(Color(0x73FFFFFF), Color(0x18FFFFFF), Color(0x33FFFFFF))),
    ),

    /** The main action on a screen. */
    Primary(
        body = Brush.verticalGradient(listOf(Color(0x47E2E8FF), Color(0x24E2E8FF))),
        sheen = Brush.linearGradient(0f to Color(0x2EFFFFFF), 0.55f to Color.Transparent),
        rim = Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x24FFFFFF), Color(0x4DFFFFFF))),
    ),

    /** The floating navigation dock. */
    Nav(
        body = Brush.verticalGradient(listOf(Color(0x33E2E8FF), Color(0x14E2E8FF))),
        sheen = Brush.linearGradient(0f to Color(0x22FFFFFF), 0.5f to Color.Transparent),
        rim = Brush.linearGradient(listOf(Color(0x80FFFFFF), Color(0x1AFFFFFF), Color(0x40FFFFFF))),
    ),

    /** The chosen item inside a glass surface, like the dock's selection pill. */
    Selected(
        body = Brush.verticalGradient(listOf(Color(0x52E2E8FF), Color(0x2AE2E8FF))),
        sheen = Brush.linearGradient(0f to Color(0x30FFFFFF), 0.6f to Color.Transparent),
        rim = Brush.linearGradient(listOf(Color(0x8CFFFFFF), Color(0x20FFFFFF), Color(0x44FFFFFF))),
    ),
}

fun Modifier.glass(shape: Shape, level: GlassLevel): Modifier = this
    .background(level.body, shape)
    .background(level.sheen, shape)
    .border(1.dp, level.rim, shape)

/** Kept for existing call sites: [strong] means the primary level, otherwise a normal control. */
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier =
    glass(shape, if (strong) GlassLevel.Primary else GlassLevel.Control)
