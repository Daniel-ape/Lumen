package com.yourname.lumen.domain.model

data class Source(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
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

data class HomeContent(
    val hero: List<MediaItem>,
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val heroLabel: String = "Recently added",
)

sealed interface HomeState {
    data object Loading : HomeState
    data object NoSource : HomeState
    data object Failed : HomeState
    data class Ready(val content: HomeContent) : HomeState
}
