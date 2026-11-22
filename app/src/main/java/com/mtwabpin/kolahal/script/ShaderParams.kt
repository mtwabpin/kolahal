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
 * What people set a shader's parameters to ([ShaderParam]), by shader and name. Kept as text, and kept
 * when a shader is updated or removed: what its new version doesn't have or allow isn't used.
 *
 * Shaders on screen follow [values], so a slider shows what it does while it is dragged ([tryOut]).
 */
object ShaderParams {
    private val _values = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    /** What is set: a shader's name, then a parameter's, then its value. */
    val values: StateFlow<Map<String, Map<String, String>>> = _values.asStateFlow()

    private fun key(shader: String, param: String) = "$shader:$param"

    /** Reads what was saved. With the shaders' names at start, and after a backup is imported. */
    fun load(context: Context) {
        val read = HashMap<String, HashMap<String, String>>()
        for ((key, value) in Stores.SHADER_PARAMS.open(context).all) {
            val text = value as? String ?: continue
            read.getOrPut(key.substringBefore(':')) { HashMap() }[key.substringAfter(':')] = text
        }
        _values.value = read
    }

    /** The value of [param] of shader [shader]: what was set, or its default. */
    fun of(shader: String, param: ShaderParam): Float = of(_values.value[shader].orEmpty(), param)

    /** The value of [param] where a shader's [set] values are these. */
    fun of(set: Map<String, String>, param: ShaderParam): Float = set[param.name]?.let(param::parse) ?: param.default

    /** [param] is [value] until [set] or [load]: for a slider that is being dragged. */
    fun tryOut(shader: String, param: ShaderParam, value: Float) =
        _values.update { it + (shader to it[shader].orEmpty() + (param.name to param.text(value))) }

    fun set(context: Context, shader: String, param: ShaderParam, value: Float) {
        tryOut(shader, param, value)
        Stores.SHADER_PARAMS.open(context).edit { putString(key(shader, param.name), param.text(value)) }
    }

    /** The shader's parameters are as it comes, or only [param]. */
    fun reset(context: Context, shader: String, param: ShaderParam? = null) {
        val names = if (param != null) setOf(param.name) else _values.value[shader].orEmpty().keys
        _values.update {
            val left = it[shader].orEmpty() - names
            if (left.isEmpty()) it - shader else it + (shader to left)
        }
        Stores.SHADER_PARAMS.open(context).edit { names.forEach { remove(key(shader, it)) } }
    }
}
