package com.mtwabpin.kolahal.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A button's or a switch's value: inside Shape's shape only when short, and said after the label. */
class ControlValueTest {
    @Test
    fun shapeTakesAValueOfUpToFourCharacters() {
        assertEquals("21°", ControlValues.inShape("21°"))
        assertEquals("100%", ControlValues.inShape("100%"))
        assertNull(ControlValues.inShape("set 22°"))
        assertNull(ControlValues.inShape(null))
    }

    @Test
    fun theValueIsSaidAfterTheLabel() {
        assertEquals("Heating, 21°", ControlValues.said("Heating", "21°"))
        assertEquals("Fan", ControlValues.said("Fan", null))
    }
}
