/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

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

    private const val FOLLOW = ":follow"

    private fun key(shader: String, param: String) = "$shader:$param"

    private fun store(context: Context) = Stores.SHADER_PARAMS.open(context)

    /** Reads what was saved. With the shaders' names at start, and after a backup is imported. */
    fun load(context: Context) {
        val set = HashMap<String, HashMap<String, String>>()
        val followed = HashMap<String, HashMap<String, String>>()
        for ((key, value) in store(context).all) {
            val text = value as? String ?: continue
            val name = key.substringAfter(':')
            val to = if (name.endsWith(FOLLOW)) followed else set
            to.getOrPut(key.substringBefore(':')) { HashMap() }[name.removeSuffix(FOLLOW)] = text
        }
        _values.value = set
        _follows.value = followed
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

    /** What [param] of [shader] is now: the value it follows, when there is one that it can have, else what was set. */
    fun now(shader: String, param: ShaderParam, phone: ShaderValues.Phone): Float = followed(shader, param, phone) ?: of(shader, param)

    /** What the value that [param] follows makes it, or null when it follows none, or one that it can't have. */
    fun followed(shader: String, param: ShaderParam, phone: ShaderValues.Phone): Float? =
        follow(shader, param)?.let { ShaderValues.of(it, ShaderValues.given.value, phone) }?.let(param::followed)

    /** [param] is [value] until [set] or [load]: for a slider that is being dragged. */
    fun tryOut(shader: String, param: ShaderParam, value: Float) =
        _values.update { it + (shader to it[shader].orEmpty() + (param.name to param.text(value))) }

    fun set(context: Context, shader: String, param: ShaderParam, value: Float) {
        tryOut(shader, param, value)
        store(context).edit { putString(key(shader, param.name), param.text(value)) }
    }

    /** [param] of [shader] follows the value [id]; null for none. */
    fun setFollow(context: Context, shader: String, param: ShaderParam, id: String?) {
        _follows.update { it + (shader to it[shader].orEmpty() + (param.name to id.orEmpty())) }
        store(context).edit { putString(key(shader, param.name) + FOLLOW, id.orEmpty()) }
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
        store(context).edit {
            set.forEach { remove(key(shader, it)) }
            followed.forEach { remove(key(shader, it) + FOLLOW) }
        }
    }

    /** The script [from] is called [to] now: what follows its values goes on. */
    fun renameScript(context: Context, from: String, to: String) {
        fun renamed(id: String) = if (id.startsWith("$from.")) to + id.removePrefix(from) else id
        store(context).edit {
            for ((shader, params) in _follows.value) for ((name, id) in params) {
                if (renamed(id) != id) putString(key(shader, name) + FOLLOW, renamed(id))
            }
        }
        _follows.update { all -> all.mapValues { (_, params) -> params.mapValues { renamed(it.value) } } }
    }
}
