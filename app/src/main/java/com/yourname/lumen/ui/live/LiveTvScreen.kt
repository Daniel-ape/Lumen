package com.yourname.lumen.ui.live

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.util.progressOf
import com.yourname.lumen.core.util.qualityLabel
import com.yourname.lumen.core.util.timeRangeText
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.LiveContent
import com.yourname.lumen.domain.model.LiveState
import com.yourname.lumen.domain.model.NowNext
import com.yourname.lumen.domain.model.favoriteKey
import com.yourname.lumen.ui.components.ChannelListRow
import com.yourname.lumen.ui.components.ChannelLogo
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * Live TV, arranged like TiviMate but in Lumen's own glass style:
 * a preview and what's on at the top, categories on the left, a compact channel list beside them.
 *
 * Three different things happen on three different gestures, so moving around never opens anything:
 *  - moving focus only highlights a channel;
 *  - OK on a channel plays it in the preview;
 *  - OK on the channel that is already playing opens fullscreen.
 */
@Composable
fun LiveTvScreen(
    state: LiveState,
    sourceId: String,
    favorites: Set<String>,
    focusedChannelId: String?,
    playingChannelId: String?,
    videoHeight: Int,
    showQuality: Boolean,
    epg: EpgCache?,
    onFocusChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onSelect: (LiveChannel) -> Unit,
    onFullscreen: (List<LiveChannel>, Int) -> Unit,
    onOpenGuide: (List<LiveChannel>) -> Unit,
    onPreviewBounds: (Rect?) -> Unit,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    onAddSource: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LiveState.Loading -> LiveMessage(
            title = "Loading channels…",
            message = "Getting the channel list from your source.",
            buttonLabel = null,
            onButton = {},
            dockFocus = dockFocus,
            contentFocus = contentFocus,
            modifier = modifier,
        )

        LiveState.NoSource -> LiveMessage(
            title = "Add your first source",
            message = "Connect your IPTV service to watch live TV.",
            buttonLabel = "Add source",
            onButton = onAddSource,
            dockFocus = dockFocus,
            contentFocus = contentFocus,
            modifier = modifier,
        )

        is LiveState.Failed -> LiveMessage(
            title = "Unable to connect to this source.",
            message = state.message,
            buttonLabel = "Retry",
            onButton = onRetry,
            dockFocus = dockFocus,
            contentFocus = contentFocus,
            modifier = modifier,
        )

        is LiveState.Ready -> if (state.content.channels.isEmpty()) {
            LiveMessage(
                title = "No channels found",
                message = "This source didn't list any live channels.",
                buttonLabel = "Retry",
                onButton = onRetry,
                dockFocus = dockFocus,
                contentFocus = contentFocus,
                modifier = modifier,
            )
        } else {
            LiveBrowser(
                content = state.content,
                sourceId = sourceId,
                favorites = favorites,
                focusedChannelId = focusedChannelId,
                playingChannelId = playingChannelId,
                videoHeight = videoHeight,
                showQuality = showQuality,
                epg = epg,
                onFocusChannel = onFocusChannel,
                onToggleFavorite = onToggleFavorite,
                onSelect = onSelect,
                onFullscreen = onFullscreen,
                onOpenGuide = onOpenGuide,
                onPreviewBounds = onPreviewBounds,
                dockFocus = dockFocus,
                contentFocus = contentFocus,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun LiveMessage(
    title: String,
    message: String,
    buttonLabel: String?,
    onButton: () -> Unit,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LumenText(title, LumenTheme.typography.title)
            LumenText(message, LumenTheme.typography.body, color = LumenTheme.colors.textSecondary)
            if (buttonLabel != null) {
                Spacer(Modifier.height(6.dp))
                LumenButton(
                    text = buttonLabel,
                    modifier = Modifier
                        .focusRequester(contentFocus)
                        .onDirection(Key.DirectionUp) { runCatching { dockFocus.requestFocus() } },
                    primary = true,
                    onClick = onButton,
                )
            }
        }
    }
}

private data class Group(val title: String, val count: Int, val categoryId: String?, val kind: Int)

@Composable
private fun LiveBrowser(
    content: LiveContent,
    sourceId: String,
    favorites: Set<String>,
    focusedChannelId: String?,
    playingChannelId: String?,
    videoHeight: Int,
    showQuality: Boolean,
    epg: EpgCache?,
    onFocusChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onSelect: (LiveChannel) -> Unit,
    onFullscreen: (List<LiveChannel>, Int) -> Unit,
    onOpenGuide: (List<LiveChannel>) -> Unit,
    onPreviewBounds: (Rect?) -> Unit,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val groupFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }

    val byCategory = remember(content) { content.channels.groupBy { it.categoryId } }
    val favoriteChannels = remember(content, favorites) {
        content.channels.filter { favoriteKey(sourceId, it) in favorites }
    }
    val groups = remember(content, favoriteChannels.size) {
        buildList {
            add(Group("All channels", content.channels.size, null, 0))
            add(Group("Favorites", favoriteChannels.size, null, 1))
            content.categories.forEach { category ->
                val count = byCategory[category.id]?.size ?: 0
                if (count > 0) add(Group(category.name, count, category.id, 2))
            }
        }
    }

    var groupIndex by remember { mutableIntStateOf(0) }
    val group = groups[groupIndex.coerceIn(0, groups.lastIndex)]
    val channels = remember(group, content, favoriteChannels) {
        when (group.kind) {
            0 -> content.channels
            1 -> favoriteChannels
            else -> byCategory[group.categoryId].orEmpty()
        }
    }
    val focused = channels.firstOrNull { it.id == focusedChannelId } ?: channels.firstOrNull()
    val playing = content.channels.firstOrNull { it.id == playingChannelId }

    // The category column slides away once you've settled in the channel list, and returns when you go back.
    var listHasFocus by remember { mutableStateOf(false) }
    var sidebarExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(listHasFocus) {
        if (listHasFocus) {
            delay(1_200)
            sidebarExpanded = false
        } else {
            sidebarExpanded = true
        }
    }
    val sidebarWidth by animateDpAsState(if (sidebarExpanded) 178.dp else 0.dp, tween(240), label = "sidebarWidth")
    val sidebarAlpha by animateFloatAsState(if (sidebarExpanded) 1f else 0f, tween(200), label = "sidebarAlpha")

    // The preview slot reports where it is so the shared video surface can sit exactly over it.
    DisposableEffect(Unit) { onDispose { onPreviewBounds(null) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 72.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- top: preview and what's on
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewSlot(
                hasChannel = playing != null,
                onBounds = onPreviewBounds,
                modifier = Modifier.width(250.dp),
            )
            InfoPanel(
                channel = focused,
                groupName = group.title,
                favorite = focused != null && favoriteKey(sourceId, focused) in favorites,
                playing = focused != null && focused.id == playingChannelId,
                quality = if (showQuality && focused?.id == playingChannelId) qualityLabel(videoHeight) else null,
                epg = epg,
                infoFocus = infoFocus,
                onFullscreen = {
                    val i = channels.indexOfFirst { it.id == focused?.id }
                    if (i >= 0) onFullscreen(channels, i)
                },
                onGuide = { onOpenGuide(channels) },
                onToggleFavorite = { focused?.let(onToggleFavorite) },
                onUp = { runCatching { dockFocus.requestFocus() } },
                onDown = { runCatching { contentFocus.requestFocus() } },
                modifier = Modifier.weight(1f),
            )
        }

        // ---- bottom: categories and channels
        Row(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                GroupList(
                    groups = groups,
                    selectedIndex = groupIndex.coerceIn(0, groups.lastIndex),
                    onSelect = { groupIndex = it },
                    selectedFocus = groupFocus,
                    onRight = { runCatching { contentFocus.requestFocus() } },
                    modifier = Modifier
                        .width(164.dp)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = sidebarAlpha },
                )
            }

            // A new list state per category, so switching starts at the top.
            androidx.compose.runtime.key(groupIndex) {
                ChannelList(
                    channels = channels,
                    sourceId = sourceId,
                    favorites = favorites,
                    epg = epg,
                    focusedId = focused?.id,
                    playingId = playingChannelId,
                    onFocusChannel = onFocusChannel,
                    onClickChannel = { i ->
                        val channel = channels[i]
                        if (channel.id == playingChannelId) onFullscreen(channels, i) else onSelect(channel)
                    },
                    onToggleFavorite = onToggleFavorite,
                    onListFocus = { listHasFocus = it },
                    onLeft = {
                        sidebarExpanded = true
                        runCatching { groupFocus.requestFocus() }
                    },
                    onUpFromTop = { runCatching { infoFocus.requestFocus() } },
                    contentFocus = contentFocus,
                    emptyText = if (group.kind == 1) {
                        "No favorites yet. Press the Menu button on a channel, or use the star in the panel above."
                    } else {
                        "No channels in this group."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

// ---------- preview

@Composable
private fun PreviewSlot(
    hasChannel: Boolean,
    onBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(shape)
            .glass(shape, GlassLevel.Panel)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) },
        contentAlignment = Alignment.Center,
    ) {
        if (!hasChannel) {
            LumenText(
                text = "Press OK on a channel to preview it here",
                style = LumenTheme.typography.label,
                color = LumenTheme.colors.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// ---------- groups

@Composable
private fun GroupList(
    groups: List<Group>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    selectedFocus: FocusRequester,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(groups) { i, g ->
            GroupItem(
                group = g,
                selected = i == selectedIndex,
                onSelect = { onSelect(i) },
                modifier = Modifier
                    .then(if (i == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier)
                    .onDirection(Key.DirectionRight, onRight),
            )
        }
    }
}

@Composable
private fun GroupItem(
    group: Group,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                onClick = onSelect,
                shape = shape,
                focusedScale = 1.03f,
                showOutline = false,
                onFocusChange = {
                    focused = it
                    if (it) onSelect()
                },
            )
            .then(
                if (selected) {
                    Modifier.glass(shape, if (focused) GlassLevel.Selected else GlassLevel.Control)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LumenText(
            text = group.title,
            style = LumenTheme.typography.body.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected || focused) Color.White else LumenTheme.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        LumenText(
            text = group.count.toString(),
            style = LumenTheme.typography.label.copy(fontSize = 11.sp),
            color = LumenTheme.colors.textTertiary,
        )
    }
}

// ---------- channels

@Composable
private fun ChannelList(
    channels: List<LiveChannel>,
    sourceId: String,
    favorites: Set<String>,
    epg: EpgCache?,
    focusedId: String?,
    playingId: String?,
    onFocusChannel: (LiveChannel) -> Unit,
    onClickChannel: (Int) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onListFocus: (Boolean) -> Unit,
    onLeft: () -> Unit,
    onUpFromTop: () -> Unit,
    contentFocus: FocusRequester,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            LumenText(
                text = emptyText,
                style = LumenTheme.typography.body.copy(fontSize = 14.sp),
                color = LumenTheme.colors.textSecondary,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val focusedIndex = channels.indexOfFirst { it.id == focusedId }

    // Coming back to the list: start from the channel you were on.
    LaunchedEffect(Unit) {
        if (focusedIndex > 0) listState.scrollToItem((focusedIndex - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .onFocusChanged { onListFocus(it.hasFocus) }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp -> if (focusedIndex <= 0) {
                            onUpFromTop()
                            true
                        } else {
                            false
                        }

                        Key.DirectionLeft -> {
                            onLeft()
                            true
                        }

                        // Nothing to the right of the list.
                        Key.DirectionRight -> true

                        else -> false
                    }
                }
            },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(channels) { i, channel ->
            // Whichever row is current answers to the dock's "down", so coming back lands on it.
            val isCurrent = if (focusedIndex >= 0) i == focusedIndex else i == 0
            ChannelListRow(
                channel = channel,
                epg = epg,
                favorite = favoriteKey(sourceId, channel) in favorites,
                playing = channel.id == playingId,
                onFocus = { onFocusChannel(channel) },
                onClick = { onClickChannel(i) },
                onMenu = { onToggleFavorite(channel) },
                modifier = if (isCurrent) Modifier.focusRequester(contentFocus) else Modifier,
            )
        }
    }
}

// ---------- what's on for the highlighted channel

@Composable
private fun InfoPanel(
    channel: LiveChannel?,
    groupName: String,
    favorite: Boolean,
    playing: Boolean,
    quality: String?,
    epg: EpgCache?,
    infoFocus: FocusRequester,
    onFullscreen: () -> Unit,
    onGuide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography

    if (channel == null) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            LumenText("Pick a channel to see what's on.", type.body, color = colors.textSecondary)
        }
        return
    }

    // The guide for the highlighted channel, fetched after a short pause so scrolling stays smooth.
    var nowNext by remember(channel.id, epg) { mutableStateOf(epg?.peek(channel)) }
    var loaded by remember(channel.id, epg) { mutableStateOf(nowNext != null) }
    LaunchedEffect(channel.id, epg) {
        if (epg != null && nowNext == null) {
            delay(300)
            nowNext = try {
                epg.nowNext(channel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            loaded = true
        }
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChannelLogo(channel, Modifier.width(40.dp).height(40.dp))
            Column(modifier = Modifier.weight(1f)) {
                LumenText(
                    channel.name,
                    type.title.copy(fontSize = 18.sp),
                    maxLines = 1,
                )
                LumenText(
                    text = "${channel.number} · $groupName" + (if (playing) " · Playing" else ""),
                    style = type.label.copy(fontSize = 12.sp),
                    color = if (playing) colors.accent else colors.textSecondary,
                    maxLines = 1,
                )
            }
            if (quality != null) {
                LumenText(
                    text = quality,
                    style = type.label.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    modifier = Modifier
                        .glass(RoundedCornerShape(6.dp), GlassLevel.Panel)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        val now = nowNext?.now
        when {
            now != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LumenText(
                        text = now.title,
                        style = type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    timeRangeText(now.startSec, now.endSec)?.let {
                        LumenText(it, type.label.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1)
                    }
                }
                progressOf(now.startSec, now.endSec)?.let { LumenProgressBar(progress = it) }
                nowNext?.next?.let { next ->
                    LumenText(
                        text = "Next: ${next.title}" + (timeRangeText(next.startSec, null)?.let { "  ·  $it" } ?: ""),
                        style = type.label.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }

            loaded -> LumenText(
                "No programme information for this channel.",
                type.label.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )

            else -> Spacer(Modifier.height(1.dp))
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .focusGroup()
                .onDirection(Key.DirectionUp, onUp)
                .onDirection(Key.DirectionDown, onDown),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LumenButton(
                text = "Fullscreen",
                modifier = Modifier.focusRequester(infoFocus),
                primary = true,
                compact = true,
                onClick = onFullscreen,
            )
            LumenButton(text = "Guide", compact = true, onClick = onGuide)
            LumenButton(
                text = if (favorite) "★ Favorite" else "☆ Favorite",
                compact = true,
                onClick = onToggleFavorite,
            )
        }
    }
}
