package com.yourname.lumen.data.xtream

import com.yourname.lumen.data.source.SourceClient
import com.yourname.lumen.domain.model.ConnectionResult
import android.util.Base64
import com.yourname.lumen.domain.model.HomeContent
import com.yourname.lumen.domain.model.LiveCategory
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.LiveContent
import com.yourname.lumen.domain.model.NowNext
import com.yourname.lumen.domain.model.ProgramInfo
import com.yourname.lumen.domain.model.MediaDetails
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType
import com.yourname.lumen.domain.model.Source
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Talks to an Xtream Codes compatible server. Self-hosted panels that speak the same API work too.
 * Posters and backdrops come straight from the provider.
 * Never log URLs from here: they contain the username and password.
 */
class XtreamClient(private val source: Source) : SourceClient {

    override val supportsStreamFormats: Boolean = true

    private val base = source.baseUrl.trimEnd('/')

    private fun url(action: String? = null, vararg params: Pair<String, String>): String {
        val sb = StringBuilder("$base/player_api.php?username=${enc(source.username)}&password=${enc(source.password)}")
        if (action != null) sb.append("&action=").append(action)
        params.forEach { (k, v) -> sb.append('&').append(k).append('=').append(enc(v)) }
        return sb.toString()
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
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

    /** True when the server accepts the username and password. */
    suspend fun authenticate(): Boolean = withContext(Dispatchers.Default) {
        val o = JSONObject(fetch(url()))
        o.optJSONObject("user_info")?.optString("auth") == "1"
    }

    /** Tests the connection and explains a failure in plain words. */
    override suspend fun checkConnection(): ConnectionResult = try {
        if (authenticate()) {
            ConnectionResult.Connected
        } else {
            ConnectionResult.Failed("The server rejected the username or password.")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ConnectionResult.Failed(describeFailure(e))
    }

    override suspend fun loadHome(onPartial: (HomeContent) -> Unit): HomeContent = coroutineScope {
        val moviesJob = async { runCatching { newestMovies() } }
        val seriesJob = async { runCatching { newestSeries() } }

        // Series lists already include backdrops, so they can fill the hero and a row straight away.
        val seriesResult = seriesJob.await()
        val series = seriesResult.getOrDefault(emptyList())
        val heroSeries = (series.filter { it.backdropUrl != null }.ifEmpty { series }).take(3)
        if (series.isNotEmpty()) {
            onPartial(HomeContent(hero = heroSeries, movies = emptyList(), series = series.take(20)))
        }

        val moviesResult = moviesJob.await()
        if (moviesResult.isFailure && seriesResult.isFailure) {
            throw moviesResult.exceptionOrNull() ?: IOException("No data")
        }
        val movies = moviesResult.getOrDefault(emptyList())
        if (movies.isNotEmpty()) {
            onPartial(HomeContent(hero = heroSeries, movies = movies.take(20), series = series.take(20)))
        }

        // Movies only have a small poster in the list, so ask for the wide backdrop of the few we feature.
        val movieCandidates = movies.take(6).map { async { withDetails(it) } }.awaitAll()
        val heroMovies = movieCandidates.filter { it.backdropUrl != null }.ifEmpty { movieCandidates }.take(3)

        // Only titles with usable artwork can be featured.
        val hero = buildList {
            for (i in 0 until maxOf(heroMovies.size, heroSeries.size)) {
                heroMovies.getOrNull(i)?.let { add(it) }
                heroSeries.getOrNull(i)?.let { add(it) }
            }
        }.filter { it.backdropUrl != null || it.posterUrl != null }
        HomeContent(hero = hero, movies = movies.take(20), series = series.take(20))
    }

    /** Full details for the details page. Falls back to what we already know if the server won't say more. */
    override suspend fun loadDetails(item: MediaItem): MediaDetails = withContext(Dispatchers.Default) {
        val basic = MediaDetails(
            title = item.title,
            type = item.type,
            year = item.year,
            rating = item.rating,
            plot = item.plot,
            genres = null,
            director = null,
            cast = null,
            duration = null,
            seasons = null,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
        )
        try {
            if (item.type == MediaType.Movie) {
                val info = JSONObject(fetch(url("get_vod_info", "vod_id" to item.id))).optJSONObject("info")
                    ?: return@withContext basic
                basic.copy(
                    plot = info.str("plot") ?: info.str("description") ?: basic.plot,
                    genres = info.str("genre"),
                    director = info.str("director"),
                    cast = info.str("cast") ?: info.str("actors"),
                    duration = info.str("duration"),
                    year = basic.year ?: info.str("releasedate")?.take(4),
                    rating = basic.rating ?: ratingOf(info.optString("rating")),
                    posterUrl = info.str("movie_image") ?: info.str("cover_big") ?: basic.posterUrl,
                    backdropUrl = info.firstOf("backdrop_path")?.let { upgradeImage(it) } ?: basic.backdropUrl,
                )
            } else {
                val root = JSONObject(fetch(url("get_series_info", "series_id" to item.id)))
                val info = root.optJSONObject("info") ?: return@withContext basic
                basic.copy(
                    plot = info.str("plot") ?: basic.plot,
                    genres = info.str("genre"),
                    director = info.str("director"),
                    cast = info.str("cast"),
                    duration = info.str("episode_run_time")?.let { "$it min per episode" },
                    seasons = root.optJSONObject("episodes")?.length(),
                    year = basic.year ?: info.str("releaseDate")?.take(4) ?: info.str("releasedate")?.take(4),
                    rating = basic.rating ?: ratingOf(info.optString("rating")),
                    posterUrl = info.str("cover") ?: basic.posterUrl,
                    backdropUrl = info.firstOf("backdrop_path")?.let { upgradeImage(it) } ?: basic.backdropUrl,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            basic
        }
    }

    // ---------- Live TV

    override suspend fun loadLive(): LiveContent = coroutineScope {
        val categoriesJob = async { runCatching { liveCategories() } }
        val channels = liveChannels()
        LiveContent(categoriesJob.await().getOrDefault(emptyList()), channels)
    }

    private suspend fun liveCategories(): List<LiveCategory> = withContext(Dispatchers.Default) {
        val arr = JSONArray(fetch(url("get_live_categories")))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("category_id")
            val name = o.optString("category_name").trim()
            if (id.isBlank() || name.isEmpty()) null else LiveCategory(id, name)
        }
    }

    private suspend fun liveChannels(): List<LiveChannel> = withContext(Dispatchers.Default) {
        val arr = JSONArray(fetch(url("get_live_streams")))
        val out = ArrayList<LiveChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stream_id")
            val name = cleanChannelName(o.optString("name"))
            if (id.isBlank() || name.isEmpty()) continue
            out.add(
                LiveChannel(
                    id = id,
                    number = o.optInt("num", out.size + 1),
                    name = name,
                    logoUrl = o.str("stream_icon"),
                    categoryId = o.optString("category_id"),
                ),
            )
        }
        out
    }

    /** Current and next programme for one channel, from the server's short guide. */
    override suspend fun shortEpg(channel: LiveChannel, offsetSec: Long): NowNext = withContext(Dispatchers.Default) {
        val root = JSONObject(fetch(url("get_short_epg", "stream_id" to channel.id, "limit" to "3")))
        val list = root.optJSONArray("epg_listings") ?: return@withContext NowNext(null, null)
        val items = (0 until list.length())
            .mapNotNull { list.optJSONObject(it) }
            .map { toProgram(it, offsetSec) }
            .filter { it.title.isNotBlank() }
        val nowSec = System.currentTimeMillis() / 1000
        val current = items.indexOfFirst { p ->
            p.startSec != null && p.endSec != null && p.startSec <= nowSec && nowSec < p.endSec
        }
        if (current >= 0) {
            NowNext(items[current], items.getOrNull(current + 1))
        } else {
            NowNext(items.firstOrNull(), items.getOrNull(1))
        }
    }

    /** Upcoming programmes for one channel (today's guide, from now on). */
    override suspend fun schedule(channel: LiveChannel, offsetSec: Long): List<ProgramInfo> =
        withContext(Dispatchers.Default) {
            val root = JSONObject(fetch(url("get_simple_data_table", "stream_id" to channel.id)))
            val list = root.optJSONArray("epg_listings") ?: return@withContext emptyList()
            val nowSec = System.currentTimeMillis() / 1000
            (0 until list.length())
                .mapNotNull { list.optJSONObject(it) }
                .map { toProgram(it, offsetSec) }
                .filter { it.title.isNotBlank() && (it.endSec == null || it.endSec > nowSec) }
                .sortedBy { it.startSec ?: Long.MAX_VALUE }
                .take(40)
        }

    private fun toProgram(o: JSONObject, offsetSec: Long): ProgramInfo = ProgramInfo(
        title = decodeBase64(o.optString("title")),
        description = decodeBase64(o.optString("description")).takeIf { it.isNotBlank() },
        startSec = o.optString("start_timestamp").toLongOrNull()?.plus(offsetSec),
        endSec = o.optString("stop_timestamp").toLongOrNull()?.plus(offsetSec),
    )

    /** Address of a live stream. Contains the login, so never log it. */
    override fun streamUrl(channel: LiveChannel, extension: String): String =
        "$base/live/${enc(source.username)}/${enc(source.password)}/${channel.id}.$extension"

    private suspend fun newestMovies(): List<MediaItem> = withContext(Dispatchers.Default) {
        val arr = JSONArray(fetch(url("get_vod_streams")))
        val raw = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("stream_icon") != null) raw.add(o)
        }
        raw.sortedByDescending { it.optString("added").toLongOrNull() ?: 0L }
            .take(30)
            .map { o ->
                val name = o.optString("name")
                MediaItem(
                    id = o.optString("stream_id"),
                    type = MediaType.Movie,
                    title = cleanTitle(name),
                    year = yearOf(name, o.optString("year")),
                    rating = ratingOf(o.optString("rating")),
                    plot = null,
                    posterUrl = o.str("stream_icon"),
                    backdropUrl = null,
                )
            }
    }

