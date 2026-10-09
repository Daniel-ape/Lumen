package com.yourname.lumen.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.routeKey
import com.yourname.lumen.domain.model.Source

@Composable
fun SettingsScreen(
    sources: List<Source>,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    onAddSource: () -> Unit,
    onRemove: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 84.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LumenText("Sources", type.title)
        LumenButton(
            text = "Add Xtream source",
            modifier = Modifier
                .focusRequester(contentFocus)
                .routeKey(Key.DirectionUp, dockFocus),
            primary = true,
            onClick = onAddSource,
        )
        if (sources.isEmpty()) {
            LumenText("No sources added yet.", type.body, color = colors.textSecondary)
        }
        sources.forEach { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.width(320.dp)) {
                    LumenText(
                        text = source.name,
                        style = type.body.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                    LumenText("Xtream Codes", type.label, color = colors.textSecondary)
                }
                LumenButton(text = "Remove", onClick = { onRemove(source) })
            }
        }
    }
}
