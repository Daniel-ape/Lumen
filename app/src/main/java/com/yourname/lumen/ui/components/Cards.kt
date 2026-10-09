package com.yourname.lumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
            .width(260.dp)
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

/** Poster from the provider. While it loads, or if there is none, a soft colored tile is shown. */
@Composable
fun PosterCard(
    title: String,
    subtitle: String,
    hue: Float,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .width(150.dp)
            .aspectRatio(2f / 3f)
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(Dimens.CardRadius))
            .background(
                Brush.linearGradient(
                    listOf(hueColor(hue, 0.5f, 0.34f), hueColor(hue + 50f, 0.45f, 0.09f)),
                ),
            ),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            LumenText(
                text = title,
                style = LumenTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
            )
            if (subtitle.isNotBlank()) {
                LumenText(
                    text = subtitle,
                    style = LumenTheme.typography.label,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}
