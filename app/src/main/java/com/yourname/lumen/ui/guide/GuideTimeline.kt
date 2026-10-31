package com.yourname.lumen.ui.guide

import com.yourname.lumen.core.util.dayAndTime
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.util.clockTime
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.ProgramInfo
import com.yourname.lumen.ui.components.ChannelLogo
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ChannelColumnWidth = 150.dp
private val RowHeight = 46.dp
private val HeaderHeight = 26.dp

// The axis covers three hours before now and twenty-one hours after, so earlier and later programmes can
// be browsed with LEFT and RIGHT. Only programmes the source really lists are drawn on it.
private const val HOURS_BEFORE = 3
private const val HOURS_TOTAL = 24
private val NowColor = Color(0xFFFF6B6B)

/** One programme placed on the axis. [fromSec]/[toSec] are where it is drawn; the real times are on [program]. */
private class Placement(
    val program: ProgramInfo,
    val x: Dp,
    val width: Dp,
    val fromSec: Long,
    val toSec: Long,
)

/**
 * Places a channel's programmes on the axis by their real start and end. Programmes that overlap (some
 * providers list overlapping or duplicate entries) are trimmed so two blocks never draw on top of each other.
 */
private fun placementsOf(
    schedule: List<ProgramInfo>?,
    axisStart: Long,
    axisEnd: Long,
    dpPerMinute: Float,
): List<Placement> {
    if (schedule.isNullOrEmpty()) return emptyList()
    val out = ArrayList<Placement>()
    var cursor = axisStart
    for (p in schedule.filter { it.startSec != null }.sortedBy { it.startSec }) {
        val rawStart = p.startSec ?: continue
        val rawEnd = p.endSec ?: (rawStart + 30 * 60L)
        val from = maxOf(rawStart, cursor)
        val to = minOf(rawEnd, axisEnd)
        if (to - from < 120) continue
        val width = (to - from) / 60f * dpPerMinute - 3f
        if (width < 12f) continue
        out.add(Placement(p, ((from - axisStart) / 60f * dpPerMinute).dp, width.dp, from, to))
        cursor = to
    }
    return out
}

/** The programme at time [t]; if none is, the next one after it, else the last one. */
private fun indexAt(row: List<Placement>, t: Long): Int =
    row.indexOfFirst { it.fromSec <= t && t < it.toSec }.takeIf { it >= 0 }
        ?: row.indexOfFirst { it.fromSec > t }.takeIf { it >= 0 }
        ?: row.lastIndex

