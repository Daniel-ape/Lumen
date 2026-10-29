package com.yourname.lumen.ui.settings

import com.yourname.lumen.core.diagnostics.formatMillis
import com.yourname.lumen.core.diagnostics.Perf
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.data.settings.AppSettings
import com.yourname.lumen.domain.model.Source
import com.yourname.lumen.domain.model.SourceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(14.dp)

// ============================================================ Sources

internal fun LazyListScope.sourcesItems(
    sources: List<Source>,
    info: SettingsInfo,
    settings: AppSettings,
    actions: SettingsActions,
) {
    item(key = "add_title") { SettingGroupTitle("Add a source") }
    item(key = "add_xtream") {
        SettingActionRow(
            label = "Xtream Codes API",
            description = "Server URL, username and password from your provider.",
            onClick = { actions.onAddSource(SourceType.XTREAM) },
        )
    }
    item(key = "add_m3u") {
        SettingActionRow(
            label = "M3U playlist",
            description = "A playlist link, or a playlist file from this device.",
            onClick = { actions.onAddSource(SourceType.M3U) },
        )
    }
    item(key = "add_self") {
        SettingActionRow(
            label = "Self-hosted server",
            description = "Your own server that provides the Xtream Codes API.",
            onClick = { actions.onAddSource(SourceType.SELF_HOSTED) },
        )
    }
    item(key = "self_note") {
        SettingNote(
            "A self-hosted server must offer the Xtream Codes API. Software that gives you a playlist link " +
                "instead can be added as an M3U playlist. Lumen can't connect to a server that offers neither.",
        )
    }

    item(key = "saved_title") { SettingGroupTitle("Saved sources") }
    if (sources.isEmpty()) {
        item(key = "empty") { SettingNote("No sources yet.") }
    }
    items(sources, key = { it.id }) { source ->
        SourceCard(
            source = source,
            isActive = source.id == info.activeSourceId,
            testing = info.testingId == source.id,
            confirmDelete = settings.confirmDestructive,
            actions = actions,
        )
    }
}

@Composable
private fun SourceCard(
    source: Source,
    isActive: Boolean,
    testing: Boolean,
    confirmDelete: Boolean,
    actions: SettingsActions,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val confirm = LocalConfirm.current

    val status = when {
        testing -> "Testing the connection…"
        source.lastCheckedAt == 0L -> "Not checked yet"
        source.lastError != null -> "Couldn't connect · ${source.lastError}"
        else -> "Working · checked ${formatDateTime(source.lastCheckedAt)}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(CardShape, GlassLevel.Panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
            LumenText(
                text = source.type.label,
                style = type.label.copy(fontSize = 11.sp),
                color = colors.textSecondary,
                modifier = Modifier
                    .glass(RoundedCornerShape(6.dp), GlassLevel.Control)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (isActive) LumenText("Active", type.label, color = colors.accent)
        }
        LumenText(
            text = if (source.type == SourceType.M3U && source.baseUrl.startsWith("file://")) {
                "Imported file"
            } else {
                source.baseUrl
            },
            style = type.label.copy(fontSize = 12.sp),
            color = colors.textTertiary,
            maxLines = 1,
        )
        LumenText(
            text = status,
            style = type.label,
            color = if (source.lastError != null) Color(0xFFFF8A80) else colors.textSecondary,
            maxLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isActive) LumenButton("Set active", compact = true, onClick = { actions.onSetActive(source) })
            LumenButton(
                text = if (testing) "Testing…" else "Test",
                compact = true,
                onClick = { if (!testing) actions.onTestSource(source) },
            )
            if (isActive) LumenButton("Refresh data", compact = true, onClick = actions.onRefreshData)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LumenButton("Edit", compact = true, onClick = { actions.onEditSource(source) })
            LumenButton(
                text = "Delete",
                compact = true,
                onClick = {
                    if (confirmDelete) {
                        confirm(
                            ConfirmRequest(
                                title = "Delete \"${source.name}\"?",
                                message = "This removes the source and its login from this device. " +
                                    "Your other sources, favorites and settings are not affected.",
                                confirmLabel = "Delete",
                                onConfirm = { actions.onRemoveSource(source) },
                            ),
                        )
                    } else {
                        actions.onRemoveSource(source)
                    }
                },
            )
        }
    }
}

