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

/** Builds "scheme://host:port" for a self-hosted server, or null when the address or port can't be right. */
fun buildSelfHostedUrl(address: String, port: String, https: Boolean): String? {
    val hasScheme = address.startsWith("http://", ignoreCase = true) || address.startsWith("https://", ignoreCase = true)
    val withScheme = (if (hasScheme) address else (if (https) "https://" else "http://") + address).trimEnd('/')
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val scheme = uri.scheme ?: return null
    val portNumber = if (port.isNotEmpty()) {
        port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    } else {
        uri.port
    }
    return buildString {
        append(scheme).append("://").append(host)
        if (portNumber > 0) append(":").append(portNumber)
        uri.path?.trimEnd('/')?.takeIf { it.isNotBlank() }?.let { append(it) }
    }
}

/** Returns a message when a playlist link can't be right, or null when it looks fine. */
fun validatePlaylistLink(raw: String): String? {
    val link = raw.trim()
    if (!link.startsWith("http://", ignoreCase = true) && !link.startsWith("https://", ignoreCase = true)) {
        return "The playlist link must start with http:// or https://."
    }
    val uri = runCatching { URI(link) }.getOrNull() ?: return "That playlist link isn't valid."
    return if (uri.host.isNullOrBlank()) "That playlist link isn't valid." else null
}
