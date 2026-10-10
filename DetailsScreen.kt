package com.yourname.lumen.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.domain.model.MediaDetails
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType
import kotlin.coroutines.cancellation.CancellationException

private val TextShadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 3f), blurRadius = 14f)

/**
 * Details for one movie or series. Shows what the list already knew right away, then fills in
 * the rest (genres, cast, duration...) from the source as soon as it answers.
 */
@Composable
fun DetailsScreen(
    item: MediaItem,
    loadDetails: suspend (MediaItem) -> MediaDetails?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography

    val known = remember(item) {
        MediaDetails(
            title = item.title,
            type = item.type,
            year = item.year,
            rating = item.rating,
            plot = item.plot,
            genres = null,
            director = null,
            cast = null,
            duration = null,
            seasons = null,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
        )
    }
    var loaded by remember(item) { mutableStateOf<MediaDetails?>(null) }
    var loading by remember(item) { mutableStateOf(true) }
    val backFocus = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    LaunchedEffect(item) {
        loaded = try {
            loadDetails(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        loading = false
    }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    val d = loaded ?: known
    val backdrop = d.backdropUrl ?: d.posterUrl

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Soft shading on the left and bottom so the text stays readable over any artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xD9070708),
                        0.55f to Color(0x66070708),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to colors.background)),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.ScreenPadding, bottom = 48.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LumenText(
                text = d.title,
                style = type.title.copy(fontSize = 28.sp, lineHeight = 32.sp, shadow = TextShadow),
                maxLines = 3,
            )

            val meta = listOfNotNull(
                if (d.type == MediaType.Series) "Series" else "Movie",
                d.year,
                d.rating?.let { "★ $it" },
                d.duration,
                d.seasons?.let { "$it season${if (it == 1) "" else "s"}" },
            ).joinToString(" · ")
            LumenText(
                text = meta,
                style = type.label.copy(fontSize = 13.sp, shadow = TextShadow),
                color = Color.White.copy(alpha = 0.85f),
            )

            if (!d.genres.isNullOrBlank()) {
                LumenText(
                    text = d.genres,
                    style = type.label.copy(fontSize = 13.sp, shadow = TextShadow),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                )
            }

            if (!d.plot.isNullOrBlank()) {
                LumenText(
                    text = d.plot,
                    style = type.body.copy(fontSize = 14.sp, lineHeight = 20.sp, shadow = TextShadow),
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 6,
                )
            } else if (loading) {
                LumenText("Loading details…", type.label, color = colors.textSecondary)
            }

            if (!d.director.isNullOrBlank()) {
                LumenText(
                    text = "Director: ${d.director}",
                    style = type.label.copy(shadow = TextShadow),
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
            if (!d.cast.isNullOrBlank()) {
                LumenText(
                    text = "Cast: ${d.cast}",
                    style = type.label.copy(shadow = TextShadow),
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(6.dp))
            LumenButton(
                text = "Back",
                modifier = Modifier.focusRequester(backFocus),
                primary = true,
                onClick = onBack,
            )
        }

        // Poster on the right when the page is using a different image as its backdrop.
        val poster = d.posterUrl
        if (poster != null && poster != backdrop) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dimens.ScreenPadding, bottom = 48.dp)
                    .width(150.dp)
                    .aspectRatio(2f / 3f)
                    .clip(shape)
                    .glass(shape),
            ) {
                AsyncImage(
                    model = poster,
                    contentDescription = d.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
