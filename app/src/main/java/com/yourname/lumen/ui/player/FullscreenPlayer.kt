package com.yourname.lumen.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.util.progressOf
import com.yourname.lumen.core.util.qualityLabel
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.NowNext
import kotlinx.coroutines.delay

/**
 * Controls drawn over the fullscreen video (the video itself is the shared [VideoLayer]).
 *
 * - Up / Down (or Channel +/-) change channel. Nothing else is activated by moving around.
 * - OK shows the compact info bar and puts focus on its Guide button. A second OK opens the guide.
 * - The bar hides itself after a few seconds; any key brings it back.
 * - Back leaves fullscreen. The stream keeps playing in the Live TV preview.
 */
@Composable
fun FullscreenControls(
    controller: PlaybackController,
    channels: List<LiveChannel>,
    epg: EpgCache?,
    showQuality: Boolean,
    overlaySeconds: Int,
    guideOpen: Boolean,
    onZap: (LiveChannel) -> Unit,
    onOpenGuide: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val channel = controller.channel

    var tick by remember { mutableIntStateOf(0) }
    var overlayVisible by remember { mutableStateOf(true) }
    var focusGuide by remember { mutableStateOf(false) }
    var nowNext by remember { mutableStateOf<NowNext?>(null) }
    val rootFocus = remember { FocusRequester() }
    val guideFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    // The bar stays while the guide is open, and hides itself a few seconds after the last key.
    LaunchedEffect(tick, guideOpen) {
        overlayVisible = true
        if (!guideOpen) {
            delay(overlaySeconds * 1000L)
            overlayVisible = false
            focusGuide = false
        }
    }

    // Focus belongs to the video surface unless the user asked for the bar with OK.
    LaunchedEffect(overlayVisible, focusGuide, guideOpen) {
        if (guideOpen) return@LaunchedEffect
        if (overlayVisible && focusGuide) {
            delay(60)
            runCatching { guideFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    LaunchedEffect(channel?.id, epg) {
        nowNext = null
        val current = channel ?: return@LaunchedEffect
        nowNext = epg?.peek(current) ?: epg?.nowNext(current)
    }

    fun zap(delta: Int) {
        if (channels.isEmpty()) return
        val i = channels.indexOfFirst { it.id == channel?.id }.coerceAtLeast(0)
        onZap(channels[(i + delta + channels.size) % channels.size])
        focusGuide = false
        tick++
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp, Key.ChannelUp -> {
                            zap(1)
                            true
                        }

                        Key.DirectionDown, Key.ChannelDown -> {
                            zap(-1)
                            true
                        }

                        Key.DirectionCenter, Key.Enter -> {
                            if (controller.failed) controller.retry()
                            focusGuide = true
                            tick++
                            true
                        }

                        // Keep focus on the video instead of letting it wander underneath.
                        Key.DirectionLeft, Key.DirectionRight -> {
                            tick++
                            true
                        }

                        else -> false
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        if (controller.buffering && !controller.failed) {
            StatusPill("Loading…", Modifier.align(Alignment.Center))
        }
        if (controller.failed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 380.dp)
                    .glass(RoundedCornerShape(16.dp), GlassLevel.Nav)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LumenText("This channel isn't playing right now.", type.heading.copy(fontSize = 17.sp))
                LumenText(
                    "Press OK to try again, or Up and Down to change channel.",
                    type.body.copy(fontSize = 14.sp),
                    color = colors.textSecondary,
                )
            }
        }

        AnimatedVisibility(
            visible = overlayVisible && channel != null,
            modifier = Modifier.align(Alignment.BottomStart),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(260)),
        ) {
            if (channel != null) {
                InfoBar(
                    channel = channel,
                    quality = if (showQuality) qualityLabel(controller.videoHeight) else null,
                    nowNext = nowNext,
                    guideFocus = guideFocus,
                    onGuide = onOpenGuide,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, modifier: Modifier = Modifier) {
    LumenText(
        text = text,
        style = LumenTheme.typography.label,
        modifier = modifier
            .glass(RoundedCornerShape(50), GlassLevel.Nav)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** Compact bar: channel, quality, what's on, and the Guide button. */
@Composable
private fun InfoBar(
    channel: LiveChannel,
    quality: String?,
    nowNext: NowNext?,
    guideFocus: FocusRequester,
    onGuide: () -> Unit,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .padding(start = Dimens.ScreenPadding, bottom = 22.dp)
            .widthIn(max = 560.dp)
            .glass(shape, GlassLevel.Nav)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LumenText(
                    text = channel.name,
                    style = type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (quality != null) {
                    // Secondary: smaller and quieter than the name.
                    LumenText(
                        text = quality,
                        style = type.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .glass(RoundedCornerShape(5.dp), GlassLevel.Panel)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            val now = nowNext?.now
            if (now != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LumenText(
                        text = now.title,
                        style = type.label.copy(fontSize = 12.sp),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    timeRangeText(now.startSec, now.endSec)?.let {
                        LumenText(it, type.label.copy(fontSize = 11.sp), color = colors.textSecondary, maxLines = 1)
                    }
                }
                progressOf(now.startSec, now.endSec)?.let { LumenProgressBar(progress = it) }
            }
        }
        LumenButton(
            text = "Guide",
            modifier = Modifier.focusRequester(guideFocus),
            compact = true,
            onClick = onGuide,
        )
    }
}
