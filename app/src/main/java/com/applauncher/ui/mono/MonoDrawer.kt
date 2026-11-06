package com.applauncher.ui.mono

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.applauncher.LauncherViewModel
import com.applauncher.SettingsActivity
import com.applauncher.data.AppEntry
import com.applauncher.data.searchApps
import com.applauncher.ui.rememberHomeRequester

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** App drawer: a dark sheet with a search bar and a 4-column grid of round icons. */
@Composable
fun MonoDrawer(vm: LauncherViewModel, sheet: DrawerSheet, resetKey: Int) {
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    val favoriteKeys by vm.favoriteKeys.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(apps, query) { if (query.isBlank()) apps else searchApps(apps, query) }
    var menuOpen by remember { mutableStateOf(false) }
    val requestHome = rememberHomeRequester(vm)
    val context = LocalContext.current

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()

    val focusManager = LocalFocusManager.current

    // Drawer fully closed: start fresh next time.
    LaunchedEffect(resetKey) {
        query = ""
        keyboard?.hide()
        focusManager.clearFocus()
        gridState.scrollToItem(0)
    }
    LaunchedEffect(query) { gridState.scrollToItem(0) }
    // Scrolling the grid puts the keyboard away.
    LaunchedEffect(gridState.isScrollInProgress) { if (gridState.isScrollInProgress) keyboard?.hide() }

    // At the top of the list, pulling down drags the whole drawer down with your finger.
    val dragSheet = remember(sheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Drawer partly pulled down and finger goes back up: move the drawer, not the list.
                if (source == NestedScrollSource.UserInput && available.y < 0f && sheet.progress.value < 1f) {
                    sheet.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    sheet.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheet.progress.value < 1f) {
                    keyboard?.hide()
                    sheet.settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val headerDrag = rememberDraggableState { dy -> sheet.dragBy(dy) }

    fun launch(app: AppEntry) {
        keyboard?.hide()
        vm.launch(app)
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
            .clip(SheetShape)
            .background(Mono.Sheet)
            .imePadding(),
    ) {
      // Handle + search bar: drag down here to close the drawer.
      Column(
          Modifier.draggable(
              state = headerDrag,
              orientation = Orientation.Vertical,
              onDragStarted = { keyboard?.hide() },
              onDragStopped = { velocity -> sheet.settle(velocity) },
          )
      ) {
        // Handle: tap to close.
        Box(
            Modifier.fillMaxWidth().clickable(onClick = sheet::close).padding(top = 10.dp, bottom = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Mono.Grey.copy(alpha = 0.5f)))
        }

        // Search pill.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(50))
                .background(Mono.Circle)
                .padding(start = 16.dp, end = 6.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Mono.Grey, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Mono.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Mono.Red),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(::launch) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) Text("Search", color = Mono.Grey, fontSize = 16.sp)
                        field()
                    }
                },
            )
            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { if (query.isNotEmpty()) query = "" else menuOpen = true },
                ) {
                    Icon(
                        if (query.isNotEmpty()) Icons.Default.Close else Icons.Default.MoreVert,
                        contentDescription = if (query.isNotEmpty()) "Clear" else "More",
                        tint = Mono.Grey,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Launcher settings") },
                        onClick = {
                            menuOpen = false
                            vm.startIntent(Intent(context, SettingsActivity::class.java))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Choose home app") },
                        onClick = { menuOpen = false; requestHome() },
                    )
                }
            }
        }
      }

        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            modifier = Modifier.weight(1f).nestedScroll(dragSheet),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(results, key = { it.key }) { app ->
                AppCircle(
                    app = app,
                    vm = vm,
                    isFavorite = app.key in favoriteKeys,
                    circleSize = 54.dp,
                    showLabel = true,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (query.isNotBlank()) {
                item(key = "web", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Search the web for “${query.trim()}”  →",
                        style = Mono.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keyboard?.hide(); vm.searchWeb(query.trim()) }
                            .padding(horizontal = 12.dp, vertical = 18.dp),
                    )
                }
            }
        }
    }
}
