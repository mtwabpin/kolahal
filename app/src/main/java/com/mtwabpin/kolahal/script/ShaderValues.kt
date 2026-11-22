/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.content.Context
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Stores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The values that shaders' parameters can follow ([ShaderParams.follow]): Kolahal's own, and those that
 * scripts give with `shader.give(name, value)` under their `@gives` line. A value has one owner, and any
 * number of parameters can follow it.
 * - Kolahal's are one word (`battery`); a script's has the script's name first (`weather.rain`).
 * - A value is a number from 0 to 1, or a word.
 * - A script's values go when it is switched off, changed from outside or deleted. They stay over a restart.
 */
object ShaderValues {
    /** Kolahal's own values, and what they are called. */
    val BUILT_IN: Map<String, String> = linkedMapOf(
        "battery" to "Battery",
        "charging" to "Charging",
        "day" to "Time of day",
        "night" to "Night",
    )

    /** A value's name, after the script's. */
    val NAME = Regex("[a-z][a-z0-9_-]{0,19}")

    /** A value that is a word: one that a parameter can have. */
    private val WORD = Regex("[A-Za-z][A-Za-z0-9-]{0,19}")

    /** The most values a script can give. */
    const val MAX = 8

    /** The phone, for Kolahal's own values. */
    data class Phone(val battery: Float, val charging: Boolean, val time: LocalTime)

    /** A value to follow: its [id], what it is called, and what it is now (null: nothing yet). */
    data class Source(val id: String, val label: String, val value: String?)

    private val _given = MutableStateFlow<Map<String, String>>(emptyMap())

    /** What scripts gave, by id. */
    val given: StateFlow<Map<String, String>> = _given.asStateFlow()

    fun id(script: String, name: String) = "$script.$name"

    /** What the value [id] is called. */
    fun label(id: String): String = BUILT_IN[id] ?: id.replace(".", " · ")

    /** The value [id] as people read it: a battery in percent, charging as yes or no, other numbers with two decimals at most. */
    fun shown(id: String, value: String): String {
        val n = value.toFloatOrNull() ?: return value
        return when (id) {
            "battery" -> "${(n * 100).roundToInt()}%"
            "charging" -> if (n > 0.5f) "yes" else "no"
            else -> String.format(Locale.ROOT, "%.2f", n).trimEnd('0').trimEnd('.')
        }
    }

    /** Reads what scripts gave: at start, and when one may have given something ([LogRepository.statusVersion]). Main process. */
    fun load(context: Context) {
        val all = KvStore(context, Stores.SHADER_VALUES).all()
        _given.value = all.flatMap { (script, raw) -> read(raw).map { (name, value) -> id(script, name) to value } }.toMap()
    }

    /** The value [id] as it is now, or null when there is none. */
    fun of(id: String, given: Map<String, String>, phone: Phone): String? =
        if (id in BUILT_IN) builtIn(id, phone)?.toString() else given[id]

    private fun builtIn(id: String, phone: Phone): Float? = when (id) {
        "battery" -> phone.battery.coerceIn(0f, 1f)
        "charging" -> if (phone.charging) 1f else 0f
        "day" -> phone.time.toSecondOfDay() / 86_400f
        "night" -> night(phone.time.toSecondOfDay() / 3_600f)
        else -> null
    }

    /** 1 at night and 0 by day, with an hour and a half of dusk and dawn around 6 and 19. */
    private fun night(hour: Float): Float = when {
        hour < 5.25f || hour >= 19.75f -> 1f
        hour < 6.75f -> (6.75f - hour) / 1.5f
        hour < 18.25f -> 0f
        else -> (hour - 18.25f) / 1.5f
    }

    /**
     * What a script gave as [value], as it is kept: a number taken into 0 to 1, or a word. `true` is 1 and
     * `false` 0. Null for nothing, which takes the value away. Anything else is an error.
     */
    fun clean(value: Any?): String? = when (value) {
        null -> null
        is Boolean -> if (value) "1" else "0"
        is Number -> {
            require(value.toDouble().isFinite()) { "shader.give: a value is a number from 0 to 1, or a word" }
            value.toDouble().coerceIn(0.0, 1.0).toFloat().toString()
        }

        is String -> value.trim().also {
            require(WORD.matches(it)) { "shader.give: a value is a number from 0 to 1, or a word of letters, digits and '-', at most 20" }
        }

        else -> throw IllegalArgumentException("shader.give: a value is a number from 0 to 1, or a word")
    }

    /** Keeps [script]'s value [name], and tells home. Null takes it away. True when that changed something. Any process. */
    fun give(context: Context, script: String, name: String, value: String?): Boolean {
        val store = KvStore(context, Stores.SHADER_VALUES)
        val before = read(store.get(script))
        if (before[name] == value) return false
        write(store, script, if (value == null) before - name else before + (name to value))
        LogRepository.get(context).onStatusChanged()
        return true
    }

    /** Takes [script]'s values away, but those named in [keep]. */
    fun forget(context: Context, script: String, keep: Set<String> = emptySet()) {
        val store = KvStore(context, Stores.SHADER_VALUES)
        val before = read(store.get(script))
        val left = before.filterKeys { it in keep }
        if (left.size == before.size) return
        write(store, script, left)
        LogRepository.get(context).onStatusChanged()
    }

    private fun write(store: KvStore, script: String, values: Map<String, String>) =
        store.set(script, if (values.isEmpty()) null else Json.stringify(values))

    private fun read(raw: String?): Map<String, String> =
        (raw?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *>)
            ?.entries?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }?.toMap().orEmpty()
}
