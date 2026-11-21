package com.mtwabpin.kolahal.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetTest {
    @Test
    fun webLinksOnly() {
        assertEquals("https://example.com/a", Net.webUrl(" https://example.com/a "))
        assertNull(Net.webUrl("upi://pay?pa=x@y&am=9"))
        assertNull(Net.webUrl("tel:*21*123#"))
        assertNull(Net.webUrl("intent://x#Intent;end"))
        assertNull(Net.webUrl("file:///sdcard/x"))
    }
}
