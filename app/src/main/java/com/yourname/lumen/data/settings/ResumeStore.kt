package com.yourname.lumen.data.settings

import android.content.Context

/**
 * Where you stopped in each movie or episode, so Play can continue from there. Kept on the device, per
 * connection. Positions under 30 seconds are ignored (nothing worth resuming), and a title watched to
 * the end is forgotten.
 */
class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("lumen_resume", Context.MODE_PRIVATE)

    /** Milliseconds to resume from, or 0 when there is nothing to resume. */
    fun position(key: String): Long =
        prefs.getString(key, null)?.substringBefore(':')?.toLongOrNull() ?: 0L

    fun save(key: String, positionMs: Long, durationMs: Long) {
        if (durationMs > 0 && positionMs > durationMs * 0.95) {
            prefs.edit().remove(key).apply()
        } else if (positionMs >= MIN_MS) {
            prefs.edit().putString(key, "$positionMs:$durationMs").apply()
        }
    }

    fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val MIN_MS = 30_000L
    }
}
