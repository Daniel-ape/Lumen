package com.yourname.lumen.ui.home

/** Placeholder content for Phase 1. Replaced by real repositories in later phases. */
data class HeroItem(
    val label: String,
    val title: String,
    val meta: String,
    val overview: String,
    val hue: Float,
)

data class ChannelItem(val name: String, val program: String, val progress: Float)

data class PosterItem(val title: String, val subtitle: String, val hue: Float)

object SampleData {
    val hero = listOf(
        HeroItem(
            "Trending this week",
            "The Quiet Harbor",
            "2026 · Drama · 2h 04m",
            "A retired lighthouse keeper uncovers a decades-old secret when a storm washes a stranger ashore.",
            218f,
        ),
        HeroItem(
            "Trending this week",
            "Glass Meridian",
            "2026 · Sci-Fi · 1h 52m",
            "Two cartographers chart a city that rearranges itself every night.",
            160f,
        ),
        HeroItem(
            "Trending this week",
            "Northbound",
            "2026 · Adventure · 2h 11m",
            "Four strangers share one truck and one impossible deadline across the Arctic circle.",
            190f,
        ),
    )

    val channels = listOf(
        ChannelItem("Nordlys One", "Evening News", 0.62f),
        ChannelItem("Kuvio Sports", "Match Replay", 0.35f),
        ChannelItem("Harbor Cinema", "The Long Tide", 0.78f),
        ChannelItem("Aalto Kids", "Moon Garden", 0.20f),
        ChannelItem("Fjord Docs", "Deep Ocean", 0.50f),
        ChannelItem("Metro Music", "Sunset Sessions", 0.88f),
    )

    val movies = listOf(
        PosterItem("Paper Skies", "1h 38m", 30f),
        PosterItem("Low Tide", "2h 01m", 200f),
        PosterItem("Orchard", "1h 29m", 100f),
        PosterItem("Saint Elm", "1h 55m", 340f),
        PosterItem("Velvet Hour", "1h 44m", 280f),
        PosterItem("The Last Ferry", "2h 08m", 60f),
    )

    val series = listOf(
        PosterItem("Marrow", "S2 · E4", 250f),
        PosterItem("Slow Fire", "S1 · E7", 20f),
        PosterItem("Kestrel Bay", "S3 · E1", 150f),
        PosterItem("Parallel Lines", "S1 · E3", 320f),
        PosterItem("Winter Sun", "S2 · E9", 190f),
        PosterItem("Tin Roof", "S4 · E2", 80f),
    )
}
