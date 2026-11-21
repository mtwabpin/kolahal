package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TipsTest {
    @Test
    fun theFirstNotUsedNorShown() {
        assertEquals("todo", Tips.next(emptyList(), emptySet())?.id)
        // Used: todo and the calculator (a line that starts with =, spaces or not).
        assertEquals("remind", Tips.next(listOf("todo milk", "=2+2"), emptySet())?.id)
        assertEquals("note", Tips.next(listOf("todo milk", "=2+2"), setOf("remind"))?.id)
        // A synonym counts as its command: `?` is help.
        assertEquals("alias", Tips.next(listOf("?"), setOf("todo", "remind", "calc", "note"))?.id)
    }

    @Test
    fun noneLeft() = assertNull(Tips.next(emptyList(), Tips.ALL.map { it.id }.toSet()))

    @Test
    fun idsAreUnique() = assertEquals(Tips.ALL.size, Tips.ALL.map { it.id }.toSet().size)
}
