package com.yourname.lumen.data.m3u

import com.yourname.lumen.data.source.SourceClient
import com.yourname.lumen.data.xtream.describeFailure
import com.yourname.lumen.domain.model.ConnectionResult
import com.yourname.lumen.domain.model.GuideEntry
import com.yourname.lumen.domain.model.HomeContent
import com.yourname.lumen.domain.model.LibraryContent
import com.yourname.lumen.domain.model.MediaType
import com.yourname.lumen.domain.model.LiveCategory
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.LiveContent
import com.yourname.lumen.domain.model.MediaDetails
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.NowNext
import com.yourname.lumen.domain.model.ProgramInfo
import com.yourname.lumen.domain.model.Source
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads an M3U or M3U8 playlist from a web link or from a file the user imported.
 * Playlists carry live channels only here: there is no movie/series library and no programme guide.
 */
class M3uClient(private val source: Source) : SourceClient {

    override val supportsStreamFormats: Boolean = false

    private suspend fun readPlaylist(): String = withContext(Dispatchers.IO) {
        if (source.baseUrl.startsWith(FILE_PREFIX)) {
            val file = File(source.baseUrl.removePrefix(FILE_PREFIX))
            if (!file.exists()) throw IOException("The imported playlist file is missing. Import it again.")
            file.readText()
        } else {
            val conn = URL(source.baseUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 40_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Lumen/0.1")
                if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    override suspend fun checkConnection(): ConnectionResult = try {
        val content = parseM3u(readPlaylist())
        if (content.channels.isEmpty()) {
            ConnectionResult.Failed("The playlist loaded, but it doesn't contain any channels.")
        } else {
            ConnectionResult.Connected
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ConnectionResult.Failed(describeFailure(e))
    }

    override suspend fun loadLive(onProgress: (Int) -> Unit): LiveContent =
        withContext(Dispatchers.Default) { parseM3u(readPlaylist()).also { onProgress(it.channels.size) } }

    // Playlists have no movie or series library.
    override suspend fun loadHome(onPartial: (HomeContent) -> Unit): HomeContent =
        HomeContent(emptyList(), emptyList(), emptyList())

    override suspend fun loadDetails(item: MediaItem): MediaDetails? = null

    // Playlists have no movie or series library, and no programme guide.
    // Playlists have no movies or series.
    override fun vodUrl(itemId: String, extension: String): String = ""

    override fun episodeUrl(episodeId: String, extension: String): String = ""

    override suspend fun loadCategories(type: MediaType): List<LiveCategory> = emptyList()

    override suspend fun loadLibrary(onMovies: (Int) -> Unit, onSeries: (Int) -> Unit): LibraryContent =
        LibraryContent(emptyList(), emptyList())

    override suspend fun loadGuide(channels: List<LiveChannel>, offsetSec: Long): List<GuideEntry>? = null

    // Playlists have no programme guide of their own.
    override suspend fun shortEpg(channel: LiveChannel, offsetSec: Long): NowNext = NowNext(null, null)

    override suspend fun schedule(channel: LiveChannel, offsetSec: Long): List<ProgramInfo> = emptyList()

    override fun streamUrl(channel: LiveChannel, extension: String): String = channel.streamUrl.orEmpty()

    companion object {
        const val FILE_PREFIX = "file://"
    }
}

private val attributePattern = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

/** Tolerant M3U reader: missing attributes, blank lines, comment lines and Windows line endings are fine. */
internal fun parseM3u(text: String): LiveContent {
    val channels = ArrayList<LiveChannel>()
    val categories = LinkedHashMap<String, LiveCategory>()
    var pendingName: String? = null
    var pendingAttributes: Map<String, String> = emptyMap()

    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        when {
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                pendingAttributes = attributePattern.findAll(line)
                    .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                pendingName = titleAfterAttributes(line)
            }

            line.startsWith("#") -> Unit

            else -> {
                val name = (pendingName?.takeIf { it.isNotBlank() }
                    ?: pendingAttributes["tvg-name"]?.takeIf { it.isNotBlank() }
                    ?: line).trim()
                val group = pendingAttributes["group-title"]?.trim().orEmpty().ifEmpty { "Other" }
                categories.getOrPut(group) { LiveCategory(id = group, name = group) }
                channels.add(
                    LiveChannel(
                        id = Integer.toHexString(line.hashCode()) + "-" + channels.size,
                        number = pendingAttributes["tvg-chno"]?.toIntOrNull() ?: (channels.size + 1),
                        name = name,
                        logoUrl = pendingAttributes["tvg-logo"]?.takeIf { it.isNotBlank() },
                        categoryId = group,
                        streamUrl = line,
                    ),
                )
                pendingName = null
                pendingAttributes = emptyMap()
            }
        }
    }
    return LiveContent(categories.values.toList(), channels)
}

/** The name is everything after the first comma that is not inside quotes. */
private fun titleAfterAttributes(line: String): String {
    var inQuotes = false
    for (i in line.indices) {
        when (line[i]) {
            '"' -> inQuotes = !inQuotes
            ',' -> if (!inQuotes) return line.substring(i + 1).trim()
        }
    }
    return ""
}