    private suspend fun newestSeries(): List<MediaItem> = withContext(Dispatchers.Default) {
        val arr = JSONArray(fetch(url("get_series")))
        val raw = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("cover") != null) raw.add(o)
        }
        raw.sortedByDescending { it.optString("last_modified").toLongOrNull() ?: 0L }
            .take(30)
            .map { o ->
                val name = o.optString("name")
                MediaItem(
                    id = o.optString("series_id"),
                    type = MediaType.Series,
                    title = cleanTitle(name),
                    year = o.str("year") ?: yearOf(name, ""),
                    rating = ratingOf(o.optString("rating")),
                    plot = o.str("plot"),
                    posterUrl = o.str("cover"),
                    backdropUrl = o.firstOf("backdrop_path")?.let { upgradeImage(it) },
                )
            }
    }

    private suspend fun withDetails(item: MediaItem): MediaItem = try {
        val info = JSONObject(fetch(url("get_vod_info", "vod_id" to item.id))).optJSONObject("info")
        if (info == null) {
            item
        } else {
            item.copy(
                backdropUrl = info.firstOf("backdrop_path")?.let { upgradeImage(it) },
                plot = info.str("plot") ?: info.str("description"),
                year = item.year ?: info.str("releasedate")?.take(4),
            )
        }
    } catch (e: IOException) {
        item
    } catch (e: JSONException) {
        item
    }
}

