package com.yourname.lumen.domain.model

enum class SourceType(val label: String) {
    XTREAM("Xtream Codes"),
    M3U("M3U playlist"),
    SELF_HOSTED("Self-hosted server"),
}

data class Source(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val lastCheckedAt: Long = 0L,
    val lastError: String? = null,
    val type: SourceType = SourceType.XTREAM,
)

enum class MediaType { Movie, Series }

data class MediaItem(
    val id: String,
    val type: MediaType,
    val title: String,
    val year: String?,
    val rating: String?,
    val plot: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    /** The source's category for this title, used by the Movies and Series screens. */
    val categoryId: String? = null,
    /** Genres as the source lists them (series lists carry them; movie lists do not). */
    val genre: String? = null,
)

/** Everything the details page can show. Fields are null when the source doesn't provide them. */
data class MediaDetails(
    val title: String,
    val type: MediaType,
    val year: String?,
    val rating: String?,
    val plot: String?,
    val genres: String?,
    val director: String?,
    val cast: String?,
    val duration: String?,
    val seasons: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    /** For series: every episode the source lists. */
    val episodes: List<Episode> = emptyList(),
    /** For movies: the file type the source plays it as (mp4, mkv ...). */
    val streamExtension: String? = null,
)

/** One episode of a series, as listed by the source. */
data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String?,
    val imageUrl: String?,
    val extension: String,
    val duration: String?,
)

data class HomeContent(
    val hero: List<MediaItem>,
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val heroLabel: String = "Recently added",
)

sealed interface ConnectionResult {
    data object Connected : ConnectionResult
    data class Failed(val message: String) : ConnectionResult
}

sealed interface HomeState {
    data object Loading : HomeState
    data object NoSource : HomeState
    data class Failed(val message: String) : HomeState
    data class Ready(val content: HomeContent) : HomeState
}

// ---------- Live TV

data class LiveCategory(val id: String, val name: String)

data class LiveChannel(
    val id: String,
    val number: Int,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    /** Direct stream address, for sources (like M3U playlists) that list one per channel. */
    val streamUrl: String? = null,
    /** The id the source's programme guide uses for this channel, when it has one. */
    val epgId: String? = null,
)

data class LiveContent(
    val categories: List<LiveCategory>,
    val channels: List<LiveChannel>,
)

/** One program from the source's guide data. Times are seconds since 1970, or null when unknown. */
data class ProgramInfo(
    val title: String,
    val description: String?,
    val startSec: Long?,
    val endSec: Long?,
)

data class NowNext(val now: ProgramInfo?, val next: ProgramInfo?)

sealed interface LiveState {
    data object Loading : LiveState
    data object NoSource : LiveState
    data class Failed(val message: String) : LiveState
    data class Ready(val content: LiveContent) : LiveState
}

/** Favorites are saved per source, so two providers can't mix up channel ids. */
fun favoriteKey(sourceId: String, channel: LiveChannel): String = "$sourceId:${channel.id}"

/** The saved-list key for a movie or series of one connection. */
fun myListKey(sourceId: String, item: MediaItem): String = "$sourceId|${item.type.name}|${item.id}"

// ---------- Search

/** Every movie and series the source lists, for searching beyond what Home shows. */
data class LibraryContent(
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
)

/** One programme from the source's guide. Times are seconds since 1970. */
data class GuideEntry(
    val epgId: String,
    val title: String,
    val startSec: Long,
    val endSec: Long,
)
