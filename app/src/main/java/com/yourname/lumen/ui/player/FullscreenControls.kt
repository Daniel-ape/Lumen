package com.yourname.lumen.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.motionMs
import com.yourname.lumen.core.util.progressOf
import com.yourname.lumen.core.util.qualityClass
import com.yourname.lumen.core.util.qualityLabel
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.NowNext
import com.yourname.lumen.ui.components.ChannelLogo
import com.yourname.lumen.ui.guide.GuideTimeline
import kotlinx.coroutines.delay

private enum class PanelMode { Hidden, Info, Guide }

private val InfoWidth = 540.dp
private val InfoHeight = 74.dp
private val PanelColor = Color(0xE00D0F12)

/**
 * Controls drawn over the fullscreen video (the video itself is the shared [VideoLayer]).
 *
 * One rounded panel sits at the top centre and has two states: a compact channel info bar and the
 * expanded programme guide. Opening the guide makes the same panel grow into the larger one, and
 * closing it shrinks back, so it reads as a single element changing shape.
 *
 * - Up / Down (or Channel +/-) change channel. Moving around never activates anything.
 * - OK shows the info bar. A second OK, with focus on Guide, opens the guide. The Menu and Guide keys open it directly.
 * - The info bar hides itself after a few seconds. Back closes the guide first, then leaves fullscreen.
 */
