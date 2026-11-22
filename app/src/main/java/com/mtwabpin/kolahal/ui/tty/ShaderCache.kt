/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

/**
 * The shaders loaded last, by name ([rememberTtyShader]), so that a surface composed again (home after a
 * full page, a screen that opens over it) has its shader without reading, hashing, checking and compiling
 * it again. An entry holds for the versions of the shader files and of the trips it was loaded at
 * ([com.mtwabpin.kolahal.shader.Shaders.version], [com.mtwabpin.kolahal.shader.ShaderSafety.version]):
 * at others its code can have changed, or it was turned off or on again, and it is loaded again.
 * At most [max] names are kept. Any thread.
 */
internal class ShaderCache<T : Any>(private val max: Int = 2) {
    private class Entry<T>(val files: Int, val trips: Int, val value: T)

    /** The oldest used first. */
    private val entries = LinkedHashMap<String, Entry<T>>(4, 0.75f, true)

    /** The shader [name] loaded at these versions, or null. */
    @Synchronized
    fun get(name: String, files: Int, trips: Int): T? {
        val entry = entries[name] ?: return null
        if (entry.files == files && entry.trips == trips) return entry.value
        entries.remove(name)
        return null
    }

    /** [value] is the shader [name] at these versions. One loaded at older versions than an entry is left out. */
    @Synchronized
    fun put(name: String, files: Int, trips: Int, value: T) {
        if (entries.values.any { it.files > files || it.trips > trips }) return
        // What was loaded at other versions can't be used any more.
        entries.values.removeAll { it.files != files || it.trips != trips }
        entries[name] = Entry(files, trips, value)
        while (entries.size > max) entries.remove(entries.keys.first())
    }
}
