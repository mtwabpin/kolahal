package com.mtwabpin.kolahal.control

import com.mtwabpin.kolahal.status.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The mode words of Do Not Disturb, the same for `dnd` and `device.setDnd`; the network kind with none. */
class DndTest {
    @Test fun eachWordNamesAMode() {
        for (word in listOf("off", "priority", "alarms", "none")) assertEquals(word, Dnd.wordOf(Dnd.filterOf(word)!!))
        // The other words the command takes are names for the same modes.
        assertEquals(Dnd.filterOf("priority"), Dnd.filterOf("on"))
        assertEquals(Dnd.filterOf("none"), Dnd.filterOf("silence"))
        assertEquals(Dnd.filterOf("none"), Dnd.filterOf(" Total "))
        assertNull(Dnd.filterOf("loud"))
    }

    @Test fun noNetworkIsOffline() = assertEquals("offline", NetworkKind.of(null))
}
