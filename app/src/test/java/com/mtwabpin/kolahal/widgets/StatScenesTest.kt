package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.shader.ShaderCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which scenes a card draws ([StatScenes.drawn]). */
class StatScenesTest {
    /**
     * Only Kolahal's own scenes: a user's own shader can't be marked and turned off after a crash beside
     * home's, so a card draws the plain stat for it. A shader that works on text or plays once is no scene.
     */
    @Test
    fun aCardDrawsOnlyKolahalsOwnScenes() {
        StatScenes.WEATHER.forEach { assertTrue(it, StatScenes.drawn(it)) }
        assertTrue(StatScenes.drawn("candle"))
        StatScenes.NOT_SCENES.forEach { assertFalse(it, StatScenes.drawn(it)) }
        assertFalse(StatScenes.drawn("glow"))
        assertFalse(StatScenes.drawn("template-scene"))
        assertTrue(ShaderCatalog.BUNDLED.count(StatScenes::drawn) == ShaderCatalog.BUNDLED.size - StatScenes.NOT_SCENES.size)
    }
}
