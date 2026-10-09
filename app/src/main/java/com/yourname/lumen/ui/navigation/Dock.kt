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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.GearIcon
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.SearchIcon
import com.yourname.lumen.core.designsystem.tvFocusable

@Composable
fun LumenDock(
    selected: Section,
    onSelect: (Section) -> Unit,
    selectedFocusRequester: FocusRequester,
    downTarget: FocusRequester,
    onDockFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    Row(
        modifier = modifier
            .focusProperties { down = downTarget }
            .onFocusChanged { onDockFocusChange(it.hasFocus) }
            .focusGroup()
            .clip(RoundedCornerShape(50))
            .background(colors.dock)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Section.entries.forEach { section ->
            val isSelected = section == selected
            DockItem(
                selected = isSelected,
                onClick = { onSelect(section) },
                modifier = if (isSelected) {
                    Modifier.focusRequester(selectedFocusRequester)
                } else {
                    Modifier
                },
            ) { color ->
                when (section) {
                    Section.Search -> SearchIcon(color)
                    Section.Settings -> GearIcon(color)
                    else -> LumenText(
                        text = section.title,
                        style = LumenTheme.typography.label.copy(fontSize = 13.sp),
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit,
) {
    val colors = LumenTheme.colors
    Box(
        modifier = modifier
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(50), focusedScale = 1.05f)
            .background(if (selected) colors.surfaceHigh else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        content(if (selected) colors.textPrimary else colors.textSecondary)
    }
}
