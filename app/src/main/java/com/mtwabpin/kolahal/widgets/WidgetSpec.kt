package com.mtwabpin.kolahal.widgets

import androidx.compose.runtime.Immutable
import com.mtwabpin.kolahal.data.StyledText

/**
 * The size a script widget starts with, in cells of a 4-column grid, as home screens count them.
 * One cell is 3 of the page's 12 columns ([WidgetLayout.COLUMNS]). After it is placed, a widget is
 * resized like any other.
 */
enum class WidgetSize(val id: String, val cells: Int, val rows: Int) {
    S1X1("1x1", 1, 1), S2X1("2x1", 2, 1), S2X2("2x2", 2, 2), S4X1("4x1", 4, 1), S4X2("4x2", 4, 2), S4X4("4x4", 4, 4);

    /** Its width in the page's columns. */
    val span get() = cells * 3

    companion object {
        /** The size written as "2x1", in any case; "2×1" works too. Null for anything else. */
        fun named(id: String?): WidgetSize? {
            val wanted = id?.trim()?.lowercase()?.replace('×', 'x') ?: return null
            return entries.firstOrNull { it.id == wanted }
        }
    }
}

/** The kinds of card Kolahal draws for a script's widget. [size] is the size a kind gets when its `@widget` line names none. */
enum class WidgetKind(val id: String, val size: WidgetSize) {
    STAT("stat", WidgetSize.S2X1),
    TEXT("text", WidgetSize.S2X2),
    LIST("list", WidgetSize.S4X2),
    PROGRESS("progress", WidgetSize.S2X1),
    CHART("chart", WidgetSize.S2X2),
    COUNTDOWN("countdown", WidgetSize.S2X1);

    companion object {
        /** The kind with this name, in any case. Null for anything else. */
        fun named(id: String?): WidgetKind? {
            val wanted = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == wanted }
        }
    }
}

/**
 * What a tap does. It has exactly one of:
 * - [args]: the script runs again with these arguments.
 * - [url]: the web link opens.
 * [label] is what a screen reader says. With [toLog], home turns to the log after a tap that runs the script.
 */
data class WidgetTap(val args: String? = null, val url: String? = null, val label: String? = null, val toLog: Boolean = false)

/** The fields every kind has. All are optional. [staleAfter] and [refresh] are in seconds. */
data class WidgetBase(
    val title: String? = null,
    val icon: String? = null,
    val color: StyledText.Colour? = null,
    val tap: WidgetTap? = null,
    val staleAfter: Int? = null,
    val refresh: Int? = null,
)

/**
 * A script's widget description after it was checked: one class for each kind. Texts are plain and
 * already cut to their limits, and colours are roles of the theme.
 * - WidgetParser makes one from what a script passes to `widget.show`.
 * - [WidgetJson] stores it and reads it back.
 */
@Immutable
sealed interface WidgetSpec {
    val base: WidgetBase

    /** One of a [Stat]'s smaller values. */
    data class Small(val value: String, val label: String?)

    /** Kind `stat`: one big value. [trend] is `up`, `down` or `flat`. [progress] is 0 to 1. */
    data class Stat(
        override val base: WidgetBase,
        val value: String,
        val label: String? = null,
        val unit: String? = null,
        val delta: String? = null,
        val trend: String? = null,
        val more: List<Small> = emptyList(),
        val progress: Float? = null,
        val moving: Boolean = false,
    ) : WidgetSpec

    /** Kind `text`: a few lines and a caption. */
    data class Text(override val base: WidgetBase, val text: String, val caption: String? = null) : WidgetSpec

    /** One row of [Rows]. */
    data class Row(
        val title: String,
        val detail: String? = null,
        val trailing: String? = null,
        val icon: String? = null,
        val color: StyledText.Colour? = null,
        val tap: WidgetTap? = null,
    )

    /** Kind `list`: rows. [empty] is what to say with no rows. */
    data class Rows(override val base: WidgetBase, val items: List<Row>, val empty: String? = null) : WidgetSpec

    /** One bar of a [Progress] with several. */
    data class Bar(val label: String?, val value: Float, val max: Float, val text: String?, val color: StyledText.Colour?)

    /** Kind `progress`: a ring or bars. [style] is `ring` or `bar`; without it the box decides. */
    data class Progress(
        override val base: WidgetBase,
        val value: Float,
        val max: Float = 1f,
        val text: String? = null,
        val label: String? = null,
        val style: String? = null,
        val items: List<Bar> = emptyList(),
        val moving: Boolean = false,
    ) : WidgetSpec

    /** Kind `chart`: numbers over time. [style] is `line` or `bars`. [labels] name the first and the last point. */
    data class Chart(
        override val base: WidgetBase,
        val points: List<Float>,
        val style: String = "line",
        val min: Float? = null,
        val max: Float? = null,
        val value: String? = null,
        val label: String? = null,
        val labels: List<String> = emptyList(),
        val summary: String? = null,
    ) : WidgetSpec

    /**
     * Kind `countdown`: the time to or since a moment. [to] and [from] are in seconds since 1970.
     * [style] is `days` or `clock`. [reached] is what to say on the day.
     */
    data class Countdown(
        override val base: WidgetBase,
        val to: Long,
        val from: Long? = null,
        val label: String? = null,
        val style: String? = null,
        val reached: String? = null,
    ) : WidgetSpec
}

/**
 * The stored form of a [WidgetSpec]: JSON whose keys are the script's field names, plus `v`
 * ([FORMAT]) and `kind`. A tap is `{ args | url, label, page }`.
 */
object WidgetJson {
    const val FORMAT = 1

    fun encode(spec: WidgetSpec): String = ""

    /** The description stored as [text]. Null for anything that isn't one: it never throws. */
    fun decode(text: String?): WidgetSpec? = null
}
