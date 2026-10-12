package com.yourname.lumen.ui.settings

import android.os.Build
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

// ---------- Connection

internal fun LazyListScope.connectionItems(
    sources: List<Source>,
    info: SettingsInfo,
    actions: SettingsActions,
) {
    item(key = "add_title") { SettingGroupTitle("Add a connection") }
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

    item(key = "saved_title") { SettingGroupTitle("Saved connections") }
    if (sources.isEmpty()) {
        item(key = "empty") { SettingNote("No connections yet.") }
    }
    items(sources, key = { it.id }) { source ->
        SourceCard(
            source = source,
            isActive = source.id == info.activeSourceId,
            testing = info.testingId == source.id,
            actions = actions,
        )
    }
}

@Composable
private fun SourceCard(
    source: Source,
    isActive: Boolean,
    testing: Boolean,
    actions: SettingsActions,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    var confirming by remember(source.id) { mutableStateOf(false) }
    val deleteFocus = remember { FocusRequester() }

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
                modifier = Modifier.focusRequester(deleteFocus),
                compact = true,
                onClick = { confirming = true },
            )
        }
        if (confirming) {
            LumenText("Delete this connection? Its login is removed from this device.", type.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LumenButton(
                    text = "Yes, delete",
                    primary = true,
                    compact = true,
                    onClick = {
                        confirming = false
                        actions.onRemoveSource(source)
                    },
                )
                LumenButton(
                    text = "Cancel",
                    compact = true,
                    onClick = {
                        confirming = false
                        runCatching { deleteFocus.requestFocus() }
                    },
                )
            }
        }
    }
}

// ---------- EPG

private val OffsetOptions = listOf(
    -180 to "−3 hours",
    -120 to "−2 hours",
    -60 to "−1 hour",
    0 to "None",
    60 to "+1 hour",
    120 to "+2 hours",
    180 to "+3 hours",
)

internal fun LazyListScope.epgItems(settings: AppSettings, actions: SettingsActions) {
    item(key = "offset") {
        val current = OffsetOptions.firstOrNull { it.first == settings.epgOffsetMinutes } ?: OffsetOptions[3]
        SettingChoiceRow(
            label = "Time offset",
            description = "Shifts programme times if your source's guide is off by a few hours.",
            value = current.second,
            onCycle = {
                val next = OffsetOptions[(OffsetOptions.indexOf(current) + 1) % OffsetOptions.size]
                settings.updateEpgOffsetMinutes(next.first)
            },
        )
    }
    item(key = "clear_guide") {
        SettingActionRow(
            label = "Refresh guide data",
            description = "Forgets the programme information shown so far and asks your source again.",
            onClick = actions.onClearGuideCache,
        )
    }
    item(key = "epg_note") {
        SettingNote(
            "Programme information comes from your own source. XMLTV guide files are not supported yet, " +
                "and M3U playlists show no programme information.",
        )
    }
}

// ---------- Playback

private val OverlayOptions = listOf(3 to "3 seconds", 5 to "5 seconds", 8 to "8 seconds")

internal fun LazyListScope.playbackItems(settings: AppSettings) {
    item(key = "format") {
        SettingChoiceRow(
            label = "Stream format",
            description = "Which format to try first. Lumen switches to the other one if it fails.",
            value = if (settings.preferTs) "TS first" else "HLS first",
            onCycle = { settings.updatePreferTs(!settings.preferTs) },
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
            label = "Show video quality",
            description = "Shows 720p, 1080p or 4K when the player can tell.",
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
}

// ---------- Appearance

private val ScaleOptions = listOf(0.9f to "Small", 1.0f to "Default", 1.1f to "Large")

internal fun LazyListScope.appearanceItems(settings: AppSettings) {
    item(key = "scale") {
        val current = ScaleOptions.firstOrNull { it.first == settings.uiScale } ?: ScaleOptions[0]
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

internal fun LazyListScope.generalItems(
    settings: AppSettings,
    info: SettingsInfo,
    actions: SettingsActions,
) {
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
    item(key = "refresh") {
        SettingActionRow(
            label = "Refresh now",
            description = if (info.lastRefreshAt == 0L) {
                "Not refreshed yet in this session."
            } else {
                "Last refreshed ${formatDateTime(info.lastRefreshAt)}."
            },
            onClick = actions.onRefreshData,
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
            SettingActionRow(label = "Clear log", description = null, onClick = { AppLog.clear() })
        }
    }
}

// ---------- About & Support

internal fun LazyListScope.aboutItems() {
    item(key = "about") { AboutContent() }
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
        SettingNote("A channel won't play: try Stream format under Playback, or press OK to retry.")
        SettingNote("No programme information: your source may not provide it for that channel.")
        SettingNote("Pictures are missing: try Clear image cache under General.")
    }
}

// ---------- helpers

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
