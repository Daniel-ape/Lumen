package com.yourname.lumen.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.motionMs
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.util.backdropRequest
import com.yourname.lumen.core.util.thumbRequest
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType

private val TextShadow = Shadow(color = Color(0xB3000000), offset = Offset(0f, 2f), blurRadius = 8f)

/**
 * The top of Home: one title shown large across the whole screen, with a row of wide "Featured" cards over
 * the bottom of it. Moving along the cards changes the big picture, title and description to the card you are
 * on; pressing OK opens that title's page (where Play is). The artwork is the provider's own and dissolves into
 * the page at the bottom, so the cards sit on it with no edge.
 *
 * [index] and [onIndexChange] are owned by the caller, so the position survives leaving and returning.
 */
@Composable
fun HeroSection(
    items: List<MediaItem>,
    label: String,
    index: Int,
    onIndexChange: (Int) -> Unit,
    contentFocus: FocusRequester,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onFocused: () -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    restoreItemId: String?,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") autoRotate: Boolean = false,
) {
    val type = LumenTheme.typography
    val context = LocalContext.current
    val current = index.coerceIn(0, items.lastIndex)

    Box(modifier = modifier.fillMaxWidth()) {
        // Artwork and text change together in one crossfade, so one title is never over another's picture.
        Crossfade(targetState = current, animationSpec = tween(motionMs(450)), label = "hero") { i ->
            val item = items[i.coerceIn(0, items.lastIndex)]
            val image = item.backdropUrl ?: item.posterUrl
            Box(modifier = Modifier.fillMaxSize()) {
                if (image != null) {
                    // The picture and its shading are drawn together and then faded to fully transparent toward the
                    // bottom. It fades to nothing (not to a colour), so the page behind shows through: no edge, no line.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Black,
                                        0.5f to Color.Black,
                                        0.7f to Color(0xD9000000),
                                        0.86f to Color(0x80000000),
                                        0.96f to Color(0x24000000),
                                        1f to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        AsyncImage(
                            model = backdropRequest(context, image),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Shading for the text: darker on the left, and a band low down for the cards.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0f to Color(0xB3000000),
                                        0.5f to Color(0x40000000),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.5f to Color.Transparent,
                                        0.82f to Color(0x80000000),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Dimens.ScreenPadding, bottom = 120.dp)
                        .widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LumenText(
                        item.title,
                        type.title.copy(fontSize = 34.sp, lineHeight = 38.sp, shadow = TextShadow),
                        color = Color.White,
                        maxLines = 2,
                    )
                    val meta = listOfNotNull(
                        if (item.type == MediaType.Series) "Series" else "Movie",
                        item.year,
                        item.rating?.let { "★ $it" },
                    ).joinToString(" · ")
                    LumenText(
                        meta,
                        type.label.copy(fontSize = 12.sp, shadow = TextShadow),
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    if (!item.plot.isNullOrBlank()) {
                        LumenText(
                            item.plot,
                            type.body.copy(fontSize = 13.sp, lineHeight = 18.sp, shadow = TextShadow),
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 3,
                        )
                    }
                }
            }
        }

        // The Featured row, over the bottom of the picture.
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp)) {
            LumenText(
                text = "Featured",
                style = type.heading.copy(fontSize = 15.sp, shadow = TextShadow),
                modifier = Modifier.padding(start = Dimens.ScreenPadding),
            )
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
            LazyRow(
                state = listState,
                modifier = Modifier
                    .onDirection(Key.DirectionUp, onUp)
                    .onDirection(Key.DirectionDown, onDown)
                    .focusGroup(),
                // Vertical padding leaves room for the focus scale-up.
                contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(items, key = { _, m -> m.type.name + m.id }) { i, m ->
                    val restore = remember { FocusRequester() }
                    val isRestoreTarget = restoreItemId == "hero:" + m.id
                    LaunchedEffect(isRestoreTarget) {
                        if (isRestoreTarget) {
                            runCatching { restore.requestFocus() }
                            onRestored()
                        }
                    }
                    FeaturedCard(
                        item = m,
                        onFocus = {
                            onIndexChange(i)
                            onFocused()
                        },
                        onClick = { onOpenDetails(m) },
                        modifier = when {
                            isRestoreTarget -> Modifier.focusRequester(restore)
                            i == 0 -> Modifier.focusRequester(contentFocus)
                            else -> Modifier
                        },
                    )
                }
            }
        }
    }
}

/** A wide card with the title's picture and name. Moving onto it puts that title in the big picture above. */
@Composable
private fun FeaturedCard(
    item: MediaItem,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(172.dp)
            .aspectRatio(16f / 9f)
            .tvFocusable(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.08f,
                onFocusChange = { if (it) onFocus() },
            )
            .background(Color(0x22FFFFFF), shape),
    ) {
        val image = item.backdropUrl ?: item.posterUrl
        if (image != null) {
            AsyncImage(
                model = thumbRequest(context, image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000))),
        )
        LumenText(
            text = item.title,
            style = LumenTheme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
