package com.yourname.lumen.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.util.clockTime
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.ProgramInfo
import com.yourname.lumen.ui.components.ChannelLogo
import kotlinx.coroutines.delay

private val ChannelColumnWidth = 150.dp
private val RowHeight = 46.dp
private val HeaderHeight = 24.dp
private const val HOURS = 8
private val NowColor = Color(0xFFFF6B6B)

/**
 * A horizontal programme guide. Each channel is one row; every programme is a block placed by its
 * real start and end time on a shared time axis, so rows always line up. All rows scroll together
 * sideways (RIGHT moves into the future), and a red line marks the current time.
 * Only programme data from the source is shown: a channel with none gets one plain "no information" block.
 */
@Composable
fun GuideTimeline(
    channels: List<LiveChannel>,
    currentId: String?,
    epg: EpgCache?,
    dpPerMinute: Float,
    showLogos: Boolean,
    onPick: (LiveChannel) -> Unit,
    onFocusChannel: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis() / 1000
        }
    }

    // The axis starts at the half hour before the previous one, so a little of the past is visible.
    val axisStart = remember { (System.currentTimeMillis() / 1000 / 1800) * 1800 - 1800 }
    val axisEnd = axisStart + HOURS * 3600L
    val totalWidth = (HOURS * 60 * dpPerMinute).dp

    val scroll = rememberScrollState()
    var scrolledToNow by remember { mutableStateOf(false) }
    LaunchedEffect(scroll.maxValue) {
        if (!scrolledToNow && scroll.maxValue > 0) {
            val minutes = ((now - axisStart) / 60f - 20f).coerceAtLeast(0f)
            scroll.scrollTo(with(density) { (minutes * dpPerMinute).dp.roundToPx() })
            scrolledToNow = true
        }
    }

    val startIndex = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 1).coerceAtLeast(0))
    var focusDone by remember { mutableStateOf(false) }
    val fallbackFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(1_400)
        if (!focusDone) {
            runCatching { fallbackFocus.requestFocus() }
            focusDone = true
        }
    }

    Column(modifier = modifier) {
        // time axis
        Row(modifier = Modifier.fillMaxWidth().height(HeaderHeight)) {
            Spacer(Modifier.width(ChannelColumnWidth))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .horizontalScroll(scroll, enabled = false),
            ) {
                TimeAxis(axisStart, now, dpPerMinute, totalWidth)
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(channels) { i, channel ->
                GuideRow(
                    channel = channel,
                    epg = epg,
                    axisStart = axisStart,
                    axisEnd = axisEnd,
                    now = now,
                    dpPerMinute = dpPerMinute,
                    totalWidth = totalWidth,
                    scroll = scroll,
                    showLogos = showLogos,
                    isCurrent = channel.id == currentId || (currentId == null && i == 0),
                    fallbackFocus = if (i == 0) fallbackFocus else null,
                    focusPending = !focusDone,
                    onAutoFocused = { focusDone = true },
                    onPick = onPick,
                    onFocusChannel = onFocusChannel,
                )
            }
        }
    }
}

