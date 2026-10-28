package com.yourname.lumen.data.cache

import com.yourname.lumen.domain.model.HomeContent
import com.yourname.lumen.domain.model.LiveCategory
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.LiveContent
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved copy of the last Home content and channel list, so the next start can show real content
 * at once and refresh it in the background. It holds only what the source itself returned.
 * No logins or addresses are stored here. Read and write it off the main thread.
 */
class LibraryCache(private val dir: File) {

    fun saveHome(sourceId: String, content: HomeContent) {
        val json = JSONObject()
            .put("hero", itemsToJson(content.hero))
            .put("movies", itemsToJson(content.movies))
            .put("series", itemsToJson(content.series))
            .put("label", content.heroLabel)
        write(file("home", sourceId), json.toString())
    }

    fun loadHome(sourceId: String): HomeContent? = runCatching {
        val text = read(file("home", sourceId)) ?: return null
        val json = JSONObject(text)
        HomeContent(
            hero = itemsFromJson(json.optJSONArray("hero")),
            movies = itemsFromJson(json.optJSONArray("movies")),
            series = itemsFromJson(json.optJSONArray("series")),
            heroLabel = json.optString("label", "Recently added"),
        )
    }.getOrNull()

    /** How many minutes ago the saved Home copy was written, or null if there is none. */
    fun homeAgeMinutes(sourceId: String): Long? {
        val target = file("home", sourceId)
        return if (target.exists()) (System.currentTimeMillis() - target.lastModified()) / 60_000L else null
    }

    fun saveLive(sourceId: String, content: LiveContent) {
        val categories = JSONArray()
        content.categories.forEach { categories.put(JSONObject().put("id", it.id).put("name", it.name)) }
        val channels = JSONArray()
        content.channels.forEach {
            channels.put(
                JSONObject()
                    .put("id", it.id)
                    .put("n", it.number)
                    .put("name", it.name)
                    .put("logo", it.logoUrl ?: "")
                    .put("cat", it.categoryId)
                    .put("url", it.streamUrl ?: "")
                    .put("epg", it.epgId ?: ""),
            )
        }
        write(file("live", sourceId), JSONObject().put("categories", categories).put("channels", channels).toString())
    }

    /** The saved channel list, and how many minutes old it is, or null if there is none. */
    fun loadLive(sourceId: String): Pair<LiveContent, Long>? = runCatching {
        val target = file("live", sourceId)
        val text = read(target) ?: return null
        val json = JSONObject(text)
        val categories = json.optJSONArray("categories") ?: JSONArray()
        val channels = json.optJSONArray("channels") ?: JSONArray()
        val content = LiveContent(
            categories = (0 until categories.length()).mapNotNull { i ->
                categories.optJSONObject(i)?.let { LiveCategory(it.optString("id"), it.optString("name")) }
            },
            channels = (0 until channels.length()).mapNotNull { i ->
                channels.optJSONObject(i)?.let {
                    LiveChannel(
                        id = it.optString("id"),
                        number = it.optInt("n"),
                        name = it.optString("name"),
                        logoUrl = it.optString("logo").takeIf { s -> s.isNotEmpty() },
                        categoryId = it.optString("cat"),
                        streamUrl = it.optString("url").takeIf { s -> s.isNotEmpty() },
                        epgId = it.optString("epg").takeIf { s -> s.isNotEmpty() },
                    )
                }
            },
        )
        content to ((System.currentTimeMillis() - target.lastModified()) / 60_000L)
    }.getOrNull()

    fun saveLibrary(sourceId: String, content: com.yourname.lumen.domain.model.LibraryContent) {
        val json = JSONObject()
            .put("movies", itemsToJson(content.movies))
            .put("series", itemsToJson(content.series))
        write(file("library", sourceId), json.toString())
    }

    /** The saved movie and series list, and how many minutes old it is, or null if there is none. */
    fun loadLibrary(sourceId: String): Pair<com.yourname.lumen.domain.model.LibraryContent, Long>? = runCatching {
        val target = file("library", sourceId)
        val text = read(target) ?: return null
        val json = JSONObject(text)
        com.yourname.lumen.domain.model.LibraryContent(
            movies = itemsFromJson(json.optJSONArray("movies")),
            series = itemsFromJson(json.optJSONArray("series")),
        ) to ((System.currentTimeMillis() - target.lastModified()) / 60_000L)
    }.getOrNull()

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun file(kind: String, sourceId: String) = File(dir, "$kind-${sourceId.take(36)}.json")

    private fun write(target: File, text: String) {
        dir.mkdirs()
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(text)
        // Write to a temporary file first, so a half-written file never replaces a good one.
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    private fun read(target: File): String? = if (target.exists()) target.readText() else null

    private fun itemsToJson(items: List<MediaItem>): JSONArray {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("type", it.type.name)
                    .put("title", it.title)
                    .put("year", it.year ?: "")
                    .put("rating", it.rating ?: "")
                    .put("plot", it.plot ?: "")
                    .put("poster", it.posterUrl ?: "")
                    .put("backdrop", it.backdropUrl ?: ""),
            )
        }
        return arr
    }

    private fun itemsFromJson(arr: JSONArray?): List<MediaItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MediaItem(
                id = o.optString("id"),
                type = runCatching { MediaType.valueOf(o.optString("type")) }.getOrDefault(MediaType.Movie),
                title = o.optString("title"),
                year = o.optString("year").takeIf { it.isNotEmpty() },
                rating = o.optString("rating").takeIf { it.isNotEmpty() },
                plot = o.optString("plot").takeIf { it.isNotEmpty() },
                posterUrl = o.optString("poster").takeIf { it.isNotEmpty() },
                backdropUrl = o.optString("backdrop").takeIf { it.isNotEmpty() },
            )
        }
    }
}
