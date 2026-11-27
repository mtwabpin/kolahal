/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.wallpaper

import android.content.Context
import com.mtwabpin.kolahal.shader.ParamTarget
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.terminal.ShaderActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * A picture's own parameters ([ParamTarget]): at first what home has for [shader], then what is changed in the
 * composer. They stay with the picture: nothing is kept, and the values home and the live wallpaper use stay as
 * they are. [time] is the hour the picture shows, for the values that follow the time of day.
 */
internal class PictureParams(private val context: Context, private val shader: String, private val time: () -> LocalTime) : ParamTarget {
    private val _values = MutableStateFlow(mapOf(shader to ShaderParams.values.value[shader].orEmpty()))
    private val _follows = MutableStateFlow(mapOf(shader to ShaderParams.follows.value[shader].orEmpty()))
    private val _formulas = MutableStateFlow(mapOf(shader to ShaderParams.formulas.value[shader].orEmpty()))

    override val values: StateFlow<Map<String, Map<String, String>>> = _values.asStateFlow()
    override val follows: StateFlow<Map<String, Map<String, String>>> = _follows.asStateFlow()
    override val formulas: StateFlow<Map<String, Map<String, String>>> = _formulas.asStateFlow()

    private fun MutableStateFlow<Map<String, Map<String, String>>>.put(name: String, param: String, value: String) =
        update { it + (name to it[name].orEmpty() + (param to value)) }

    override fun setParam(name: String, param: ShaderParam, value: Float) = _values.put(name, param.name, param.text(value))

    override fun tryOut(name: String, param: ShaderParam, value: Float) = setParam(name, param, value)

    override fun follow(name: String, param: ShaderParam, id: String?, formula: String?) {
        _formulas.put(name, param.name, formula.orEmpty().take(ShaderParams.MAX_FORMULA))
        _follows.put(name, param.name, id.orEmpty())
    }

    override fun resetParams(name: String) {
        _values.update { it - name }
        _follows.update { it - name }
        _formulas.update { it - name }
    }

    /** The phone as it is now, at the picture's hour. */
    override fun phone(): ShaderValues.Phone = ShaderActions.phone(context).copy(time = time())

    override suspend fun sources(): List<ShaderValues.Source> =
        withContext(Dispatchers.IO) { ShaderActions.sources(context, series = false, fresh = true) }

    /** The value of each of [params] in the picture, in order ([PictureMath.values]). */
    fun now(params: List<ShaderParam>): List<Float> = PictureMath.values(
        params,
        _values.value[shader].orEmpty(),
        _follows.value[shader].orEmpty(),
        _formulas.value[shader].orEmpty(),
        phone(),
    )
}
