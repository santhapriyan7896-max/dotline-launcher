package com.applauncher.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.squish
import com.applauncher.ui.mono.toggleTick

/** A round button that squishes when pressed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoundButton(
    size: Dp,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .squish(interaction, pressedScale = 0.9f)
            .clip(CircleShape)
            .background(color)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = { view.toggleTick(); onClick() },
                onLongClick = onLongClick,
            ),
    ) { content() }
}

/** Handset icon; [hangUp] tilts it down like a hang-up button. */
@Composable
fun CallIcon(color: Color, size: Dp = 30.dp, hangUp: Boolean = false) {
    Icon(
        Icons.Default.Call,
        contentDescription = if (hangUp) "Hang up" else "Call",
        tint = color,
        modifier = Modifier.size(size).then(if (hangUp) Modifier.rotate(135f) else Modifier),
    )
}

/** Round toggle with a text label, e.g. MUTE / SPEAKER. White when on. */
@Composable
fun LabelButton(label: String, on: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    RoundButton(
        size = 76.dp,
        color = when {
            !enabled -> Mono.Tile.copy(alpha = 0.4f)
            on -> Mono.White
            else -> Mono.Tile
        },
        onClick = { if (enabled) onClick() },
    ) {
        Text(
            label,
            style = Mono.label.copy(
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = when {
                    !enabled -> Mono.Grey.copy(alpha = 0.5f)
                    on -> Mono.Ink
                    else -> Mono.White
                },
            ),
        )
    }
}

private val KEYS = listOf(
    '1' to "", '2' to "ABC", '3' to "DEF",
    '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
    '*' to "", '0' to "+", '#' to "",
)

/** 12-key phone keypad. Long-press 0 types "+". */
@Composable
fun Keypad(onKey: (Char) -> Unit, modifier: Modifier = Modifier, keySize: Dp = 72.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    RoundButton(
                        size = keySize,
                        color = Mono.Tile,
                        onClick = { onKey(digit) },
                        onLongClick = if (digit == '0') ({ onKey('+') }) else null,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(digit.toString(), color = Mono.White, fontSize = 28.sp, textAlign = TextAlign.Center)
                            if (letters.isNotEmpty()) {
                                Text(letters, style = Mono.label.copy(fontSize = 9.sp, letterSpacing = 1.sp))
                            } else {
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun timeAgo(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 1 -> "NOW"
        minutes < 60 -> "${minutes}M AGO"
        minutes < 24 * 60 -> "${minutes / 60}H AGO"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}D AGO"
        else -> java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(time).uppercase()
    }
}
