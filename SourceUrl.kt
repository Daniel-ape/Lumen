package com.yourname.lumen.data.xtream

import java.net.URI

/** Cleans what people type: adds http://, removes trailing slashes and /player_api.php or /c. */
fun normalizeServerUrl(raw: String): String {
    var url = raw.trim()
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "http://$url"
    }
    return url.trimEnd('/')
        .removeSuffix("/player_api.php")
        .removeSuffix("/c")
        .trimEnd('/')
}

/** Returns a message for the user when the address can't be right, or null when it looks fine. */
fun validateServerUrl(raw: String): String? {
    if (raw.isBlank()) return "Enter the server address."
    val uri = runCatching { URI(normalizeServerUrl(raw)) }.getOrNull()
        ?: return "That server address isn't valid."
    if (uri.host.isNullOrBlank()) return "That server address isn't valid."
    return null
}

fun hostName(url: String): String =
    runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "My provider"
