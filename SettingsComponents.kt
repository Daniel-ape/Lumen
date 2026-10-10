package com.yourname.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.tvFocusable

private val RowShape = RoundedCornerShape(12.dp)

/** On/off setting. OK on the remote flips it. */
@Composable
fun SettingToggleRow(
    label: String,
    description: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onToggle, shape = RowShape, focusedScale = 1.02f)
            .glass(RowShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowLabel(label, description, Modifier.weight(1f))
        SwitchPill(checked)
    }
}

/** Setting with a few choices. OK on the remote moves to the next choice. */
@Composable
fun SettingChoiceRow(
    label: String,
    description: String?,
    value: String,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onCycle, shape = RowShape, focusedScale = 1.02f)
            .glass(RowShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowLabel(label, description, Modifier.weight(1f))
        LumenText(
            text = "$value  ›",
            style = LumenTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = colors.accent,
            maxLines = 1,
        )
    }
}

/** A button-like row that does something when pressed. */
@Composable
fun SettingActionRow(
    label: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onClick, shape = RowShape, focusedScale = 1.02f)
            .glass(RowShape, strong = strong)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowLabel(label, description, Modifier.weight(1f))
        LumenText("›", LumenTheme.typography.body, color = LumenTheme.colors.textSecondary)
    }
}

/** Read-only fact, like the app version. Not focusable. */
@Composable
fun SettingInfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LumenTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LumenText(label, LumenTheme.typography.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        LumenText(value, LumenTheme.typography.body, maxLines = 2, modifier = Modifier.weight(1.4f))
    }
}

@Composable
fun SettingNote(text: String, modifier: Modifier = Modifier) {
    LumenText(
        text = text,
        style = LumenTheme.typography.label,
        color = LumenTheme.colors.textSecondary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
fun SettingGroupTitle(text: String, modifier: Modifier = Modifier) {
    LumenText(
        text = text,
        style = LumenTheme.typography.heading,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun RowLabel(label: String, description: String?, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LumenText(label, LumenTheme.typography.body, maxLines = 1)
        if (!description.isNullOrBlank()) {
            LumenText(
                text = description,
                style = LumenTheme.typography.label,
                color = LumenTheme.colors.textSecondary,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SwitchPill(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(CircleShape)
            .background(if (checked) LumenTheme.colors.accent else Color(0x33FFFFFF))
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
