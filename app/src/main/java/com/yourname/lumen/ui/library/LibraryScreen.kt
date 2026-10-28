package com.yourname.lumen.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.GlassLevel
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.core.designsystem.hueOf
import com.yourname.lumen.core.designsystem.onDirection
import com.yourname.lumen.core.designsystem.routeKey
import com.yourname.lumen.core.designsystem.tvFocusable
import com.yourname.lumen.domain.model.LiveCategory
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.MediaType
import com.yourname.lumen.ui.components.PosterCard

private val SidebarColor = Color(0xB3090A0C)
private val SortLabels = listOf("Newest", "A–Z", "Top rated")

private class Tab(val id: String?, val name: String, val count: Int)

/**
 * The Movies and Series screens: categories in a darker strip on the left, and every title of the
 * chosen category as a poster grid. Picking a poster opens the usual details page; coming back returns
 * to the same category, scroll position and poster. Everything shown comes from the source.
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
    gridState: LazyGridState,
    categoryIndex: Int,
    onCategoryIndex: (Int) -> Unit,
    sortMode: Int,
    onSortMode: (Int) -> Unit,
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

    // Category list: "All", then every category that actually holds titles.
    val tabs = remember(items, categories) {
        val counts = items.groupingBy { it.categoryId }.eachCount()
        buildList {
            add(Tab(null, "All", items.size))
            categories.forEach { c ->
                val n = counts[c.id] ?: 0
                if (n > 0) add(Tab(c.id, c.name, n))
            }
        }
    }
    val tabIndex = categoryIndex.coerceIn(0, tabs.lastIndex)
    val tab = tabs[tabIndex]

    val shown = remember(items, tab.id, sortMode) {
        val base = if (tab.id == null) items else items.filter { it.categoryId == tab.id }
        when (sortMode) {
            1 -> base.sortedBy { it.title.lowercase() }
            2 -> base.sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
            else -> base
        }
    }

    Row(modifier = modifier.fillMaxSize().padding(top = 64.dp)) {
        // ---- categories: a flat, darker strip
        Box(modifier = Modifier.width(196.dp).fillMaxHeight().background(SidebarColor)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(tabs, key = { _, t -> t.id ?: "all" }) { i, t ->
                    CategoryRow(
                        tab = t,
                        selected = i == tabIndex,
                        onFocused = { onCategoryIndex(i) },
                        modifier = Modifier
                            .routeKey(Key.DirectionRight, contentFocus)
                            .then(if (i == 0) Modifier.onDirection(Key.DirectionUp) { runCatching { dockFocus.requestFocus() } } else Modifier),
                    )
                }
            }
        }

        // ---- title, sort, and the grid
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = Dimens.ScreenPadding, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LumenText(title, type.title.copy(fontSize = 22.sp))
                LumenText(
                    text = if (tab.id == null) "${shown.size}" else "${tab.name} · ${shown.size}",
                    style = type.label,
                    color = colors.textTertiary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (shown.isNotEmpty()) {
                    LumenButton(
                        text = "Sort: ${SortLabels[sortMode.coerceIn(0, SortLabels.lastIndex)]}",
                        compact = true,
                        onClick = { onSortMode((sortMode + 1) % SortLabels.size) },
                    )
                }
            }

            when {
                !hasSource -> Message("Add a connection to see your $noun.", "Add connection", onAddSource)
                !sourceHasLibrary -> Message("This source is a playlist, and playlists only carry live channels, so there are no $noun to show.")
                items.isEmpty() && loading -> Message("Loading $noun…")
                items.isEmpty() -> Message("No $noun were found on this source.")
                shown.isEmpty() -> Message("Nothing in this category.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    // The padding leaves room for the focus scale-up, so a focused poster is never cut off.
                    contentPadding = PaddingValues(start = 16.dp, end = Dimens.ScreenPadding, top = 14.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    gridItemsIndexed(shown, key = { _, m -> m.type.name + m.id }) { index, m ->
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
                            fillWidth = true,
                            onClick = { onOpen(m) },
                            modifier = when {
                                isRestoreTarget -> Modifier.focusRequester(restore)
                                index == 0 -> Modifier.focusRequester(contentFocus)
                                else -> Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(tab: Tab, selected: Boolean, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LumenTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                onClick = onFocused,
                shape = shape,
                focusedScale = 1.02f,
                showOutline = false,
                onFocusChange = {
                    focused = it
                    if (it) onFocused()
                },
            )
            .then(
                when {
                    focused -> Modifier.glass(shape, GlassLevel.Selected)
                    selected -> Modifier.background(Color(0x1FFFFFFF), shape)
                    else -> Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LumenText(
            text = tab.name,
            style = LumenTheme.typography.body.copy(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected || focused) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        LumenText(tab.count.toString(), LumenTheme.typography.label.copy(fontSize = 11.sp), color = colors.textTertiary)
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
        Spacer(Modifier.width(0.dp))
    }
}
