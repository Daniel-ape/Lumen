package com.yourname.lumen.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.hueOf
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.domain.model.HomeState
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.ui.components.PosterCard
import kotlinx.coroutines.launch

/**
 * Home scrolls row by row instead of nudging a little on every focus change. That removes the
 * shaking, and it always returns to the full-screen hero when you go back up.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    dockHasFocus: Boolean,
    showRatings: Boolean,
    autoRotate: Boolean,
    onOpenDetails: (MediaItem) -> Unit,
    onAtTopChange: (Boolean) -> Unit,
    onAddSource: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topInset = with(LocalDensity.current) { 72.dp.roundToPx() }
    val rowFocus = remember { List(2) { FocusRequester() } }

    // Horizontal rows keep Android's normal scrolling; only the page itself is scrolled by us.
    val defaultSpec = LocalBringIntoViewSpec.current
    val pageSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
        }
    }

    val rows: List<Pair<String, List<MediaItem>>> = (state as? HomeState.Ready)?.content?.let {
        listOf("Recently added movies" to it.movies, "Recently added series" to it.series)
            .filter { row -> row.second.isNotEmpty() }
    } ?: emptyList()

    // Tell the app when we are at the very top so it can hide or show the dock.
    // Two thresholds (leave at 80px, return at 8px) stop it flickering near the edge.
    LaunchedEffect(listState) {
        var atTop = true
        onAtTopChange(true)
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val next = if (atTop) !(index > 0 || offset > 80) else (index == 0 && offset <= 8)
                if (next != atTop) {
                    atTop = next
                    onAtTopChange(next)
                }
            }
    }

    // Whenever the dock has focus, show the full hero behind it.
    LaunchedEffect(dockHasFocus) {
        if (dockHasFocus) listState.animateScrollToItem(0)
    }

    val goToHero: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(0)
            runCatching { contentFocus.requestFocus() }
        }
    }
    val goToRow: (Int) -> Unit = { k ->
        scope.launch {
            listState.animateScrollToItem(1 + 2 * k, -topInset)
            runCatching { rowFocus[k].requestFocus() }
        }
    }
    val goToDock: () -> Unit = { runCatching { dockFocus.requestFocus() } }

    CompositionLocalProvider(LocalBringIntoViewSpec provides pageSpec) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            when (state) {
                HomeState.Loading -> item {
                    HeroMessage(
                        title = "Loading…",
                        message = "Getting what's new from your source.",
                        primaryLabel = null,
                        onPrimary = {},
                        contentFocus = contentFocus,
                        onUp = goToDock,
                        modifier = Modifier.fillParentMaxHeight(),
                    )
                }

                HomeState.NoSource -> item {
                    HeroMessage(
                        title = "Add your first source",
                        message = "Connect your IPTV service to see what's new from your provider.",
                        primaryLabel = "Add source",
                        onPrimary = onAddSource,
                        contentFocus = contentFocus,
                        onUp = goToDock,
                        modifier = Modifier.fillParentMaxHeight(),
                    )
                }

                is HomeState.Failed -> item {
                    HeroMessage(
                        title = "Unable to connect to this source.",
                        message = state.message,
                        primaryLabel = "Retry",
                        onPrimary = onRetry,
                        contentFocus = contentFocus,
                        onUp = goToDock,
                        modifier = Modifier.fillParentMaxHeight(),
                    )
                }

                is HomeState.Ready -> {
                    if (state.content.hero.isEmpty()) {
                        item {
                            HeroMessage(
                                title = "Nothing here yet",
                                message = "No movies or series were found on this source.",
                                primaryLabel = null,
                                onPrimary = {},
                                contentFocus = contentFocus,
                                onUp = goToDock,
                                modifier = Modifier.fillParentMaxHeight(),
                            )
                        }
                    } else {
                        item {
                            HeroSection(
                                items = state.content.hero,
                                contentFocus = contentFocus,
                                onUp = goToDock,
                                onDown = { if (rows.isNotEmpty()) goToRow(0) },
                                onFocused = { scope.launch { listState.animateScrollToItem(0) } },
                                label = state.content.heroLabel,
                                autoRotate = autoRotate,
                                onOpenDetails = onOpenDetails,
                                modifier = Modifier.fillParentMaxHeight(),
                            )
                        }
                        rows.forEachIndexed { k, row ->
                            item { SectionTitle(row.first) }
                            item {
                                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                    PosterRow(
                                        items = row.second,
                                        showRatings = showRatings,
                                        onOpen = onOpenDetails,
                                        firstFocus = rowFocus[k],
                                        onUp = { if (k == 0) goToHero() else goToRow(k - 1) },
                                        onDown = { if (k < rows.lastIndex) goToRow(k + 1) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterRow(
    items: List<MediaItem>,
    showRatings: Boolean,
    onOpen: (MediaItem) -> Unit,
    firstFocus: FocusRequester,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    LazyRow(
        // The requester sits on the whole row, so it works even after the row was scrolled sideways.
        modifier = Modifier
            .focusRequester(firstFocus)
            .focusGroup()
            .onDirection(Key.DirectionUp, onUp)
            .onDirection(Key.DirectionDown, onDown),
        // Vertical padding leaves room for the focus scale-up so it never clips.
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.RowGap),
    ) {
        items(items) { m ->
            PosterCard(
                title = m.title,
                subtitle = listOfNotNull(m.year, if (showRatings) m.rating?.let { "★ $it" } else null)
                    .joinToString(" · "),
                hue = hueOf(m.title),
                imageUrl = m.posterUrl,
                onClick = { onOpen(m) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    LumenText(
        text = text,
        style = LumenTheme.typography.heading,
        modifier = Modifier.padding(
            start = Dimens.ScreenPadding,
            top = Dimens.SectionGap,
            bottom = 4.dp,
        ),
    )
}
