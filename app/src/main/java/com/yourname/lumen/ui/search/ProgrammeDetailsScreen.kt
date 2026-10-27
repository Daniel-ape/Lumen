package com.yourname.lumen.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.util.dayAndTime
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.search.ProgrammeHit
import com.yourname.lumen.ui.components.ChannelLogo

/**
 * Details for a programme that has not started yet. Only what the guide says is shown: its title,
 * channel and times. The button opens that channel in the normal player.
 */
@Composable
fun ProgrammeDetailsScreen(
    hit: ProgrammeHit,
    onOpenChannel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val openFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { runCatching { openFocus.requestFocus() } }

    val minutes = ((hit.startSec - System.currentTimeMillis() / 1000) / 60).coerceAtLeast(0)
    val startsIn = when {
        hit.isNow -> "On now"
        minutes < 60 -> "Starts in $minutes min"
        minutes < 24 * 60 -> "Starts in ${minutes / 60} h ${minutes % 60} min"
        else -> "Starts ${dayAndTime(hit.startSec)}"
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.ScreenPadding, bottom = 56.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LumenText("TV programme", type.label.copy(fontSize = 12.sp), color = colors.accent)
            LumenText(hit.title, type.title.copy(fontSize = 26.sp, lineHeight = 30.sp), maxLines = 3)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChannelLogo(hit.channel, Modifier.size(34.dp))
                LumenText(hit.channel.name, type.body.copy(fontSize = 16.sp), maxLines = 1)
            }
            LumenText(
                text = listOfNotNull(dayAndTime(hit.startSec), timeRangeText(hit.startSec, hit.endSec)?.substringAfter("– ")?.let { "until $it" })
                    .joinToString(" · "),
                style = type.body.copy(fontSize = 14.sp),
                color = colors.textSecondary,
            )
            LumenText(startsIn, type.label, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LumenButton(
                    text = "Open channel",
                    modifier = Modifier.focusRequester(openFocus),
                    primary = true,
                    onClick = onOpenChannel,
                )
                LumenButton(text = "Back", onClick = onBack)
            }
        }
    }
}
