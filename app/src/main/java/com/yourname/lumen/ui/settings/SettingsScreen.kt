package com.yourname.lumen.ui.settings

import android.os.Build
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.GearIcon
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.data.settings.AppSettings
import com.yourname.lumen.domain.model.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Things the settings screen asks the app to do. */
class SettingsActions(
    val onAddSource: () -> Unit,
    val onEditSource: (Source) -> Unit,
    val onRemoveSource: (Source) -> Unit,
    val onSetActive: (Source) -> Unit,
    val onReconnect: (Source) -> Unit,
    val onRefreshLibrary: () -> Unit,
    val onClearImageCache: () -> Unit,
)

/** Live facts the settings screen shows. */
class SettingsInfo(
    val activeSourceId: String?,
    val reconnectingId: String?,
    val lastRefreshAt: Long,
    val imageCacheBytes: Long,
)

private enum class SettingsCategory(val title: String) {
    Connections("Connections"),
    Appearance("Appearance"),
    General("General"),
    About("About & Support"),
}

private val CardShape = RoundedCornerShape(14.dp)

/**
 * Wide screens (TV, tablets): heading on the left, categories and their controls on the right.
 * Narrow screens (phones): everything stacks.
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
    var selected by remember { mutableStateOf(SettingsCategory.Connections) }
    val categories = SettingsCategory.entries
    // The first category is where focus lands when you come down from the dock.
    val categoryFocus = remember { List(categories.size) { if (it == 0) contentFocus else FocusRequester() } }
    val panelFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun categoryModifier(index: Int): Modifier = Modifier
        .focusRequester(categoryFocus[index])
        .then(
            if (index == 0) {
                Modifier.onDirection(Key.DirectionUp) { runCatching { dockFocus.requestFocus() } }
            } else {
                Modifier
            },
        )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 84.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                SettingsHeader(Modifier.width(190.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        modifier = Modifier.width(210.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        categories.forEachIndexed { i, category ->
                            CategoryItem(
                                title = category.title,
                                selected = selected == category,
                                onSelect = { selected = category },
                                modifier = categoryModifier(i)
                                    .fillMaxWidth()
                                    .onDirection(Key.DirectionRight) { runCatching { panelFocus.requestFocus() } },
                            )
                        }
                    }
                    SettingsPanel(
                        category = selected,
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
                                // Move left inside the panel if there is somewhere to go, otherwise back to the categories.
                                if (!focusManager.moveFocus(FocusDirection.Left)) {
                                    runCatching { categoryFocus[selected.ordinal].requestFocus() }
                                }
                            },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 72.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsHeader(Modifier.fillMaxWidth(), horizontal = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories.size) { i ->
                        CategoryItem(
                            title = categories[i].title,
                            selected = selected == categories[i],
                            onSelect = { selected = categories[i] },
                            modifier = categoryModifier(i),
                        )
                    }
                }
                SettingsPanel(
                    category = selected,
                    sources = sources,
                    info = info,
                    settings = settings,
                    actions = actions,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    if (horizontal) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GearIcon(color = Color.White, iconSize = 28.dp)
            LumenText("Settings", type.title)
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GearIcon(color = Color.White, iconSize = 44.dp)
            LumenText("Settings", type.title)
            LumenText("Lumen TV", type.label, color = colors.textSecondary)
        }
    }
}

/** A category in the list. Moving onto it shows its settings; the glass pill marks the open one. */
@Composable
private fun CategoryItem(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
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
                    Modifier.glass(shape, strong = focused)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        LumenText(
            text = title,
            style = LumenTheme.typography.body.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected || focused) Color.White else LumenTheme.colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsPanel(
    category: SettingsCategory,
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (category) {
            SettingsCategory.Connections -> connectionsItems(sources, info, actions)
            SettingsCategory.Appearance -> appearanceItems(settings)
            SettingsCategory.General -> generalItems(settings, info, actions)
            SettingsCategory.About -> item(key = "about") { AboutContent() }
        }
    }
}

// ---------- Connections

private fun LazyListScope.connectionsItems(
    sources: List<Source>,
    info: SettingsInfo,
    actions: SettingsActions,
) {
    item(key = "add") {
        SettingActionRow(
            label = "Add Xtream source",
            description = "Server address, username and password.",
            onClick = actions.onAddSource,
            strong = true,
        )
    }
    if (sources.isEmpty()) {
        item(key = "empty") {
            SettingNote(
                "No sources yet. Add the Xtream Codes details from your provider. " +
                    "Self-hosted servers that support the Xtream API work the same way.",
            )
        }
    }
    items(sources, key = { it.id }) { source ->
        SourceCard(
            source = source,
            isActive = source.id == info.activeSourceId,
            reconnecting = info.reconnectingId == source.id,
            actions = actions,
        )
    }
    item(key = "note") {
        SettingNote("M3U playlists and program guides (EPG) are not supported yet, so they are not listed here.")
    }
}

