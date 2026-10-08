package com.yourname.lumen.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.ui.components.ChannelCard
import com.yourname.lumen.ui.components.PosterCard

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        item { HeroSection(items = SampleData.hero) }

        item { SectionTitle("Recently watched channels") }
        item {
            // Vertical content padding leaves room for the focus scale-up so it never clips.
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.RowGap),
            ) {
                items(SampleData.channels) { c ->
                    ChannelCard(name = c.name, program = c.program, progress = c.progress)
                }
            }
        }

        item { SectionTitle("Continue watching movies") }
        item { PosterRow(SampleData.movies) }

        item { SectionTitle("Recently watched series") }
        item { PosterRow(SampleData.series) }
    }
}

@Composable
private fun PosterRow(posters: List<PosterItem>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.RowGap),
    ) {
        items(posters) { p ->
            PosterCard(title = p.title, subtitle = p.subtitle, hue = p.hue)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    LumenText(
        text = text,
        style = LumenTheme.typography.heading,
        modifier = Modifier.padding(
            start = Dimens.ScreenPadding,
            top = Dimens.SectionGap,
            bottom = 4.dp,
        ),
    )
}
