package com.yourname.lumen.data.settings

import android.content.Context
import com.yourname.lumen.domain.model.LiveChannel
import org.json.JSONArray
import org.json.JSONObject

/**
 * The channels the user has actually watched, newest first, kept per connection and across restarts.
 * A channel appears once; watching it again moves it to the front. Each entry keeps just enough of the
 * channel (name, logo, id, category, guide id) to show it and play it again, even before the channel
 * list has loaded.
 */
class RecentChannelsStore(context: Context) {
    private val prefs = context.getSharedPreferences("lumen_recent_channels", Context.MODE_PRIVATE)

    fun load(sourceId: String): List<LiveChannel> = runCatching {
        val arr = JSONArray(prefs.getString(sourceId, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            LiveChannel(
                id = id,
                number = o.optInt("n"),
                name = o.optString("name"),
                logoUrl = o.optString("logo").takeIf { it.isNotEmpty() },
                categoryId = o.optString("cat"),
                streamUrl = o.optString("url").takeIf { it.isNotEmpty() },
                epgId = o.optString("epg").takeIf { it.isNotEmpty() },
            )
        }
    }.getOrDefault(emptyList())

    /** Puts [channel] at the front and returns the new list. */
    fun record(sourceId: String, channel: LiveChannel): List<LiveChannel> {
        val list = (listOf(channel) + load(sourceId).filterNot { it.id == channel.id }).take(MAX)
        val arr = JSONArray()
        list.forEach {
            arr.put(
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
        prefs.edit().putString(sourceId, arr.toString()).apply()
        return list
    }

    private companion object {
        const val MAX = 20
    }
}
