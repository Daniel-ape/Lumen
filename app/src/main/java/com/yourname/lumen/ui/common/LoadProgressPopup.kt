package com.yourname.lumen.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.progress.LoadTask
import com.yourname.lumen.core.progress.LoadTracker
import com.yourname.lumen.core.progress.TaskState

/**
 * A small glass card in the top-right corner showing what is loading after a source is added or
 * refreshed. It never takes focus and does not block the remote; it hides itself a few seconds after
 * everything has finished.
 */
@Composable
fun LoadProgressPopup(tracker: LoadTracker, show: Boolean, modifier: Modifier = Modifier) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    AnimatedVisibility(
        visible = tracker.visible && show,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 2 },
        exit = fadeOut(tween(220)) + slideOutHorizontally(tween(260)) { it / 2 },
    ) {
        Column(
            modifier = Modifier
                .width(272.dp)
                .glass(RoundedCornerShape(16.dp), GlassLevel.Nav)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LumenText(
                text = if (tracker.pending) "Setting up your source" else "Your source is ready",
                style = type.heading.copy(fontSize = 14.sp),
                maxLines = 1,
            )
            LumenProgressBar(progress = tracker.fraction)
            tracker.all.forEach { task -> TaskRow(task) }
        }
    }
}

@Composable
private fun TaskRow(task: LoadTask) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val ok = Color(0xFF7EE0A1)
    val bad = Color(0xFFFF8A80)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    when (task.state) {
                        TaskState.Waiting -> Color(0x40FFFFFF)
                        TaskState.Running -> colors.accent
                        TaskState.Done -> ok
                        TaskState.Failed -> bad
                    },
                    CircleShape,
                ),
        )
        LumenText(
            text = task.label,
            style = type.body.copy(fontSize = 13.sp),
            color = if (task.state == TaskState.Waiting) colors.textTertiary else colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        LumenText(
            text = when (task.state) {
                TaskState.Waiting -> "Waiting"
                TaskState.Running -> if (task.count > 0) "%,d loaded".format(task.count) else "Loading…"
                TaskState.Done -> task.note ?: "%,d".format(task.count)
                TaskState.Failed -> "Failed"
            },
            style = type.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = when (task.state) {
                TaskState.Done -> ok
                TaskState.Failed -> bad
                else -> colors.textSecondary
            },
            maxLines = 1,
        )
    }
}