/**
 * A horizontal programme guide. Each channel is one row; every programme is a block placed by its real
 * start and end time on a shared time axis, so rows always line up. All rows scroll together sideways, and
 * a red marker shows the current time.
 *
 * Navigation is handled here directly, not by Android's automatic focus search, so it cannot lose its place:
 * the guide owns one focus point, UP and DOWN move between channels, LEFT and RIGHT move between programmes
 * (earlier and later), and OK tunes to the channel. The highlighted programme is simply drawn highlighted.
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
    val scope = rememberCoroutineScope()
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
    var viewportPx by remember { mutableIntStateOf(0) }
    var scrolledToNow by remember { mutableStateOf(false) }
    LaunchedEffect(scroll.maxValue, viewportPx) {
        if (!scrolledToNow && scroll.maxValue > 0 && viewportPx > 0) {
            val minutes = ((now - axisStart) / 60f - 20f).coerceAtLeast(0f)
            scroll.scrollTo(with(density) { (minutes * dpPerMinute).dp.roundToPx() })
            scrolledToNow = true
        }
    }

    // Channels the source has no programme information for are left out of the guide, so it never shows
    // empty rows. The channel that is playing always stays. Rows report themselves as empty once they have loaded.
    val empties = remember { mutableStateMapOf<String, Boolean>() }
    val shown by remember(channels, currentId) {
        derivedStateOf { channels.filter { it.id == currentId || empties[it.id] != true } }
    }
    var selectedId by remember { mutableStateOf(currentId ?: channels.firstOrNull()?.id) }
    var lastIndex by remember { mutableIntStateOf(0) }
    val selected = shown.indexOfFirst { it.id == selectedId }.let { if (it >= 0) it else lastIndex.coerceIn(0, (shown.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(selected, shown.size) {
        lastIndex = selected
        // If the highlighted channel just turned out to have nothing to show, move to the one that took its place.
        if (shown.isNotEmpty() && shown[selected].id != selectedId) selectedId = shown[selected].id
    }
    var anchor by remember { mutableLongStateOf(now) }
    var detail by remember { mutableStateOf<GuideDetail?>(null) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    val root = remember { FocusRequester() }

    // The guide takes focus itself, with a few tries because the panel is still animating open.
    LaunchedEffect(Unit) {
        repeat(5) {
            runCatching { root.requestFocus() }
            delay(60)
        }
    }
    BackHandler(enabled = detail != null) { detail = null }

    fun rowFor(index: Int): List<Placement> =
        shown.getOrNull(index)?.let { placementsOf(epg?.peekSchedule(it), axisStart, axisEnd, dpPerMinute) }.orEmpty()

    /** Scrolls sideways just enough to show the highlighted programme, with a little room around it. */
    fun showHorizontally(p: Placement) {
        if (viewportPx <= 0) return
        val margin = with(density) { 24.dp.roundToPx() }
        val left = with(density) { p.x.roundToPx() }
        val right = left + with(density) { p.width.roundToPx() }
        val target = when {
            right - left >= viewportPx - 2 * margin -> left - margin
            left < scroll.value + margin -> left - margin
            right > scroll.value + viewportPx - margin -> right - viewportPx + margin
            else -> return
        }
        scope.launch { scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue), tween(160)) }
    }

    fun showVertically(index: Int) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return
        val first = visible.first().index
        val last = visible.last().index
        scope.launch {
            when {
                index <= first -> listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                index >= last -> listState.animateScrollToItem((index - (visible.size - 2)).coerceAtLeast(0))
            }
        }
    }

    fun moveChannel(delta: Int) {
        detail = null
        val next = (selected + delta).coerceIn(0, (shown.size - 1).coerceAtLeast(0))
        if (next == selected) return
        selectedId = shown[next].id
        onFocusChannel(shown[next])
        showVertically(next)
        val row = rowFor(next)
        if (row.isNotEmpty()) showHorizontally(row[indexAt(row, anchor)])
    }

    fun moveTime(direction: Int) {
        detail = null
        val row = rowFor(selected)
        if (row.isEmpty()) return
        val current = indexAt(row, anchor)
        val target = (current + direction).coerceIn(0, row.lastIndex)
        anchor = row[target].fromSec
        showHorizontally(row[target])
    }

    /** OK: show more about the highlighted programme. A second OK (with the card open) watches that channel. */
    fun select() {
        val channel = shown.getOrNull(selected) ?: return
        val open = detail
        if (open != null) {
            detail = null
            onPick(channel)
            return
        }
        val row = rowFor(selected)
        if (row.isEmpty()) return
        detail = GuideDetail(channel, row[indexAt(row, anchor)].program)
    }

    Box(
        modifier = modifier
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        moveChannel(-1)
                        true
                    }
                    Key.DirectionDown -> {
                        moveChannel(1)
                        true
                    }
                    Key.DirectionLeft -> {
                        moveTime(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        moveTime(1)
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        select()
                        true
                    }
                    else -> false
                }
            },
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // time axis
        Row(modifier = Modifier.fillMaxWidth().height(HeaderHeight)) {
            Spacer(Modifier.width(ChannelColumnWidth))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { viewportPx = it.width }
                    .clipToBounds()
                    .horizontalScroll(scroll, enabled = false),
            ) {
                TimeAxis(axisStart, now, dpPerMinute, totalWidth)
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(shown, key = { _, c -> c.id }) { i, channel ->
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
                    isPlaying = channel.id == currentId,
                    isSelected = i == selected,
                    anchor = anchor,
                    onEmpty = { empties[channel.id] = true },
                    onTap = { selectedId = channel.id },
                )
            }
        }
    }

    // More about the highlighted programme: what it is, when, on which channel, and what it is about.
    detail?.let { d ->
        DetailCard(
            detail = d,
            now = now,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
    }
}

private class GuideDetail(val channel: LiveChannel, val program: ProgramInfo)

