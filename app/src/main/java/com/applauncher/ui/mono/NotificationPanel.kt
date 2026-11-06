package com.applauncher.ui.mono

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applauncher.LauncherViewModel
import com.applauncher.system.NotificationItem
import com.applauncher.system.NotificationStore
import com.applauncher.ui.IconDispatcher
import com.applauncher.ui.Status
import kotlinx.coroutines.withContext

/**
 * Nothing-style notification panel that slides down from the top of the home screen.
 * [sheet] progress: 0 = hidden, 1 = fully down.
 */
@Composable
fun NotificationPanel(vm: LauncherViewModel, sheet: DrawerSheet, status: Status) {
    val context = LocalContext.current
    val view = LocalView.current
    val items by NotificationStore.items.collectAsStateWithLifecycle()
    val connected by NotificationStore.connected.collectAsStateWithLifecycle()
    val dnd by NotificationStore.doNotDisturb.collectAsStateWithLifecycle()
    val torchOn by vm.torch.on.collectAsStateWithLifecycle()
    val wifiOn by vm.wifiOn.collectAsStateWithLifecycle()
    val bluetoothOn by vm.bluetoothOn.collectAsStateWithLifecycle()

    // Each time the panel starts coming down, re-check Wi-Fi and Bluetooth (they may have changed elsewhere).
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.progress.value > 0f }.distinctUntilChanged().collect { showing ->
            if (showing) vm.refreshRadios()
        }
    }

    // The panel comes down from the top, so finger directions are the reverse of the drawer's.
    val headerDrag = rememberDraggableState { dy -> sheet.dragBy(-dy) }
    val listDrag = remember(sheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Panel partly pushed up and finger comes back down: move the panel, not the list.
                if (source == NestedScrollSource.UserInput && available.y > 0f && sheet.progress.value < 1f) {
                    sheet.dragBy(-available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Pushing up past the end of the list closes the panel.
                if (source == NestedScrollSource.UserInput && available.y < 0f) {
                    sheet.dragBy(-available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheet.progress.value < 1f) {
                    sheet.settle(-available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mono.Black)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // Header + toggles: drag up here to close.
        Column(
            Modifier
                .draggable(
                    state = headerDrag,
                    orientation = Orientation.Vertical,
                    onDragStopped = { v -> sheet.settle(-v) },
                )
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp, start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    DotMatrixText(status.time, dotSize = 4.dp, gap = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(status.date, style = Mono.label)
                }
                PillButton("SYSTEM") { sheet.closeNow(); vm.openNotifications() }
                Spacer(Modifier.width(8.dp))
                PillButton("SETTINGS") { vm.openSettings() }
            }

            Row(Modifier.fillMaxWidth().height(56.dp)) {
                ToggleTile("TORCH", torchOn, onClick = { view.toggleTick(); vm.torch.toggle() }, modifier = Modifier.weight(1f).fillMaxHeight())
                Gap()
                ToggleTile("WI-FI", wifiOn, onClick = vm::openWifiPanel, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(56.dp)) {
                ToggleTile("BLUETOOTH", bluetoothOn, onClick = vm::openBluetoothSettings, modifier = Modifier.weight(1f).fillMaxHeight())
                Gap()
                ToggleTile(
                    "SILENT", dnd,
                    onClick = {
                        view.toggleTick()
                        if (connected) NotificationStore.setDoNotDisturb(!dnd) else vm.openNotificationAccess()
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, top = 24.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (items.isEmpty()) "NOTIFICATIONS" else "NOTIFICATIONS  ${items.size}",
                    style = Mono.label,
                    modifier = Modifier.weight(1f),
                )
                if (items.any { it.clearable }) {
                    Text(
                        "CLEAR ALL",
                        style = Mono.label.copy(color = Mono.Red),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { view.toggleTick(); NotificationStore.dismissAll() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !connected -> EmptyMessage("Allow notification access to see them here") { vm.openNotificationAccess() }
                items.isEmpty() -> EmptyMessage("No notifications", onClick = null)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(listDrag),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.key }) { item ->
                        NotificationCard(item, onOpen = {
                            sheet.closeNow()
                            NotificationStore.open(context, item)
                        })
                    }
                }
            }
        }

        // Handle: tap or drag up to close.
        Box(
            Modifier
                .fillMaxWidth()
                .draggable(headerDrag, Orientation.Vertical, onDragStopped = { v -> sheet.settle(-v) })
                .clickable(onClick = sheet::close)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Mono.Grey.copy(alpha = 0.5f)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(item: NotificationItem, onOpen: () -> Unit) {
    val view = LocalView.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                view.snapTick()
                NotificationStore.dismiss(item.key)
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = item.clearable,
        enableDismissFromEndToStart = item.clearable,
        backgroundContent = {},
    ) {
        Tile(Modifier.fillMaxWidth(), Mono.Tile, shape = RoundedCornerShape(24.dp), onClick = onOpen) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Mono.Circle),
                    contentAlignment = Alignment.Center,
                ) {
                    NotificationIcon(item)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.appLabel.uppercase(),
                            style = Mono.label.copy(fontSize = 10.sp, letterSpacing = 1.5.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("  ·  ${timeAgo(item.time)}", style = Mono.label.copy(fontSize = 10.sp, letterSpacing = 1.sp))
                    }
                    if (item.title.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(item.title, color = Mono.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.text.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(item.text, color = Mono.Grey, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The app's own small notification icon, drawn white. */
@Composable
private fun NotificationIcon(item: NotificationItem) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { 18.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, item.key, item.smallIcon) {
        value = withContext(IconDispatcher) {
            runCatching {
                val drawable = item.smallIcon?.loadDrawable(context) ?: return@runCatching null
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, px, px)
                drawable.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
                drawable.draw(Canvas(bmp))
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = Mono.label.copy(color = Mono.White, fontSize = 10.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Mono.Tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun EmptyMessage(text: String, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Mono.Grey, fontSize = 14.sp)
    }
}

private fun timeAgo(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 1 -> "NOW"
        minutes < 60 -> "${minutes}M"
        minutes < 24 * 60 -> "${minutes / 60}H"
        else -> "${minutes / (24 * 60)}D"
    }
}
