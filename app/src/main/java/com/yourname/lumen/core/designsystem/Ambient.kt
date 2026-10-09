package com.yourname.lumen.core.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Soft frosted backdrop: the current artwork, decoded at a tiny size and stretched across the
 * screen so it turns into a smooth blur. This looks the same on every Android version, costs almost
 * nothing, and fades when the artwork changes. Without artwork it is the plain dark base.
 */
@Composable
fun AmbientBackground(imageUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumenTheme.colors.background),
    ) {
        Crossfade(targetState = imageUrl, animationSpec = tween(900), label = "ambient") { url ->
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).size(96, 54).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Keeps text readable over any artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x990B0B0D)),
        )
    }
}