// ============================================================ Appearance (staged)

private val TextScaleOptions = listOf(0.9f to "Small", 1.0f to "Default", 1.15f to "Large", 1.3f to "Extra large")
private val DensityOptions = listOf(0.9f to "Compact", 1.0f to "Comfortable", 1.1f to "Spacious")

internal fun LazyListScope.appearanceItems(settings: AppSettings, actions: SettingsActions) {
    if (settings.hasPendingAppearanceChanges) {
        item(key = "restart_notice") { RestartNotice(settings, actions) }
    }
    item(key = "text_size") {
        val current = TextScaleOptions.firstOrNull { it.first == settings.pending.textScale } ?: TextScaleOptions[1]
        SettingChoiceRow(
            label = "Text size",
            description = "Applies after a restart.",
            value = current.second,
            onCycle = {
                val next = TextScaleOptions[(TextScaleOptions.indexOf(current) + 1) % TextScaleOptions.size]
                settings.updatePending { it.copy(textScale = next.first) }
            },
        )
    }
    item(key = "text_preview") { TextSizePreview(settings) }
    item(key = "density") {
        val current = DensityOptions.firstOrNull { it.first == settings.pending.density } ?: DensityOptions[0]
        SettingChoiceRow(
            label = "Interface density",
            description = "How large buttons, lists and spacing are. Applies after a restart.",
            value = current.second,
            onCycle = {
                val next = DensityOptions[(DensityOptions.indexOf(current) + 1) % DensityOptions.size]
                settings.updatePending { it.copy(density = next.first) }
            },
        )
    }
    item(key = "background") {
        SettingChoiceRow(
            label = "Background",
            description = "Reduced uses a flat charcoal instead of the soft gradient. Applies after a restart.",
            value = if (settings.pending.reducedBackground) "Reduced effects" else "Standard",
            onCycle = { settings.updatePending { it.copy(reducedBackground = !it.reducedBackground) } },
        )
    }
    item(key = "motion") {
        SettingChoiceRow(
            label = "Animations",
            description = "Reduced Motion turns off most transitions. Applies after a restart.",
            value = if (settings.pending.reducedMotion) "Reduced motion" else "Standard",
            onCycle = { settings.updatePending { it.copy(reducedMotion = !it.reducedMotion) } },
        )
    }
    item(key = "reset_appearance") {
        SettingActionRow(
            label = "Reset appearance to defaults",
            description = "Selects the default look. It still applies only after a restart.",
            onClick = { settings.resetAppearance() },
        )
    }
    item(key = "appearance_note") {
        SettingNote("Theme options are not available yet: Lumen currently has its dark appearance only.")
    }
}

