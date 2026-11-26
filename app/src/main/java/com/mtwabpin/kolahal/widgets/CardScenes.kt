/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.shader.ShaderCatalog

/**
 * Scene: a choice on the settings page of any of Kolahal's own cards and any script's card, to draw a
 * still of one of Kolahal's scene shaders behind it ([PlacedWidget.scene]), as a stat's `scene` does
 * ([StatScenes]). Off by default.
 * - What is stored: null (the card's own: none, or the stat's `scene` when its script sets one),
 *   [NONE] (no scene, also over the script's), or a scene's name ([names]).
 * - It shows in the looks whose card has a fill for it to replace ([drawnIn]); Split's tiles, Shape's
 *   shape and Poster's type keep their own ([why]).
 * - The Now playing card's cover wins while there is one ([overArt]).
 * - Figures read over the stat's scrim, strong at the start; every other kind, with words and marks
 *   all over the card, has a fuller one ([full]).
 * Pure: the page, the card and the stored form ask here.
 */
object CardScenes {
    /** The row's name on the settings page. */
    const val LABEL = "Scene"

    /** The first choice: no scene. */
    const val NONE_LABEL = "None"

    /** The first choice for a script that sets its own `scene`. */
    const val SCRIPT_LABEL = "As the script sets"

    /** How "no scene, over the script's own" is stored. No shader may be called so: [ShaderCatalog.OFF]'s like. */
    const val NONE = "none"

    /** Its key in a backup's entry, there only when set. */
    const val BACKUP = "scene"

    /** The line under the choice on Now playing, where the cover and the scene are one choice. */
    const val ART = "With album art as background, the cover shows while there is one"

    /** The scenes offered, in the order Kolahal's shaders are listed: the set a stat's `scene` accepts ([StatScenes.drawn]). */
    val names: List<String> = ShaderCatalog.BUNDLED.filter(StatScenes::drawn)

    /** Whether the settings page offers the row: for any card Kolahal draws ([source]), not an app's widget. */
    fun offered(source: String?): Boolean = source != null && WidgetSource.isSource(source)

    /** A scene's name as the list says it: "grove-snow" is "Grove snow". */
    fun label(name: String): String = name.replace('-', ' ').replaceFirstChar(Char::titlecase)

    /**
     * The scene a card draws: the one [chosen] on its page, else the one its script names ([script],
     * a stat's `scene`). [NONE] is none, also over the script's.
     */
    fun shown(chosen: String?, script: String?): String? = when (chosen) {
        null -> script
        NONE -> null
        else -> chosen
    }

    /**
     * Whether a card in [look] draws a scene: Plain, Tonal and Bold, and the looks of one kind drawn on
     * a plain card (Next, Stack, Now). Their card has a fill for the scene to replace.
     */
    fun drawnIn(look: WidgetLook): Boolean = why(look) == null

    /** Why a card in [look] draws no scene, as the row says it, or null when it draws one. */
    fun why(look: WidgetLook): String? = when (look) {
        WidgetLook.SPLIT -> "Not in Split: its tiles keep their color"
        WidgetLook.SHAPE -> "Not in Shape: the shape keeps its color"
        WidgetLook.POSTER -> "Not in Poster: the large type is the picture"
        else -> null
    }

    /**
     * Whether the scene shows on the Now playing card: not while the cover is its ground ([art], from
     * [MediaArt.ground]). Album art and a scene are one choice: the cover while there is one, else the scene.
     */
    fun overArt(art: Boolean): Boolean = !art

    /**
     * Whether a card of [kind] has the fuller scrim: every kind but a stat, whose figures stand at
     * the start and keep the scrim that is lighter toward the end.
     */
    fun full(kind: WidgetKind?): Boolean = kind != WidgetKind.STAT

    /**
     * The choices the page offers, in order: the card's own first (null: none, or "As the script
     * sets" when its script names a scene, [script]), then [NONE] after the script's own, then each scene.
     */
    fun choices(script: String?): List<String?> = (if (script != null) listOf(null, NONE) else listOf(null)) + names

    /** A choice of [choices] as the page says it. */
    fun choiceLabel(choice: String?, script: String?): String = when {
        choice == null && script != null -> SCRIPT_LABEL
        choice == null || choice == NONE -> NONE_LABEL
        else -> label(choice)
    }

    /** What the row says the card has: "Pond", "None", "As the script sets: Rain". */
    fun summary(chosen: String?, script: String?): String =
        if (chosen == null && script != null) "$SCRIPT_LABEL: ${label(script)}" else choiceLabel(chosen, script)

    /** A stored choice ([PlacedWidget.scene]): [NONE] or a scene's name; anything else, a scene this version doesn't have too, is the card's own. */
    fun read(raw: Any?): String? =
        (raw as? String)?.trim()?.lowercase()?.takeIf { it == NONE || it in names }
}