@Composable
private fun SourceCard(
    source: Source,
    isActive: Boolean,
    reconnecting: Boolean,
    actions: SettingsActions,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    var confirming by remember(source.id) { mutableStateOf(false) }
    val removeFocus = remember { FocusRequester() }

    val status = when {
        reconnecting -> "Connecting…"
        source.lastCheckedAt == 0L -> "Not checked yet"
        source.lastError != null -> "Couldn't connect · ${source.lastError}"
        else -> "Connected · checked ${formatDateTime(source.lastCheckedAt)}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LumenText(
                text = source.name,
                style = type.body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isActive) LumenText("Active", type.label, color = colors.accent)
        }
        LumenText(source.baseUrl, type.label, color = colors.textTertiary, maxLines = 1)
        LumenText(
            text = status,
            style = type.label,
            color = if (source.lastError != null) Color(0xFFFF8A80) else colors.textSecondary,
            maxLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isActive) {
                LumenButton("Set active", onClick = { actions.onSetActive(source) })
            }
            LumenButton(
                text = if (reconnecting) "Connecting…" else "Reconnect",
                onClick = { if (!reconnecting) actions.onReconnect(source) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LumenButton("Edit", onClick = { actions.onEditSource(source) })
            LumenButton(
                text = "Remove",
                modifier = Modifier.focusRequester(removeFocus),
                onClick = { confirming = true },
            )
        }
        if (confirming) {
            LumenText(
                "Remove this source? Its login is deleted from this device.",
                type.label,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LumenButton(
                    text = "Yes, remove",
                    primary = true,
                    onClick = {
                        confirming = false
                        actions.onRemoveSource(source)
                    },
                )
                LumenButton(
                    text = "Cancel",
                    onClick = {
                        confirming = false
                        runCatching { removeFocus.requestFocus() }
                    },
                )
            }
        }
    }
}

// ---------- Appearance

private val ScaleOptions = listOf(0.9f to "Small", 1.0f to "Default", 1.1f to "Large")

private fun LazyListScope.appearanceItems(settings: AppSettings) {
    item(key = "scale") {
        val current = ScaleOptions.firstOrNull { it.first == settings.uiScale } ?: ScaleOptions[1]
        SettingChoiceRow(
            label = "Interface size",
            description = "Makes text and buttons smaller or larger.",
            value = current.second,
            onCycle = {
                val next = ScaleOptions[(ScaleOptions.indexOf(current) + 1) % ScaleOptions.size]
                settings.updateUiScale(next.first)
            },
        )
    }
    item(key = "ratings") {
        SettingToggleRow(
            label = "Show ratings on posters",
            description = "Adds the score next to the year under each poster.",
            checked = settings.showRatings,
            onToggle = { settings.updateShowRatings(!settings.showRatings) },
        )
    }
    item(key = "rotate") {
        SettingToggleRow(
            label = "Auto-rotate featured titles",
            description = "Changes the big featured title on Home every few seconds.",
            checked = settings.heroAutoRotate,
            onToggle = { settings.updateHeroAutoRotate(!settings.heroAutoRotate) },
        )
    }
}

// ---------- General

private val RefreshOptions = listOf(0 to "Off", 6 to "Every 6 hours", 12 to "Every 12 hours", 24 to "Every day")

private fun LazyListScope.generalItems(
    settings: AppSettings,
    info: SettingsInfo,
    actions: SettingsActions,
) {
    item(key = "autorefresh") {
        val current = RefreshOptions.firstOrNull { it.first == settings.autoRefreshHours } ?: RefreshOptions[0]
        SettingChoiceRow(
            label = "Auto-refresh library",
            description = "Reloads what's new from your source while the app is open.",
            value = current.second,
            onCycle = {
                val next = RefreshOptions[(RefreshOptions.indexOf(current) + 1) % RefreshOptions.size]
                settings.updateAutoRefreshHours(next.first)
            },
        )
    }
    item(key = "refresh") {
        SettingActionRow(
            label = "Refresh library now",
            description = if (info.lastRefreshAt == 0L) {
                "Not refreshed yet in this session."
            } else {
                "Last refreshed ${formatDateTime(info.lastRefreshAt)}."
            },
            onClick = actions.onRefreshLibrary,
        )
    }
    item(key = "cache") {
        SettingActionRow(
            label = "Clear image cache",
            description = "Using ${formatBytes(info.imageCacheBytes)}. Pictures download again when needed.",
            onClick = actions.onClearImageCache,
        )
    }
    item(key = "diag_title") { SettingGroupTitle("Diagnostics") }
    item(key = "diag") {
        val entries = AppLog.entries
        if (entries.isEmpty()) {
            SettingNote("No events yet. Connection problems will show up here.")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(CardShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entries.take(8).forEach { line ->
                    LumenText(line, LumenTheme.typography.label, color = LumenTheme.colors.textSecondary, maxLines = 2)
                }
            }
        }
    }
    item(key = "diag_clear") {
        if (AppLog.entries.isNotEmpty()) {
            SettingActionRow(label = "Clear log", description = null, onClick = { AppLog.clear() })
        }
    }
}

// ---------- About & Support

@Composable
private fun AboutContent() {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it).toString() } ?: "unknown"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingGroupTitle("Lumen TV")
        SettingInfoRow("Version", "$versionName (build $versionCode)")
        SettingInfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        SettingInfoRow("Device", Build.MODEL ?: "unknown")
        SettingNote(
            "Lumen TV is a personal-use player. It contains no channels or content of its own; " +
                "it only shows what your own source provides.",
        )
        SettingGroupTitle("Troubleshooting")
        SettingNote("Can't connect: make sure the address includes the port (for example http://server:8080), then press Reconnect.")
        SettingNote("Login rejected: use Edit to re-enter the username and password.")
        SettingNote("Home is empty or slow: your source may have no movies or series yet, or may be slow. Try Refresh library now.")
        SettingNote("Pictures are missing: try Clear image cache.")
    }
}

// ---------- helpers

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
