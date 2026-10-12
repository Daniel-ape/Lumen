package com.yourname.lumen.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.data.settings.AppSettings
import com.yourname.lumen.domain.model.Source
import com.yourname.lumen.domain.model.SourceType

/** Things the settings screens ask the app to do. */
class SettingsActions(
    val onAddSource: (SourceType) -> Unit,
    val onEditSource: (Source) -> Unit,
    val onRemoveSource: (Source) -> Unit,
    val onSetActive: (Source) -> Unit,
    val onTestSource: (Source) -> Unit,
    val onRefreshData: () -> Unit,
    val onClearImageCache: () -> Unit,
    val onClearGuideCache: () -> Unit,
)

/** Live facts the settings screens show. */
class SettingsInfo(
    val activeSourceId: String?,
    val testingId: String?,
    val lastRefreshAt: Long,
    val imageCacheBytes: Long,
)

enum class SettingsPage(val title: String) {
    Connection("Connection"),
    Epg("EPG"),
    Playback("Playback"),
    Appearance("Appearance"),
    General("General"),
    About("About & Support"),
}

/**
 * Settings is two levels. The first shows only the categories, as glass buttons on the right with
 * the left side left empty. Choosing one opens its own page; Back returns to the categories.
 */
@Composable
fun SettingsScreen(
    sources: List<Source>,
    info: SettingsInfo,
    settings: AppSettings,
    actions: SettingsActions,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    var lastPage by remember { mutableStateOf(SettingsPage.Connection) }

    BackHandler(enabled = page != null) { page = null }

    Crossfade(
        targetState = page,
        animationSpec = tween(200),
        label = "settingsPage",
        modifier = modifier.fillMaxSize(),
    ) { current ->
        if (current == null) {
            SettingsMenu(
                lastPage = lastPage,
                dockFocus = dockFocus,
                contentFocus = contentFocus,
                onOpen = {
                    lastPage = it
                    page = it
                },
            )
        } else {
            SettingsPageScreen(
                page = current,
                sources = sources,
                info = info,
                settings = settings,
                actions = actions,
                dockFocus = dockFocus,
                onBack = { page = null },
            )
        }
    }
}

@Composable
private fun SettingsMenu(
    lastPage: SettingsPage,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    onOpen: (SettingsPage) -> Unit,
) {
    val pages = SettingsPage.entries
    // The first button is where focus lands from the dock.
    val requesters = remember { List(pages.size) { if (it == 0) contentFocus else FocusRequester() } }

    // Coming back from a page, focus the category you just left.
    LaunchedEffect(Unit) {
        if (lastPage != SettingsPage.Connection) runCatching { requesters[lastPage.ordinal].requestFocus() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        // The left half stays empty on wide screens; on a phone the buttons simply fill the width.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (wide) Dimens.ScreenPadding else 20.dp,
                    end = if (wide) 72.dp else 20.dp,
                    top = 72.dp,
                    bottom = 24.dp,
                ),
        ) {
            if (wide) Box(modifier = Modifier.weight(1f))
            Column(
                modifier = if (wide) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                pages.forEachIndexed { i, entry ->
                    MenuButton(
                        title = entry.title,
                        onClick = { onOpen(entry) },
                        modifier = Modifier
                            .focusRequester(requesters[i])
                            .then(
                                if (i == 0) {
                                    Modifier.onDirection(Key.DirectionUp) { runCatching { dockFocus.requestFocus() } }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onClick, shape = shape, focusedScale = 1.03f)
            .glass(shape, GlassLevel.Control)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LumenText(
            text = title,
            style = LumenTheme.typography.body.copy(fontSize = 16.sp),
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 1,
        )
        LumenText("›", LumenTheme.typography.body, color = LumenTheme.colors.textSecondary)
    }
}

@Composable
private fun SettingsPageScreen(
    page: SettingsPage,
    sources: List<Source>,
    info: SettingsInfo,
    settings: AppSettings,
    actions: SettingsActions,
    dockFocus: FocusRequester,
    onBack: () -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    val contentRequester = remember { FocusRequester() }

    // Land on the first control of the page.
    LaunchedEffect(page) { runCatching { contentRequester.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 70.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LumenButton(
                text = "‹ Back",
                modifier = Modifier
                    .focusRequester(backFocus)
                    .onDirection(Key.DirectionUp) { runCatching { dockFocus.requestFocus() } },
                compact = true,
                onClick = onBack,
            )
            LumenText(page.title, LumenTheme.typography.title.copy(fontSize = 22.sp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxHeight()
                    .focusRequester(contentRequester)
                    .focusGroup(),
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (page) {
                    SettingsPage.Connection -> connectionItems(sources, info, actions)
                    SettingsPage.Epg -> epgItems(settings, actions)
                    SettingsPage.Playback -> playbackItems(settings)
                    SettingsPage.Appearance -> appearanceItems(settings)
                    SettingsPage.General -> generalItems(settings, info, actions)
                    SettingsPage.About -> aboutItems()
                }
            }
        }
    }
}

