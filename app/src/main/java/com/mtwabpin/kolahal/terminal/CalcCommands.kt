/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.commands.Calc
import com.mtwabpin.kolahal.commands.Units
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.Net
import com.mtwabpin.kolahal.script.ScriptScheduler

/** The `=` calculator, with unit and currency conversion, and the `lua` and `js` commands, which run a snippet. */
class CalcCommands(
    context: Context,
    /** Runs the currency lookup outside the command's thread. */
    private val background: (label: String, block: () -> Unit) -> Unit,
) : CommandGroup(context) {

    fun eval(lang: ScriptLang, code: String) {
        if (code.isBlank()) {
            usage(lang.ext)
            return
        }
        if (!ScriptScheduler.eval(context, lang, code)) error("Snippet too long — use 'script new'")
    }

    // ---- calculator & conversions --------------------------------------------------------

    /** `= 2^10`, `= 5 km in mi`, `= 72 f to c`, `= 100 usd in inr`. Currency uses live rates, cached for 6h. */
    fun calc(expr: String) {
        val conversion = Units.parse(expr)
        if (conversion != null) {
            val (amount, from, to) = conversion
            Units.convert(amount, from, to)?.let {
                return answer(
                    it.substringAfter("= "),
                    it.substringBefore(" = "),
                    fallback = it
                )
            }
            if (Units.looksLikeCurrency(from) && Units.looksLikeCurrency(to)) {
                return background("convert") { currency(amount, from.uppercase(), to.uppercase()) }
            }
        }
        // Arithmetic is answered here at once. Anything else (Lua code) goes to Lua.
        val answer = try {
            Calc.answer(expr)
        } catch (e: ArithmeticException) {
            return error("$expr: ${e.message}")
        }
        if (answer != null) return answer(
            answer,
            expr.trim().takeIf { it.length <= ANSWER_LABEL_MAX }.orEmpty(),
            fallback = answer
        )
        eval(ScriptLang.LUA, expr)
    }

    /**
     * A calculator's [result], large, with [of] (what it answers) under it. Copy gives [copy].
     * A result too long for big type stays a line of text, [fallback], so it doesn't wrap.
     */
    private fun answer(result: String, of: String, fallback: String, copy: String = result) {
        val ctx = LogContext.of(LogContext.COPY to copy)
        if (result.length > ANSWER_MAX) out(
            fallback,
            ctx
        ) else out(StyledText.stats(listOf(result to of)), ctx)
    }

    private fun currency(amount: Double, from: String, to: String) {
        val cache = KvStore(context, Stores.FX)
        val cached =
            cache.get(from)?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }
        val fresh = (cached?.get("at") as? Number)?.toLong()
            ?.let { System.currentTimeMillis() - it < 6 * 3_600_000L } == true
        val rates = if (fresh) {
            cached["rates"] as? Map<*, *>
        } else {
            val body = Json.parse(Net.get("https://open.er-api.com/v6/latest/$from")) as? Map<*, *>
                ?: throw IllegalStateException("invalid response")
            if (body["result"] != "success") throw IllegalStateException("unknown currency $from")
            (body["rates"] as? Map<*, *>)?.also {
                cache.set(
                    from,
                    Json.stringify(mapOf("at" to System.currentTimeMillis(), "rates" to it))
                )
            }
        }
        val rate = (rates?.get(to) as? Number)?.toDouble()
            ?: throw IllegalStateException("unknown currency $to")
        val result = "${Units.format(amount * rate)} $to"
        answer(
            result,
            "${Units.format(amount)} $from",
            fallback = "${Units.format(amount)} $from = $result",
            copy = Units.format(amount * rate)
        )
    }
}

/** A calculator's result longer than this stays a line of text: in big type it would wrap. */
internal const val ANSWER_MAX = 16

/** An expression longer than this isn't shown under its result: it's on the command line above. */
private const val ANSWER_LABEL_MAX = 32