@Composable
private fun RestartNotice(settings: AppSettings, actions: SettingsActions) {
    val type = LumenTheme.typography
    val colors = LumenTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.14f), shape)
            .glass(shape, GlassLevel.Panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LumenText("Restart required", type.heading.copy(fontSize = 16.sp), color = colors.accent)
        LumenText(
            "Restart Lumen to apply your appearance changes. Your selected preferences have been saved and will take effect the next time you open the app.",
            type.body.copy(fontSize = 13.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LumenButton("Restart Lumen", primary = true, compact = true, onClick = actions.onRestart)
            LumenButton(
                text = "Discard changes",
                compact = true,
                onClick = { settings.discardPendingAppearance() },
            )
        }
        LumenText(
            "If Lumen doesn't reopen by itself, close it and open it again from your app list.",
            type.label.copy(fontSize = 11.sp),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun TextSizePreview(settings: AppSettings) {
    val colors = LumenTheme.colors
    // The running app keeps its current size, so the sample is scaled by the difference to show the chosen one.
    val factor = settings.pending.textScale / settings.active.textScale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(CardShape, GlassLevel.Panel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LumenText("Preview", LumenTheme.typography.label.copy(fontSize = 11.sp), color = colors.textSecondary)
        LumenText(
            text = "The quick brown fox jumps over the lazy dog.",
            style = LumenTheme.typography.body.copy(fontSize = (14f * factor).sp, lineHeight = (20f * factor).sp),
        )
    }
}

// ============================================================ General

private val RefreshOptions = listOf(0 to "Off", 6 to "Every 6 hours", 12 to "Every 12 hours", 24 to "Every day")
private val StartOptions = listOf(0 to "Home", 1 to "Live TV")

internal fun LazyListScope.generalItems(settings: AppSettings, actions: SettingsActions) {
    item(key = "start") {
        val current = StartOptions.firstOrNull { it.first == settings.startSection } ?: StartOptions[0]
        SettingChoiceRow(
            label = "Open Lumen on",
            description = "The section shown when the app starts.",
            value = current.second,
            onCycle = {
                val next = StartOptions[(StartOptions.indexOf(current) + 1) % StartOptions.size]
                settings.updateStartSection(next.first)
            },
        )
    }
    item(key = "autorefresh") {
        val current = RefreshOptions.firstOrNull { it.first == settings.autoRefreshHours } ?: RefreshOptions[0]
        SettingChoiceRow(
            label = "Auto-refresh",
            description = "Reloads your channels and library while the app is open.",
            value = current.second,
            onCycle = {
                val next = RefreshOptions[(RefreshOptions.indexOf(current) + 1) % RefreshOptions.size]
                settings.updateAutoRefreshHours(next.first)
            },
        )
    }
    item(key = "confirm") {
        SettingToggleRow(
            label = "Confirm before deleting",
            description = "Ask first when deleting a source or clearing stored data.",
            checked = settings.confirmDestructive,
            onToggle = { settings.updateConfirmDestructive(!settings.confirmDestructive) },
        )
    }
    item(key = "dock_focus") {
        SettingToggleRow(
            label = "Open pages as you move across the menu",
            description = "Moving over Home, Live TV, Movies and the other menu items opens them straight away. Turn off to open them with OK.",
            checked = settings.dockSwitchOnFocus,
            onToggle = { settings.updateDockSwitchOnFocus(!settings.dockSwitchOnFocus) },
        )
    }
    item(key = "reset_prefs") {
        val confirm = LocalConfirm.current
        SettingActionRow(
            label = "Reset preferences",
            description = "Puts these settings back to their defaults. Sources, logins, favorites and appearance are not touched.",
            onClick = {
                confirm(
                    ConfirmRequest(
                        title = "Reset preferences?",
                        message = "General, Player, Live TV and Movies & Series settings go back to their defaults. " +
                            "Your sources, logins, favorites and appearance stay as they are.",
                        confirmLabel = "Reset",
                        onConfirm = { settings.resetPreferences() },
                    ),
                )
            },
        )
    }
    item(key = "general_note") {
        SettingNote("These settings take effect straight away, except \"Open Lumen on\", which is used the next time the app starts.")
    }
}

// ============================================================ Player

private val OverlayOptions = listOf(3 to "3 seconds", 5 to "5 seconds", 8 to "8 seconds")
private val ScaleModeOptions = listOf(0 to "Fit", 1 to "Fill", 2 to "Zoom")

internal fun LazyListScope.playerItems(settings: AppSettings) {
    item(key = "format") {
        SettingChoiceRow(
            label = "Stream format",
            description = "Which format to try first. Lumen switches to the other one if it fails.",
            value = if (settings.preferTs) "TS first" else "HLS first",
            onCycle = { settings.updatePreferTs(!settings.preferTs) },
        )
    }
    item(key = "scaling") {
        val current = ScaleModeOptions.firstOrNull { it.first == settings.videoScale } ?: ScaleModeOptions[0]
        SettingChoiceRow(
            label = "Picture scaling",
            description = "Fit shows the whole picture, Fill stretches it, Zoom crops to fill the screen.",
            value = current.second,
            onCycle = {
                val next = ScaleModeOptions[(ScaleModeOptions.indexOf(current) + 1) % ScaleModeOptions.size]
                settings.updateVideoScale(next.first)
            },
        )
    }
    item(key = "sound") {
        SettingToggleRow(
            label = "Sound in the preview",
            description = "Plays the channel's sound in the Live TV preview window.",
            checked = settings.previewSound,
            onToggle = { settings.updatePreviewSound(!settings.previewSound) },
        )
    }
    item(key = "quality") {
        SettingToggleRow(
            label = "Show stream information",
            description = "Shows resolution and audio format in the info bar when the player can tell.",
            checked = settings.showQuality,
            onToggle = { settings.updateShowQuality(!settings.showQuality) },
        )
    }
    item(key = "overlay") {
        val current = OverlayOptions.firstOrNull { it.first == settings.overlaySeconds } ?: OverlayOptions[1]
        SettingChoiceRow(
            label = "Hide the info bar after",
            description = "How long the fullscreen info bar stays on screen.",
            value = current.second,
            onCycle = {
                val next = OverlayOptions[(OverlayOptions.indexOf(current) + 1) % OverlayOptions.size]
                settings.updateOverlaySeconds(next.first)
            },
        )
    }
    item(key = "player_note") {
        SettingNote("Subtitle and audio-track choices are not available yet. All Player settings apply immediately.")
    }
}

// ============================================================ Live TV

private val GuideDensityOptions = listOf(0 to "Compact", 1 to "Standard", 2 to "Wide")
private val LiveStartOptions = listOf(0 to "All channels", 1 to "Favorites")
private val OffsetOptions = listOf(
    -180 to "−3 hours",
    -120 to "−2 hours",
    -60 to "−1 hour",
    0 to "None",
    60 to "+1 hour",
    120 to "+2 hours",
    180 to "+3 hours",
)

internal fun LazyListScope.liveTvItems(settings: AppSettings) {
    item(key = "live_start") {
        val current = LiveStartOptions.firstOrNull { it.first == settings.liveStartGroup } ?: LiveStartOptions[0]
        SettingChoiceRow(
            label = "Open Live TV on",
            description = "The view shown when you open Live TV.",
            value = current.second,
            onCycle = {
                val next = LiveStartOptions[(LiveStartOptions.indexOf(current) + 1) % LiveStartOptions.size]
                settings.updateLiveStartGroup(next.first)
            },
        )
    }
    item(key = "remember") {
        SettingToggleRow(
            label = "Remember the last channel",
            description = "Opens Live TV with the channel you were watching highlighted.",
            checked = settings.rememberLastChannel,
            onToggle = { settings.updateRememberLastChannel(!settings.rememberLastChannel) },
        )
    }
    item(key = "time_format") {
        SettingChoiceRow(
            label = "Time format",
            description = "How programme times are shown.",
            value = if (settings.use24h) "24-hour" else "12-hour",
            onCycle = { settings.updateUse24h(!settings.use24h) },
        )
    }
    item(key = "guide_density") {
        val current = GuideDensityOptions.firstOrNull { it.first == settings.guideDensity } ?: GuideDensityOptions[1]
        SettingChoiceRow(
            label = "Guide width",
            description = "How much room each minute gets in the programme guide.",
            value = current.second,
            onCycle = {
                val next = GuideDensityOptions[(GuideDensityOptions.indexOf(current) + 1) % GuideDensityOptions.size]
                settings.updateGuideDensity(next.first)
            },
        )
    }
    item(key = "logos") {
        SettingToggleRow(
            label = "Show channel logos",
            description = "Shows each channel's logo in lists and in the guide.",
            checked = settings.showLogos,
            onToggle = { settings.updateShowLogos(!settings.showLogos) },
        )
    }
    item(key = "offset") {
        val current = OffsetOptions.firstOrNull { it.first == settings.epgOffsetMinutes } ?: OffsetOptions[3]
        SettingChoiceRow(
            label = "Guide time offset",
            description = "Shifts programme times if your source's guide is off by a few hours.",
            value = current.second,
            onCycle = {
                val next = OffsetOptions[(OffsetOptions.indexOf(current) + 1) % OffsetOptions.size]
                settings.updateEpgOffsetMinutes(next.first)
            },
        )
    }
    item(key = "live_note") {
        SettingNote(
            "Programme information comes from your own source. XMLTV guide files are not supported yet, " +
                "and M3U playlists show no programme information.",
        )
    }
}

// ============================================================ Movies & Series

internal fun LazyListScope.contentItems(settings: AppSettings) {
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

// ============================================================ Storage & Data

internal fun LazyListScope.storageItems(info: SettingsInfo, settings: AppSettings, actions: SettingsActions) {
    item(key = "usage") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingInfoRow("Cached pictures", formatBytes(info.imageCacheBytes))
            SettingInfoRow("Saved library data", formatBytes(info.libraryCacheBytes))
        }
    }
    item(key = "refresh") {
        SettingActionRow(
            label = "Refresh content",
            description = if (info.lastRefreshAt == 0L) {
                "Reloads your channels and library from the active source."
            } else {
                "Reloads from the active source. Last refreshed ${formatDateTime(info.lastRefreshAt)}."
            },
            onClick = actions.onRefreshData,
        )
    }
    item(key = "refresh_guide") {
        SettingActionRow(
            label = "Refresh guide data",
            description = "Forgets the programme information shown so far and asks your source again.",
            onClick = actions.onClearGuideCache,
        )
    }
    item(key = "clear_images") {
        val confirm = LocalConfirm.current
        SettingActionRow(
            label = "Clear cached pictures",
            description = "Posters and logos download again when needed.",
            onClick = {
                val run = actions.onClearImageCache
                if (settings.confirmDestructive) {
                    confirm(
                        ConfirmRequest(
                            title = "Clear cached pictures?",
                            message = "Posters, backdrops and logos stored on this device are deleted and download again when needed. Nothing else is affected.",
                            confirmLabel = "Clear",
                            onConfirm = run,
                        ),
                    )
                } else {
                    run()
                }
            },
        )
    }
    item(key = "clear_library") {
        val confirm = LocalConfirm.current
        SettingActionRow(
            label = "Clear saved library data",
            description = "Removes the saved copy of your channel list and Home content. It loads again from your source.",
            onClick = {
                val run = actions.onClearLibraryCache
                if (settings.confirmDestructive) {
                    confirm(
                        ConfirmRequest(
                            title = "Clear saved library data?",
                            message = "The saved channel list and Home content are deleted and loaded again from your source. " +
                                "Your sources, logins, favorites and settings are not affected.",
                            confirmLabel = "Clear",
                            onConfirm = run,
                        ),
                    )
                } else {
                    run()
                }
            },
        )
    }
}

// ============================================================ About & Support

internal fun LazyListScope.aboutItems() {
    item(key = "about") { AboutContent() }
    item(key = "perf_title") { SettingGroupTitle("Loading times") }
    item(key = "perf") {
        val timings = Perf.timings
        if (timings.isEmpty()) {
            SettingNote("Nothing measured yet. Open Home, Live TV or Search and the times appear here.")
        } else {
            val slowest = timings.filter { !it.milestone }.maxByOrNull { it.millis }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(CardShape, GlassLevel.Panel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (slowest != null) {
                    LumenText(
                        "Slowest step so far: ${slowest.label} (${formatMillis(slowest.millis)})",
                        LumenTheme.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = LumenTheme.colors.accent,
                        maxLines = 2,
                    )
                }
                timings.take(16).forEach { t ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LumenText(
                            text = t.label + (t.detail?.let { " · $it" } ?: ""),
                            style = LumenTheme.typography.label.copy(fontSize = 12.sp),
                            color = if (t.milestone) Color.White else LumenTheme.colors.textSecondary,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        LumenText(
                            text = formatMillis(t.millis),
                            style = LumenTheme.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
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
                    .glass(CardShape, GlassLevel.Panel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                entries.take(8).forEach { line ->
                    LumenText(line, LumenTheme.typography.label.copy(fontSize = 12.sp), color = LumenTheme.colors.textSecondary, maxLines = 2)
                }
            }
        }
    }
    item(key = "diag_clear") {
        if (AppLog.entries.isNotEmpty()) {
            SettingActionRow(label = "Clear log", description = null, onClick = { AppLog.clear(); Perf.clear() })
        }
    }
}

@Composable
private fun AboutContent() {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it).toString() } ?: "unknown"

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SettingGroupTitle("Lumen TV")
        SettingInfoRow("Version", "$versionName (build $versionCode)")
        SettingInfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        SettingInfoRow("Device", Build.MODEL ?: "unknown")
        SettingNote(
            "Lumen TV is a personal-use player. It contains no channels or content of its own; " +
                "it only shows what your own source provides.",
        )
        SettingGroupTitle("Troubleshooting")
        SettingNote("Can't connect: check that the address includes the port (for example http://server:8080), then press Test.")
        SettingNote("Login rejected: use Edit to re-enter the username and password.")
        SettingNote("A channel won't play: try Stream format under Player, or press OK to retry.")
        SettingNote("No programme information: your source may not provide it for that channel.")
        SettingNote("Pictures are missing: try Clear cached pictures under Storage & Data.")
    }
}

// ============================================================ helpers

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
