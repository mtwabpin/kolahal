/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

/**
 * A button's or a switch's value ([com.mtwabpin.kolahal.widgets.WidgetSpec.Button.value],
 * [com.mtwabpin.kolahal.widgets.WidgetSpec.Toggle.value]): "21°", "40%". It takes the line where a
 * switch says "On" or "Off", and the Shape look draws a short one inside the shape. Pure, so it is
 * unit tested.
 */
internal object ControlValues {
    /** The most characters of a value inside a Shape button's or switch's shape. */
    const val SHAPE_MAX = 4

    /**
     * [value] as the Shape look draws it, inside the shape in place of the icon. Null where there is
     * none, or where it is longer than [SHAPE_MAX] characters: Shape leaves it out.
     */
    fun inShape(value: String?): String? =
        value?.takeIf { it.codePointCount(0, it.length) <= SHAPE_MAX }

    /** What a screen reader hears for a control's [label] with the [value] drawn with it: "Heating, 21°". */
    fun said(label: String, value: String?): String =
        if (value == null) label else "$label, $value"
}
