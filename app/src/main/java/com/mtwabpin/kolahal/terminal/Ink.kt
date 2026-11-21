/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.StyledText

/**
 * The theme's color roles, by name. There are no other colors: ui/ maps each role to the
 * theme's color (the GUI) or to a cell's color on the grid (TTY), so every theme works, light
 * ones and Material You included.
 *
 * At most 16 roles: a grid cell keeps each of its two inks in 4 bits
 * ([com.mtwabpin.kolahal.terminal.tty.CellStyle]).
 */
enum class Ink {
    BG, SURFACE, FG, DIM, PROMPT, ERROR, INFO, LINK, SCRIPT, PERSONAL, WORK, PRIVATE, GOOD, WARN;

    companion object {
        /** A script's color role ([StyledText.ColorRole]). */
        fun of(role: StyledText.ColorRole): Ink = when (role) {
            StyledText.ColorRole.ACCENT -> PROMPT
            StyledText.ColorRole.LINK -> LINK
            StyledText.ColorRole.INFO -> INFO
            StyledText.ColorRole.MUTED -> DIM
            StyledText.ColorRole.TEXT -> FG
            StyledText.ColorRole.GOOD -> GOOD
            StyledText.ColorRole.WARN -> WARN
            StyledText.ColorRole.BAD -> ERROR
        }

        /** A profile's color. A clone profile has the info color. */
        fun of(profile: ProfileKind): Ink = when (profile) {
            ProfileKind.PERSONAL -> PERSONAL
            ProfileKind.WORK -> WORK
            ProfileKind.PRIVATE -> PRIVATE
            ProfileKind.CLONE -> INFO
        }
    }
}
