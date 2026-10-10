package com.yourname.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.tvFocusable

/**
 * On-screen keyboard built for a remote. It does not depend on the TV's own keyboard app,
 * so it behaves the same on every device.
 */
@Composable
fun TvKeyboard(
    title: String,
    value: String,
    masked: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    var shift by remember { mutableStateOf(false) }
    val startFocus = remember { FocusRequester() }
    val rows = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm", "./:-_@", "!#\$%&+=?*")

    LaunchedEffect(Unit) { runCatching { startFocus.requestFocus() } }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LumenText(title, type.heading)
        Box(
            modifier = Modifier
                .widthIn(min = 460.dp)
                .glass(RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            LumenText(
                text = if (masked) "•".repeat(value.length) else value.ifEmpty { " " },
                style = type.body,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { r, chars ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chars.forEachIndexed { c, ch ->
                    val shown = if (shift) ch.uppercaseChar() else ch
                    KeyButton(
                        label = shown.toString(),
                        modifier = if (r == 1 && c == 0) Modifier.focusRequester(startFocus) else Modifier,
                    ) { onValueChange(value + shown) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyButton(if (shift) "Shift on" else "Shift", wide = true) { shift = !shift }
            KeyButton("Space", wide = true) { onValueChange("$value ") }
            KeyButton("Delete", wide = true) { onValueChange(value.dropLast(1)) }
            KeyButton("Clear", wide = true) { onValueChange("") }
            KeyButton("Done", wide = true, primary = true, onClick = onDone)
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LumenTheme.colors
    Box(
        modifier = modifier
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(8.dp), focusedScale = 1.08f)
            .glass(RoundedCornerShape(8.dp), strong = primary)
            .widthIn(min = if (wide) 84.dp else 38.dp)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        LumenText(
            text = label,
            style = LumenTheme.typography.label,
            color = Color.White,
        )
    }
}
