package com.yourname.lumen.ui.home

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.yourname.lumen.ui.components.ChannelLogo
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.GlassLevel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import com.yourname.lumen.domain.model.LiveChannel
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
import androidx.compose.foundation.lazy.LazyListState
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
    onRowOpen: (MediaItem) -> Unit,
    onAtTopChange: (Boolean) -> Unit,
    onAddSource: () -> Unit,
    onRetry: () -> Unit,
    listState: LazyListState,
    rowStates: List<LazyListState>,
    heroIndex: Int,
    onHeroIndex: (Int) -> Unit,
    restoreItemId: String?,
    onRestored: () -> Unit,
    recentChannels: List<LiveChannel>,
    recentFocusId: String?,
    exitSignal: Int,
    onPlayRecent: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val topInset = with(LocalDensity.current) { 72.dp.roundToPx() }
    val rowFocus = remember { List(3) { FocusRequester() } }
    val recentCardFocus = remember { FocusRequester() }

    // Horizontal rows keep Android's normal scrolling; only the page itself is scrolled by us.
    val defaultSpec = LocalBringIntoViewSpec.current
    val pageSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
        }
    }

    // Rows under the hero. "Recently Watched Channels" exists only when there really is watch history.
    val rows: List<HomeRow> = buildList {
        if (state is HomeState.Ready && recentChannels.isNotEmpty()) {
            add(HomeRow.Channels("Recently Watched Channels", recentChannels))
        }
        (state as? HomeState.Ready)?.content?.let {
            if (it.movies.isNotEmpty()) add(HomeRow.Posters("Recently added movies", it.movies, 0))
            if (it.series.isNotEmpty()) add(HomeRow.Posters("Recently added series", it.series, 1))
        }
    }

    // Back from fullscreen: scroll to the row and put focus on the channel that was opened.
    var seenExit by remember { mutableIntStateOf(exitSignal) }
    LaunchedEffect(exitSignal) {
        if (exitSignal != seenExit) {
            seenExit = exitSignal
            val k = rows.indexOfFirst { it is HomeRow.Channels }
            val index = recentChannels.indexOfFirst { it.id == recentFocusId }
            if (k >= 0 && index >= 0) {
                listState.scrollToItem(1 + 2 * k, -topInset)
                rowStates[0].scrollToItem(index)
                delay(90)
                runCatching { recentCardFocus.requestFocus() }
            }
        }
    }

    // Tell the app when we are at the very top so it can hide or show the dock.
    // Two thresholds (leave at 80px, return at 8px) stop it flickering near the edge.
    LaunchedEffect(listState) {
        // Start from where the page really is, so coming back mid-scroll keeps the dock hidden.
        var atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 8
        onAtTopChange(atTop)
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

    // Focus moves at once and the page scrolls along with it. (It used to wait for the scroll to finish before the
    // highlight moved, which felt like a delay on every press.) The scroll is short and eased.
    val goToHero: () -> Unit = {
        scope.launch {
            val scrolling = launch { listState.animateScrollToItem(0) }
            runCatching { contentFocus.requestFocus() }
            scrolling.join()
        }
    }
    val goToRow: (Int) -> Unit = { k ->
        scope.launch {
            val target = 1 + 2 * k
            val scrolling = launch {
                val shown = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                if (shown != null) {
                    listState.animateScrollBy((shown.offset - topInset).toFloat(), tween(210, easing = FastOutSlowInEasing))
                } else {
                    listState.animateScrollToItem(target, -topInset)
                }
            }
            // The row may not be on screen yet; try for a moment until it is.
            var focused = false
            repeat(10) {
                if (!focused) {
                    focused = runCatching { rowFocus[k].requestFocus() }.isSuccess
                    if (!focused) delay(25)
                }
            }
            scrolling.join()
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
                        message = "Getting what's new from your source. The first time can take a moment.",
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
                                message = "No movies or series were found on this source. Live channels are under Live TV.",
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
                                index = heroIndex,
                                onIndexChange = onHeroIndex,
                                contentFocus = contentFocus,
                                onUp = goToDock,
                                onDown = { if (rows.isNotEmpty()) goToRow(0) },
                                onFocused = { scope.launch { listState.animateScrollToItem(0) } },
                                label = state.content.heroLabel,
                                onOpenDetails = onOpenDetails,
                                restoreItemId = restoreItemId,
                                onRestored = onRestored,
                                // Nearly the whole screen, with the next row's title just showing underneath.
                                modifier = Modifier.fillParentMaxHeight(0.93f),
                            )
                        }
                        rows.forEachIndexed { k, row ->
                            item { SectionTitle(row.title) }
                            item {
                                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                    when (row) {
                                        is HomeRow.Posters -> PosterRow(
                                            items = row.items,
                                            showRatings = showRatings,
                                            onOpen = onRowOpen,
                                            state = rowStates[1 + row.stateIndex],
                                            restoreItemId = restoreItemId,
                                            onRestored = onRestored,
                                            firstFocus = rowFocus[k],
                                            onUp = { if (k == 0) goToHero() else goToRow(k - 1) },
                                            onDown = { if (k < rows.lastIndex) goToRow(k + 1) },
                                        )

                                        is HomeRow.Channels -> ChannelRow(
                                            channels = row.channels,
                                            state = rowStates[0],
                                            focusId = recentFocusId,
                                            focusRequester = recentCardFocus,
                                            onPlay = onPlayRecent,
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
}

@Composable
private fun PosterRow(
    items: List<MediaItem>,
    showRatings: Boolean,
    onOpen: (MediaItem) -> Unit,
    state: LazyListState,
    restoreItemId: String?,
    onRestored: () -> Unit,
    firstFocus: FocusRequester,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    LazyRow(
        state = state,
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
            // After coming back from a details page, focus returns to the poster that was opened.
            val restore = remember { FocusRequester() }
            val isRestoreTarget = restoreItemId != null && m.id == restoreItemId
            LaunchedEffect(isRestoreTarget) {
                if (isRestoreTarget) {
                    runCatching { restore.requestFocus() }
                    onRestored()
                }
            }
            PosterCard(
                title = m.title,
                subtitle = listOfNotNull(m.year, if (showRatings) m.rating?.let { "★ $it" } else null)
                    .joinToString(" · "),
                hue = hueOf(m.title),
                imageUrl = m.posterUrl,
                onClick = { onOpen(m) },
                modifier = if (isRestoreTarget) Modifier.focusRequester(restore) else Modifier,
                width = 118.dp,
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

private sealed interface HomeRow {
    val title: String

    class Posters(override val title: String, val items: List<MediaItem>, val stateIndex: Int) : HomeRow

    class Channels(override val title: String, val channels: List<LiveChannel>) : HomeRow
}

/** Channels the user has watched, newest first. Selecting one plays it straight away. */
@Composable
private fun ChannelRow(
    channels: List<LiveChannel>,
    state: LazyListState,
    focusId: String?,
    focusRequester: FocusRequester,
    onPlay: (LiveChannel) -> Unit,
    firstFocus: FocusRequester,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    LazyRow(
        state = state,
        modifier = Modifier
            .focusRequester(firstFocus)
            .focusGroup()
            .onDirection(Key.DirectionUp, onUp)
            .onDirection(Key.DirectionDown, onDown),
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.RowGap),
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                onClick = { onPlay(channel) },
                modifier = if (channel.id == focusId) Modifier.focusRequester(focusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun ChannelCard(channel: LiveChannel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .width(150.dp)
            .tvFocusable(onClick = onClick, shape = shape, focusedScale = 1.05f)
            .glass(shape, GlassLevel.Panel)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            ChannelLogo(channel, Modifier.size(60.dp))
        }
        LumenText(
            text = channel.name,
            style = LumenTheme.typography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}
