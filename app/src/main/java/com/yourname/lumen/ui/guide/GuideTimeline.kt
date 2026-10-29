package com.yourname.lumen.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import kotlin.math.abs
import kotlinx.coroutines.delay

private val ChannelColumnWidth = 150.dp
private val RowHeight = 46.dp
private val HeaderHeight = 26.dp

// The axis covers three hours before now and twenty-one hours after, so earlier and later programmes can
// be browsed with LEFT and RIGHT. Only programmes the source really lists are drawn on it.
private const val HOURS_BEFORE = 3
private const val HOURS_TOTAL = 24
private val NowColor = Color(0xFFFF6B6B)

private class Placement(val program: ProgramInfo, val x: Dp, val width: Dp, val startSec: Long, val endSec: Long)

/**
 * A horizontal programme guide. Each channel is one row; every programme is a block placed by its
 * real start and end time on a shared time axis, so rows always line up. All rows scroll together
 * sideways (RIGHT moves into the future, LEFT into the past), and a red line marks the current time.
 *
 * Focus is kept steady: every row always owns exactly one "main" block (the programme airing now, or a
 * plain loading / no-information block) and that same block simply changes content when the data arrives,
 * so focus is never lost while a row is still loading.
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

    val axisStart = remember { (System.currentTimeMillis() / 1000 / 1800) * 1800 - HOURS_BEFORE * 3600L }
    val axisEnd = axisStart + HOURS_TOTAL * 3600L
    val totalWidth = (HOURS_TOTAL * 60 * dpPerMinute).dp

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
            itemsIndexed(channels, key = { _, c -> c.id }) { i, channel ->
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
    val nowInside = now >= axisStart && now <= axisStart + HOURS_TOTAL * 3600L
    val nowX = ((now - axisStart) / 60f * dpPerMinute).dp
    Box(modifier = Modifier.width(totalWidth).fillMaxHeight()) {
        for (i in 0 until HOURS_TOTAL * 2) {
            val x = (i * 30 * dpPerMinute).dp
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0x22FFFFFF)),
            )
            // A time label is skipped where the NOW marker sits, so the two never overlap.
            if (!nowInside || abs(x.value - nowX.value) > 46f) {
                LumenText(
                    text = clockTime(axisStart + i * 1800L),
                    style = LumenTheme.typography.label.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = x + 6.dp)
                        .padding(top = 6.dp),
                )
            }
        }
        if (nowInside) {
            LumenText(
                text = "NOW",
                style = LumenTheme.typography.label.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .offset(x = nowX - 17.dp)
                    .padding(top = 5.dp)
                    .background(NowColor, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
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

    // Where each programme goes on the axis, from its real start and end.
    val placements = remember(schedule, axisStart, axisEnd, dpPerMinute) {
        schedule.orEmpty().mapNotNull { p ->
            val start = p.startSec ?: return@mapNotNull null
            val end = p.endSec ?: (start + 30 * 60L)
            val from = maxOf(start, axisStart)
            val to = minOf(end, axisEnd)
            if (to <= from) return@mapNotNull null
            val width = ((to - from) / 60f * dpPerMinute) - 3f
            if (width < 14f) return@mapNotNull null
            Placement(p, ((from - axisStart) / 60f * dpPerMinute).dp, width.dp, start, end)
        }
    }
    // The main block: what is on now, else the next programme, else the first one.
    val mainIndex = placements.indexOfFirst { it.startSec <= now && now < it.endSec }.takeIf { it >= 0 }
        ?: placements.indexOfFirst { it.endSec > now }.takeIf { it >= 0 }
        ?: 0
    val main = placements.getOrNull(mainIndex)
    val ownFocus = remember { FocusRequester() }
    val mainFocus = fallbackFocus ?: ownFocus

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
                // The current time, drawn first so programme blocks and their titles sit on top of it.
                if (now in axisStart..axisEnd) {
                    Box(
                        modifier = Modifier
                            .offset(x = ((now - axisStart) / 60f * dpPerMinute).dp)
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .background(NowColor.copy(alpha = 0.8f)),
                    )
                }

                // Always present and always the same element, so focus survives loading finishing.
                GuideCell(
                    requester = mainFocus,
                    x = main?.x ?: 0.dp,
                    width = main?.width ?: totalWidth,
                    title = when {
                        main != null -> main.program.title
                        loaded -> "No programme information"
                        else -> "Loading…"
                    },
                    times = main?.let { timeRangeText(it.startSec, it.endSec) },
                    state = when {
                        main == null -> CellState.Quiet
                        main.startSec <= now && now < main.endSec -> CellState.Now
                        main.endSec <= now -> CellState.Past
                        else -> CellState.Upcoming
                    },
                    autoFocus = focusPending && isCurrent,
                    onAutoFocused = onAutoFocused,
                    onFocus = { onFocusChannel(channel) },
                    onClick = { onPick(channel) },
                )
                placements.forEachIndexed { i, p ->
                    if (i != mainIndex) {
                        androidx.compose.runtime.key(p.startSec, p.program.title) {
                            GuideCell(
                                requester = null,
                                x = p.x,
                                width = p.width,
                                title = p.program.title,
                                times = timeRangeText(p.startSec, p.endSec),
                                state = when {
                                    p.startSec <= now && now < p.endSec -> CellState.Now
                                    p.endSec <= now -> CellState.Past
                                    else -> CellState.Upcoming
                                },
                                autoFocus = false,
                                onAutoFocused = {},
                                onFocus = { onFocusChannel(channel) },
                                onClick = { onPick(channel) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class CellState { Now, Past, Upcoming, Quiet }

@Composable
private fun GuideCell(
    requester: FocusRequester?,
    x: Dp,
    width: Dp,
    title: String,
    times: String?,
    state: CellState,
    autoFocus: Boolean,
    onAutoFocused: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val type = LumenTheme.typography
    val colors = LumenTheme.colors
    var focused by remember { mutableStateOf(false) }
    val own = remember { FocusRequester() }
    val focusTarget = requester ?: own
    val shape = RoundedCornerShape(8.dp)

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { focusTarget.requestFocus() }
            onAutoFocused()
        }
    }

    Column(
        modifier = Modifier
            .offset(x = x)
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 3.dp)
            .focusRequester(focusTarget)
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
                    // Airing now: tinted with the accent so it stands apart from what has aired and what is coming.
                    state == CellState.Now -> Modifier
                        .background(colors.accent.copy(alpha = 0.24f), shape)
                        .border(1.dp, colors.accent.copy(alpha = 0.55f), shape)

                    state == CellState.Quiet -> Modifier.background(Color(0x0DFFFFFF), shape)
                    else -> Modifier.glass(shape, GlassLevel.Panel)
                },
            )
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val textColor = when (state) {
            CellState.Past -> Color.White.copy(alpha = 0.6f)
            CellState.Quiet -> colors.textTertiary
            else -> Color.White
        }
        LumenText(
            text = title,
            style = type.body.copy(
                fontSize = if (state == CellState.Quiet) 11.sp else 12.sp,
                fontWeight = if (state == CellState.Now) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = textColor,
            maxLines = 1,
        )
        if (times != null && width >= 96.dp) {
            LumenText(times, type.label.copy(fontSize = 10.sp), color = colors.textSecondary, maxLines = 1)
        }
    }
}