@Composable
fun FullscreenControls(
    controller: PlaybackController,
    channels: List<LiveChannel>,
    epg: EpgCache?,
    showQuality: Boolean,
    showLogos: Boolean,
    overlaySeconds: Int,
    guideDpPerMinute: Float,
    onZap: (LiveChannel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val channel = controller.channel

    var mode by remember { mutableStateOf(PanelMode.Info) }
    var tick by remember { mutableIntStateOf(0) }
    var focusGuide by remember { mutableStateOf(false) }
    var nowNext by remember { mutableStateOf<NowNext?>(null) }
    val rootFocus = remember { FocusRequester() }
    val guideFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)
    BackHandler(enabled = mode == PanelMode.Guide) {
        mode = PanelMode.Info
        tick++
    }

    // The compact bar goes away a few seconds after the last key.
    LaunchedEffect(tick, mode) {
        if (mode == PanelMode.Info) {
            delay(overlaySeconds * 1000L)
            mode = PanelMode.Hidden
            focusGuide = false
        }
    }

    // Focus is on the video unless the user asked for the bar; the guide takes focus itself.
    LaunchedEffect(mode, focusGuide) {
        when {
            mode == PanelMode.Guide -> Unit
            mode == PanelMode.Info && focusGuide -> {
                delay(60)
                runCatching { guideFocus.requestFocus() }
            }

            else -> runCatching { rootFocus.requestFocus() }
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
        mode = PanelMode.Info
        tick++
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || mode == PanelMode.Guide) {
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
                            mode = PanelMode.Info
                            tick++
                            true
                        }

                        Key.Menu, Key.Guide -> {
                            focusGuide = true
                            mode = PanelMode.Guide
                            true
                        }

                        // Keep focus on the video instead of letting it wander underneath.
                        Key.DirectionLeft, Key.DirectionRight -> {
                            mode = PanelMode.Info
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
        val guideWidth = minOf(maxWidth - 64.dp, 1080.dp)
        val guideHeight = maxHeight * 0.62f

        if (controller.buffering && !controller.failed) {
            StatusPill("Loading…", Modifier.align(Alignment.Center))
        }
        if (controller.failed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 380.dp)
                    .background(PanelColor, RoundedCornerShape(16.dp))
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

        // ---- the shared top panel
        val ms = motionMs(340)
        val msFade = motionMs(220)
        val transition = updateTransition(mode, label = "topPanel")
        val width by transition.animateDp(
            transitionSpec = { tween(ms, easing = FastOutSlowInEasing) },
            label = "panelWidth",
        ) { m -> if (m == PanelMode.Guide) guideWidth else InfoWidth }
        val height by transition.animateDp(
            transitionSpec = { tween(ms, easing = FastOutSlowInEasing) },
            label = "panelHeight",
        ) { m -> if (m == PanelMode.Guide) guideHeight else InfoHeight }
        val corner by transition.animateDp(
            transitionSpec = { tween(ms, easing = FastOutSlowInEasing) },
            label = "panelCorner",
        ) { m -> if (m == PanelMode.Guide) 22.dp else 20.dp }
        val alpha by transition.animateFloat(
            transitionSpec = { tween(msFade) },
            label = "panelAlpha",
        ) { m -> if (m == PanelMode.Hidden) 0f else 1f }

        if (mode != PanelMode.Hidden || alpha > 0.01f) {
            val shape = RoundedCornerShape(corner)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .size(width = width, height = height)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(shape)
                    .background(PanelColor, shape)
                    .border(1.dp, Color(0x33FFFFFF), shape),
            ) {
                // Content keeps its final size and is simply revealed as the panel grows, so nothing reflows.
                Crossfade(
                    targetState = mode,
                    animationSpec = tween(motionMs(240)),
                    label = "panelContent",
                    modifier = Modifier.fillMaxSize(),
                ) { shown ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        when {
                            shown == PanelMode.Guide -> Column(
                                modifier = Modifier
                                    .requiredSize(guideWidth, guideHeight)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                LumenText("TV Guide", type.heading.copy(fontSize = 16.sp))
                                GuideTimeline(
                                    channels = channels,
                                    currentId = channel?.id,
                                    epg = epg,
                                    dpPerMinute = guideDpPerMinute,
                                    showLogos = showLogos,
                                    onPick = { picked ->
                                        onZap(picked)
                                        focusGuide = false
                                        mode = PanelMode.Info
                                        tick++
                                    },
                                    onFocusChannel = {},
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            shown == PanelMode.Info && channel != null -> Box(
                                modifier = Modifier.requiredSize(InfoWidth, InfoHeight),
                            ) {
                                InfoContent(
                                    channel = channel,
                                    showLogo = showLogos,
                                    quality = if (showQuality) qualityLabel(controller.videoHeight) else null,
                                    qualityClass = if (showQuality) qualityClass(controller.videoHeight) else null,
                                    audio = if (showQuality) controller.audioLabel else null,
                                    nowNext = nowNext,
                                    guideFocus = guideFocus,
                                    onGuide = {
                                        focusGuide = true
                                        mode = PanelMode.Guide
                                    },
                                )
                            }
                        }
                    }
                }
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
            .background(PanelColor, RoundedCornerShape(50))
            .glass(RoundedCornerShape(50), GlassLevel.Nav)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** The compact state: logo and name, stream details, what's on, and how far through it is. */
@Composable
private fun InfoContent(
    channel: LiveChannel,
    showLogo: Boolean,
    quality: String?,
    qualityClass: String?,
    audio: String?,
    nowNext: NowNext?,
    guideFocus: FocusRequester,
    onGuide: () -> Unit,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showLogo) ChannelLogo(channel, Modifier.size(38.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LumenText(
                    text = channel.name,
                    style = type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Only what the player really knows: nothing is guessed from the channel name.
                listOfNotNull(
                    listOfNotNull(quality, qualityClass).joinToString(" · ").ifEmpty { null },
                    audio,
                ).forEach { tag ->
                    LumenText(
                        text = tag,
                        style = type.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                        maxLines = 1,
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
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    timeRangeText(now.startSec, now.endSec)?.let {
                        LumenText(it, type.label.copy(fontSize = 11.sp), color = colors.textSecondary, maxLines = 1)
                    }
                }
                progressOf(now.startSec, now.endSec)?.let { LumenProgressBar(progress = it) }
            } else {
                LumenText("No programme information", type.label.copy(fontSize = 11.sp), color = colors.textTertiary, maxLines = 1)
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
