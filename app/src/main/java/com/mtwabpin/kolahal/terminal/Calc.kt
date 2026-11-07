package com.mtwabpin.kolahal.terminal

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The arithmetic of the `=` calculator. It runs at once on the phone, and the result shows while you type.
 *
 * Supported:
 * - `+ - * /` (also `−`, `×`, `x`, `÷`), `^` or `**`, `mod`, `n!`, parentheses;
 * - `pi` (`π`) and `e`;
 * - sqrt, cbrt, abs, round, floor, ceil, ln, log (base 10), log2, exp, min and max;
 * - sin, cos, tan, asin, acos, atan, in radians. `30°` is in degrees.
 *
 * Percent works as on a phone's calculator:
 * - `15%` is 0.15.
 * - `80 + 15%` adds 15% of 80.
 * - `15% of 80` is 12.
 * - `10% - 3` and `10%-3` are -2.9.
 * - `%` before a number is the remainder (`10 % 3`).
 * - `%` before a signed number is also the remainder, if a space separates it from the number before it (`10 % -3`).
 *
 * Other rules:
 * - Values side by side multiply: `2pi`, `3(4+5)`, `3x4`.
 * - Commas in a number are read as in [Units.number] (`1,500`, `0,5`). Inside a function's
 *   parentheses, commas separate values instead (`max(1,500)`).
 * - Anything else (Lua code) is left to Lua.
 */
object Calc {
    /**
     * Returns the answer to [text], or null when [text] is not arithmetic that this parser reads.
     * Throws [ArithmeticException] when the arithmetic has no answer.
     */
    fun eval(text: String): Double? {
        val tokens = tokenize(text) ?: return null
        if (tokens.isEmpty()) return null
        val parser = Parser(tokens)
        val result = runCatching { parser.expression() }.getOrElse { e -> if (e is ArithmeticException) throw e else return null }
        if (!parser.done()) return null
        val v = result.value
        if (v.isNaN()) throw ArithmeticException("not a real number")
        if (v.isInfinite()) throw ArithmeticException("too big")
        return v
    }

    /** The result of [eval], formatted for the log: `1024`, `1.41421356237`, `1.5e+20`. */
    fun answer(text: String): String? = eval(text)?.let(::format)

