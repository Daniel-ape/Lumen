package com.yourname.lumen.ui.player

import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.yourname.lumen.R
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlin.math.roundToInt

/**
 * The single video surface. It either fills the screen or sits exactly over the preview slot in
 * Live TV, so the same stream carries on when you go fullscreen and come back.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoLayer(
    controller: PlaybackController,
    fullscreen: Boolean,
    previewBounds: Rect?,
    scaleMode: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val placement = if (fullscreen || previewBounds == null) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .offset { IntOffset(previewBounds.left.roundToInt(), previewBounds.top.roundToInt()) }
            .size(
                width = with(density) { previewBounds.width.toDp() },
                height = with(density) { previewBounds.height.toDp() },
            )
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = placement,
            factory = { context ->
                (LayoutInflater.from(context).inflate(R.layout.lumen_player_view, null) as PlayerView).apply {
                    player = controller.exo
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            update = { view ->
                view.resizeMode = when (scaleMode) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
        )

        // Small status text over the preview while it loads or fails. Fullscreen has its own.
        if (!fullscreen && previewBounds != null) {
            val label = when {
                controller.failed -> "Can't play this channel"
                controller.buffering -> "Loading…"
                else -> null
            }
            if (label != null) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(previewBounds.left.roundToInt(), previewBounds.top.roundToInt()) }
                        .size(
                            width = with(density) { previewBounds.width.toDp() },
                            height = with(density) { previewBounds.height.toDp() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenText(
                        text = label,
                        style = LumenTheme.typography.label,
                        modifier = Modifier
                            .glass(RoundedCornerShape(50), GlassLevel.Nav)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
