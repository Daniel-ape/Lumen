package com.yourname.lumen.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.hueOf
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.util.backdropRequest
import com.yourname.lumen.core.util.thumbRequest
import com.yourname.lumen.domain.model.Episode
import com.yourname.lumen.domain.model.MediaDetails
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType
import com.yourname.lumen.ui.components.PosterCard
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

private val TextShadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 2f), blurRadius = 8f)

/**
 * The page for one movie or series. It opens straight away with what the list already knew and fills in
 * the rest from the source. Nothing plays until you choose Play (or an episode).
 *
 * Movies: Play starts the movie. Series: pick a season, then an episode; Play starts the episode that is
 * highlighted. Both can be added to or removed from My List, and similar titles from the same category
 * are offered underneath.
 */
@Composable
fun DetailsScreen(
    item: MediaItem,
    loadDetails: suspend (MediaItem) -> MediaDetails?,
    inMyList: Boolean,
    onToggleMyList: () -> Unit,
    similar: List<MediaItem>,
    resumeMs: (String) -> Long,
    onPlayMovie: (MediaDetails, Long) -> Unit,
    onPlayEpisode: (Episode, List<Episode>, Long) -> Unit,
    onOpenSimilar: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val context = LocalContext.current

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
    val playFocus = remember { FocusRequester() }

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
    // Focus starts on Play, so one OK press starts watching.
    LaunchedEffect(item) {
        repeat(3) {
            runCatching { playFocus.requestFocus() }
            delay(40)
        }
    }

    val d = loaded ?: known
    // One picture only: the wide backdrop when there is one, otherwise the poster, stretched to fill.
    val backdrop = d.backdropUrl ?: d.posterUrl
    val isSeries = d.type == MediaType.Series

    // Series: seasons and the episodes of the chosen season.
    val bySeason = remember(d.episodes) { d.episodes.groupBy { it.season }.toSortedMap() }
    var seasonChoice by remember(item) { mutableStateOf<Int?>(null) }
    val season = seasonChoice?.takeIf { it in bySeason } ?: bySeason.keys.firstOrNull()
    val episodes = remember(bySeason, season) { bySeason[season].orEmpty().sortedBy { it.number } }
    var focusedEpisodeId by remember(item) { mutableStateOf<String?>(null) }
    val episodeToPlay = episodes.firstOrNull { it.id == focusedEpisodeId } ?: episodes.firstOrNull()

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
        if (backdrop != null) {
            AsyncImage(
                model = backdropRequest(context, backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Shading: darker on the left for the text, and deepening toward the bottom for the rows.
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
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        0.75f to colors.background.copy(alpha = 0.82f),
                        1f to colors.background,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The first block starts about a third of the way down, over the lower part of the picture.
            contentPadding = PaddingValues(top = maxHeight * 0.34f, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "info") {
                Column(
                    modifier = Modifier
                        .padding(start = Dimens.ScreenPadding)
                        .widthIn(max = 580.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LumenText(
                        text = d.title,
                        style = type.title.copy(fontSize = 28.sp, lineHeight = 32.sp, shadow = TextShadow),
                        maxLines = 2,
                    )
                    val meta = listOfNotNull(
                        if (isSeries) "Series" else "Movie",
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
                            maxLines = 1,
                        )
                    }
                    if (!d.plot.isNullOrBlank()) {
                        // Always a short, fixed-height summary that ends in "…"; the page never grows.
                        LumenText(
                            text = d.plot,
                            style = type.body.copy(fontSize = 14.sp, lineHeight = 20.sp, shadow = TextShadow),
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 3,
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
                            maxLines = 1,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        // Where you stopped, if you stopped partway through this movie or episode.
                        val resume = if (!isSeries) resumeMs("movie:${item.id}") else episodeToPlay?.let { resumeMs("ep:${it.id}") } ?: 0L
                        val playLabel = when {
                            !isSeries -> if (resume > 0) "Resume ${minutesAndSeconds(resume)}" else "Play"
                            episodeToPlay != null ->
                                (if (resume > 0) "Resume" else "Play") + " S${episodeToPlay.season} E${episodeToPlay.number}"
                            loading -> "Loading…"
                            else -> "No episodes"
                        }
                        LumenButton(
                            text = playLabel,
                            modifier = Modifier.focusRequester(playFocus),
                            primary = true,
                            onClick = {
                                if (!isSeries) onPlayMovie(d, resume) else episodeToPlay?.let { onPlayEpisode(it, d.episodes, resume) }
                            },
                        )
                        if (resume > 0) {
                            LumenButton(
                                text = "Start over",
                                onClick = {
                                    if (!isSeries) onPlayMovie(d, 0L) else episodeToPlay?.let { onPlayEpisode(it, d.episodes, 0L) }
                                },
                            )
                        }
                        LumenButton(
                            text = if (inMyList) "Remove from My List" else "Add to My List",
                            onClick = onToggleMyList,
                        )
                    }
                }
            }

            if (isSeries && bySeason.isNotEmpty()) {
                item(key = "seasons") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(bySeason.keys.toList(), key = { "s$it" }) { number ->
                                SeasonChip(
                                    text = "Season $number",
                                    selected = number == season,
                                    onFocused = { seasonChoice = number },
                                )
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(episodes, key = { it.id }) { ep ->
                                EpisodeCard(
                                    episode = ep,
                                    onFocused = { focusedEpisodeId = ep.id },
                                    onClick = { onPlayEpisode(ep, d.episodes, resumeMs("ep:${ep.id}")) },
                                )
                            }
                        }
                    }
                }
            }

            if (similar.isNotEmpty()) {
                item(key = "similar") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LumenText(
                            text = if (isSeries) "More series like this" else "More movies like this",
                            style = type.heading.copy(fontSize = 17.sp),
                            modifier = Modifier.padding(start = Dimens.ScreenPadding),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(similar, key = { it.type.name + it.id }) { m ->
                                PosterCard(
                                    title = m.title,
                                    subtitle = listOfNotNull(m.year, m.rating?.let { "★ $it" }).joinToString(" · "),
                                    hue = hueOf(m.title),
                                    imageUrl = m.posterUrl,
                                    onClick = { onOpenSimilar(m) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Just an arrow, top-left; the Back button on the remote does the same.
        BackArrow(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = Dimens.ScreenPadding, top = 20.dp),
        )
    }
}

@Composable
private fun BackArrow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(44.dp)
            .tvFocusable(onClick = onClick, shape = CircleShape, focusedScale = 1.06f, onFocusChange = { focused = it })
            .glass(CircleShape, if (focused) GlassLevel.Selected else GlassLevel.Control),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w, h / 2f)
                lineTo(0f, h / 2f)
                moveTo(w * 0.45f, h * 0.06f)
                lineTo(0f, h / 2f)
                lineTo(w * 0.45f, h * 0.94f)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun SeasonChip(text: String, selected: Boolean, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .tvFocusable(
                onClick = onFocused,
                shape = shape,
                focusedScale = 1.04f,
                onFocusChange = {
                    focused = it
                    if (it) onFocused()
                },
            )
            .glass(shape, if (focused) GlassLevel.Selected else if (selected) GlassLevel.Primary else GlassLevel.Control)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        LumenText(
            text = text,
            style = LumenTheme.typography.body.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun EpisodeCard(episode: Episode, onFocused: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val type = LumenTheme.typography
    val colors = LumenTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(236.dp)
            .tvFocusable(onClick = onClick, shape = shape, focusedScale = 1.04f, onFocusChange = { if (it) onFocused() })
            .glass(shape, GlassLevel.Panel)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x22FFFFFF)),
        ) {
            val image = episode.imageUrl
            if (image != null) {
                AsyncImage(
                    model = thumbRequest(context, image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        LumenText(
            text = "E${episode.number} · ${episode.title}",
            style = type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        if (!episode.plot.isNullOrBlank()) {
            LumenText(
                text = episode.plot,
                style = type.label.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = colors.textSecondary,
                maxLines = 2,
            )
        }
    }
}

private fun minutesAndSeconds(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
