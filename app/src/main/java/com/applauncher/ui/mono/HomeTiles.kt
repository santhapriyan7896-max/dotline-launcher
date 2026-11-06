package com.applauncher.ui.mono

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.applauncher.data.TileId

private val RowGap = 10.dp
private val DotsHeight = 22.dp

/** A row is one wide tile, or one or two half-size tiles. */
private fun rowsOf(tiles: List<TileId>): List<List<TileId>> {
    val rows = ArrayList<List<TileId>>()
    var pendingHalf: TileId? = null
    for (tile in tiles) {
        if (tile.wide) {
            pendingHalf?.let { rows += listOf(it); pendingHalf = null }
            rows += listOf(tile)
        } else if (pendingHalf == null) {
            pendingHalf = tile
        } else {
            rows += listOf(pendingHalf!!, tile)
            pendingHalf = null
        }
    }
    pendingHalf?.let { rows += listOf(it) }
    return rows
}

private fun heightOf(row: List<TileId>): Dp = when (row.singleOrNull()) {
    TileId.CLOCK -> 104.dp
    TileId.TOGGLES -> 60.dp
    TileId.MUSIC, TileId.EVENT -> 84.dp
    else -> 132.dp // half-size tiles
}

/** Splits rows into pages that each fit in [available] height. */
private fun pagesOf(rows: List<List<TileId>>, available: Dp): List<List<List<TileId>>> {
    val pages = ArrayList<List<List<TileId>>>()
    var page = ArrayList<List<TileId>>()
    var used = 0.dp
    for (row in rows) {
        val needed = heightOf(row) + if (page.isEmpty()) 0.dp else RowGap
        if (page.isNotEmpty() && used + needed > available) {
            pages += page
            page = ArrayList()
            used = 0.dp
        }
        used += heightOf(row) + if (page.isEmpty()) 0.dp else RowGap
        page += row
    }
    if (page.isNotEmpty()) pages += page
    return pages
}

/**
 * Home tiles laid out in rows and split across pages you swipe left and right.
 * [render] draws one tile into the space it's given.
 */
@Composable
fun TilePages(
    tiles: List<TileId>,
    render: @Composable (TileId, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val pages = remember(tiles, maxHeight) {
            pagesOf(rowsOf(tiles), maxHeight - DotsHeight)
        }
        if (pages.isEmpty()) return@BoxWithConstraints
        val pager = rememberPagerState { pages.size }

        Column(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    pages[index].forEach { row -> TileRow(row, render) }
                }
            }
            if (pages.size > 1) {
                Row(
                    Modifier.fillMaxWidth().height(DotsHeight),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (i == pager.currentPage) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) Mono.Red else Mono.Grey.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TileRow(row: List<TileId>, render: @Composable (TileId, Modifier) -> Unit) {
    val height = heightOf(row)
    if (row.size == 1 && row[0].wide) {
        render(row[0], Modifier.fillMaxWidth().height(height))
        return
    }
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(RowGap)) {
        render(row[0], Modifier.weight(1f).fillMaxHeight())
        if (row.size == 2) render(row[1], Modifier.weight(1f).fillMaxHeight())
        else Spacer(Modifier.weight(1f))
    }
}
