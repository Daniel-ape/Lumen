package com.yourname.lumen.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A text input that uses the device's own keyboard (the system Input Method), everywhere.
 *
 * - Touch: tapping the field opens the system keyboard.
 * - Android TV remote: move onto the field with the D-pad (it lights up), then press OK to open the
 *   TV's on-screen keyboard. Back closes the keyboard first, and a second Back leaves the screen.
 * - Physical keyboard: type directly.
 * Lumen never draws a keyboard of its own.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LumenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    masked: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = focusRequester ?: remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .glass(shape, if (focused) GlassLevel.Selected else GlassLevel.Control)
            // Tapping anywhere on the row, not only on the text, starts typing.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                requester.requestFocus()
                keyboard?.show()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (label != null) {
            LumenText(label, LumenTheme.typography.body.copy(fontSize = 15.sp), color = LumenTheme.colors.textSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = if (masked) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) keyboard?.hide()
                }
                .onPreviewKeyEvent { event ->
                    // OK on the remote opens the TV's own keyboard.
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionCenter) {
                        keyboard?.show()
                        true
                    } else {
                        false
                    }
                },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        LumenText(placeholder, LumenTheme.typography.body.copy(fontSize = 15.sp), color = LumenTheme.colors.textTertiary)
                    }
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}
