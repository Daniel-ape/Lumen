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

    var uiScale by mutableFloatStateOf(prefs.getFloat(KEY_SCALE, 1.0f))
        private set
    var showRatings by mutableStateOf(prefs.getBoolean(KEY_RATINGS, true))
        private set
    var heroAutoRotate by mutableStateOf(prefs.getBoolean(KEY_ROTATE, true))
        private set
    var autoRefreshHours by mutableIntStateOf(prefs.getInt(KEY_REFRESH, 0))
        private set
    var activeSourceId by mutableStateOf<String?>(prefs.getString(KEY_ACTIVE, null))
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

    private companion object {
        const val KEY_SCALE = "ui_scale"
        const val KEY_RATINGS = "show_ratings"
        const val KEY_ROTATE = "hero_auto_rotate"
        const val KEY_REFRESH = "auto_refresh_hours"
        const val KEY_ACTIVE = "active_source"
    }
}
