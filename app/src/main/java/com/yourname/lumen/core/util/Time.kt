package com.yourname.lumen.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

/** "20:30" from seconds since 1970, in the device's time zone. */
fun clockTime(sec: Long): String = clock.format(Date(sec * 1000))

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
