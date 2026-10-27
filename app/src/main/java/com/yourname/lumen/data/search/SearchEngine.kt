package com.yourname.lumen.data.search

import com.yourname.lumen.domain.model.GuideEntry
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.MediaItem
import java.text.Normalizer

/** Makes titles and queries comparable: lower case, no accents, punctuation and spacing ignored. */
object TextMatch {
    private val marks = Regex("\\p{Mn}+")

    fun normalize(text: String): String {
        val plain = marks.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
        val out = StringBuilder(plain.length)
        var lastWasSpace = true
        for (ch in plain) {
            if (ch.isLetterOrDigit()) {
                out.append(ch)
                lastWasSpace = false
            } else if (!lastWasSpace) {
                out.append(' ')
                lastWasSpace = true
            }
        }
        return out.toString().trim()
    }

    /** 100 exact, 80 starts with, 70 starts a word, 60 contains, 40 contains every word; 0 no match. */
    fun score(norm: String, query: String, tokens: List<String>): Int = when {
        norm == query -> 100
        norm.startsWith(query) -> 80
        norm.contains(" $query") -> 70
        norm.contains(query) -> 60
        tokens.size > 1 && tokens.all { norm.contains(it) } -> 40
        else -> 0
    }
}

/** A programme found in the guide, with the channel that carries it. */
data class ProgrammeHit(
    val title: String,
    val channel: LiveChannel,
    val startSec: Long,
    val endSec: Long,
    val isNow: Boolean,
)

data class SearchResults(
    val query: String,
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val channels: List<LiveChannel>,
    val onNow: List<ProgrammeHit>,
    val upcoming: List<ProgrammeHit>,
) {
    val isEmpty: Boolean
        get() = movies.isEmpty() && series.isEmpty() && channels.isEmpty() && onNow.isEmpty() && upcoming.isEmpty()

    companion object {
        val Empty = SearchResults("", emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * Everything that can be searched, prepared once so each search is only a quick scan.
 * Build it away from the main thread; searching a large library is fast but not free.
 */
class SearchIndex(
    movies: List<MediaItem>,
    series: List<MediaItem>,
    channels: List<LiveChannel>,
    guide: List<GuideEntry>,
) {
    private class Entry<T>(val value: T, val norm: String)
    private class GuideRow(val entry: GuideEntry, val norm: String)

    private val movieRows = movies.map { Entry(it, TextMatch.normalize(it.title)) }
    private val seriesRows = series.map { Entry(it, TextMatch.normalize(it.title)) }
    private val channelRows = channels.map { Entry(it, TextMatch.normalize(it.name)) }
    private val guideRows = guide.map { GuideRow(it, TextMatch.normalize(it.title)) }
    private val channelsByGuideId: Map<String, List<LiveChannel>> =
        channels.filter { it.epgId != null }.groupBy { it.epgId!!.lowercase() }

    fun search(rawQuery: String, nowSec: Long, limit: Int = 40): SearchResults {
        val query = TextMatch.normalize(rawQuery)
        if (query.isEmpty()) return SearchResults.Empty
        val tokens = query.split(' ').filter { it.isNotEmpty() }

        fun <T> top(rows: List<Entry<T>>): List<T> {
            val hits = ArrayList<Pair<Int, Entry<T>>>()
            for (row in rows) {
                val score = TextMatch.score(row.norm, query, tokens)
                if (score > 0) hits.add(score to row)
            }
            return hits
                .sortedWith(compareByDescending<Pair<Int, Entry<T>>> { it.first }.thenBy { it.second.norm.length })
                .take(limit)
                .map { it.second.value }
        }

        // Programmes: only ones that haven't finished, one per channel guide id and start time.
        val seen = HashSet<String>()
        val now = ArrayList<Pair<Int, ProgrammeHit>>()
        val later = ArrayList<Pair<Int, ProgrammeHit>>()
        for (row in guideRows) {
            val e = row.entry
            if (e.endSec <= nowSec) continue
            val score = TextMatch.score(row.norm, query, tokens)
            if (score == 0) continue
            val channel = channelsByGuideId[e.epgId]?.firstOrNull() ?: continue
            if (!seen.add("${e.epgId}|${e.startSec}|${row.norm}")) continue
            val airing = e.startSec <= nowSec
            val hit = ProgrammeHit(e.title, channel, e.startSec, e.endSec, airing)
            if (airing) now.add(score to hit) else later.add(score to hit)
        }

        return SearchResults(
            query = rawQuery.trim(),
            movies = top(movieRows),
            series = top(seriesRows),
            channels = top(channelRows),
            onNow = now.sortedWith(compareByDescending<Pair<Int, ProgrammeHit>> { it.first }.thenBy { it.second.endSec })
                .take(30).map { it.second },
            upcoming = later.sortedWith(compareBy<Pair<Int, ProgrammeHit>> { it.second.startSec }.thenByDescending { it.first })
                .take(30).map { it.second },
        )
    }
}
