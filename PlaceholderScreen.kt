package com.yourname.lumen.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.ui.navigation.Section

/** A deliberate empty state for sections that are built in later phases. */
@Composable
fun PlaceholderScreen(section: Section, modifier: Modifier = Modifier) {
    val colors = LumenTheme.colors
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(2.dp, colors.textTertiary, CircleShape),
        )
        LumenText(section.title, LumenTheme.typography.title)
        LumenText(
            text = "This section is coming in a later phase.",
            style = LumenTheme.typography.body,
            color = colors.textSecondary,
        )
    }
}
