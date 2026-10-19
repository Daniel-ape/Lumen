package com.yourname.lumen.ui.live

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
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
import com.yourname.lumen.core.designsystem.motionMs
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.util.progressOf
import com.yourname.lumen.core.util.qualityClass
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
 * Live TV: a preview and what's on at the top, categories on the left in a darker column,
 * and a compact channel list (or the programme guide) beside them.
 *
 * Moving focus only highlights; OK plays a channel in the preview; OK on the playing channel opens fullscreen.
 * The category column stays until you press RIGHT from it, and comes back when you press LEFT from the list.
 * The channel list is free-standing, with no card around it. The programme guide lives in fullscreen only.
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
    showLogos: Boolean,
    initialGroup: Int,
    epg: EpgCache?,
    onFocusChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onSelect: (LiveChannel) -> Unit,
    onFullscreen: (List<LiveChannel>, Int) -> Unit,
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
                showLogos = showLogos,
                initialGroup = initialGroup,
                epg = epg,
                onFocusChannel = onFocusChannel,
                onToggleFavorite = onToggleFavorite,
                onSelect = onSelect,
                onFullscreen = onFullscreen,
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

private const val KIND_ALL = 0
private const val KIND_FAVORITES = 1
private const val KIND_CATEGORY = 2

private data class Group(val title: String, val count: Int, val categoryId: String?, val kind: Int)

// The two surfaces are deliberately different: the sidebar is darker than the list beside it.
private val SidebarColor = Color(0xB3090A0C)

