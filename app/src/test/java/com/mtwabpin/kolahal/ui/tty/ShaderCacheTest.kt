package com.mtwabpin.kolahal.ui.tty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The shaders loaded last ([ShaderCache]): kept for the versions they were loaded at, and only a few. */
class ShaderCacheTest {
    @Test
    fun aShaderIsKeptForItsVersions() {
        val cache = ShaderCache<String>()
        assertNull(cache.get("pond", 1, 0))
        cache.put("pond", 1, 0, "pond-1")
        assertEquals("pond-1", cache.get("pond", 1, 0))
        // Home and a screen over it get the same one.
        assertEquals("pond-1", cache.get("pond", 1, 0))
    }

    /** A file changed, or a shader was turned off or on: it is loaded again. */
    @Test
    fun anotherVersionLoadsItAgain() {
        val cache = ShaderCache<String>()
        cache.put("pond", 1, 0, "pond-1")
        assertNull(cache.get("pond", 2, 0))
        // Gone, also for the version it had.
        assertNull(cache.get("pond", 1, 0))
        cache.put("pond", 2, 0, "pond-2")
        assertNull(cache.get("pond", 2, 1))
        assertNull(cache.get("pond", 2, 0))
    }

    /** What was loaded at older versions goes with the first shader loaded at newer ones. */
    @Test
    fun aNewerLoadDropsTheOlderOnes() {
        val cache = ShaderCache<String>()
        cache.put("pond", 1, 0, "pond-1")
        cache.put("crt", 2, 0, "crt-2")
        assertNull(cache.get("pond", 1, 0))
        assertEquals("crt-2", cache.get("crt", 2, 0))
    }

    /** A load that ends after the files changed again doesn't put its old shader back. */
    @Test
    fun aLateOlderLoadIsLeftOut() {
        val cache = ShaderCache<String>()
        cache.put("pond", 2, 1, "pond-new")
        cache.put("pond", 1, 1, "pond-old")
        cache.put("crt", 2, 0, "crt-old")
        assertEquals("pond-new", cache.get("pond", 2, 1))
        assertNull(cache.get("crt", 2, 0))
    }

    /** At most two names: the one used least lately goes. */
    @Test
    fun onlyAFewAreKept() {
        val cache = ShaderCache<String>()
        cache.put("pond", 1, 0, "pond")
        cache.put("crt", 1, 0, "crt")
        cache.get("pond", 1, 0)
        cache.put("lcd", 1, 0, "lcd")
        assertEquals("pond", cache.get("pond", 1, 0))
        assertNull(cache.get("crt", 1, 0))
        assertEquals("lcd", cache.get("lcd", 1, 0))
    }
}
