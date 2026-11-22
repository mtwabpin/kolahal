package com.mtwabpin.kolahal.ui.home

import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which of home's parts the tty style draws on the grid, and the `tty.*` keys that say so ([TtyParts]). */
class TtyPartsTest {
    private val keys = listOf(
        Keys.TTY_SUGGESTIONS,
        Keys.TTY_SELECTOR,
        Keys.TTY_KEYBAR,
        Keys.TTY_QUICK_ACTIONS,
        Keys.TTY_MENU,
    )

    /** Each is gui (the default) or tty, and says what each value does. */
    @Test
    fun theKeysOfferGuiAndTty() {
        assertEquals(
            listOf("tty.suggestions", "tty.selector", "tty.keybar", "tty.quick-actions", "tty.menu"),
            keys.map { it.key },
        )
        for (key in keys) {
            assertEquals(key.key, listOf("gui", "tty"), key.choices)
            assertEquals(key.key, "gui", key.default)
            assertEquals(key.key, "gui", key.parse("gui"))
            assertEquals(key.key, "tty", key.parse("TTY"))
            assertNull(key.key, key.parse("grid"))
            assertTrue("${key.key} has no help", key.help.isNotBlank())
            for (value in key.choices) {
                val note = key.notes[value]
                assertTrue("${key.key} $value has no note", !note.isNullOrBlank())
                assertTrue("${key.key} $value: '$note' is long", note!!.length <= 70)
                assertFalse("${key.key} $value: '$note' ends with a full stop", note.endsWith("."))
            }
        }
    }

    /** `config` finds each by its name, and only `config` sets it: TTY stays out of Settings until it is released. */
    @Test
    fun onlyConfigSetsThem() {
        for (key in keys) {
            assertTrue(key.key, key in Keys.ALL)
            assertEquals(key.key, key, Keys.byName(key.key))
            assertTrue(key.key, key in Keys.CONFIG_ONLY)
        }
    }

    /** By default the tty style draws each of them as the GUI does. */
    @Test
    fun theDefaultsAreTheGuis() {
        assertEquals(TtyParts.NONE, TtyParts.of(HomeStyle.TTY) { it.default })
    }

    /** Each key moves its own part to the grid, and no other. */
    @Test
    fun eachKeyMovesItsOwnPart() {
        fun only(key: ConfigKey<String>) =
            TtyParts.of(HomeStyle.TTY) { if (it == key) "tty" else it.default }
        assertEquals(TtyParts(suggestions = true), only(Keys.TTY_SUGGESTIONS))
        assertEquals(TtyParts(selector = true), only(Keys.TTY_SELECTOR))
        assertEquals(TtyParts(keyBar = true), only(Keys.TTY_KEYBAR))
        assertEquals(TtyParts(quickActions = true), only(Keys.TTY_QUICK_ACTIONS))
        assertEquals(TtyParts(menu = true), only(Keys.TTY_MENU))
        assertEquals(
            TtyParts(suggestions = true, selector = true, keyBar = true, quickActions = true, menu = true),
            TtyParts.of(HomeStyle.TTY) { "tty" },
        )
    }

    /** In the gui style they change nothing. */
    @Test
    fun theGuiStyleHasNoneOnTheGrid() {
        assertEquals(TtyParts.NONE, TtyParts.of(HomeStyle.GUI) { "tty" })
    }
}