    fun format(v: Double): String {
        if (v == 0.0) return "0"
        if (abs(v) !in 1e-9..<1e15) return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toString().replace("E", "e")
        return BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private sealed interface Token
    private data class Num(val v: Double) : Token
    private data class Word(val w: String) : Token
    private data class Op(val c: String) : Token

    /**
     * For `3x4`: returns true when [word] starts with a times sign joined to the number after it,
     * and a value comes before it. (`2x3x4` is read as one word.)
     * But `0x10`, written without spaces, is hexadecimal, so it is left for Lua.
     */
    private fun timesThenDigits(word: String, before: List<Token>, text: String, at: Int): Boolean {
        if (!TIMES_THEN_DIGITS.matches(word)) return false
        val last = before.lastOrNull()
        return !(last is Num && last.v == 0.0 && text.getOrNull(at - 1) == '0') && (last is Num || last in ENDS_A_VALUE)
    }
    private val TIMES_THEN_DIGITS = Regex("(x\\d+)+")
    private val ENDS_A_VALUE = setOf(Op(")"), Op("!"), Op("%"), Op("°"), Word("pi"), Word("e"))

    /**
     * For `10 % -3`: the `%` at [at] is the remainder when a space separates it from the number
     * before it, and a sign is joined to the number after it.
     * `10%-3`, `10% -3` and `10 % - 3` are a percent, then a sum.
     */
    private fun remainderBeforeSign(text: String, at: Int) =
        text.getOrNull(at - 1)?.isWhitespace() == true && SIGNED_NUMBER.matchesAt(text, at + 1)
    private val SIGNED_NUMBER = Regex("""\s*[-+−][\d.(]""")

    private fun tokenize(text: String): List<Token>? {
        val out = ArrayList<Token>()
        // The depth inside a function's parentheses. There, a comma separates values.
        var inFunction = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    var j = i
                    // A comma between digits belongs to the number (`1,500`), except in a function's parentheses.
                    fun commaInNumber() = text[j] == ',' && inFunction == 0 && text.getOrNull(j + 1)?.isDigit() == true
                    while (j < text.length && (text[j].isDigit() || text[j] == '.' || commaInNumber())) j++
                    // `e` starts an exponent only when digits follow. `2e` is 2 × e.
                    if (j < text.length && (text[j] == 'e' || text[j] == 'E')) {
                        val k = if (j + 1 < text.length && (text[j + 1] == '+' || text[j + 1] == '-')) j + 2 else j + 1
                        if (k < text.length && text[k].isDigit()) {
                            j = k
                            while (j < text.length && text[j].isDigit()) j++
                        }
                    }
                    out += Num(Units.number(text.substring(i, j)) ?: return null)
                    i = j
                }
                c.isLetter() && c != 'π' -> {
                    var j = i
                    while (j < text.length && text[j].isLetterOrDigit() && text[j] != 'π') j++
                    val word = text.substring(i, j).lowercase()
                    if (timesThenDigits(word, out, text, i)) {
                        out += Word("x")
                        i++
                    } else {
                        out += Word(word)
                        i = j
                    }
                }
                c == 'π' -> { out += Word("pi"); i++ }
                c == '%' && remainderBeforeSign(text, i) -> { out += Word("mod"); i++ }
                c == '*' && text.getOrNull(i + 1) == '*' -> { out += Op("^"); i += 2 }
                else -> {
                    val op = when (c) {
                        '+' -> "+"
                        '-', '−' -> "-"
                        '*', '×', '·' -> "*"
                        '/', '÷' -> "/"
                        '^' -> "^"
                        '(', ')', '!', '%', '°', ',' -> c.toString()
                        else -> return null
                    }
                    if (c == '(' && (inFunction > 0 || (out.lastOrNull() as? Word)?.w in FUNCTIONS)) inFunction++
                    if (c == ')' && inFunction > 0) inFunction--
                    out += Op(op)
                    i++
                }
            }
        }
        return out
    }

    /** A value, and whether it is a percent (`15%`). `+` and `-` treat a percent as a share of the left side. */
    private class Value(val value: Double, val percent: Boolean = false)

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun done() = pos == tokens.size

        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun isOp(c: String) = (peek() as? Op)?.c == c
        private fun isWord(w: String) = (peek() as? Word)?.w == w

        fun expression(): Value {
            var left = term()
            while (isOp("+") || isOp("-")) {
                val minus = (tokens[pos++] as Op).c == "-"
                val right = term()
                val amount = if (right.percent) left.value * right.value else right.value
                left = Value(if (minus) left.value - amount else left.value + amount)
            }
            return left
        }

        private fun term(): Value {
            var left = unary()
            while (true) {
                left = when {
                    isOp("*") || isWord("x") || isWord("of") -> { pos++; Value(left.value * unary().value) }
                    isOp("/") -> {
                        pos++
                        val d = unary().value
                        if (d == 0.0) throw ArithmeticException("can't divide by zero")
                        Value(left.value / d)
                    }
                    isWord("mod") || isOp("%") -> {
                        pos++
                        val d = unary().value
                        if (d == 0.0) throw ArithmeticException("can't divide by zero")
                        Value(left.value - floor(left.value / d) * d)
                    }
                    // `2pi`, `3(4+5)`, `2 sqrt 9`: values side by side multiply.
                    isOp("(") || (peek() is Word && !isWord("x") && !isWord("of") && !isWord("mod")) -> Value(left.value * unary().value)
                    else -> return left
                }
            }
        }

        private fun unary(): Value = when {
            isOp("-") -> { pos++; Value(-unary().value) }
            isOp("+") -> { pos++; unary() }
            else -> power()
        }

        private fun power(): Value {
            val base = postfix()
            if (!isOp("^")) return base
            pos++
            return Value(base.value.pow(unary().value))
        }

        private fun postfix(): Value {
            var v = primary()
            while (true) {
                v = when {
                    isOp("!") -> { pos++; Value(factorial(v.value)) }
                    // A percent, unless a number or `(` follows. Then it is the remainder (`10 % 3`).
                    isOp("%") && (tokens.getOrNull(pos + 1).let { it !is Num && (it as? Op)?.c != "(" }) -> { pos++; Value(v.value / 100, percent = true) }
                    isOp("°") -> { pos++; Value(Math.toRadians(v.value)) }
                    else -> return v
                }
            }
        }

        private fun primary(): Value {
            val t = tokens.getOrNull(pos++) ?: throw IllegalStateException("ends early")
            return when (t) {
                is Num -> Value(t.v)
                is Op -> if (t.c == "(") expression().also { expectClose() } else throw IllegalStateException("unexpected ${t.c}")
                is Word -> when (t.w) {
                    "pi" -> Value(Math.PI)
                    "e" -> Value(Math.E)
                    in FUNCTIONS -> Value(FUNCTIONS.getValue(t.w)(arguments(t.w)))
                    else -> throw IllegalStateException("unknown ${t.w}")
                }
            }
        }

        /** Reads `max(1, 2)` or `sqrt 2`. In parentheses, there can be several values. Without them, it takes the one value that follows. */
        private fun arguments(name: String): List<Double> {
            if (!isOp("(")) return listOf(power().value)
            pos++
            val args = arrayListOf(expression().value)
            while (isOp(",")) { pos++; args += expression().value }
            expectClose()
            if (name !in MANY && args.size != 1) throw IllegalStateException("$name takes one value")
            return args
        }

        private fun expectClose() {
            if (!isOp(")")) throw IllegalStateException("expected )")
            pos++
        }
    }

    private fun factorial(n: Double): Double {
        if (n < 0 || n != floor(n)) throw ArithmeticException("! needs a whole number")
        if (n > 170) throw ArithmeticException("too big")
        return (2..n.toInt()).fold(1.0) { acc, k -> acc * k }
    }

    private val MANY = setOf("min", "max")

    private val FUNCTIONS: Map<String, (List<Double>) -> Double> = mapOf(
        "sqrt" to { a -> sqrt(a[0]) },
        "cbrt" to { a -> cbrt(a[0]) },
        "abs" to { a -> abs(a[0]) },
        "round" to { a -> floor(a[0]).let { f -> if (a[0] - f >= 0.5) f + 1 else f } },
        "floor" to { a -> floor(a[0]) },
        "ceil" to { a -> ceil(a[0]) },
        "ln" to { a -> ln(a[0]) },
        "log" to { a -> log10(a[0]) },
        "log2" to { a -> log2(a[0]) },
        "exp" to { a -> exp(a[0]) },
        "sin" to { a -> sin(a[0]) },
        "cos" to { a -> cos(a[0]) },
        "tan" to { a -> tan(a[0]) },
        "asin" to { a -> asin(a[0]) },
        "acos" to { a -> acos(a[0]) },
        "atan" to { a -> atan(a[0]) },
        "min" to { a -> a.min() },
        "max" to { a -> a.max() },
    )
}
