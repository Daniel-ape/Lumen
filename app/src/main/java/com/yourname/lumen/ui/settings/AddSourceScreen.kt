package com.yourname.lumen.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.tvFocusable
import kotlinx.coroutines.launch

@Composable
fun AddSourceScreen(
    onClose: () -> Unit,
    onSubmit: suspend (server: String, user: String, pass: String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val scope = rememberCoroutineScope()

    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var editing by remember { mutableIntStateOf(-1) }
    var last by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldFocus = remember { List(3) { FocusRequester() } }

    // Back closes the keyboard first, then the screen.
    BackHandler(enabled = editing >= 0) { editing = -1 }
    BackHandler(enabled = editing < 0 && !busy) { onClose() }

    // When the keyboard closes, go back to the field that was just edited.
    LaunchedEffect(editing) {
        if (editing < 0) runCatching { fieldFocus[last].requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (editing >= 0) {
            TvKeyboard(
                title = listOf("Server address", "Username", "Password")[editing],
                value = when (editing) {
                    0 -> server
                    1 -> user
                    else -> pass
                },
                masked = editing == 2,
                onValueChange = {
                    when (editing) {
                        0 -> server = it
                        1 -> user = it
                        else -> pass = it
                    }
                },
                onDone = { editing = -1 },
            )
        } else {
            Column(
                modifier = Modifier.width(480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LumenText("Add Xtream source", type.title)
                LumenText(
                    "Enter the details from your provider.",
                    type.body,
                    color = colors.textSecondary,
                )
                FieldRow("Server address", server, false, Modifier.focusRequester(fieldFocus[0])) {
                    last = 0
                    editing = 0
                }
                FieldRow("Username", user, false, Modifier.focusRequester(fieldFocus[1])) {
                    last = 1
                    editing = 1
                }
                FieldRow("Password", pass, true, Modifier.focusRequester(fieldFocus[2])) {
                    last = 2
                    editing = 2
                }
                error?.let {
                    LumenText(it, type.label, color = Color(0xFFFF8A80))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LumenButton(
                        text = if (busy) "Connecting…" else "Connect",
                        primary = true,
                        onClick = {
                            if (!busy) {
                                if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                                    error = "Fill in all three fields."
                                } else {
                                    busy = true
                                    error = null
                                    scope.launch {
                                        val ok = onSubmit(server, user, pass)
                                        busy = false
                                        if (ok) {
                                            onClose()
                                        } else {
                                            error = "Unable to connect to this source. Check the details and try again."
                                        }
                                    }
                                }
                            }
                        },
                    )
                    LumenButton(text = "Cancel", onClick = { if (!busy) onClose() })
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    masked: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LumenText(label, type.body, color = colors.textSecondary)
        LumenText(
            text = when {
                value.isEmpty() -> "Select to enter"
                masked -> "•".repeat(value.length)
                else -> value
            },
            style = type.body,
            maxLines = 1,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