@Composable
private fun LiveBrowser(
    content: LiveContent,
    sourceId: String,
    favorites: Set<String>,
    focusedChannelId: String?,
    playingChannelId: String?,
    videoHeight: Int,
    showQuality: Boolean,
    showLogos: Boolean,
    initialGroup: Int,
    epg: EpgCache?,
    onFocusChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onSelect: (LiveChannel) -> Unit,
    onFullscreen: (List<LiveChannel>, Int) -> Unit,
    onPreviewBounds: (Rect?) -> Unit,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val groupFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }

    val byCategory = remember(content) { content.channels.groupBy { it.categoryId } }
    val favoriteChannels = remember(content, favorites) {
        content.channels.filter { favoriteKey(sourceId, it) in favorites }
    }
    val groups = remember(content, favoriteChannels.size) {
        buildList {
            add(Group("All channels", content.channels.size, null, KIND_ALL))
            add(Group("Favorites", favoriteChannels.size, null, KIND_FAVORITES))
            content.categories.forEach { category ->
                val count = byCategory[category.id]?.size ?: 0
                if (count > 0) add(Group(category.name, count, category.id, KIND_CATEGORY))
            }
        }
    }

    // initialGroup is 0 All channels or 1 Favorites.
    var groupIndex by remember { mutableIntStateOf(initialGroup.coerceIn(0, 1)) }
    val group = groups[groupIndex.coerceIn(0, groups.lastIndex)]
    val channels = remember(group, content, favoriteChannels) {
        when (group.kind) {
            KIND_ALL -> content.channels
            KIND_FAVORITES -> favoriteChannels
            else -> byCategory[group.categoryId].orEmpty()
        }
    }
    val focused = channels.firstOrNull { it.id == focusedChannelId } ?: channels.firstOrNull()
    val playing = content.channels.firstOrNull { it.id == playingChannelId }

    // The category column is only ever hidden or shown by an explicit key press, never by a timer.
    var sidebarExpanded by remember { mutableStateOf(true) }
    val sidebarMs = motionMs(240)
    val sidebarWidth by animateDpAsState(if (sidebarExpanded) 188.dp else 0.dp, tween(sidebarMs), label = "sidebarWidth")
    val sidebarAlpha by animateFloatAsState(if (sidebarExpanded) 1f else 0f, tween(sidebarMs), label = "sidebarAlpha")

    val enterContent = {
        // Only hide the categories if the list really took focus, so focus can never be left on something invisible.
        if (runCatching { contentFocus.requestFocus() }.isSuccess) sidebarExpanded = false
    }
    val enterSidebar = {
        sidebarExpanded = true
        runCatching { groupFocus.requestFocus() }
        Unit
    }

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
                quality = if (showQuality && focused?.id == playingChannelId) {
                    listOfNotNull(qualityLabel(videoHeight), qualityClass(videoHeight)).joinToString(" · ").ifEmpty { null }
                } else {
                    null
                },
                showLogo = showLogos,
                epg = epg,
                infoFocus = infoFocus,
                onToggleFavorite = { focused?.let(onToggleFavorite) },
                onUp = { runCatching { dockFocus.requestFocus() } },
                onDown = { runCatching { contentFocus.requestFocus() } },
                modifier = Modifier.weight(1f),
            )
        }

        // ---- bottom: categories on the left, the channel list free beside them (no card around it)
        Row(modifier = Modifier.weight(1f)) {
            // A flat, darker strip: it separates the categories without boxing anything in.
            Box(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                Box(
                    modifier = Modifier
                        .width(176.dp)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = sidebarAlpha }
                        .background(SidebarColor),
                ) {
                    GroupList(
                        groups = groups,
                        selectedIndex = groupIndex.coerceIn(0, groups.lastIndex),
                        onSelect = { groupIndex = it },
                        selectedFocus = groupFocus,
                        onRight = { enterContent() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // A new list state per category, so switching starts at the top.
            androidx.compose.runtime.key(groupIndex) {
                ChannelList(
                    channels = channels,
                    sourceId = sourceId,
                    favorites = favorites,
                    epg = epg,
                    showLogos = showLogos,
                    focusedId = focused?.id,
                    playingId = playingChannelId,
                    onFocusChannel = onFocusChannel,
                    onClickChannel = { i ->
                        val channel = channels[i]
                        if (channel.id == playingChannelId) onFullscreen(channels, i) else onSelect(channel)
                    },
                    onToggleFavorite = onToggleFavorite,
                    onLeft = { enterSidebar() },
                    onUpFromTop = { runCatching { infoFocus.requestFocus() } },
                    contentFocus = contentFocus,
                    emptyText = if (group.kind == KIND_FAVORITES) {
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
            .background(Color(0x33000000), shape)
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
        contentPadding = PaddingValues(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
                focusedScale = 1.02f,
                showOutline = false,
                onFocusChange = {
                    focused = it
                    if (it) onSelect()
                },
            )
            .then(
                when {
                    focused -> Modifier.glass(shape, GlassLevel.Selected)
                    // The open category stays marked even while focus is in the list.
                    selected -> Modifier.background(Color(0x26FFFFFF), shape)
                    else -> Modifier
                },
            )
            .padding(horizontal = 11.dp, vertical = 8.dp),
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
    showLogos: Boolean,
    focusedId: String?,
    playingId: String?,
    onFocusChannel: (LiveChannel) -> Unit,
    onClickChannel: (Int) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
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
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(channels) { i, channel ->
            // Whichever row is current answers to the dock's "down", so coming back lands on it.
            val isCurrent = if (focusedIndex >= 0) i == focusedIndex else i == 0
            ChannelListRow(
                channel = channel,
                epg = epg,
                showLogo = showLogos,
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
    showLogo: Boolean,
    epg: EpgCache?,
    infoFocus: FocusRequester,
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
            if (showLogo) ChannelLogo(channel, Modifier.width(40.dp).height(40.dp))
            Column(modifier = Modifier.weight(1f)) {
                LumenText(channel.name, type.title.copy(fontSize = 18.sp), maxLines = 1)
                LumenText(
                    text = groupName + (if (playing) " · Playing" else ""),
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
                    maxLines = 1,
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

        // Favorite is the only control here; playback and the guide use the list and the remote.
        Row(
            modifier = Modifier
                .focusGroup()
                .onDirection(Key.DirectionUp, onUp)
                .onDirection(Key.DirectionDown, onDown),
        ) {
            LumenButton(
                text = if (favorite) "★ Favorite" else "☆ Favorite",
                modifier = Modifier.focusRequester(infoFocus),
                compact = true,
                onClick = onToggleFavorite,
            )
        }
    }
}
