package com.applauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.applauncher.LauncherViewModel
import com.applauncher.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Icon work runs on at most 2 threads so it never competes hard with scrolling. */
@OptIn(ExperimentalCoroutinesApi::class)
val IconDispatcher = Dispatchers.Default.limitedParallelism(2)

/** One icon size everywhere, so each icon is only built once. */
val IconSize = 26.dp

/** App icon, loaded in the background and cached. */
@Composable
fun AppIcon(app: AppEntry, vm: LauncherViewModel, size: Dp, mono: Boolean, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(vm.cachedIcon(app, px, mono), app.key, px, mono) {
        if (value == null) value = withContext(IconDispatcher) { vm.loadIcon(app, px, mono) }
    }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
