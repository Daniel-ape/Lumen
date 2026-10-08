package com.yourname.lumen.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.tvFocusable

@Composable
fun LumenDock(
    selected: Section,
    onSelect: (Section) -> Unit,
    selectedFocusRequester: FocusRequester,
    onDockFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .onFocusChanged { onDockFocusChange(it.hasFocus) }
            .focusGroup()
            .clip(RoundedCornerShape(50))
            .background(LumenTheme.colors.dock)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Section.entries.forEach { section ->
            DockItem(
                title = section.title,
                selected = section == selected,
                onClick = { onSelect(section) },
                modifier = if (section == selected) {
                    Modifier.focusRequester(selectedFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun DockItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    Box(
        modifier = modifier
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(50), focusedScale = 1.04f)
            .background(if (selected) colors.surfaceHigh else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        LumenText(
            text = title,
            style = LumenTheme.typography.body,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}
