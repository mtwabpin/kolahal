/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

import android.os.Build
import com.mtwabpin.kolahal.shader.Shaders

/**
 * Lab: features that aren't finished, which may change or go. It is off until it is turned on ([Keys.LAB_ON]):
 * Settings → About → Version tapped 8 times (`LabTaps`), or `config set lab.on on` typed in full. While it is
 * off nothing of it shows: no Settings page, no rows, commands, completions or help, and `lab.on` itself is left
 * out of `config`, completion and Settings search ([shows]).
 *
 * The one place that says whether a Lab feature is on: [has]. A new feature is one more [Feature].
 */
object Lab {
    /**
     * A Lab feature: its row on Settings' Lab page ([title], [subtitle]), and the oldest Android it works on
     * ([minSdk]). On an older one it doesn't show, even with Lab on.
     */
    enum class Feature(val title: String, val subtitle: String, val minSdk: Int) {
        /** A still picture of a scene: Make a picture in its details on the Shaders screen, `wallpaper picture <name>`. */
        PICTURE(
            "Make a picture from a shader",
            "Open a scene on the Shaders screen, then Make a picture: save it to the gallery or set it as your wallpaper",
            Shaders.MIN_SDK,
        ),
    }

    /** Whether Lab is on in [config]. */
    fun on(config: Config): Boolean = config[Keys.LAB_ON]

    /** Whether [feature] is on: Lab is, and this Android is new enough. */
    fun has(config: Config, feature: Feature): Boolean = has(on(config), feature, Build.VERSION.SDK_INT)

    /** The same, with Lab [on] or off, on Android [sdk]. */
    fun has(on: Boolean, feature: Feature, sdk: Int): Boolean = on && sdk >= feature.minSdk

    /** The features that are on, in order: Settings' Lab page has a row for each. */
    fun features(on: Boolean, sdk: Int): List<Feature> = Feature.entries.filter { has(on, it, sdk) }

    /** Whether `config`, completion and Settings show the setting [key] while Lab is [on]: all but `lab.on` while it is off. */
    fun shows(key: ConfigKey<*>, on: Boolean): Boolean = key != Keys.LAB_ON || on
}
