package com.yourname.lumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LogoTile
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.hueColor
import com.yourname.lumen.core.designsystem.tvFocusable

@Composable
fun ChannelCard(
    name: String,
    program: String,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = LumenTheme.colors
    Column(
        modifier = modifier
            .width(300.dp)
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(Dimens.CardRadius))
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LogoTile(initial = name.take(1))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LumenText(
                    text = name,
                    style = LumenTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                LumenText(
                    text = program,
                    style = LumenTheme.typography.label,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        LumenProgressBar(progress = progress, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun PosterCard(
    title: String,
    subtitle: String,
    hue: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .width(170.dp)
            .aspectRatio(2f / 3f)
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(Dimens.CardRadius))
            .background(
                Brush.linearGradient(
                    listOf(hueColor(hue, 0.5f, 0.34f), hueColor(hue + 50f, 0.45f, 0.09f)),
                ),
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LumenText(
                text = title,
                style = LumenTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
            )
            LumenText(
                text = subtitle,
                style = LumenTheme.typography.label,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}
