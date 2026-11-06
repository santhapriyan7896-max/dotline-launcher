package com.applauncher.ui.mono

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Mono {
    val Black = Color(0xFF000000)
    val White = Color(0xFFF2F2F2)
    val Grey = Color(0xFF8A8A8A)
    val Dim = Color(0xFF262626)
    val Red = Color(0xFFE5262B)
    val Surface = Color(0xFF141414)

    val Tile = Color(0xFF151515)   // dark widget tiles on the black home screen
    val Sheet = Color(0xFF1B1B1B)  // app drawer background
    val Circle = Color(0xFF2A2A2A) // round icon backgrounds, search bar
    val Paper = Color(0xFFF2F2F2)  // white tiles
    val Ink = Color(0xFF111111)    // text on white tiles

    /** Small, spaced-out capitals: "FRI 06 NOV · 84%". */
    val label = TextStyle(
        color = Grey,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
    )

    val appLarge = TextStyle(color = White, fontSize = 26.sp, fontWeight = FontWeight.Light)
    val app = TextStyle(color = White, fontSize = 19.sp, fontWeight = FontWeight.Light)
}

@Composable
fun MonoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mono.White,
            onPrimary = Mono.Black,
            background = Mono.Black,
            onBackground = Mono.White,
            surface = Mono.Surface,
            onSurface = Mono.White,
            surfaceContainer = Mono.Surface,
        ),
        content = content,
    )
}
