package com.applauncher.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Works out expressions like "12×(3+4)−5%" with exact decimal maths.
 * Supports + − × ÷, brackets, % (divides by 100) and a leading minus.
 */
object Evaluator {

    private val MC = MathContext.DECIMAL64

    /** The answer as text, or null if the expression isn't finished / doesn't make sense. */
    fun evaluate(expression: String): String? {
        if (expression.isBlank()) return null
        val value = runCatching { parse(expression) }.getOrNull() ?: return null
        return format(value)
    }

    fun format(value: BigDecimal): String {
        val abs = value.abs()
        if (abs.signum() != 0 && (abs >= BigDecimal("1e12") || abs < BigDecimal("1e-6"))) {
            return String.format(java.util.Locale.US, "%.5e", value.toDouble()).uppercase()
        }
        return value.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    // --- A small recursive parser: sum -> product -> unary -> postfix % -> number / brackets ---

    private class Parser(val s: String) {
        var i = 0
        fun peek(): Char? = s.getOrNull(i)
        fun next(): Char = s[i++]
    }

    private fun parse(expression: String): BigDecimal {
        // Allow a missing closing bracket at the end: "2×(3+4" -> "2×(3+4)".
        val open = expression.count { it == '(' } - expression.count { it == ')' }
        val p = Parser(expression + ")".repeat(open.coerceAtLeast(0)))
        val result = sum(p)
        require(p.i == p.s.length)
        return result
    }

    private fun sum(p: Parser): BigDecimal {
        var v = product(p)
        while (true) {
            v = when (p.peek()) {
                '+' -> { p.next(); v.add(product(p), MC) }
                '−', '-' -> { p.next(); v.subtract(product(p), MC) }
                else -> return v
            }
        }
    }

    private fun product(p: Parser): BigDecimal {
        var v = unary(p)
        while (true) {
            v = when (p.peek()) {
                '×', '*' -> { p.next(); v.multiply(unary(p), MC) }
                '÷', '/' -> {
                    p.next()
                    val d = unary(p)
                    require(d.signum() != 0) { "divide by zero" }
                    v.divide(d, MC)
                }
                else -> return v
            }
        }
    }

    private fun unary(p: Parser): BigDecimal =
        if (p.peek() == '−' || p.peek() == '-') { p.next(); unary(p).negate() } else postfix(p)

    private fun postfix(p: Parser): BigDecimal {
        var v = atom(p)
        while (p.peek() == '%') { p.next(); v = v.divide(BigDecimal(100), MC) }
        return v
    }

    private fun atom(p: Parser): BigDecimal {
        if (p.peek() == '(') {
            p.next()
            val v = sum(p)
            require(p.peek() == ')')
            p.next()
            return v
        }
        val start = p.i
        while (p.peek()?.let { it.isDigit() || it == '.' } == true) p.next()
        require(p.i > start)
        return BigDecimal(p.s.substring(start, p.i))
    }
}
