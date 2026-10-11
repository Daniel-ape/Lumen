package com.yourname.lumen.domain.model

data class Source(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val lastCheckedAt: Long = 0L,
    val lastError: String? = null,
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
