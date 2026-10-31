package com.yourname.lumen.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.hueOf
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.domain.model.LiveCategory
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType
import com.yourname.lumen.ui.components.PosterCard

/** One horizontal shelf: a category and every title in it. */
private class Shelf(val key: String, val title: String, val items: List<MediaItem>)

/**
 * The Movies and Series screens. Every category is its own shelf on one page: scroll down to move between
 * categories and sideways to browse the titles in the one you are on, so there is no separate category list.
 * The first shelf is the newest titles. Picking a poster opens the usual details page; coming back returns
 * to the same shelf, position and poster. Everything shown comes from the source.
 */
@Composable
fun LibraryScreen(
    kind: MediaType,
    items: List<MediaItem>,
    categories: List<LiveCategory>,
    loading: Boolean,
    hasSource: Boolean,
    sourceHasLibrary: Boolean,
    showRatings: Boolean,
    listState: LazyListState,
    rowStates: MutableMap<String, LazyListState>,
    restoreItemId: String?,
    onRestored: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onAddSource: () -> Unit,
    dockFocus: FocusRequester,
    contentFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val noun = if (kind == MediaType.Movie) "movies" else "series"
    val title = if (kind == MediaType.Movie) "Movies" else "Series"

    // Shelves: the newest titles first, then each category (in the source's own order), then anything uncategorised.
    val shelves = remember(items, categories) {
        val distinct = items.distinctBy { it.type.name + it.id }
        val byCategory = distinct.groupBy { it.categoryId }
        val known = categories.map { it.id }.toSet()
        buildList {
            if (distinct.isNotEmpty()) add(Shelf("new", "Recently added", distinct.take(40)))
            categories.forEach { c -> byCategory[c.id]?.let { add(Shelf("c" + c.id, c.name, it)) } }
            val rest = distinct.filter { it.categoryId == null || it.categoryId !in known }
            if (rest.isNotEmpty()) add(Shelf("other", "Other", rest))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 64.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LumenText(title, type.title.copy(fontSize = 22.sp))
            LumenText(
                text = if (items.isNotEmpty()) "%,d".format(items.size) else "",
                style = type.label,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }

        when {
            !hasSource -> Message("Add a connection to see your $noun.", "Add connection", onAddSource)
            !sourceHasLibrary -> Message("This source is a playlist, and playlists only carry live channels, so there are no $noun to show.")
            items.isEmpty() && loading -> Message("Loading $noun…")
            items.isEmpty() -> Message("No $noun were found on this source.")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(shelves, key = { _, s -> s.key }) { shelfIndex, shelf ->
                    Column {
                        Row(
                            modifier = Modifier.padding(start = Dimens.ScreenPadding, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            LumenText(shelf.title, type.heading.copy(fontSize = 16.sp), maxLines = 1, modifier = Modifier.widthIn(max = 520.dp))
                            LumenText("%,d".format(shelf.items.size), type.label, color = colors.textTertiary)
                        }
                        val rowState = rowStates.getOrPut(shelf.key) { LazyListState() }
                        LazyRow(
                            state = rowState,
                            // The padding leaves room for the focus scale-up so a focused poster is never cut off.
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(shelf.items, key = { _, m -> m.type.name + m.id }) { index, m ->
                                val restore = remember { FocusRequester() }
                                val isRestoreTarget = restoreItemId != null && m.id == restoreItemId
                                LaunchedEffect(isRestoreTarget) {
                                    if (isRestoreTarget) {
                                        runCatching { restore.requestFocus() }
                                        onRestored()
                                    }
                                }
                                PosterCard(
                                    title = m.title,
                                    subtitle = listOfNotNull(m.year, if (showRatings) m.rating?.let { "★ $it" } else null)
                                        .joinToString(" · "),
                                    hue = hueOf(m.title),
                                    imageUrl = m.posterUrl,
                                    width = 124.dp,
                                    onClick = { onOpen(m) },
                                    modifier = Modifier
                                        .then(
                                            when {
                                                isRestoreTarget -> Modifier.focusRequester(restore)
                                                shelfIndex == 0 && index == 0 -> Modifier.focusRequester(contentFocus)
                                                else -> Modifier
                                            },
                                        )
                                        // UP from the first shelf goes to the menu.
                                        .then(
                                            if (shelfIndex == 0) {
                                                Modifier.onDirection(Key.DirectionUp) { runCatching { dockFocus.requestFocus() } }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        LumenText(
            text = text,
            style = LumenTheme.typography.body.copy(fontSize = 15.sp),
            color = LumenTheme.colors.textSecondary,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        if (actionLabel != null) LumenButton(text = actionLabel, primary = true, onClick = onAction)
    }
}
