package com.yourname.lumen.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.hueColor
import com.yourname.lumen.core.designsystem.hueOf
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType
import kotlinx.coroutines.delay

/** Fades the bottom of whatever it is applied to into transparency, so it melts into the page. */
private fun Modifier.fadeBottom(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

private val TextShadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 3f), blurRadius = 14f)

/** Full-screen hero. Artwork comes from the user's own source. */
@Composable
fun HeroSection(
    items: List<MediaItem>,
    contentFocus: FocusRequester,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onFocused: () -> Unit,
    onAmbient: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    LaunchedEffect(index) {
        val current = items[index]
        onAmbient(current.backdropUrl ?: current.posterUrl)
    }

    // Auto-rotate every 8s, but never while a hero button has focus.
    LaunchedEffect(paused) {
        while (!paused) {
            delay(8_000)
            index = (index + 1) % items.size
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Artwork and text crossfade. Buttons live outside so focus is never lost mid-fade.
        Crossfade(targetState = index, animationSpec = tween(600), label = "hero") { i ->
            val item = items[i]
            val image = item.backdropUrl ?: item.posterUrl
            Box(modifier = Modifier.fillMaxSize()) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeBottom(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        hueColor(hueOf(item.title), 0.5f, 0.4f).copy(alpha = 0.45f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Dimens.ScreenPadding, bottom = 108.dp)
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LumenText(
                        "Recently added",
                        type.label.copy(shadow = TextShadow),
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    LumenText(
                        item.title,
                        type.display.copy(fontSize = 36.sp, lineHeight = 40.sp, shadow = TextShadow),
                        maxLines = 2,
                    )
                    val meta = listOfNotNull(
                        if (item.type == MediaType.Series) "Series" else "Movie",
                        item.year,
                        item.rating?.let { "★ $it" },
                    ).joinToString(" · ")
                    LumenText(
                        meta,
                        type.label.copy(shadow = TextShadow),
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    if (!item.plot.isNullOrBlank()) {
                        LumenText(
                            item.plot,
                            type.body.copy(fontSize = 15.sp, lineHeight = 21.sp, shadow = TextShadow),
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.ScreenPadding, bottom = 36.dp)
                .onDirection(Key.DirectionUp, onUp)
                .onDirection(Key.DirectionDown, onDown)
                .onFocusChanged {
                    paused = it.hasFocus
                    if (it.hasFocus) onFocused()
                }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LumenButton(
                text = "Watch",
                modifier = Modifier.focusRequester(contentFocus),
                primary = true,
            )
            LumenButton("More info")
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.ScreenPadding, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.indices.forEach { dot ->
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (dot == index) Color.White else Color.White.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

/** Shown instead of the hero while loading, with no source, or when the source can't be reached. */
@Composable
fun HeroMessage(
    title: String,
    message: String,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    contentFocus: FocusRequester,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.ScreenPadding, bottom = 56.dp)
                .widthIn(max = 560.dp)
                .onDirection(Key.DirectionUp, onUp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LumenText(title, type.display.copy(fontSize = 36.sp, lineHeight = 40.sp))
            LumenText(message, type.body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = colors.textSecondary)
            if (primaryLabel != null) {
                Spacer(Modifier.height(8.dp))
                LumenButton(
                    text = primaryLabel,
                    modifier = Modifier.focusRequester(contentFocus),
                    primary = true,
                    onClick = onPrimary,
                )
            }
        }
    }
}
