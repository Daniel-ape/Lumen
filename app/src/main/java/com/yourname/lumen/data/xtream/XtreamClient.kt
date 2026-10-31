package com.yourname.lumen.data.xtream

import com.yourname.lumen.data.source.SourceClient
import com.yourname.lumen.domain.model.ConnectionResult
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Xml
import com.yourname.lumen.core.diagnostics.Perf
import com.yourname.lumen.domain.model.Episode
import com.yourname.lumen.domain.model.GuideEntry
import com.yourname.lumen.domain.model.LibraryContent
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
import java.io.InputStreamReader
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

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

    /** The request name from an address (like "get_live_categories"), never the login. */
    private fun actionOf(url: String): String =
        Regex("[?&]action=([^&]+)").find(url)?.groupValues?.get(1) ?: "login"

    private suspend fun fetch(url: String): String =
        Perf.time("Request ${actionOf(url)}", { "${it.length / 1024} KB" }) {
            withContext(Dispatchers.IO) {
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
        }

    /** True when the server accepts the username and password. */
    suspend fun authenticate(): Boolean = withContext(Dispatchers.Default) {
        val o = JSONObject(fetch(url()))
        o.optJSONObject("user_info")?.optString("auth") == "1"
    }

    /** Tests the connection and explains a failure in plain words. */
    override suspend fun checkConnection(): ConnectionResult = try {
        if (Perf.time("Connection test") { authenticate() }) {
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
        val heroSeries = (series.filter { it.backdropUrl != null }.ifEmpty { series }).take(5)
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
        val movieCandidates = movies.filter { it.posterUrl != null }.take(5)
            .map { m -> async { withTimeoutOrNull(8_000) { withDetails(m) } ?: m } }.awaitAll()
        val heroMovies = movieCandidates.filter { it.backdropUrl != null }.ifEmpty { movieCandidates }.take(5)

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
                val movieRoot = JSONObject(fetch(url("get_vod_info", "vod_id" to item.id)))
                val info = movieRoot.optJSONObject("info") ?: return@withContext basic
                basic.copy(
                    streamExtension = movieRoot.optJSONObject("movie_data")?.str("container_extension"),
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
                    episodes = parseEpisodes(root),
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

    /** Episodes from a get_series_info reply, whether the server groups them by season key or as lists. */
    private fun parseEpisodes(root: JSONObject): List<Episode> {
        val out = ArrayList<Episode>()
        fun add(o: JSONObject, seasonFallback: Int) {
            val id = o.optString("id").trim()
            if (id.isEmpty()) return
            val info = o.optJSONObject("info")
            out.add(
                Episode(
                    id = id,
                    season = o.optString("season").toIntOrNull() ?: seasonFallback,
                    number = o.optString("episode_num").toIntOrNull() ?: (out.size + 1),
                    title = o.optString("title").trim().ifEmpty { "Episode ${out.size + 1}" },
                    plot = info?.str("plot"),
                    imageUrl = info?.str("movie_image") ?: info?.str("cover_big"),
                    extension = o.optString("container_extension").trim().ifEmpty { "mp4" },
                    duration = info?.str("duration"),
                ),
            )
        }
        val episodes = root.opt("episodes")
        when (episodes) {
            is JSONObject -> episodes.keys().forEach { key ->
                val list = episodes.optJSONArray(key) ?: return@forEach
                for (i in 0 until list.length()) list.optJSONObject(i)?.let { add(it, key.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (s in 0 until episodes.length()) {
                val list = episodes.optJSONArray(s) ?: continue
                for (i in 0 until list.length()) list.optJSONObject(i)?.let { add(it, s + 1) }
            }
        }
        return out.distinctBy { it.id }.sortedWith(compareBy({ it.season }, { it.number }))
    }

    // ---------- Live TV

    override suspend fun loadLive(onProgress: (Int) -> Unit): LiveContent = coroutineScope {
        val categoriesJob = async { runCatching { liveCategories() } }
        val channels = liveChannels(onProgress)
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

    private suspend fun liveChannels(onProgress: (Int) -> Unit): List<LiveChannel> {
        var counter = 0
        return streamList(url("get_live_streams"), channelFields, onProgress = onProgress) { f ->
            counter++
            val id = f.str("stream_id") ?: return@streamList null
            val name = cleanChannelName(f["name"].orEmpty())
            if (name.isEmpty()) return@streamList null
            LiveChannel(
                id = id,
                number = f["num"]?.toIntOrNull() ?: counter,
                name = name,
                logoUrl = f.str("stream_icon"),
                categoryId = f["category_id"].orEmpty(),
                epgId = f.str("epg_channel_id"),
            )
        }.distinctBy { it.id }
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

    /** A channel's programmes: up to three hours already aired, what is on now, and what is coming, as the source lists them. */
    override suspend fun schedule(channel: LiveChannel, offsetSec: Long): List<ProgramInfo> =
        withContext(Dispatchers.Default) {
            val root = JSONObject(fetch(url("get_simple_data_table", "stream_id" to channel.id)))
            val list = root.optJSONArray("epg_listings") ?: return@withContext emptyList()
            val nowSec = System.currentTimeMillis() / 1000
            (0 until list.length())
                .mapNotNull { list.optJSONObject(it) }
                .map { toProgram(it, offsetSec) }
                .filter { it.title.isNotBlank() && (it.endSec == null || it.endSec > nowSec - 3 * 3600L) }
                .sortedBy { it.startSec ?: Long.MAX_VALUE }
                .take(70)
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

    override fun vodUrl(itemId: String, extension: String): String =
        "$base/movie/${enc(source.username)}/${enc(source.password)}/$itemId.$extension"

    override fun episodeUrl(episodeId: String, extension: String): String =
        "$base/series/${enc(source.username)}/${enc(source.password)}/$episodeId.$extension"

    private var moviesAll: List<MediaItem>? = null
    private var seriesAll: List<MediaItem>? = null
    private val moviesLock = Mutex()
    private val seriesLock = Mutex()

    /**
     * Reads a big JSON list straight off the connection, one entry at a time, keeping only the
     * fields in [wanted]. Nothing is ever held as one giant text or object tree, which is what made
     * large libraries slow and memory-hungry to load. [build] turns an entry into what we keep.
     */
    private suspend fun <T> streamList(
        url: String,
        wanted: Set<String>,
        limit: Int = Int.MAX_VALUE,
        readTimeoutMs: Int = 40_000,
        onProgress: (Int) -> Unit = {},
        build: (Map<String, String>) -> T?,
    ): List<T> = withContext(Dispatchers.IO) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val action = actionOf(url)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Lumen/0.1")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            // How long the server took just to start answering, then how long reading the whole list took.
            Perf.record("Server answered $action", android.os.SystemClock.elapsedRealtime() - startedAt)
            val list = JsonReader(InputStreamReader(conn.inputStream, Charsets.UTF_8).buffered(64 * 1024)).use { reader ->
                val out = ArrayList<T>(2048)
                // An empty or error reply isn't a list; treat it as no entries.
                if (reader.peek() != JsonToken.BEGIN_ARRAY) return@use out
                reader.beginArray()
                // With a limit, reading stops as soon as enough entries are in; the rest is never downloaded.
                while (reader.hasNext() && out.size < limit) {
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                        reader.skipValue()
                        continue
                    }
                    val fields = HashMap<String, String>(12)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        if (name in wanted) fields[name] = readScalar(reader) else reader.skipValue()
                    }
                    reader.endObject()
                    build(fields)?.let {
                        out.add(it)
                        if (out.size % 250 == 0) onProgress(out.size)
                    }
                }
                if (!reader.hasNext()) reader.endArray()
                out
            }
            Perf.record("Read $action", android.os.SystemClock.elapsedRealtime() - startedAt, "%,d entries".format(list.size))
            list
        } finally {
            conn.disconnect()
        }
    }

    /** A value as text; for a list (like several backdrops) the first entry. */
    private fun readScalar(reader: JsonReader): String = when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean().toString()
        JsonToken.NULL -> {
            reader.nextNull()
            ""
        }
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            var first = ""
            while (reader.hasNext()) {
                if (first.isEmpty() && reader.peek() == JsonToken.STRING) first = reader.nextString() else reader.skipValue()
            }
            reader.endArray()
            first
        }
        else -> {
            reader.skipValue()
            ""
        }
    }

    private val movieFields = setOf("stream_id", "name", "year", "rating", "stream_icon", "added", "category_id")
    private val seriesFields = setOf("series_id", "name", "year", "rating", "plot", "cover", "backdrop_path", "last_modified", "category_id", "genre")
    private val channelFields = setOf("stream_id", "name", "num", "stream_icon", "category_id", "epg_channel_id")

    private fun movieFrom(f: Map<String, String>): Pair<Long, MediaItem>? {
        val id = f.str("stream_id") ?: return null
        val name = f["name"].orEmpty()
        return (f["added"]?.toLongOrNull() ?: 0L) to MediaItem(
            id = id,
            type = MediaType.Movie,
            title = cleanTitle(name),
            year = yearOf(name, f["year"].orEmpty()),
            rating = ratingOf(f["rating"].orEmpty()),
            plot = null,
            posterUrl = f.str("stream_icon"),
            backdropUrl = null,
            categoryId = f.str("category_id"),
        )
    }

    private fun seriesFrom(f: Map<String, String>): Pair<Long, MediaItem>? {
        val id = f.str("series_id") ?: return null
        val name = f["name"].orEmpty()
        return (f["last_modified"]?.toLongOrNull() ?: 0L) to MediaItem(
            id = id,
            type = MediaType.Series,
            title = cleanTitle(name),
            year = f.str("year") ?: yearOf(name, ""),
            rating = ratingOf(f["rating"].orEmpty()),
            plot = f.str("plot"),
            posterUrl = f.str("cover"),
            backdropUrl = f.str("backdrop_path")?.let { upgradeImage(it) },
            categoryId = f.str("category_id"),
            genre = f.str("genre"),
        )
    }

    /** Movies from the whole list, or from one category (a much smaller request), stopping after [limit]. */
    private suspend fun movieEntries(
        categoryId: String?,
        limit: Int,
        timeoutMs: Int,
        onProgress: (Int) -> Unit = {},
    ): List<Pair<Long, MediaItem>> {
        val params: Array<Pair<String, String>> =
            if (categoryId != null) arrayOf("category_id" to categoryId) else emptyArray()
        return streamList(url("get_vod_streams", *params), movieFields, limit, timeoutMs, onProgress, ::movieFrom)
    }

    private suspend fun seriesEntries(
        categoryId: String?,
        limit: Int,
        timeoutMs: Int,
        onProgress: (Int) -> Unit = {},
    ): List<Pair<Long, MediaItem>> {
        val params: Array<Pair<String, String>> =
            if (categoryId != null) arrayOf("category_id" to categoryId) else emptyArray()
        return streamList(url("get_series", *params), seriesFields, limit, timeoutMs, onProgress, ::seriesFrom)
    }

    /** Every movie, newest first. Fetched once per connection and kept; this is the slow, complete list. */
    private suspend fun allMovies(onProgress: (Int) -> Unit = {}): List<MediaItem> = moviesLock.withLock {
        moviesAll ?: withContext(Dispatchers.IO) {
            movieEntries(null, Int.MAX_VALUE, 120_000, onProgress).sortedByDescending { it.first }.map { it.second }
                .distinctBy { it.id }
        }.also { moviesAll = it }
    }

    /** Every series, newest first. See [allMovies]. */
    private suspend fun allSeries(onProgress: (Int) -> Unit = {}): List<MediaItem> = seriesLock.withLock {
        seriesAll ?: withContext(Dispatchers.IO) {
            seriesEntries(null, Int.MAX_VALUE, 120_000, onProgress).sortedByDescending { it.first }.map { it.second }
                .distinctBy { it.id }
        }.also { seriesAll = it }
    }

    private val preferredWords = Regex("new|recent|latest|trending|popular|202\\d", RegexOption.IGNORE_CASE)
    private val skippedWords = Regex("adult|xxx|18\\+|porn", RegexOption.IGNORE_CASE)

    /** Up to three categories to sample for Home: ones that sound current first, adult ones never. */
    private fun pickCategories(all: List<LiveCategory>): List<LiveCategory> {
        val usable = all.filterNot { skippedWords.containsMatchIn(it.name) }
        val preferred = usable.filter { preferredWords.containsMatchIn(it.name) }
        return (preferred + usable).distinctBy { it.id }.take(3)
    }

    /**
     * A small, quick sample for Home. It asks for a few small categories instead of the whole library,
     * so Home appears in seconds even when the full list holds tens of thousands of titles. (Servers
     * often take a long time just to start answering for the full list.) The full list loads later,
     * in the background, for the Movies, Series and Search screens.
     */
    private suspend fun quickMovies(): List<MediaItem> = coroutineScope {
        val picked = pickCategories(runCatching { loadCategories(MediaType.Movie) }.getOrDefault(emptyList()))
        val parts = if (picked.isEmpty()) {
            listOf(async { movieEntries(null, 150, 25_000) })
        } else {
            picked.map { c -> async { runCatching { movieEntries(c.id, 80, 25_000) }.getOrDefault(emptyList()) } }
        }
        parts.awaitAll().flatten().distinctBy { it.second.id }.sortedByDescending { it.first }.map { it.second }
    }

    private suspend fun quickSeries(): List<MediaItem> = coroutineScope {
        val picked = pickCategories(runCatching { loadCategories(MediaType.Series) }.getOrDefault(emptyList()))
        val parts = if (picked.isEmpty()) {
            listOf(async { seriesEntries(null, 150, 25_000) })
        } else {
            picked.map { c -> async { runCatching { seriesEntries(c.id, 80, 25_000) }.getOrDefault(emptyList()) } }
        }
        parts.awaitAll().flatten().distinctBy { it.second.id }.sortedByDescending { it.first }.map { it.second }
    }

    // Home only features titles that have a picture.
    private suspend fun newestMovies(): List<MediaItem> = quickMovies().filter { it.posterUrl != null }.take(30)

    private suspend fun newestSeries(): List<MediaItem> = quickSeries().filter { it.posterUrl != null }.take(30)

    override suspend fun loadCategories(type: MediaType): List<LiveCategory> = withContext(Dispatchers.Default) {
        val action = if (type == MediaType.Movie) "get_vod_categories" else "get_series_categories"
        val arr = JSONArray(fetch(url(action)))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("category_id")
            val name = o.optString("category_name").trim()
            if (id.isBlank() || name.isEmpty()) null else LiveCategory(id, name)
        }
    }

    override suspend fun loadLibrary(onMovies: (Int) -> Unit, onSeries: (Int) -> Unit): LibraryContent = coroutineScope {
        val movies = async { allMovies(onMovies) }
        val series = async { allSeries(onSeries) }
        LibraryContent(movies = movies.await(), series = series.await())
    }

    /**
     * The programme guide from the server's XMLTV file, kept to the channels we know and to roughly
     * the next day and a half. Read as a stream, so a large file never sits in memory whole.
     * Returns null when the server has no usable guide file.
     */
    override suspend fun loadGuide(channels: List<LiveChannel>, offsetSec: Long): List<GuideEntry>? =
        Perf.time("Programme guide download", { it?.let { list -> "%,d programmes".format(list.size) } }) { loadGuideTimed(channels, offsetSec) }

    private suspend fun loadGuideTimed(channels: List<LiveChannel>, offsetSec: Long): List<GuideEntry>? =
        withContext(Dispatchers.IO) {
            val wanted = channels.mapNotNull { it.epgId?.lowercase() }.toHashSet()
            if (wanted.isEmpty()) return@withContext null
            val nowSec = System.currentTimeMillis() / 1000
            val from = nowSec - 3600
            val to = nowSec + 36 * 3600
            val zoned = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
            val plain = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            fun timeOf(raw: String?): Long? {
                val text = raw?.trim() ?: return null
                return runCatching { zoned.parse(text)?.time }.getOrNull()?.div(1000)
                    ?: runCatching { plain.parse(text.take(14))?.time }.getOrNull()?.div(1000)
            }

            val conn = URL("$base/xmltv.php?username=${enc(source.username)}&password=${enc(source.password)}")
                .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 120_000
                conn.setRequestProperty("User-Agent", "Lumen/0.1")
                if (conn.responseCode !in 200..299) return@withContext null
                val out = ArrayList<GuideEntry>()
                conn.inputStream.buffered().use { input ->
                    val parser = Xml.newPullParser()
                    parser.setInput(input, null)
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT && out.size < 400_000) {
                        if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                            val channel = parser.getAttributeValue(null, "channel")?.lowercase()
                            val start = timeOf(parser.getAttributeValue(null, "start"))
                            val stop = timeOf(parser.getAttributeValue(null, "stop"))
                            var title: String? = null
                            var depth = 1
                            while (depth > 0) {
                                event = parser.next()
                                if (event == XmlPullParser.START_TAG) {
                                    if (parser.name == "title" && title == null) title = parser.nextText().trim() else depth++
                                } else if (event == XmlPullParser.END_TAG) {
                                    depth--
                                } else if (event == XmlPullParser.END_DOCUMENT) {
                                    break
                                }
                            }
                            if (channel != null && channel in wanted && start != null && stop != null &&
                                stop > from && start < to && !title.isNullOrBlank()
                            ) {
                                out.add(GuideEntry(channel, title, start + offsetSec, stop + offsetSec))
                            }
                        }
                        if (event == XmlPullParser.END_DOCUMENT) break
                        event = parser.next()
                    }
                }
                out
            } finally {
                conn.disconnect()
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

private fun Map<String, String>.str(key: String): String? =
    this[key]?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

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
