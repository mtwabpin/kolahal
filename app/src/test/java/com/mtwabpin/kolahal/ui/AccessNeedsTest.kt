package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The words for an option that needs an access, and which values a shader follows need one ([AccessNeeds]). */
class AccessNeedsTest {
    private fun access(word: String) = Accesses.find(word)!!

    /** Android's names, in a sentence: the line says what it needs, the button asks for it. */
    @Test
    fun theLineAndTheButtonUseAndroidsNames() {
        assertEquals("Needs usage access", AccessNeeds.line(access(Accesses.USAGE)))
        assertEquals("Allow usage access", AccessNeeds.button(access(Accesses.USAGE)))
        assertEquals("Needs notification access", AccessNeeds.line(access(Accesses.READ_NOTIFICATIONS)))
        assertEquals("Allow notification access", AccessNeeds.button(access(Accesses.READ_NOTIFICATIONS)))
        // A name with capitals inside keeps them.
        assertEquals("Allow Do Not Disturb access", AccessNeeds.button(access(Accesses.DND)))
        // Display over other apps, as Android's page names it: its switch there says "Allow display over other apps".
        assertEquals("Needs display over other apps", AccessNeeds.line(access(Accesses.OVERLAY)))
        assertEquals("Allow display over other apps", AccessNeeds.button(access(Accesses.OVERLAY)))
    }

    /** Every value of the phone's use needs usage access, the unlocks too; the others need none. */
    @Test
    fun thePhonesUseNeedsUsageAccess() {
        for (id in listOf("unlocks", "unlocks.usual", "unlocks.d7", "screen", "screen.usual", "screen.d1", "screen.h8", "away")) {
            assertEquals(id, Accesses.USAGE, AccessNeeds.ofValue(id))
        }
        for (id in listOf("battery", "charging", "weather.rain", "unlocksx")) assertNull(id, AccessNeeds.ofValue(id))
    }

    /** What a shader's parameters follow: what its header says, or what was picked in its place. */
    @Test
    fun aShaderNeedsWhatItsParametersFollowNeed() {
        val level = ShaderParam.read("level 0.0..2.0 = 0.0 Level @follow screen.usual")!!
        val amount = ShaderParam.read("amount 0.0..1.0 = 0.5 Amount")!!
        assertEquals(setOf(Accesses.USAGE), AccessNeeds.ofParams(listOf(level, amount), emptyMap()))
        // Nothing picked in place of the header's value, or another value.
        assertEquals(emptySet<String>(), AccessNeeds.ofParams(listOf(level, amount), mapOf("level" to "")))
        assertEquals(emptySet<String>(), AccessNeeds.ofParams(listOf(level, amount), mapOf("level" to "battery")))
        // One that follows nothing by its header, set to follow the unlocks.
        assertEquals(setOf(Accesses.USAGE), AccessNeeds.ofParams(listOf(level, amount), mapOf("level" to "", "amount" to "unlocks")))
        assertEquals(emptySet<String>(), AccessNeeds.ofParams(emptyList(), emptyMap()))
    }

    /** The scenes of the phone's use that come with Kolahal say that they need usage access. */
    @Test
    fun theScenesOfThePhonesUseNeedIt() {
        for (name in listOf("candle", "lanterns", "seismograph", "hourglass", "day-ring", "kintsugi", "record", "windows")) {
            val meta = ShaderMeta.parse(sourceFile("src/main/assets/shaders/$name.agsl").readText())
            assertEquals(name, setOf(Accesses.USAGE), AccessNeeds.ofParams(meta.params, emptyMap()))
        }
        val rain = ShaderMeta.parse(sourceFile("src/main/assets/shaders/rain.agsl").readText())
        assertEquals(emptySet<String>(), AccessNeeds.ofParams(rain.params, emptyMap()))
    }

    /** The Follow menu says it in place of a value, only while the access is missing. */
    @Test
    fun theFollowMenuSaysWhatIsMissing() {
        assertEquals("needs usage access", AccessNeeds.valueNote("unlocks", setOf(Accesses.USAGE)))
        assertEquals("needs usage access", AccessNeeds.valueNote("screen.h3", setOf(Accesses.USAGE)))
        assertNull(AccessNeeds.valueNote("unlocks", emptySet()))
        assertNull(AccessNeeds.valueNote("battery", setOf(Accesses.USAGE)))
    }
}
