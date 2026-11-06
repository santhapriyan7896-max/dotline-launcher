package com.applauncher.ui.mono

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applauncher.LauncherViewModel
import com.applauncher.ui.Status

val TileShape = RoundedCornerShape(28.dp)

/** A rounded widget tile. */
@Composable
fun Tile(
    modifier: Modifier,
    color: Color,
    shape: Shape = TileShape,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (onClick != null) Modifier.squish(interaction, pressedScale = 0.96f) else Modifier)
            .clip(shape)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, LocalIndication.current, onClick = onClick)
                } else Modifier
            ),
        content = content,
    )
}

/** Big dot-matrix clock. */
@Composable
fun ClockTile(status: Status, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Tile(modifier, Mono.Tile, onClick = onClick) {
        DotMatrixText(status.time, dotSize = 6.dp, gap = 3.dp)
    }
}

/** White tile: FRI / 06 / NOV. */
@Composable
fun DateTile(status: Status, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val small = Mono.label.copy(color = Mono.Ink, fontSize = 11.sp)
    Tile(modifier, Mono.Paper, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status.dayName, style = small)
            Text(
                status.dayOfMonth,
                color = Mono.Ink,
                fontSize = 54.sp,
                lineHeight = 56.sp,
                fontFamily = FontFamily.Serif,
            )
            Text(status.month, style = small)
        }
    }
}

/** Round battery tile with the level in dots. Turns red while charging. */
@Composable
fun BatteryTile(status: Status, modifier: Modifier = Modifier) {
    Tile(modifier, Mono.Tile, shape = CircleShape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DotMatrixText(
                text = status.battery.toString(),
                dotSize = 4.dp,
                gap = 2.dp,
                color = if (status.charging) Mono.Red else Mono.White,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (status.charging) "CHARGING" else "BATTERY",
                style = Mono.label.copy(fontSize = 10.sp, color = if (status.charging) Mono.Red else Mono.Grey),
            )
        }
    }
}

/** Pill tile like "TORCH   OFF". Red when on. */
@Composable
fun ToggleTile(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Tile(modifier, if (on) Mono.Red else Mono.Tile, shape = RoundedCornerShape(50), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Mono.label.copy(color = Mono.White))
            Text(if (on) "ON" else "OFF", style = Mono.label.copy(color = if (on) Mono.White else Mono.Grey))
        }
    }
}

/** What's playing, with previous / play-pause / next. */
@Composable
fun MusicTile(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val hasAccess by vm.music.hasAccess.collectAsStateWithLifecycle()
    val playing by vm.music.nowPlaying.collectAsStateWithLifecycle()
    val np = playing

    val onClick: (() -> Unit)? = when {
        !hasAccess -> vm::openNotificationAccess
        np != null -> vm.music::openApp
        else -> null
    }
    Tile(modifier, Mono.Tile, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(start = 22.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (np == null) {
                    Text("MUSIC", style = Mono.label.copy(color = Mono.White))
                } else {
                    Text(
                        np.title,
                        color = Mono.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        !hasAccess -> "Tap to show what's playing"
                        np == null -> "Nothing playing"
                        np.artist.isNotBlank() -> np.artist
                        else -> np.appLabel
                    },
                    color = Mono.Grey,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (np != null) {
                MediaButton(MediaIcon.Previous, onClick = { vm.music.previous() })
                MediaButton(
                    if (np.playing) MediaIcon.Pause else MediaIcon.Play,
                    background = Mono.Red,
                    onClick = { vm.music.playPause() },
                )
                MediaButton(MediaIcon.Next, onClick = { vm.music.next() })
            }
        }
    }
}

enum class MediaIcon { Previous, Play, Pause, Next }

@Composable
private fun MediaButton(icon: MediaIcon, onClick: () -> Unit, background: Color = Color.Transparent) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(44.dp)
            .squish(interaction, pressedScale = 0.85f)
            .clip(CircleShape)
            .background(background)
            .clickable(interaction, LocalIndication.current) { view.toggleTick(); onClick() },
    ) {
        MediaIconView(icon, Mono.White, 16.dp)
    }
}

/** Simple play / pause / skip shapes, drawn so we don't need an icon library. */
@Composable
private fun MediaIconView(icon: MediaIcon, color: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        fun triangle(left: Float, right: Float, pointRight: Boolean) = Path().apply {
            if (pointRight) {
                moveTo(left, w * 0.1f); lineTo(right, w * 0.5f); lineTo(left, w * 0.9f)
            } else {
                moveTo(right, w * 0.1f); lineTo(left, w * 0.5f); lineTo(right, w * 0.9f)
            }
            close()
        }
        when (icon) {
            MediaIcon.Play -> drawPath(triangle(w * 0.18f, w * 0.92f, true), color)
            MediaIcon.Pause -> {
                drawRect(color, Offset(w * 0.18f, w * 0.1f), Size(w * 0.22f, w * 0.8f))
                drawRect(color, Offset(w * 0.6f, w * 0.1f), Size(w * 0.22f, w * 0.8f))
            }
            MediaIcon.Next -> {
                drawPath(triangle(w * 0.08f, w * 0.72f, true), color)
                drawRect(color, Offset(w * 0.76f, w * 0.1f), Size(w * 0.14f, w * 0.8f))
            }
            MediaIcon.Previous -> {
                drawRect(color, Offset(w * 0.1f, w * 0.1f), Size(w * 0.14f, w * 0.8f))
                drawPath(triangle(w * 0.28f, w * 0.92f, false), color)
            }
        }
    }
}

/** Keeps [Spacer] with a fixed width handy for rows of tiles. */
@Composable
fun Gap(width: Dp = 10.dp) = Spacer(Modifier.width(width))
