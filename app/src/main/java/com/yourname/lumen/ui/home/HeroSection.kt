package com.yourname.lumen.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.hueColor
import kotlinx.coroutines.delay

@Composable
fun HeroSection(
    items: List<HeroItem>,
    heroFocus: FocusRequester,
    upTarget: FocusRequester,
    downTarget: FocusRequester,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    // Tell the app which color the page backdrop should glow.
    LaunchedEffect(index) { onHueChange(items[index].hue) }

    // Auto-rotate every 8s, but never while a hero button has focus.
    LaunchedEffect(paused) {
        while (!paused) {
            delay(8_000)
            index = (index + 1) % items.size
        }
    }

    Box(modifier = modifier.fillMaxWidth().height(360.dp)) {
        // Backdrop and text crossfade. Buttons live outside so focus is never lost mid-fade.
        Crossfade(targetState = index, animationSpec = tween(600), label = "hero") { i ->
            val item = items[i]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                hueColor(item.hue, 0.5f, 0.4f).copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Dimens.ScreenPadding, bottom = 120.dp)
                        .widthIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LumenText(item.label, type.body, color = colors.textSecondary)
                    LumenText(item.title, type.display)
                    LumenText(item.meta, type.body, color = colors.textSecondary)
                    LumenText(item.overview, type.body, maxLines = 2)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.ScreenPadding, bottom = 36.dp)
                .focusProperties {
                    up = upTarget
                    down = downTarget
                }
                .onFocusChanged { paused = it.hasFocus }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LumenButton(
                text = "Watch",
                modifier = Modifier.focusRequester(heroFocus),
                primary = true,
            )
            LumenButton("More info")
            LumenButton(
                text = "Next",
                onClick = { index = (index + 1) % items.size },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.ScreenPadding, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.indices.forEach { dot ->
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (dot == index) Color.White else Color.White.copy(alpha = 0.25f)),
                )
            }
        }
    }
}
