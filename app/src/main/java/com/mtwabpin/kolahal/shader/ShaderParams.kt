/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Stores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What people set a shader's parameters to ([ShaderParam]), by shader and name, and which values they
 * follow instead ([ShaderValues]). Kept as text, and kept when a shader is updated or removed: what its
 * new version doesn't have or allow isn't used.
 *
 * Shaders on screen follow [values], so a slider shows what it does while it is dragged ([tryOut]).
 */
object ShaderParams {
    private val _values = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    /** What is set: a shader's name, then a parameter's, then its value. */
    val values: StateFlow<Map<String, Map<String, String>>> = _values.asStateFlow()

    private val _follows = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    /** What is followed: a shader's name, then a parameter's, then the value's id, or "" for nothing. */
    val follows: StateFlow<Map<String, Map<String, String>>> = _follows.asStateFlow()

    private val _formulas = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    /** The formulas for what is followed: a shader's name, then a parameter's, then the formula, or "" for none. */
    val formulas: StateFlow<Map<String, Map<String, String>>> = _formulas.asStateFlow()

    private const val FOLLOW = ":follow"
    private const val FORMULA = ":formula"

    /** The longest formula. */
    const val MAX_FORMULA = 80

    private fun key(shader: String, param: String) = "$shader:$param"

    private fun store(context: Context) = Stores.SHADER_PARAMS.open(context)

    /** Reads what was saved: at start, off the main thread ([Shaders.load]), and after a backup is imported. */
    fun load(context: Context) {
        val set = HashMap<String, HashMap<String, String>>()
        val followed = HashMap<String, HashMap<String, String>>()
        val worked = HashMap<String, HashMap<String, String>>()
        for ((key, value) in store(context).all) {
            val text = value as? String ?: continue
            val name = key.substringAfter(':')
            val to = if (name.endsWith(FOLLOW)) followed else if (name.endsWith(FORMULA)) worked else set
            to.getOrPut(key.substringBefore(':')) { HashMap() }[name.removeSuffix(FOLLOW).removeSuffix(FORMULA)] = text
        }
        _values.value = set
        _follows.value = followed
        _formulas.value = worked
    }

    /** What home handed over for [shader], in the live wallpaper's process: nothing is kept there. */
    fun restore(shader: String, set: Map<String, String>, follows: Map<String, String>, formulas: Map<String, String>) {
        _values.value = mapOf(shader to set)
        _follows.value = mapOf(shader to follows)
        _formulas.value = mapOf(shader to formulas)
    }

    /** The value of [param] of shader [shader]: what was set, or its default. */
    fun of(shader: String, param: ShaderParam): Float = of(_values.value[shader].orEmpty(), param)

    /** The value of [param] where a shader's [set] values are these. */
    fun of(set: Map<String, String>, param: ShaderParam): Float = set[param.name]?.let(param::parse) ?: param.default

    /** The id of the value that [param] follows where a shader's [followed] ones are these: what was picked, or what the shader suggests. */
    fun follow(followed: Map<String, String>, param: ShaderParam): String? =
        followed[param.name].let { if (it == null) param.follow else it.ifEmpty { null } }

    /** The id of the value that [param] of [shader] follows, if it follows one. */
    fun follow(shader: String, param: ShaderParam): String? = follow(_follows.value[shader].orEmpty(), param)

    /** The formula for what [param] of [shader] follows: the one that was set, or the shader's own for the value it suggests. */
    fun formula(shader: String, param: ShaderParam): String? =
        _formulas.value[shader]?.get(param.name)?.let { return it.ifEmpty { null } }
            ?: param.formula.takeIf { follow(shader, param) == param.follow }

    /** What [param] of [shader] is now: the value it follows, when there is one that it can have, else what was set. */
    fun now(shader: String, param: ShaderParam, phone: ShaderValues.Phone): Float = followed(shader, param, phone) ?: of(shader, param)

    /** What the value that [param] follows makes it, or null when it follows none, or one that it can't have. */
    fun followed(shader: String, param: ShaderParam, phone: ShaderValues.Phone): Float? =
        follow(shader, param)?.let { followed(param, it, formula(shader, param), phone) }

    /** What the value [id] makes [param], with [formula] or over its whole range. Null: there is none, or one that it can't have. */
    fun followed(param: ShaderParam, id: String, formula: String?, phone: ShaderValues.Phone): Float? {
        val value = ShaderValues.of(id, ShaderValues.given.value, phone) ?: return null
        if (formula != null) return ShaderValues.work(formula, id, value)?.let(param::clean)
        return param.followed(ShaderValues.unit(id, value))
    }

    /** [param] is [value] until [set] or [load]: for a slider that is being dragged. */
    fun tryOut(shader: String, param: ShaderParam, value: Float) =
        _values.update { it + (shader to it[shader].orEmpty() + (param.name to param.text(value))) }

    fun set(context: Context, shader: String, param: ShaderParam, value: Float) {
        tryOut(shader, param, value)
        store(context).edit { putString(key(shader, param.name), param.text(value)) }
    }

    /** [param] of [shader] follows the value [id], with [formula] if there is one; null for no value. */
    fun setFollow(context: Context, shader: String, param: ShaderParam, id: String?, formula: String? = null) {
        val work = formula.orEmpty().take(MAX_FORMULA)
        _formulas.update { it + (shader to it[shader].orEmpty() + (param.name to work)) }
        _follows.update { it + (shader to it[shader].orEmpty() + (param.name to id.orEmpty())) }
        store(context).edit {
            putString(key(shader, param.name) + FOLLOW, id.orEmpty())
            putString(key(shader, param.name) + FORMULA, work)
        }
    }

    /** The shader's parameters are as it comes, or only [param]: what they follow, too. */
    fun reset(context: Context, shader: String, param: ShaderParam? = null) {
        fun names(of: Map<String, Map<String, String>>) =
            if (param != null) setOf(param.name) else of[shader].orEmpty().keys

        fun Map<String, Map<String, String>>.without(names: Set<String>): Map<String, Map<String, String>> {
            val left = this[shader].orEmpty() - names
            return if (left.isEmpty()) this - shader else this + (shader to left)
        }

        val set = names(_values.value)
        val followed = names(_follows.value)
        _values.update { it.without(set) }
        _follows.update { it.without(followed) }
        val worked = names(_formulas.value)
        _formulas.update { it.without(worked) }
        store(context).edit {
            set.forEach { remove(key(shader, it)) }
            followed.forEach { remove(key(shader, it) + FOLLOW) }
            worked.forEach { remove(key(shader, it) + FORMULA) }
        }
    }

    /** The script [from] is called [to] now: what follows its values goes on. */
    fun renameScript(context: Context, from: String, to: String) {
        fun renamed(id: String) = if (id.startsWith("$from.")) to + id.removePrefix(from) else id
        store(context).edit {
            for ((shader, params) in _follows.value) for ((name, id) in params) {
                if (renamed(id) == id) continue
                putString(key(shader, name) + FOLLOW, renamed(id))
                // A formula can have the value by its whole name.
                _formulas.value[shader]?.get(name)?.let { f ->
                    val work = f.replace(id, renamed(id), ignoreCase = true)
                    putString(key(shader, name) + FORMULA, work)
                    _formulas.update { it + (shader to it[shader].orEmpty() + (name to work)) }
                }
            }
        }
        _follows.update { all -> all.mapValues { (_, params) -> params.mapValues { renamed(it.value) } } }
    }
}