/** Plain-language reason for a failed connection. Never includes the address or login. */
fun describeFailure(e: Exception): String = when (e) {
    is UnknownHostException -> "The server address could not be found."
    is SocketTimeoutException -> "The server took too long to respond."
    is ConnectException -> "Could not reach the server. Check the address and port."
    is MalformedURLException -> "The server address isn't valid."
    is SSLException -> "A secure connection could not be made. Try http:// instead of https://."
    is JSONException -> "The server's reply wasn't what Lumen TV expects. Check that this is an Xtream Codes server."
    is IOException -> when {
        e.message?.startsWith("HTTP 401") == true || e.message?.startsWith("HTTP 403") == true ->
            "The server rejected the login."
        else -> "The server returned an error (${e.message ?: "unknown"})."
    }
    else -> "Could not connect to this source."
}

private fun JSONObject.str(key: String): String? =
    optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }

private fun JSONObject.firstOf(key: String): String? = when (val v = opt(key)) {
    is JSONArray -> (0 until v.length()).map { v.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" }
    is String -> v.takeIf { it.isNotBlank() && it != "null" }
    else -> null
}

private val tagPrefix = Regex("""^\s*(\|[^|]{1,8}\||\[[^\]]{1,8}\]|\([A-Z]{1,5}\)|[A-Z]{2,4}\s*[-:|]\s+)\s*""")
private val yearSuffix = Regex("""\s*[(\[]((19|20)\d{2})[)\]]\s*$""")

private fun cleanTitle(raw: String): String {
    var t = raw.trim()
    repeat(3) { t = tagPrefix.replace(t, "") }
    return yearSuffix.replace(t, "").trim().ifEmpty { raw.trim() }
}

private fun yearOf(raw: String, field: String): String? =
    field.takeIf { it.length >= 4 && it.take(4).all(Char::isDigit) }?.take(4)
        ?: yearSuffix.find(raw.trim())?.groupValues?.get(1)

private fun ratingOf(raw: String): String? =
    raw.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(it) }

// Many providers serve TMDB images in a small size. Ask for the full-size version when we can.
private val tmdbImageSize = Regex("""/t/p/w\d+/""")

private fun upgradeImage(url: String): String = tmdbImageSize.replace(url, "/t/p/original/")

private fun decodeBase64(raw: String): String =
    runCatching { String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(raw).trim()

private fun cleanChannelName(raw: String): String {
    var t = raw.trim()
    repeat(3) { t = tagPrefix.replace(t, "") }
    return t.trim().ifEmpty { raw.trim() }
}
