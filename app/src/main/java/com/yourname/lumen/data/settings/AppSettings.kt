package com.yourname.lumen.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yourname.lumen.core.util.ClockFormat

/**
 * How the interface looks. These are the options that only take effect after a restart.
 * [density] is the interface scale (0.9 compact, 1.0 comfortable, 1.1 spacious).
 */
data class AppearanceConfig(
    val density: Float = 0.9f,
    val textScale: Float = 1.0f,
    val reducedBackground: Boolean = false,
    val reducedMotion: Boolean = false,
)

/**
 * Everything the user can change, saved the moment it changes.
 *
 * Appearance is staged: [pending] is what the user picked (saved), [active] is what this running
 * session uses. They are equal after launch, because the pending choice is loaded as the active one
 * before anything is drawn. Passwords are NOT stored here; they live in the encrypted SourceStore.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE)

    // ---------- appearance (staged)

    /** The configuration this session is using. Never changes while the app runs. */
    val active: AppearanceConfig = readAppearance()

    /** The saved choice that becomes active on the next launch. */
    var pending by mutableStateOf(active)
        private set

    val hasPendingAppearanceChanges: Boolean get() = pending != active

    fun updatePending(change: (AppearanceConfig) -> AppearanceConfig) {
        pending = change(pending)
        writeAppearance(pending)
    }

    fun discardPendingAppearance() {
        pending = active
        writeAppearance(active)
    }

    fun resetAppearance() {
        pending = AppearanceConfig()
        writeAppearance(pending)
    }

    private fun readAppearance() = AppearanceConfig(
        // Falls back to the old "ui_scale" key so an existing choice is kept.
        density = prefs.getFloat(KEY_DENSITY, prefs.getFloat(KEY_OLD_SCALE, 0.9f)),
        textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f),
        reducedBackground = prefs.getBoolean(KEY_REDUCED_BG, false),
        reducedMotion = prefs.getBoolean(KEY_REDUCED_MOTION, false),
    )

    private fun writeAppearance(config: AppearanceConfig) {
        prefs.edit()
            .putFloat(KEY_DENSITY, config.density)
            .putFloat(KEY_TEXT_SCALE, config.textScale)
            .putBoolean(KEY_REDUCED_BG, config.reducedBackground)
            .putBoolean(KEY_REDUCED_MOTION, config.reducedMotion)
            .apply()
    }

    // ---------- content (immediate)

    var showRatings by mutableStateOf(prefs.getBoolean(KEY_RATINGS, true))
        private set
    var heroAutoRotate by mutableStateOf(prefs.getBoolean(KEY_ROTATE, true))
        private set

    fun updateShowRatings(value: Boolean) {
        showRatings = value
        prefs.edit().putBoolean(KEY_RATINGS, value).apply()
    }

    fun updateHeroAutoRotate(value: Boolean) {
        heroAutoRotate = value
        prefs.edit().putBoolean(KEY_ROTATE, value).apply()
    }

    // ---------- general (immediate)

    /** 0 = Home, 1 = Live TV. Used the next time the app opens. */
    var startSection by mutableIntStateOf(prefs.getInt(KEY_START_SECTION, 0))
        private set
    var confirmDestructive by mutableStateOf(prefs.getBoolean(KEY_CONFIRM, true))
        private set
    var autoRefreshHours by mutableIntStateOf(prefs.getInt(KEY_REFRESH, 0))
        private set

    /** When on, moving across the top menu opens each page straight away; when off, OK opens it. */
    var dockSwitchOnFocus by mutableStateOf(prefs.getBoolean(KEY_DOCK_FOCUS, true))
        private set

    fun updateStartSection(value: Int) {
        startSection = value
        prefs.edit().putInt(KEY_START_SECTION, value).apply()
    }

    fun updateDockSwitchOnFocus(value: Boolean) {
        dockSwitchOnFocus = value
        prefs.edit().putBoolean(KEY_DOCK_FOCUS, value).apply()
    }

    fun updateConfirmDestructive(value: Boolean) {
        confirmDestructive = value
        prefs.edit().putBoolean(KEY_CONFIRM, value).apply()
    }

    fun updateAutoRefreshHours(value: Int) {
        autoRefreshHours = value
        prefs.edit().putInt(KEY_REFRESH, value).apply()
    }

    // ---------- player (immediate)

    var preferTs by mutableStateOf(prefs.getBoolean(KEY_PREFER_TS, false))
        private set
    var previewSound by mutableStateOf(prefs.getBoolean(KEY_PREVIEW_SOUND, true))
        private set
    var showQuality by mutableStateOf(prefs.getBoolean(KEY_SHOW_QUALITY, true))
        private set
    var overlaySeconds by mutableIntStateOf(prefs.getInt(KEY_OVERLAY_SECONDS, 5))
        private set

    /** 0 = fit the whole picture, 1 = fill (stretch), 2 = zoom (crop). */
    var videoScale by mutableIntStateOf(prefs.getInt(KEY_VIDEO_SCALE, 0))
        private set

    fun updatePreferTs(value: Boolean) {
        preferTs = value
        prefs.edit().putBoolean(KEY_PREFER_TS, value).apply()
    }

    fun updatePreviewSound(value: Boolean) {
        previewSound = value
        prefs.edit().putBoolean(KEY_PREVIEW_SOUND, value).apply()
    }

    fun updateShowQuality(value: Boolean) {
        showQuality = value
        prefs.edit().putBoolean(KEY_SHOW_QUALITY, value).apply()
    }

    fun updateOverlaySeconds(value: Int) {
        overlaySeconds = value
        prefs.edit().putInt(KEY_OVERLAY_SECONDS, value).apply()
    }

    fun updateVideoScale(value: Int) {
        videoScale = value
        prefs.edit().putInt(KEY_VIDEO_SCALE, value).apply()
    }

    // ---------- live TV (immediate)

    var epgOffsetMinutes by mutableIntStateOf(prefs.getInt(KEY_EPG_OFFSET, 0))
        private set
    var use24h by mutableStateOf(prefs.getBoolean(KEY_24H, true))
        private set

    /** 0 = compact, 1 = standard, 2 = wide: how much room a minute gets in the guide. */
    var guideDensity by mutableIntStateOf(prefs.getInt(KEY_GUIDE_DENSITY, 1))
        private set
    var showLogos by mutableStateOf(prefs.getBoolean(KEY_LOGOS, true))
        private set
    var rememberLastChannel by mutableStateOf(prefs.getBoolean(KEY_REMEMBER, true))
        private set
    var lastChannelId by mutableStateOf<String?>(prefs.getString(KEY_LAST_CHANNEL, null))
        private set

    /** 0 = All channels, 1 = Favorites. */
    var liveStartGroup by mutableIntStateOf(prefs.getInt(KEY_LIVE_GROUP, 0).coerceIn(0, 1))
        private set

    init {
        ClockFormat.use24h = use24h
    }

    fun updateEpgOffsetMinutes(value: Int) {
        epgOffsetMinutes = value
        prefs.edit().putInt(KEY_EPG_OFFSET, value).apply()
    }

    fun updateUse24h(value: Boolean) {
        use24h = value
        ClockFormat.use24h = value
        prefs.edit().putBoolean(KEY_24H, value).apply()
    }

    fun updateGuideDensity(value: Int) {
        guideDensity = value
        prefs.edit().putInt(KEY_GUIDE_DENSITY, value).apply()
    }

    fun updateShowLogos(value: Boolean) {
        showLogos = value
        prefs.edit().putBoolean(KEY_LOGOS, value).apply()
    }

    fun updateRememberLastChannel(value: Boolean) {
        rememberLastChannel = value
        prefs.edit().putBoolean(KEY_REMEMBER, value).apply()
    }

    fun updateLastChannel(id: String?) {
        if (id == lastChannelId) return
        lastChannelId = id
        prefs.edit().putString(KEY_LAST_CHANNEL, id).apply()
    }

    fun updateLiveStartGroup(value: Int) {
        liveStartGroup = value
        prefs.edit().putInt(KEY_LIVE_GROUP, value).apply()
    }

    // ---------- sources and favorites

    var activeSourceId by mutableStateOf<String?>(prefs.getString(KEY_ACTIVE, null))
        private set
    var favoriteChannels by mutableStateOf<Set<String>>(prefs.getStringSet(KEY_FAVORITES, null)?.toSet() ?: emptySet())
        private set

    fun updateActiveSource(id: String?) {
        activeSourceId = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Movies and series the user saved to My List, as keys from [com.yourname.lumen.domain.model.myListKey]. */
    var myList by mutableStateOf<Set<String>>(prefs.getStringSet(KEY_MY_LIST, null)?.toSet() ?: emptySet())
        private set

    fun toggleMyList(key: String) {
        val next = if (key in myList) myList - key else myList + key
        myList = next
        prefs.edit().putStringSet(KEY_MY_LIST, next).apply()
    }

    fun toggleFavoriteChannel(key: String) {
        val next = if (key in favoriteChannels) favoriteChannels - key else favoriteChannels + key
        favoriteChannels = next
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    // ---------- reset

    /**
     * Puts every preference except appearance back to its default. It does not touch connections,
     * their logins, favorites, or the active connection.
     */
    fun resetPreferences() {
        updateShowRatings(true)
        updateHeroAutoRotate(true)
        updateStartSection(0)
        updateConfirmDestructive(true)
        updateDockSwitchOnFocus(true)
        updateAutoRefreshHours(0)
        updatePreferTs(false)
        updatePreviewSound(true)
        updateShowQuality(true)
        updateOverlaySeconds(5)
        updateVideoScale(0)
        updateEpgOffsetMinutes(0)
        updateUse24h(true)
        updateGuideDensity(1)
        updateShowLogos(true)
        updateRememberLastChannel(true)
        updateLastChannel(null)
        updateLiveStartGroup(0)
    }

    private companion object {
        const val KEY_OLD_SCALE = "ui_scale"
        const val KEY_DENSITY = "ap_density"
        const val KEY_TEXT_SCALE = "ap_text_scale"
        const val KEY_REDUCED_BG = "ap_reduced_background"
        const val KEY_REDUCED_MOTION = "ap_reduced_motion"
        const val KEY_RATINGS = "show_ratings"
        const val KEY_ROTATE = "hero_auto_rotate"
        const val KEY_START_SECTION = "start_section"
        const val KEY_CONFIRM = "confirm_destructive"
        const val KEY_MY_LIST = "my_list"
        const val KEY_DOCK_FOCUS = "dock_switch_on_focus"
        const val KEY_REFRESH = "auto_refresh_hours"
        const val KEY_PREFER_TS = "prefer_ts"
        const val KEY_PREVIEW_SOUND = "preview_sound"
        const val KEY_SHOW_QUALITY = "show_quality"
        const val KEY_OVERLAY_SECONDS = "overlay_seconds"
        const val KEY_VIDEO_SCALE = "video_scale"
        const val KEY_EPG_OFFSET = "epg_offset_minutes"
        const val KEY_24H = "use_24h"
        const val KEY_GUIDE_DENSITY = "guide_density"
        const val KEY_LOGOS = "show_logos"
        const val KEY_REMEMBER = "remember_last_channel"
        const val KEY_LAST_CHANNEL = "last_channel"
        const val KEY_LIVE_GROUP = "live_start_group"
        const val KEY_ACTIVE = "active_source"
        const val KEY_FAVORITES = "favorite_channels"
    }
}
