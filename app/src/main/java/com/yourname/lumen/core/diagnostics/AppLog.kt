package com.yourname.lumen.core.diagnostics

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small in-memory event log shown under Settings > General > Diagnostics.
 * Never put usernames, passwords or full URLs in a message.
 */
object AppLog {
    private val items = mutableStateListOf<String>()

    val entries: List<String> get() = items

    fun add(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        items.add(0, "$time  $message")
        while (items.size > 30) items.removeAt(items.lastIndex)
    }

    fun clear() = items.clear()
}
