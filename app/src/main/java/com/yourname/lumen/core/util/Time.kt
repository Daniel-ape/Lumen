package com.yourname.lumen.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Whether times are shown as 20:30 (true) or 8:30 PM (false). Set from Settings. */
object ClockFormat {
    @Volatile
    var use24h: Boolean = true
}

private val clock24 = SimpleDateFormat("HH:mm", Locale.getDefault())
private val clock12 = SimpleDateFormat("h:mm a", Locale.getDefault())

/** "20:30" (or "8:30 PM") from seconds since 1970, in the device's time zone. */
fun clockTime(sec: Long): String =
    (if (ClockFormat.use24h) clock24 else clock12).format(Date(sec * 1000))

/** "20:00 – 21:00", "20:00", or null when the source gave no times. */
fun timeRangeText(startSec: Long?, endSec: Long?): String? = when {
    startSec != null && endSec != null -> "${clockTime(startSec)} – ${clockTime(endSec)}"
    startSec != null -> clockTime(startSec)
    else -> null
}

/** How far through a programme we are, 0..1, or null when the times are missing or odd. */
fun progressOf(startSec: Long?, endSec: Long?): Float? {
    if (startSec == null || endSec == null || endSec <= startSec) return null
    val now = System.currentTimeMillis() / 1000
    return ((now - startSec).toFloat() / (endSec - startSec).toFloat()).coerceIn(0f, 1f)
}

/** "1080p", "4K" and so on from the decoded video height. Null when the height isn't known. */
fun qualityLabel(heightPx: Int): String? = when {
    heightPx <= 0 -> null
    heightPx >= 2000 -> "4K"
    heightPx >= 1400 -> "1440p"
    heightPx >= 1000 -> "1080p"
    heightPx >= 700 -> "720p"
    heightPx >= 540 -> "576p"
    else -> "${heightPx}p"
}

/** "HD", "Full HD" or "4K" from the decoded video height. Null below 720 or when unknown. */
fun qualityClass(heightPx: Int): String? = when {
    heightPx >= 2000 -> "4K"
    heightPx >= 1000 -> "Full HD"
    heightPx >= 700 -> "HD"
    else -> null
}
