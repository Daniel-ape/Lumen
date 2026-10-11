package com.yourname.lumen.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.LiveContent
import com.yourname.lumen.domain.model.LiveState
import com.yourname.lumen.domain.model.NowNext
import com.yourname.lumen.domain.model.favoriteKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * Live TV in the style of TiviMate: groups on the left, the channel list in the middle,
 * and what's on right now for the highlighted channel on the right.
 * OK on a channel plays it. The Menu button, or the button in the info panel, toggles favorite.
 */
@Composable
fun LiveTvScreen(
    state: LiveState,
    sourceId: String,
    favorites: Set<String>,
    focusedChannelId: String?,
    onFocusChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onPlay: (List<LiveChannel>, Int) -> Unit,
    loadNowNext: suspend (LiveChannel) -> NowNext?,
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
                onFocusChannel = onFocusChannel,
                onToggleFavorite = onToggleFavorite,
                onPlay = onPlay,
                loadNowNext = loadNowNext,
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
    onFocusChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onPlay: (List<LiveChannel>, Int) -> Unit,
    loadNowNext: suspend (LiveChannel) -> NowNext?,
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

    // What's on now for the highlighted channel. Waits a moment so fast scrolling doesn't spam the server.
    val cache = remember { mutableMapOf<String, NowNext?>() }
    var nowNext by remember { mutableStateOf<NowNext?>(null) }
    var epgLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(focused?.id) {
        val channel = focused
        if (channel == null) {
            nowNext = null
            epgLoaded = true
            return@LaunchedEffect
        }
        if (cache.containsKey(channel.id)) {
            nowNext = cache[channel.id]
            epgLoaded = true
            return@LaunchedEffect
        }
        nowNext = null
        epgLoaded = false
        delay(350)
        val result = try {
            loadNowNext(channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        cache[channel.id] = result
        nowNext = result
        epgLoaded = true
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 84.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GroupList(
            groups = groups,
            selectedIndex = groupIndex.coerceIn(0, groups.lastIndex),
            onSelect = { groupIndex = it },
            selectedFocus = groupFocus,
            onRight = { runCatching { contentFocus.requestFocus() } },
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight(),
        )

        // A new list state per group, so switching group starts at the top.
        key(groupIndex) {
            ChannelList(
                channels = channels,
                sourceId = sourceId,
                favorites = favorites,
                focusedId = focused?.id,
                onFocusChannel = onFocusChannel,
                onPlay = { onPlay(channels, it) },
                onToggleFavorite = onToggleFavorite,
                onLeft = {
                    if (!focusManager.moveFocus(FocusDirection.Left)) {
                        runCatching { groupFocus.requestFocus() }
                    }
                },
                onRight = { runCatching { infoFocus.requestFocus() } },
                onUpFromTop = { runCatching { dockFocus.requestFocus() } },
                contentFocus = contentFocus,
                emptyText = if (group.kind == 1) {
                    "No favorites yet. Press the Menu button on a channel, or use the button on the right."
                } else {
                    "No channels in this group."
                },
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight(),
            )
        }

        InfoPanel(
            channel = focused,
            groupName = group.title,
            favorite = focused != null && favoriteKey(sourceId, focused) in favorites,
            nowNext = nowNext,
            epgLoaded = epgLoaded,
            infoFocus = infoFocus,
            onWatch = {
                val i = channels.indexOfFirst { it.id == focused?.id }
                if (i >= 0) onPlay(channels, i)
            },
            onToggleFavorite = { focused?.let(onToggleFavorite) },
            onLeft = {
                if (!focusManager.moveFocus(FocusDirection.Left)) {
                    runCatching { contentFocus.requestFocus() }
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
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
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
    val shape = RoundedCornerShape(12.dp)
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
            .then(if (selected) Modifier.glass(shape, strong = focused) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LumenText(
            text = group.title,
            style = LumenTheme.typography.body.copy(
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected || focused) Color.White else LumenTheme.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        LumenText(
            text = group.count.toString(),
            style = LumenTheme.typography.label,
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
    focusedId: String?,
    onFocusChannel: (LiveChannel) -> Unit,
    onPlay: (Int) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUpFromTop: () -> Unit,
    contentFocus: FocusRequester,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            LumenText(
                text = emptyText,
                style = LumenTheme.typography.body,
                color = LumenTheme.colors.textSecondary,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val focusedIndex = channels.indexOfFirst { it.id == focusedId }

    // Return to the channel you were on: start from it if the list is just being shown.
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

                        Key.DirectionRight -> {
                            onRight()
                            true
                        }

                        else -> false
                    }
                }
            },
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(channels) { i, channel ->
            // Whichever row is the current one answers to the dock's "down", so coming back lands on it.
            val isCurrent = if (focusedIndex >= 0) i == focusedIndex else i == 0
            ChannelRow(
                channel = channel,
                favorite = favoriteKey(sourceId, channel) in favorites,
                onFocus = { onFocusChannel(channel) },
                onOpen = { onPlay(i) },
                onToggleFavorite = { onToggleFavorite(channel) },
                modifier = if (isCurrent) Modifier.focusRequester(contentFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    favorite: Boolean,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                    onToggleFavorite()
                    true
                } else {
                    false
                }
            }
            .tvFocusable(
                onClick = onOpen,
                shape = shape,
                focusedScale = 1.02f,
                onFocusChange = {
                    focused = it
                    if (it) onFocus()
                },
            )
            .then(if (focused) Modifier.glass(shape, strong = true) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LumenText(
            text = channel.number.toString(),
            style = LumenTheme.typography.label,
            color = LumenTheme.colors.textTertiary,
            maxLines = 1,
            modifier = Modifier.width(34.dp),
        )
        ChannelLogo(channel, Modifier.size(40.dp))
        LumenText(
            text = channel.name,
            style = LumenTheme.typography.body.copy(fontSize = 16.sp),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (favorite) LumenText("★", LumenTheme.typography.body, color = LumenTheme.colors.accent)
    }
}

@Composable
private fun ChannelLogo(channel: LiveChannel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0x14FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logoUrl != null) {
            SubcomposeAsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                loading = { LogoInitial(channel.name) },
                error = { LogoInitial(channel.name) },
            )
        } else {
            LogoInitial(channel.name)
        }
    }
}

@Composable
private fun LogoInitial(name: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        LumenText(
            text = name.take(1).uppercase(),
            style = LumenTheme.typography.heading,
            color = LumenTheme.colors.textSecondary,
        )
    }
}

// ---------- info panel

@Composable
private fun InfoPanel(
    channel: LiveChannel?,
    groupName: String,
    favorite: Boolean,
    nowNext: NowNext?,
    epgLoaded: Boolean,
    infoFocus: FocusRequester,
    onWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val shape = RoundedCornerShape(16.dp)

    if (channel == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            LumenText("Pick a channel to see what's on.", type.body, color = colors.textSecondary)
        }
        return
    }

    Column(
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChannelLogo(channel, Modifier.size(64.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LumenText(channel.name, type.title.copy(fontSize = 22.sp), maxLines = 2)
                LumenText("${channel.number} · $groupName", type.label, color = colors.textSecondary)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val now = nowNext?.now
            when {
                now != null -> {
                    LumenText("NOW", type.label, color = colors.accent)
                    LumenText(now.title, type.heading, maxLines = 2)
                    val range = timeRangeText(now.startSec, now.endSec)
                    if (range != null) LumenText(range, type.label, color = colors.textSecondary)
                    progress(now.startSec, now.endSec)?.let { LumenProgressBar(progress = it) }
                    if (!now.description.isNullOrBlank()) {
                        LumenText(
                            text = now.description,
                            style = type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                            color = colors.textSecondary,
                            maxLines = 4,
                        )
                    }
                }

                !epgLoaded -> LumenText("Loading program info…", type.body, color = colors.textSecondary)
                else -> LumenText(
                    "No program information for this channel.",
                    type.body,
                    color = colors.textSecondary,
                )
            }
            nowNext?.next?.let { next ->
                LumenText(
                    text = "NEXT  ${next.title}" + (timeRangeText(next.startSec, null)?.let { "  ·  $it" } ?: ""),
                    style = type.label,
                    color = colors.textSecondary,
                    maxLines = 2,
                )
            }
        }

        Row(
            modifier = Modifier
                .focusGroup()
                .onDirection(Key.DirectionLeft, onLeft),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LumenButton(
                text = "Watch",
                modifier = Modifier.focusRequester(infoFocus),
                primary = true,
                onClick = onWatch,
            )
            LumenButton(
                text = if (favorite) "Remove favorite" else "Add to favorites",
                onClick = onToggleFavorite,
            )
        }
    }
}

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun timeRangeText(startSec: Long?, endSec: Long?): String? = when {
    startSec != null && endSec != null -> "${clock.format(Date(startSec * 1000))} – ${clock.format(Date(endSec * 1000))}"
    startSec != null -> clock.format(Date(startSec * 1000))
    else -> null
}

private fun progress(startSec: Long?, endSec: Long?): Float? {
    if (startSec == null || endSec == null || endSec <= startSec) return null
    val now = System.currentTimeMillis() / 1000
    return ((now - startSec).toFloat() / (endSec - startSec).toFloat()).coerceIn(0f, 1f)
}
