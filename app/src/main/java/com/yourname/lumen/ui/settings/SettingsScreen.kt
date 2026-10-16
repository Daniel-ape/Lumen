package com.yourname.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.GearIcon
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
    val onClearLibraryCache: () -> Unit,
    val onRestart: () -> Unit,
)

/** Live facts the settings screens show. */
class SettingsInfo(
    val activeSourceId: String?,
    val testingId: String?,
    val lastRefreshAt: Long,
    val imageCacheBytes: Long,
    val libraryCacheBytes: Long,
)

enum class SettingsPage(val title: String) {
    Sources("Sources"),
    Appearance("Appearance"),
    General("General"),
    Player("Player"),
    LiveTv("Live TV"),
    Content("Movies & Series"),
    Storage("Storage & Data"),
    About("About & Support"),
}

/** A question the user must answer before something destructive happens. */
class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

/** Settings pages call this to ask for confirmation; the screen shows the dialog. */
val LocalConfirm = staticCompositionLocalOf<(ConfirmRequest) -> Unit> { {} }

// The category column is darker than the settings beside it, so the two read as separate areas.
private val SidebarColor = Color(0xD90A0B0D)
private val SidebarEdge = Color(0x1AFFFFFF)

/**
 * Two columns: categories on the left, the selected category's settings on the right.
 * Moving onto a category shows its settings. RIGHT enters them, LEFT returns to the categories.
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
    var selected by remember { mutableStateOf(SettingsPage.Sources) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    val pages = SettingsPage.entries
    // The first category is where focus lands from the dock.
    val sidebarFocus = remember { List(pages.size) { if (it == 0) contentFocus else FocusRequester() } }
    val panelFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val pending = settings.hasPendingAppearanceChanges

    CompositionLocalProvider(LocalConfirm provides { confirm = it }) {
        val request = confirm
        if (request != null) {
            // While a question is open it replaces the settings, so focus can't wander behind it.
            ConfirmDialog(request = request, onDismiss = { confirm = null }, modifier = modifier)
        } else {
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val wide = maxWidth >= 720.dp
                if (wide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 70.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(236.dp)
                                .fillMaxHeight()
                                .background(SidebarColor, RoundedCornerShape(16.dp))
                                .border(1.dp, SidebarEdge, RoundedCornerShape(16.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GearIcon(color = Color.White, iconSize = 18.dp)
                                LumenText("Settings", LumenTheme.typography.heading.copy(fontSize = 17.sp))
                            }
                            pages.forEachIndexed { i, page ->
                                SidebarItem(
                                    title = page.title,
                                    selected = selected == page,
                                    badge = page == SettingsPage.Appearance && pending,
                                    onSelect = { selected = page },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(sidebarFocus[i])
                                        .then(
                                            if (i == 0) {
                                                Modifier.onDirection(Key.DirectionUp) { runCatching { dockFocus.requestFocus() } }
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .onDirection(Key.DirectionRight) { runCatching { panelFocus.requestFocus() } },
                                )
                            }
                        }

                        SettingsPanel(
                            page = selected,
                            sources = sources,
                            info = info,
                            settings = settings,
                            actions = actions,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(panelFocus)
                                .focusGroup()
                                .onDirection(Key.DirectionLeft) {
                                    // Move left inside the settings if there is somewhere to go, otherwise back to the categories.
                                    if (!focusManager.moveFocus(FocusDirection.Left)) {
                                        runCatching { sidebarFocus[selected.ordinal].requestFocus() }
                                    }
                                },
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 70.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(pages.size) { i ->
                                SidebarItem(
                                    title = pages[i].title,
                                    selected = selected == pages[i],
                                    badge = pages[i] == SettingsPage.Appearance && pending,
                                    onSelect = { selected = pages[i] },
                                    modifier = Modifier
                                        .focusRequester(sidebarFocus[i])
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
                        SettingsPanel(
                            page = selected,
                            sources = sources,
                            info = info,
                            settings = settings,
                            actions = actions,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    selected: Boolean,
    badge: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
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
                    selected -> Modifier.background(Color(0x26FFFFFF), shape)
                    else -> Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LumenText(
            text = title,
            style = LumenTheme.typography.body.copy(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected || focused) Color.White else LumenTheme.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        // A small dot while there are appearance changes waiting for a restart.
        if (badge) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(LumenTheme.colors.accent, CircleShape),
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    page: SettingsPage,
    sources: List<Source>,
    info: SettingsInfo,
    settings: AppSettings,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        // Side padding leaves room for the focus scale-up so rows never get clipped.
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (page) {
            SettingsPage.Sources -> sourcesItems(sources, info, settings, actions)
            SettingsPage.Appearance -> appearanceItems(settings, actions)
            SettingsPage.General -> generalItems(settings, actions)
            SettingsPage.Player -> playerItems(settings)
            SettingsPage.LiveTv -> liveTvItems(settings)
            SettingsPage.Content -> contentItems(settings)
            SettingsPage.Storage -> storageItems(info, settings, actions)
            SettingsPage.About -> aboutItems()
        }
    }
}

@Composable
private fun ConfirmDialog(request: ConfirmRequest, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val cancelFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(18.dp)
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // Up and Down have nothing to do here; keep focus on the two buttons.
                event.type == KeyEventType.KeyDown && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .background(Color(0xF00D0F12), shape)
                .glass(shape, GlassLevel.Panel)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LumenText(request.title, LumenTheme.typography.heading.copy(fontSize = 18.sp))
            LumenText(request.message, LumenTheme.typography.body.copy(fontSize = 14.sp), color = LumenTheme.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LumenButton(
                    text = "Cancel",
                    modifier = Modifier.focusRequester(cancelFocus),
                    compact = true,
                    onClick = onDismiss,
                )
                LumenButton(
                    text = request.confirmLabel,
                    primary = true,
                    compact = true,
                    onClick = {
                        onDismiss()
                        request.onConfirm()
                    },
                )
            }
        }
    }
}
