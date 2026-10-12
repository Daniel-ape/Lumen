package com.yourname.lumen.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.ProgramInfo
import com.yourname.lumen.ui.components.ChannelListRow
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * The programme guide: channels on the left with what's on now, and the highlighted channel's
 * schedule on the right. It sits over whatever is behind it, so a stream that is playing keeps
 * playing. OK on a channel picks it; Back closes the guide.
 */
@Composable
fun GuidePanel(
    channels: List<LiveChannel>,
    currentId: String?,
    epg: EpgCache?,
    favorites: Set<String>,
    favoriteKeyOf: (LiveChannel) -> String,
    onPick: (LiveChannel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val shape = RoundedCornerShape(20.dp)

    val startIndex = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 2).coerceAtLeast(0))
    val startFocus = remember { FocusRequester() }
    var focusedId by remember { mutableStateOf(currentId ?: channels.firstOrNull()?.id) }
    val focused = channels.firstOrNull { it.id == focusedId } ?: channels.firstOrNull()

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { startFocus.requestFocus() } }

    // Schedule of the highlighted channel, fetched after a short pause so scrolling stays smooth.
    var schedule by remember { mutableStateOf<List<ProgramInfo>?>(null) }
    var scheduleLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(focused?.id, epg) {
        schedule = null
        scheduleLoaded = false
        val channel = focused
        if (channel != null && epg != null) {
            delay(300)
            schedule = try {
                epg.schedule(channel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        scheduleLoaded = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Keep the remote inside the guide: sideways keys would otherwise escape to the screen underneath.
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
            },
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.64f)
                .padding(horizontal = 28.dp, vertical = 20.dp)
                // A dark base keeps text readable over bright video, then the glass layers go on top.
                .background(Color(0xB3101215), shape)
                .glass(shape, GlassLevel.Panel)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(430.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(channels) { i, channel ->
                    ChannelListRow(
                        channel = channel,
                        epg = epg,
                        favorite = favoriteKeyOf(channel) in favorites,
                        playing = channel.id == currentId,
                        onFocus = { focusedId = channel.id },
                        onClick = { onPick(channel) },
                        onMenu = {},
                        modifier = if (i == startIndex) Modifier.focusRequester(startFocus) else Modifier,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LumenText(
                    text = focused?.name ?: "",
                    style = type.heading.copy(fontSize = 17.sp),
                    maxLines = 1,
                )
                val items = schedule
                when {
                    items == null && !scheduleLoaded -> LumenText(
                        "Loading guide…",
                        type.label,
                        color = colors.textSecondary,
                    )

                    items.isNullOrEmpty() -> LumenText(
                        "No programme information for this channel.",
                        type.body.copy(fontSize = 14.sp),
                        color = colors.textSecondary,
                    )

                    else -> items.forEachIndexed { i, program ->
                        ScheduleLine(program = program, isNow = i == 0 && isOnNow(program))
                    }
                }
            }
        }
    }
}

private fun isOnNow(program: ProgramInfo): Boolean {
    val start = program.startSec ?: return false
    val end = program.endSec ?: return false
    val now = System.currentTimeMillis() / 1000
    return now in start until end
}

@Composable
private fun ScheduleLine(program: ProgramInfo, isNow: Boolean) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LumenText(
            text = timeRangeText(program.startSec, null) ?: "",
            style = type.label.copy(fontSize = 12.sp),
            color = if (isNow) colors.accent else colors.textSecondary,
            modifier = Modifier.width(44.dp),
        )
        LumenText(
            text = program.title,
            style = type.body.copy(fontSize = 14.sp, fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (isNow) LumenText("NOW", type.label.copy(fontSize = 10.sp), color = colors.accent)
    }
}