@Composable
private fun DetailCard(detail: GuideDetail, now: Long, modifier: Modifier = Modifier) {
    val type = LumenTheme.typography
    val colors = LumenTheme.colors
    val p = detail.program
    val whenText = when {
        p.startSec != null && p.endSec != null && p.startSec <= now && now < p.endSec -> "On now"
        p.endSec != null && p.endSec <= now -> "Already aired"
        p.startSec != null -> {
            val minutes = ((p.startSec - now) / 60).coerceAtLeast(0)
            if (minutes < 60) "Starts in $minutes min" else "Starts " + dayAndTime(p.startSec)
        }
        else -> ""
    }
    Column(
        modifier = modifier
            .widthIn(max = 640.dp)
            .background(Color(0xF2101218), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        LumenText(p.title, type.heading.copy(fontSize = 16.sp), maxLines = 2)
        LumenText(
            text = listOfNotNull(detail.channel.name, timeRangeText(p.startSec, p.endSec), whenText.takeIf { it.isNotEmpty() })
                .joinToString("  ·  "),
            style = type.label.copy(fontSize = 12.sp),
            color = colors.textSecondary,
            maxLines = 1,
        )
        p.description?.let {
            LumenText(it, type.label.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Color.White.copy(alpha = 0.9f), maxLines = 3)
        }
        LumenText(
            "OK: watch this channel    Back: close",
            type.label.copy(fontSize = 11.sp),
            color = colors.textTertiary,
        )
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
            if (!nowInside || abs(x.value - nowX.value) > 50f) {
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
    isPlaying: Boolean,
    isSelected: Boolean,
    anchor: Long,
    onEmpty: () -> Unit,
    onTap: () -> Unit,
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
    val placements = remember(schedule, axisStart, axisEnd, dpPerMinute) {
        placementsOf(schedule, axisStart, axisEnd, dpPerMinute)
    }
    // Loaded and nothing to show: the guide drops this channel.
    LaunchedEffect(loaded, placements.isEmpty()) {
        if (loaded && placements.isEmpty()) onEmpty()
    }
    val highlighted = if (isSelected && placements.isNotEmpty()) indexAt(placements, anchor) else -1
    val nowX = ((now - axisStart) / 60f * dpPerMinute).dp

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
                style = type.body.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected || isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isSelected) Color.White else LumenTheme.colors.textSecondary,
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
                            .offset(x = nowX)
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .background(NowColor.copy(alpha = 0.8f)),
                    )
                }

                if (placements.isEmpty()) {
                    // Still loading: just a small, plain word by the current time, no block.
                    if (!loaded) {
                        LumenText(
                            text = "Loading…",
                            style = type.label.copy(fontSize = 11.sp),
                            color = LumenTheme.colors.textTertiary,
                            modifier = Modifier.offset(x = (nowX + 8.dp)).padding(top = 14.dp),
                        )
                    }
                } else {
                    placements.forEachIndexed { i, p ->
                        androidx.compose.runtime.key(p.fromSec, p.program.title) {
                            Cell(
                                x = p.x,
                                width = p.width,
                                title = p.program.title,
                                times = timeRangeText(p.program.startSec, p.program.endSec),
                                state = when {
                                    p.fromSec <= now && now < p.toSec -> CellState.Now
                                    p.toSec <= now -> CellState.Past
                                    else -> CellState.Upcoming
                                },
                                selected = i == highlighted,
                                onTap = onTap,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class CellState { Now, Past, Upcoming, Quiet }

/** One programme block. It draws only; the guide itself decides which block is highlighted. */
@Composable
private fun Cell(
    x: Dp,
    width: Dp,
    title: String,
    times: String?,
    state: CellState,
    selected: Boolean,
    onTap: () -> Unit,
) {
    val type = LumenTheme.typography
    val colors = LumenTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .offset(x = x)
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 3.dp)
            .clip(shape)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .then(
                when {
                    selected -> Modifier
                        .background(colors.accent.copy(alpha = 0.55f), shape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), shape)

                    // Airing now: tinted with the accent so it stands apart from what has aired and what is coming.
                    state == CellState.Now -> Modifier
                        .background(colors.accent.copy(alpha = 0.22f), shape)
                        .border(1.dp, colors.accent.copy(alpha = 0.5f), shape)

                    state == CellState.Quiet -> Modifier.background(Color(0x0DFFFFFF), shape)
                    else -> Modifier.glass(shape, GlassLevel.Panel)
                },
            )
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val textColor = when {
            selected -> Color.White
            state == CellState.Past -> Color.White.copy(alpha = 0.6f)
            state == CellState.Quiet -> colors.textTertiary
            else -> Color.White
        }
        LumenText(
            text = title,
            style = type.body.copy(
                fontSize = if (state == CellState.Quiet) 11.sp else 12.sp,
                fontWeight = if (selected || state == CellState.Now) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = textColor,
            maxLines = 1,
        )
        if (times != null && width >= 100.dp) {
            LumenText(times, type.label.copy(fontSize = 10.sp), color = colors.textSecondary, maxLines = 1)
        }
    }
}
