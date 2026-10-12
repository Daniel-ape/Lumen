package com.yourname.lumen.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App preferences. Each value is saved the moment it changes and is read back on the next start.
 * Passwords are NOT stored here; they live in the encrypted SourceStore.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE)

    var uiScale by mutableFloatStateOf(prefs.getFloat(KEY_SCALE, DEFAULT_SCALE))
        private set
    var showRatings by mutableStateOf(prefs.getBoolean(KEY_RATINGS, true))
        private set
    var heroAutoRotate by mutableStateOf(prefs.getBoolean(KEY_ROTATE, true))
        private set
    var autoRefreshHours by mutableIntStateOf(prefs.getInt(KEY_REFRESH, 0))
        private set
    var activeSourceId by mutableStateOf<String?>(prefs.getString(KEY_ACTIVE, null))
        private set
    /** Shifts programme times shown in the guide, for sources whose clock is off. */
    var epgOffsetMinutes by mutableIntStateOf(prefs.getInt(KEY_EPG_OFFSET, 0))
        private set
    var preferTs by mutableStateOf(prefs.getBoolean(KEY_PREFER_TS, false))
        private set
    var previewSound by mutableStateOf(prefs.getBoolean(KEY_PREVIEW_SOUND, true))
        private set
    var showQuality by mutableStateOf(prefs.getBoolean(KEY_SHOW_QUALITY, true))
        private set
    var overlaySeconds by mutableIntStateOf(prefs.getInt(KEY_OVERLAY_SECONDS, 5))
        private set
    var favoriteChannels by mutableStateOf<Set<String>>(prefs.getStringSet(KEY_FAVORITES, null)?.toSet() ?: emptySet())
        private set

    fun updateUiScale(value: Float) {
        uiScale = value
        prefs.edit().putFloat(KEY_SCALE, value).apply()
    }

    fun updateShowRatings(value: Boolean) {
        showRatings = value
        prefs.edit().putBoolean(KEY_RATINGS, value).apply()
    }

    fun updateHeroAutoRotate(value: Boolean) {
        heroAutoRotate = value
        prefs.edit().putBoolean(KEY_ROTATE, value).apply()
    }

    fun updateAutoRefreshHours(value: Int) {
        autoRefreshHours = value
        prefs.edit().putInt(KEY_REFRESH, value).apply()
    }

    fun updateActiveSource(id: String?) {
        activeSourceId = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun updateEpgOffsetMinutes(value: Int) {
        epgOffsetMinutes = value
        prefs.edit().putInt(KEY_EPG_OFFSET, value).apply()
    }

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

    fun toggleFavoriteChannel(key: String) {
        val next = if (key in favoriteChannels) favoriteChannels - key else favoriteChannels + key
        favoriteChannels = next
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    private companion object {
        const val DEFAULT_SCALE = 0.9f
        const val KEY_FAVORITES = "favorite_channels"
        const val KEY_EPG_OFFSET = "epg_offset_minutes"
        const val KEY_PREFER_TS = "prefer_ts"
        const val KEY_PREVIEW_SOUND = "preview_sound"
        const val KEY_SHOW_QUALITY = "show_quality"
        const val KEY_OVERLAY_SECONDS = "overlay_seconds"
        const val KEY_SCALE = "ui_scale"
        const val KEY_RATINGS = "show_ratings"
        const val KEY_ROTATE = "hero_auto_rotate"
        const val KEY_REFRESH = "auto_refresh_hours"
        const val KEY_ACTIVE = "active_source"
    }
}
