package com.yourname.lumen.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenCircleButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.motionMs
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.util.backdropRequest
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType

private val TextShadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 3f), blurRadius = 14f)
private const val SLIDE_MS = 8_000

/**
 * Full-screen hero. The artwork is the provider's own, shown full-bleed and dissolving into the page.
 * The progress segments at the bottom are real controls: select one to jump to that title.
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
    autoRotate: Boolean,
    onOpenDetails: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LumenTheme.typography
    val context = LocalContext.current
    val current = index.coerceIn(0, items.lastIndex)
    var paused by remember { mutableStateOf(false) }
    val indicatorFocus = remember { FocusRequester() }
    val progress = remember { Animatable(0f) }

    // Every change of slide, pause or setting restarts the timer from zero, and an old timer is
    // cancelled first, so quick presses can never leave two timers running or skip a slide.
    LaunchedEffect(current, paused, autoRotate, items.size) {
        progress.snapTo(0f)
        if (autoRotate && !paused && items.size > 1) {
            progress.animateTo(1f, tween(SLIDE_MS, easing = LinearEasing))
            onIndexChange((current + 1) % items.size)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        val fadeMs = motionMs(600)
        // Artwork and text change together in one crossfade, so one title is never over another's picture.
        Crossfade(targetState = current, animationSpec = tween(fadeMs), label = "hero") { i ->
            val item = items[i.coerceIn(0, items.lastIndex)]
            val image = item.backdropUrl ?: item.posterUrl
            Box(modifier = Modifier.fillMaxSize()) {
                if (image != null) {
                    AsyncImage(
                        model = backdropRequest(context, image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // The picture melts into the page in stages, so there is no edge where it ends.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.42f to Color.Transparent,
                                    0.68f to LumenTheme.colors.background.copy(alpha = 0.35f),
                                    0.86f to LumenTheme.colors.background.copy(alpha = 0.80f),
                                    1f to LumenTheme.colors.background,
                                ),
                            ),
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Dimens.ScreenPadding, bottom = 92.dp)
                        .widthIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LumenText(
                        label,
                        type.label.copy(fontSize = 11.sp, shadow = TextShadow),
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    LumenText(
                        item.title,
                        type.title.copy(fontSize = 24.sp, lineHeight = 28.sp, shadow = TextShadow),
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
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        // Play and info: both open the details page for the title on screen right now.
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LumenButton(
                text = "Play",
                modifier = Modifier.focusRequester(contentFocus),
                primary = true,
                onClick = { onOpenDetails(items[current]) },
            )
            LumenCircleButton(
                text = "i",
                modifier = Modifier.onDirection(Key.DirectionRight) {
                    if (items.size > 1) runCatching { indicatorFocus.requestFocus() }
                },
                onClick = { onOpenDetails(items[current]) },
            )
        }

        // Progress segments, centred along the bottom. Each one can be selected.
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .onDirection(Key.DirectionUp, onUp)
                    .onDirection(Key.DirectionDown, onDown)
                    .onFocusChanged {
                        paused = it.hasFocus
                        if (it.hasFocus) onFocused()
                    }
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.indices.forEach { i ->
                    ProgressSegment(
                        state = when {
                            i < current -> SegmentState.Done
                            i == current -> SegmentState.Active
                            else -> SegmentState.Waiting
                        },
                        progress = { if (autoRotate && !paused) progress.value else 1f },
                        onSelect = { if (i != current) onIndexChange(i) },
                        modifier = Modifier
                            .then(if (i == current) Modifier.focusRequester(indicatorFocus) else Modifier)
                            .then(
                                if (i == 0) {
                                    Modifier.onDirection(Key.DirectionLeft) { runCatching { contentFocus.requestFocus() } }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

private enum class SegmentState { Done, Active, Waiting }

@Composable
private fun ProgressSegment(
    state: SegmentState,
    progress: () -> Float,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .tvFocusable(
                onClick = onSelect,
                shape = shape,
                focusedScale = 1.1f,
                showOutline = false,
                onFocusChange = { focused = it },
            )
            // A generous touch/focus area around a thin bar.
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .size(width = 30.dp, height = if (focused) 6.dp else 4.dp)
            .drawBehind {
                val corner = CornerRadius(size.height / 2f)
                drawRoundRect(Color.White.copy(alpha = if (focused) 0.45f else 0.28f), cornerRadius = corner)
                val filled = when (state) {
                    SegmentState.Done -> 1f
                    SegmentState.Active -> progress()
                    SegmentState.Waiting -> 0f
                }
                if (filled > 0f) {
                    drawRoundRect(
                        Color.White,
                        size = Size(size.width * filled, size.height),
                        cornerRadius = corner,
                    )
                }
            },
    )
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
