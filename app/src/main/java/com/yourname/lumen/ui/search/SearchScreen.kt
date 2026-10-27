package com.yourname.lumen.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTextField
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.hueOf
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.util.dayAndTime
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.data.epg.GuideIndex
import com.yourname.lumen.data.search.ProgrammeHit
import com.yourname.lumen.data.search.SearchIndex
import com.yourname.lumen.data.search.SearchResults
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.ui.components.ChannelListRow
import com.yourname.lumen.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What is still loading, so the screen can say so while showing whatever results already exist. */
class SearchStatus(
    val loadingLibrary: Boolean,
    val loadingChannels: Boolean,
    val guidePhase: GuideIndex.Phase,
    val guideMessage: String?,
)

/**
 * Universal search: movies, series, live channels and programme listings in one list, whichever
 * section you came from. Typing uses the device's own keyboard. Results update as you type (after a
 * short pause), and again whenever more of the library finishes loading.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    index: SearchIndex?,
    status: SearchStatus,
    epg: EpgCache?,
    showRatings: Boolean,
    onOpenItem: (MediaItem) -> Unit,
    onPlayChannel: (LiveChannel) -> Unit,
    onOpenProgramme: (ProgrammeHit) -> Unit,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var results by remember { mutableStateOf(SearchResults.Empty) }
    var searching by remember { mutableStateOf(false) }

    // A short pause after the last keystroke, then the search runs away from the main thread.
    // It runs again when the index changes, so results fill in as more content loads.
    LaunchedEffect(query, index) {
        if (query.isBlank() || index == null) {
            results = SearchResults.Empty
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(220)
        results = withContext(Dispatchers.Default) { index.search(query, System.currentTimeMillis() / 1000) }
        searching = false
    }

    // On a phone or tablet the keyboard opens right away; on a TV it waits until you press OK.
    LaunchedEffect(Unit) {
        if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN) && query.isEmpty()) {
            delay(250)
            keyboard?.show()
        }
    }

    val loading = status.loadingLibrary || status.loadingChannels || status.guidePhase == GuideIndex.Phase.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 70.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LumenTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search movies, series, channels and programmes",
            imeAction = ImeAction.Search,
            onImeAction = { keyboard?.hide() },
            focusRequester = contentFocus,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .onDirection(Key.DirectionUp) { runCatching { dockFocus.requestFocus() } },
        )

        // Status line, and the Clear button when there is something to clear.
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (query.isNotEmpty()) {
                LumenButton(text = "Clear", compact = true, onClick = { onQueryChange("") })
            }
            LumenText(
                text = statusText(status, loading, searching),
                style = type.label.copy(fontSize = 12.sp),
                color = colors.textSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            query.isBlank() -> Hint(
                "Search your whole library at once: movies, series, live channels, and what's on TV now or coming up.",
            )

            results.isEmpty && !searching && !loading -> Hint("No results for \"${query.trim()}\".")
            results.isEmpty -> Hint(if (loading) "Searching what has loaded so far…" else "Searching…")
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (results.movies.isNotEmpty()) {
                    item(key = "h_movies") { SectionTitle("Movies", results.movies.size) }
                    item(key = "movies") { PosterStrip(results.movies, showRatings, onOpenItem) }
                }
                if (results.series.isNotEmpty()) {
                    item(key = "h_series") { SectionTitle("Series", results.series.size) }
                    item(key = "series") { PosterStrip(results.series, showRatings, onOpenItem) }
                }
                if (results.channels.isNotEmpty()) {
                    item(key = "h_channels") { SectionTitle("Live channels", results.channels.size) }
                    items(results.channels, key = { "c" + it.id }) { channel ->
                        ChannelListRow(
                            channel = channel,
                            epg = epg,
                            showLogo = true,
                            favorite = false,
                            playing = false,
                            onFocus = {},
                            onClick = { onPlayChannel(channel) },
                            onMenu = {},
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }
                if (results.onNow.isNotEmpty()) {
                    item(key = "h_now") { SectionTitle("On TV now", results.onNow.size) }
                    items(results.onNow, key = { "n" + it.channel.id + it.startSec + it.title }) { hit ->
                        ProgrammeRow(hit, onClick = { onOpenProgramme(hit) })
                    }
                }
                if (results.upcoming.isNotEmpty()) {
                    item(key = "h_later") { SectionTitle("Coming up on TV", results.upcoming.size) }
                    items(results.upcoming, key = { "u" + it.channel.id + it.startSec + it.title }) { hit ->
                        ProgrammeRow(hit, onClick = { onOpenProgramme(hit) })
                    }
                }
            }
        }
    }
}

private fun statusText(status: SearchStatus, loading: Boolean, searching: Boolean): String {
    val parts = ArrayList<String>()
    if (status.loadingLibrary) parts.add("loading movies and series")
    if (status.loadingChannels) parts.add("loading channels")
    if (status.guidePhase == GuideIndex.Phase.Loading) parts.add("loading the programme guide")
    return when {
        parts.isNotEmpty() -> "Still " + parts.joinToString(", ") + "… results update as they arrive."
        status.guidePhase == GuideIndex.Phase.Unavailable -> status.guideMessage ?: "Programme search isn't available."
        searching || loading -> "Searching…"
        else -> ""
    }
}

@Composable
private fun Hint(text: String) {
    LumenText(
        text = text,
        style = LumenTheme.typography.body.copy(fontSize = 15.sp),
        color = LumenTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 24.dp, start = 4.dp).widthIn(max = 560.dp),
    )
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        LumenText(text, LumenTheme.typography.heading.copy(fontSize = 17.sp))
        LumenText(count.toString(), LumenTheme.typography.label, color = LumenTheme.colors.textTertiary)
    }
}

@Composable
private fun PosterStrip(items: List<MediaItem>, showRatings: Boolean, onOpen: (MediaItem) -> Unit) {
    LazyRow(
        // Vertical padding leaves room for the focus scale-up so it never clips.
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = { it.type.name + it.id }) { m ->
            PosterCard(
                title = m.title,
                subtitle = listOfNotNull(m.year, if (showRatings) m.rating?.let { "★ $it" } else null).joinToString(" · "),
                hue = hueOf(m.title),
                imageUrl = m.posterUrl,
                onClick = { onOpen(m) },
            )
        }
    }
}

/** A programme from the guide: what it is, which channel carries it, and when. */
@Composable
private fun ProgrammeRow(hit: ProgrammeHit, onClick: () -> Unit) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .tvFocusable(
                onClick = onClick,
                shape = shape,
                focusedScale = 1.015f,
                onFocusChange = { focused = it },
            )
            .then(if (focused) Modifier.glass(shape, GlassLevel.Selected) else Modifier.glass(shape, GlassLevel.Panel))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Airing now is marked LIVE in the accent colour; upcoming shows when it starts.
        LumenText(
            text = if (hit.isNow) "LIVE" else dayAndTime(hit.startSec),
            style = type.label.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = if (hit.isNow) colors.accent else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(end = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            LumenText(hit.title, type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            LumenText(hit.channel.name, type.label.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1)
        }
        timeRangeText(hit.startSec, hit.endSec)?.let {
            LumenText(it, type.label.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1)
        }
    }
}
