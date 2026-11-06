package com.applauncher.ui.mono

import android.app.ActivityOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applauncher.LauncherViewModel
import com.applauncher.data.AppEntry
import com.applauncher.ui.AppIcon
import com.applauncher.ui.IconDispatcher
import com.applauncher.ui.IconSize
import com.applauncher.ui.SymbolIcons
import com.applauncher.system.NotificationStore
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import com.applauncher.data.LauncherSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.produceState
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withContext

/** An app as a white symbol in a dark circle, optionally with its name underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCircle(
    app: AppEntry,
    vm: LauncherViewModel,
    isFavorite: Boolean,
    circleSize: Dp,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    // Handle to this icon's on-screen position, read only when tapped (cheap while scrolling).
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val interaction = remember { MutableInteractionSource() }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(circleSize)
                    .onPlaced { placed[0] = it }
                    .squish(interaction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .background(Mono.Circle)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = {
                            // The app grows out of this icon, like with a built-in launcher.
                            val r = placed[0]?.takeIf { it.isAttached }?.boundsInWindow()
                            if (r == null) {
                                vm.launch(app)
                            } else {
                                val bounds = android.graphics.Rect(
                                    r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt(),
                                )
                                val options = ActivityOptions.makeScaleUpAnimation(
                                    view, bounds.left, bounds.top, bounds.width(), bounds.height(),
                                ).toBundle()
                                vm.launch(app, bounds, options)
                            }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                    ),
            ) {
                AppGlyph(app, vm, settings, circleSize)
            }

            NotificationDot(app, vm, settings.notificationDots, circleSize)

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Remove from home" else "Add to home") },
                    onClick = { menuOpen = false; vm.toggleFavorite(app) },
                )
                DropdownMenuItem(
                    text = { Text("Change icon") },
                    onClick = { menuOpen = false; pickerOpen = true },
                )
                if (!isFavorite) {
                    DropdownMenuItem(
                        text = { Text("Hide app") },
                        onClick = { menuOpen = false; vm.setHidden(app, true) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("App info") },
                    onClick = { menuOpen = false; vm.openAppInfo(app) },
                )
                if (!app.isSystem && !app.isWork) {
                    DropdownMenuItem(
                        text = { Text("Uninstall", color = Mono.Red) },
                        onClick = { menuOpen = false; vm.uninstall(app) },
                    )
                }
            }
        }
        if (pickerOpen) {
            IconPicker(
                app = app,
                current = settings.iconOverrides[app.key],
                onPick = { choice -> pickerOpen = false; vm.setIconOverride(app, choice) },
                onDismiss = { pickerOpen = false },
            )
        }
        if (showLabel && settings.drawerLabels) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Mono.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Small red dot on the circle's top-right edge when the app has notifications.
 * Pops in and out with a little spring.
 */
@Composable
private fun NotificationDot(app: AppEntry, vm: LauncherViewModel, enabled: Boolean, circleSize: Dp) {
    val badged by NotificationStore.badged.collectAsStateWithLifecycle()
    val show = enabled && vm.hasDot(app, badged)
    val scale by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "dot",
    )
    if (scale < 0.01f) return
    val dot = if (circleSize > 56.dp) 13.dp else 12.dp
    val r = circleSize / 2
    // Centre the dot on the circle's edge at 45° (top-right).
    Box(
        Modifier
            .offset(x = r + r * 0.707f - dot / 2, y = r - r * 0.707f - dot / 2)
            .size(dot)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Mono.Black)
            .padding(2.dp)
            .clip(CircleShape)
            .background(Mono.Red)
    )
}

/**
 * What's drawn inside the circle, in this order:
 * your choice for this app > colour icons if Mono is off > the app's own one-colour icon >
 * an automatic symbol for preinstalled apps without one > a cut-out of the app's icon.
 */
@Composable
private fun AppGlyph(app: AppEntry, vm: LauncherViewModel, settings: LauncherSettings, circleSize: Dp) {
    val override = settings.iconOverrides[app.key]
    val symbolName = override?.removePrefix("sym:")?.takeIf { override.startsWith("sym:") }
    when {
        symbolName != null -> SymbolGlyph(symbolName)
        override == "color" || (override == null && !settings.monoIcons) -> ColorGlyph(app, vm, circleSize)
        else -> {
            val auto = remember(app.label, app.isPreinstalled) {
                if (app.isPreinstalled) SymbolIcons.forLabel(app.label) else null
            }
            if (auto == null) {
                AppIcon(app, vm, size = IconSize, mono = true, modifier = Modifier.clip(CircleShape))
            } else {
                // Only use the symbol if the app has no proper one-colour icon of its own.
                val ownMono by produceState<Boolean?>(null, app.key) {
                    value = withContext(IconDispatcher) { vm.hasOwnMonochrome(app) }
                }
                when (ownMono) {
                    false -> SymbolGlyph(auto)
                    true -> AppIcon(app, vm, size = IconSize, mono = true, modifier = Modifier.clip(CircleShape))
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun SymbolGlyph(name: String) {
    SymbolIcons.named(name)?.let {
        Icon(it, contentDescription = null, tint = Mono.White, modifier = Modifier.size(IconSize))
    }
}

/** The app's normal colour icon, filling most of the circle. */
@Composable
private fun ColorGlyph(app: AppEntry, vm: LauncherViewModel, circleSize: Dp) {
    AppIcon(app, vm, size = circleSize * 0.78f, mono = false, modifier = Modifier.clip(CircleShape))
}

/** Pick an icon for one app: automatic, its colour icon, or any symbol. */
@Composable
private fun IconPicker(app: AppEntry, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Mono.Sheet)
                .padding(20.dp),
        ) {
            Text("CHANGE ICON", style = Mono.label.copy(color = Mono.White))
            Spacer(Modifier.height(4.dp))
            Text(app.label, color = Mono.Grey, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerPill("AUTO", selected = current == null) { onPick(null) }
                PickerPill("COLOUR", selected = current == "color") { onPick("color") }
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 340.dp),
            ) {
                items(SymbolIcons.all, key = { it.first }) { (name, vector) ->
                    val selected = current == "sym:$name"
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (selected) Mono.Red else Mono.Circle)
                            .clickable { onPick("sym:$name") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(vector, contentDescription = name, tint = Mono.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = Mono.label.copy(fontSize = 11.sp, color = if (selected) Mono.Ink else Mono.White),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Mono.White else Mono.Circle)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
