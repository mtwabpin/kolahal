/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import kotlinx.coroutines.flow.StateFlow

/**
 * Where a shader's parameter rows (`ShaderParamRows`) read and write what its parameters are set to and follow:
 * the values home uses ([ShaderParams], through the Shaders screen's actions), or a picture's own copy of them,
 * which home never sees (Lab's pictures).
 */
interface ParamTarget {
    /** What is set: a shader's name, then a parameter's, then its value ([ShaderParams.values]). */
    val values: StateFlow<Map<String, Map<String, String>>>

    /** What is followed, by shader and parameter: a value's id, or "" for nothing ([ShaderParams.follows]). */
    val follows: StateFlow<Map<String, Map<String, String>>>

    /** The formulas for what is followed, by shader and parameter ([ShaderParams.formulas]). */
    val formulas: StateFlow<Map<String, Map<String, String>>>

    /** Sets [param] of [name] to [value], and keeps it. */
    fun setParam(name: String, param: ShaderParam, value: Float)

    /** [param] of [name] is [value] while a slider is dragged, until it is set. */
    fun tryOut(name: String, param: ShaderParam, value: Float)

    /** [param] of [name] follows the value [id], with [formula] if there is one; null for no value. */
    fun follow(name: String, param: ShaderParam, id: String?, formula: String? = null)

    /** The parameters of [name] are as it comes. */
    fun resetParams(name: String)

    /** The phone as it is now, for the values that Kolahal gives. */
    fun phone(): ShaderValues.Phone

    /** The values there are to follow, for the Follow menu. */
    suspend fun sources(): List<ShaderValues.Source>
}
