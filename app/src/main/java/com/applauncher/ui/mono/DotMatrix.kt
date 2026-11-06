package com.applauncher.ui.mono

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 5x7 dot font: digits, A–Z and a few symbols. '1' = lit dot. Used by the clock and the lock wallpaper. */
object DotFont {
    const val ROWS = 7

    /** The glyph for [c] (letters are upper-cased); unknown characters become a space. */
    fun glyph(c: Char): List<String> = GLYPHS[c.uppercaseChar()] ?: GLYPHS.getValue(' ')

    fun has(c: Char): Boolean = c.uppercaseChar() in GLYPHS
}

private val GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    ':' to listOf("0", "0", "1", "0", "1", "0", "0"),
    '°' to listOf("111", "101", "111", "000", "000", "000", "000"),
    '-' to listOf("000", "000", "000", "111", "000", "000", "000"),
    ',' to listOf("0", "0", "0", "0", "0", "1", "1"),
    '.' to listOf("0", "0", "0", "0", "0", "0", "1"),
    '!' to listOf("1", "1", "1", "1", "1", "0", "1"),
    ' ' to listOf("000", "000", "000", "000", "000", "000", "000"),
    'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'C' to listOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
    'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'G' to listOf("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
    'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to listOf("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
    'J' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
    'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to listOf("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
    'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'W' to listOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
    'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
)

private const val ROWS = DotFont.ROWS

/**
 * Draws text as a grid of round dots, like an LED sign.
 * Unlit dots are drawn very faintly so the grid is just visible.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    dotSize: Dp = 7.dp,
    gap: Dp = 3.dp,
    color: Color = Mono.White,
    accentColor: Color = Mono.Red,
    accentChars: Set<Char> = setOf(':'),
    unlitColor: Color = Color.White.copy(alpha = 0.06f),
    /**
     * Optional 0..1 animation: dots light up column by column from the left.
     * Read while drawing only, so animating it doesn't rebuild the layout.
     */
    reveal: (() -> Float)? = null,
) {
    val glyphs = text.map { it to DotFont.glyph(it) }
    val columns = glyphs.sumOf { it.second[0].length } + (glyphs.size - 1).coerceAtLeast(0)
    val pitch = dotSize + gap
    val width = pitch * columns - gap
    val height = pitch * ROWS - gap

    Canvas(modifier.size(width, height)) {
        val p = pitch.toPx()
        val r = dotSize.toPx() / 2f
        val progress = reveal?.invoke()
        var col = 0
        glyphs.forEach { (char, rows) ->
            val lit = if (char in accentChars) accentColor else color
            val glyphWidth = rows[0].length
            for (x in 0 until glyphWidth) {
                // Each column fades in a little after the one to its left.
                val fade = if (progress == null) 1f
                else ((progress * (columns + 4) - (col + x)) / 4f).coerceIn(0f, 1f)
                if (fade == 0f) continue
                for (y in 0 until ROWS) {
                    val on = rows[y][x] == '1'
                    val c = if (on) lit else unlitColor
                    drawCircle(
                        color = if (fade < 1f) c.copy(alpha = c.alpha * fade) else c,
                        radius = if (on && fade < 1f) r * (0.6f + 0.4f * fade) else r,
                        center = Offset((col + x) * p + r, y * p + r),
                    )
                }
            }
            col += glyphWidth + 1
        }
    }
}