@Composable
private fun TimeAxis(axisStart: Long, now: Long, dpPerMinute: Float, totalWidth: Dp) {
    val colors = LumenTheme.colors
    Box(modifier = Modifier.width(totalWidth).fillMaxHeight()) {
        for (i in 0 until HOURS * 2) {
            val x = (i * 30 * dpPerMinute).dp
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0x22FFFFFF)),
            )
            LumenText(
                text = clockTime(axisStart + i * 1800L),
                style = LumenTheme.typography.label.copy(fontSize = 11.sp),
                color = colors.textSecondary,
                maxLines = 1,
                modifier = Modifier
                    .offset(x = x + 6.dp)
                    .padding(top = 5.dp),
            )
        }
        val nowX = ((now - axisStart) / 60f * dpPerMinute).dp
        LumenText(
            text = "NOW",
            style = LumenTheme.typography.label.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = NowColor,
            modifier = Modifier
                .offset(x = nowX + 5.dp)
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun GuideRow(
    channel: LiveChannel,
    epg: EpgCache?,
    axisStart: Long,
    axisEnd: Long,
    now: Long,
    dpPerMinute: Float,
    totalWidth: Dp,
    scroll: androidx.compose.foundation.ScrollState,
    showLogos: Boolean,
    isCurrent: Boolean,
    fallbackFocus: FocusRequester?,
    focusPending: Boolean,
    onAutoFocused: () -> Unit,
    onPick: (LiveChannel) -> Unit,
    onFocusChannel: (LiveChannel) -> Unit,
) {
    val type = LumenTheme.typography
    var schedule by remember(channel.id, epg) { mutableStateOf(epg?.peekSchedule(channel)) }
    var loaded by remember(channel.id, epg) { mutableStateOf(schedule != null || epg == null) }
    LaunchedEffect(channel.id, epg) {
        if (epg != null && schedule == null) {
            delay(200)
            schedule = epg.schedule(channel)
            loaded = true
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(ChannelColumnWidth).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showLogos) ChannelLogo(channel, Modifier.width(26.dp).height(26.dp))
            LumenText(
                text = channel.name,
                style = type.body.copy(fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .horizontalScroll(scroll),
        ) {
            Box(modifier = Modifier.width(totalWidth).fillMaxHeight()) {
                val items = schedule
                var placed = 0
                items?.forEach { program ->
                    val start = program.startSec ?: return@forEach
                    val end = program.endSec ?: (start + 30 * 60L)
                    val from = maxOf(start, axisStart)
                    val to = minOf(end, axisEnd)
                    if (to <= from) return@forEach
                    val x = ((from - axisStart) / 60f * dpPerMinute).dp
                    val w = (((to - from) / 60f * dpPerMinute) - 3f).dp
                    if (w < 14.dp) return@forEach
                    val isNow = start <= now && now < end
                    ProgrammeBlock(
                        fallback = if (placed == 0) fallbackFocus else null,
                        program = program,
                        x = x,
                        width = w,
                        isNow = isNow,
                        autoFocus = focusPending && isCurrent && (isNow || placed == 0),
                        onAutoFocused = onAutoFocused,
                        onFocus = { onFocusChannel(channel) },
                        onClick = { onPick(channel) },
                    )
                    placed++
                }
                if (placed == 0 && loaded) {
                    // No guide data: one quiet block so the row can still be selected.
                    EmptyBlock(
                        fallback = fallbackFocus,
                        width = totalWidth,
                        autoFocus = focusPending && isCurrent,
                        onAutoFocused = onAutoFocused,
                        onFocus = { onFocusChannel(channel) },
                        onClick = { onPick(channel) },
                    )
                }
                // The current time, drawn through every row so it reads as one continuous line.
                if (now in axisStart..axisEnd) {
                    Box(
                        modifier = Modifier
                            .offset(x = ((now - axisStart) / 60f * dpPerMinute).dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(NowColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(
    fallback: FocusRequester?,
    program: ProgramInfo,
    x: Dp,
    width: Dp,
    isNow: Boolean,
    autoFocus: Boolean,
    onAutoFocused: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val type = LumenTheme.typography
    val colors = LumenTheme.colors
    var focused by remember { mutableStateOf(false) }
    val ownRequester = remember { FocusRequester() }
    val requester = fallback ?: ownRequester
    val shape = RoundedCornerShape(8.dp)

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { requester.requestFocus() }
            onAutoFocused()
        }
    }

    Column(
        modifier = Modifier
            .offset(x = x)
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 3.dp)
            .focusRequester(requester)
            .tvFocusable(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.02f,
                onFocusChange = {
                    focused = it
                    if (it) onFocus()
                },
            )
            .then(
                when {
                    focused -> Modifier.glass(shape, GlassLevel.Selected)
                    // Airing now: tinted with the accent so it stands apart from what's coming up.
                    isNow -> Modifier
                        .background(colors.accent.copy(alpha = 0.22f), shape)
                        .border(1.dp, colors.accent.copy(alpha = 0.55f), shape)

                    else -> Modifier.glass(shape, GlassLevel.Panel)
                },
            )
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        LumenText(
            text = program.title,
            style = type.body.copy(fontSize = 12.sp, fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal),
            color = Color.White,
            maxLines = 1,
        )
        if (width >= 96.dp) {
            timeRangeText(program.startSec, program.endSec)?.let {
                LumenText(it, type.label.copy(fontSize = 10.sp), color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyBlock(
    fallback: FocusRequester?,
    width: Dp,
    autoFocus: Boolean,
    onAutoFocused: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val ownRequester = remember { FocusRequester() }
    val requester = fallback ?: ownRequester
    val shape = RoundedCornerShape(8.dp)
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { requester.requestFocus() }
            onAutoFocused()
        }
    }
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 3.dp)
            .focusRequester(requester)
            .tvFocusable(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.0f,
                onFocusChange = {
                    focused = it
                    if (it) onFocus()
                },
            )
            .then(if (focused) Modifier.glass(shape, GlassLevel.Selected) else Modifier.background(Color(0x0DFFFFFF), shape))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        LumenText(
            text = "No programme information",
            style = LumenTheme.typography.label.copy(fontSize = 11.sp),
            color = LumenTheme.colors.textTertiary,
            maxLines = 1,
        )
    }
}
