package com.yourname.lumen.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.GearIcon
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.SearchIcon
import com.yourname.lumen.core.designsystem.routeKey
import com.yourname.lumen.core.designsystem.tvFocusable
import kotlin.math.roundToInt

/** Where an item sits inside the dock, in pixels. Used to slide the glass selection indicator. */
data class DockSlot(val x: Int, val w: Int, val h: Int)

private val DockShape = RoundedCornerShape(50)
private val DockFill = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF)))
private val DockEdge = Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x1FFFFFFF)))
private val IndicatorFill = Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x2EFFFFFF)))
private val IndicatorEdge = Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x1AFFFFFF)))

/**
 * Floating glass dock. The selected section is shown by a soft glass pill that slides between
 * items, with no outline around the name. Focus shows as a lighter glow and a small scale-up.
 */
@Composable
fun LumenDock(
    selected: Section,
    slots: SnapshotStateMap<Section, DockSlot>,
    onSelect: (Section) -> Unit,
    selectedFocusRequester: FocusRequester,
    downTarget: FocusRequester,
    onDockFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .routeKey(Key.DirectionDown, downTarget)
            .onFocusChanged { onDockFocusChange(it.hasFocus) }
            .focusGroup()
            .clip(DockShape)
            .background(DockFill)
            .border(1.dp, DockEdge, DockShape)
            .padding(4.dp),
    ) {
        slots[selected]?.let { SelectionIndicator(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Section.entries.forEach { section ->
                val isSelected = section == selected
                DockItem(
                    selected = isSelected,
                    onClick = { onSelect(section) },
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            val slot = DockSlot(
                                x = coordinates.positionInParent().x.roundToInt(),
                                w = coordinates.size.width,
                                h = coordinates.size.height,
                            )
                            if (slots[section] != slot) slots[section] = slot
                        }
                        .then(if (isSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier),
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
}

@Composable
private fun SelectionIndicator(slot: DockSlot) {
    val density = LocalDensity.current
    val x by animateFloatAsState(slot.x.toFloat(), tween(260, easing = FastOutSlowInEasing), label = "indicatorX")
    val w by animateFloatAsState(slot.w.toFloat(), tween(260, easing = FastOutSlowInEasing), label = "indicatorW")
    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), 0) }
            .size(width = with(density) { w.toDp() }, height = with(density) { slot.h.toDp() })
            .background(IndicatorFill, DockShape)
            .border(1.dp, IndicatorEdge, DockShape),
    )
}

@Composable
private fun DockItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .tvFocusable(
                onClick = onClick,
                shape = DockShape,
                focusedScale = 1.04f,
                showOutline = false,
                onFocusChange = { focused = it },
            )
            .background(if (focused) Color(0x26FFFFFF) else Color.Transparent, DockShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        content(if (selected || focused) Color.White else Color(0xB3FFFFFF))
    }
}
