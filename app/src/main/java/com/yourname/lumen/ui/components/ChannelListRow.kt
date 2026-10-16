package com.yourname.lumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import com.yourname.lumen.core.util.logoRequest
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.util.progressOf
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.domain.model.LiveChannel
import kotlinx.coroutines.delay

/**
 * One compact channel row: logo and name on the left, what's on right now beside them (title, start and end time, progress).
 * - Focused: bright glass and a light outline.
 * - Playing (in the preview or fullscreen): an accent bar and accent number.
 * Programme details only appear when the source really provides them.
 */
@Composable
fun ChannelListRow(
    channel: LiveChannel,
    epg: EpgCache?,
    showLogo: Boolean,
    favorite: Boolean,
    playing: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    // Ask for the guide only once the row has been on screen for a moment, so fast scrolling is cheap.
    var nowNext by remember(channel.id, epg) { mutableStateOf(epg?.peek(channel)) }
    var loaded by remember(channel.id, epg) { mutableStateOf(nowNext != null) }
    LaunchedEffect(channel.id, epg) {
        if (epg != null && nowNext == null) {
            delay(250)
            nowNext = epg.nowNext(channel)
            loaded = true
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                    onMenu()
                    true
                } else {
                    false
                }
            }
            .tvFocusable(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.015f,
                onFocusChange = {
                    focused = it
                    if (it) onFocus()
                },
            )
            .then(if (focused) Modifier.glass(shape, GlassLevel.Selected) else Modifier)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Playing marker
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (playing) colors.accent else Color.Transparent),
        )
        if (showLogo) ChannelLogo(channel, Modifier.size(30.dp))
        Row(
            modifier = Modifier.width(160.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LumenText(
                text = channel.name,
                style = type.body.copy(
                    fontSize = 14.sp,
                    fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (favorite) LumenText("★", type.label.copy(fontSize = 11.sp), color = colors.accent)
        }

        // What's on beside the channel
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val now = nowNext?.now
            if (now != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LumenText(
                        text = now.title,
                        style = type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    timeRangeText(now.startSec, now.endSec)?.let {
                        LumenText(it, type.label.copy(fontSize = 11.sp), color = colors.textSecondary, maxLines = 1)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val progress = progressOf(now.startSec, now.endSec)
                    if (progress != null) {
                        LumenProgressBar(progress = progress, modifier = Modifier.width(70.dp))
                    }
                    val next = nowNext?.next
                    if (next != null) {
                        LumenText(
                            text = "Next: ${next.title}",
                            style = type.label.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else if (loaded) {
                LumenText(
                    "No programme information",
                    type.label.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ChannelLogo(channel: LiveChannel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0x14FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logoUrl != null) {
            SubcomposeAsyncImage(
                model = logoRequest(LocalContext.current, channel.logoUrl),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                loading = { LogoInitial(channel.name) },
                error = { LogoInitial(channel.name) },
            )
        } else {
            LogoInitial(channel.name)
        }
    }
}

@Composable
private fun LogoInitial(name: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        LumenText(
            text = name.take(1).uppercase(),
            style = LumenTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = LumenTheme.colors.textSecondary,
        )
    }
}
