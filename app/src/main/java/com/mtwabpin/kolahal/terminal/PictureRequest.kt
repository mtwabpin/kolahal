/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The scene that Lab's picture composer ([Screen.PICTURE]) makes a picture of: set by Make a picture in a scene's
 * details and by `wallpaper picture <name>`, just before the screen opens.
 */
object PictureRequest {
    private val _shader = MutableStateFlow<String?>(null)

    /** The scene's name, or null before one was asked for. */
    val shader: StateFlow<String?> = _shader.asStateFlow()

    /** Asks for a picture of the scene [name]. The caller opens [Screen.PICTURE]. */
    fun ask(name: String) {
        _shader.value = name
    }
}
