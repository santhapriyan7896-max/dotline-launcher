package com.applauncher.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applauncher.phone.RoundButton
import com.applauncher.ui.mono.DotFont
import com.applauncher.ui.mono.DotMatrixText
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme

/** Calculator with a dot-matrix answer. */
class CalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { MonoTheme { CalculatorScreen() } }
    }
}

private const val OPERATORS = "+−×÷"

private enum class Kind { Digit, Operator, Equals, Clear }

private data class Key(val label: String, val kind: Kind)

private val KEYS = listOf(
    Key("AC", Kind.Clear), Key("( )", Kind.Operator), Key("%", Kind.Operator), Key("÷", Kind.Operator),
    Key("7", Kind.Digit), Key("8", Kind.Digit), Key("9", Kind.Digit), Key("×", Kind.Operator),
    Key("4", Kind.Digit), Key("5", Kind.Digit), Key("6", Kind.Digit), Key("−", Kind.Operator),
    Key("1", Kind.Digit), Key("2", Kind.Digit), Key("3", Kind.Digit), Key("+", Kind.Operator),
    Key("0", Kind.Digit), Key(".", Kind.Digit), Key("⌫", Kind.Digit), Key("=", Kind.Equals),
)

/** Applies one key press to the expression, avoiding things like "5++" or "1.2.3". */
private fun press(expr: String, key: String): String = when (key) {
    "AC" -> ""
    "⌫" -> expr.dropLast(1)
    "( )" -> {
        val open = expr.count { it == '(' } - expr.count { it == ')' }
        val last = expr.lastOrNull()
        if (open > 0 && last != null && (last.isDigit() || last == ')' || last == '%')) "$expr)"
        else if (last != null && (last.isDigit() || last == ')')) "$expr×(" else "$expr("
    }
    "." -> {
        val number = expr.takeLastWhile { it.isDigit() || it == '.' }
        when {
            '.' in number -> expr
            number.isEmpty() -> expr + "0."
            else -> "$expr."
        }
    }
    "%" -> if (expr.lastOrNull()?.let { it.isDigit() || it == ')' } == true) "$expr%" else expr
    in OPERATORS.map { it.toString() } -> {
        val last = expr.lastOrNull()
        when {
            expr.isEmpty() -> if (key == "−") key else expr
            last == '(' -> if (key == "−") expr + key else expr
            last != null && last in OPERATORS -> expr.dropLast(1) + key // swap the operator
            else -> expr + key
        }
    }
    else -> expr + key // digits
}

@Composable
private fun CalculatorScreen() {
    var expr by rememberSaveable { mutableStateOf("") }
    var justAnswered by rememberSaveable { mutableStateOf(false) }
    // The sum you just worked out, shown above the answer after "=".
    var lastSum by rememberSaveable { mutableStateOf("") }
    val live = remember(expr) { Evaluator.evaluate(expr) }
    var error by remember { mutableStateOf(false) }
    val exprScroll = rememberScrollState()
    LaunchedEffect(expr) { exprScroll.scrollTo(exprScroll.maxValue) }

    fun onKey(key: Key) {
        error = false
        when (key.kind) {
            Kind.Equals -> {
                val answer = Evaluator.evaluate(expr)
                if (answer == null) error = expr.isNotEmpty()
                else if (!justAnswered) { lastSum = expr; expr = answer.replace("-", "−"); justAnswered = true }
            }
            else -> {
                // Typing a digit right after "=" starts a new calculation; an operator continues it.
                val start = if (justAnswered && key.kind == Kind.Digit && key.label != "⌫") "" else expr
                expr = press(start, key.label)
                justAnswered = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Mono.Black).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.weight(1f))
        // Expression (after "=", the sum you worked out, in grey), right-aligned
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                (if (justAnswered) lastSum else expr).ifEmpty { " " },
                color = if (justAnswered) Mono.Grey else Mono.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.horizontalScroll(exprScroll),
            )
        }
        Spacer(Modifier.height(18.dp))
        // Answer in dots, sized to fit the width. After "=" it stays, as the result.
        val shown = when {
            error -> "ERROR"
            justAnswered -> expr.replace("−", "-")
            else -> live?.takeIf { it != expr.replace("−", "-") } ?: ""
        }
        AnswerDots(shown, if (error) Mono.Red else Mono.White)
        Spacer(Modifier.height(28.dp))

        // Keys
        KEYS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    val (bg, fg) = when {
                        key.kind == Kind.Equals -> Mono.Red to Mono.White
                        key.kind == Kind.Clear -> Mono.Circle to Mono.Red
                        key.kind == Kind.Operator -> Mono.White to Mono.Ink
                        else -> Mono.Tile to Mono.White
                    }
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            RoundButton(size = maxWidth, color = bg, onClick = { onKey(key) }) {
                                Text(
                                    key.label, color = fg,
                                    fontSize = if (key.label.length > 1 && key.label != "⌫") 18.sp else 26.sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The answer drawn in dots, shrinking for long numbers so it always fits. */
@Composable
private fun AnswerDots(text: String, color: Color) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterEnd) {
        if (text.isEmpty()) return@BoxWithConstraints
        val columns = text.sumOf { DotFont.glyph(it)[0].length + 1 } - 1
        val pitch = minOf(9.dp, maxWidth / columns, maxHeight / DotFont.ROWS)
        DotMatrixText(
            text = text,
            dotSize = pitch * 0.75f,
            gap = pitch * 0.25f,
            color = color,
            accentChars = emptySet(),
        )
    }
}
