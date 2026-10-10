package com.yourname.lumen.ui.boot

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import kotlinx.coroutines.delay

/** Shown while the app starts and loads your library. Calm, short, and always moving. */
@Composable
fun BootScreen(modifier: Modifier = Modifier) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val accent = colors.accent

    val transition = rememberInfiniteTransition(label = "boot")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val slide by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "slide",
    )

    val messages = remember {
        listOf("Starting up…", "Connecting to your source…", "Updating your library…", "Almost ready…")
    }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (step < messages.lastIndex) {
            delay(1300)
            step++
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                // Spinning ring
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotation },
                ) {
                    val stroke = 4.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, accent)),
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                // Breathing play mark
                Canvas(
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(w * 0.25f, h * 0.12f)
                        lineTo(w * 0.88f, h * 0.5f)
                        lineTo(w * 0.25f, h * 0.88f)
                        close()
                    }
                    drawPath(path, Color.White)
                }
            }

            LumenText(
                text = "LUMEN",
                style = type.title.copy(fontSize = 22.sp, letterSpacing = 6.sp),
            )

            Crossfade(targetState = step, animationSpec = tween(400), label = "bootMessage") { i ->
                LumenText(messages[i], type.label, color = colors.textSecondary)
            }

            // Slim bar with a light segment sliding across
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .drawBehind {
                        val segment = size.width * 0.35f
                        val x = (size.width + segment) * slide - segment
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(x, 0f),
                            size = Size(segment, size.height),
                        )
                    },
            )
        }
    }
}
