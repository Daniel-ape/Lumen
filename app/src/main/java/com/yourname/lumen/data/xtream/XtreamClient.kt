package com.yourname.lumen.data.xtream

import com.yourname.lumen.domain.model.HomeContent
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType
import com.yourname.lumen.domain.model.Source
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to an Xtream Codes compatible server. Posters and backdrops come straight from
 * the provider, so what you see is what your own service offers.
 * Never log URLs from here: they contain the username and password.
 */
class XtreamClient(private val source: Source) {

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

    suspend fun loadHome(): HomeContent = coroutineScope {
        val moviesJob = async { runCatching { newestMovies() } }
        val seriesJob = async { runCatching { newestSeries() } }
        val moviesResult = moviesJob.await()
        val seriesResult = seriesJob.await()
        if (moviesResult.isFailure && seriesResult.isFailure) {
            throw moviesResult.exceptionOrNull() ?: IOException("No data")
        }
        val movies = moviesResult.getOrDefault(emptyList())
        val series = seriesResult.getOrDefault(emptyList())

        // Movies only have a small poster in the list, so ask for the big backdrop of the few we feature.
        val heroMovies = movies.take(3).map { async { withDetails(it) } }.awaitAll()
        val heroSeries = (series.filter { it.backdropUrl != null }.ifEmpty { series }).take(2)

        val hero = buildList {
            for (i in 0 until maxOf(heroMovies.size, heroSeries.size)) {
                heroMovies.getOrNull(i)?.let { add(it) }
                heroSeries.getOrNull(i)?.let { add(it) }
            }
        }
        HomeContent(hero = hero, movies = movies.take(20), series = series.take(20))
    }

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
                    backdropUrl = o.firstOf("backdrop_path"),
                )
            }
    }

    private suspend fun withDetails(item: MediaItem): MediaItem = try {
        val info = JSONObject(fetch(url("get_vod_info", "vod_id" to item.id))).optJSONObject("info")
        if (info == null) {
            item
        } else {
            item.copy(
                backdropUrl = info.firstOf("backdrop_path"),
                plot = info.str("plot") ?: info.str("description"),
                year = item.year ?: info.str("releasedate")?.take(4),
            )
        }
    } catch (e: IOException) {
        item
    } catch (e: org.json.JSONException) {
        item
    }
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
