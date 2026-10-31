package com.yourname.lumen.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.progress.LoadTask
import com.yourname.lumen.core.progress.LoadTracker
import com.yourname.lumen.core.progress.TaskState

/**
 * A small dark card in the top-right corner showing what is loading after a source is added or refreshed.
 * Plain white text only. It never takes focus and does not block the remote; it hides itself a few seconds
 * after everything has finished.
 */
@Composable
fun LoadProgressPopup(tracker: LoadTracker, show: Boolean, modifier: Modifier = Modifier) {
    val type = LumenTheme.typography
    AnimatedVisibility(
        visible = tracker.visible && show,
        modifier = modifier,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        Column(
            modifier = Modifier
                .width(210.dp)
                .background(Color(0xF2090A0D), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            LumenText(
                text = if (tracker.pending) "Loading" else "Ready",
                style = type.label.copy(fontSize = 12.sp),
                color = Color.White,
                maxLines = 1,
            )
            tracker.all.forEach { task -> TaskRow(task) }
        }
    }
}

@Composable
private fun TaskRow(task: LoadTask) {
    val type = LumenTheme.typography
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LumenText(
            text = task.label,
            style = type.label.copy(fontSize = 11.sp),
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        LumenText(
            text = when (task.state) {
                TaskState.Waiting -> "waiting"
                TaskState.Running -> if (task.count > 0) "%,d".format(task.count) else "…"
                TaskState.Done -> task.note ?: "%,d".format(task.count)
                TaskState.Failed -> "failed"
            },
            style = type.label.copy(fontSize = 11.sp),
            color = Color.White,
            maxLines = 1,
        )
    }
}
