package com.mtwabpin.kolahal.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The terminal fonts by name, and the file of the one you imported (`font import`):
 * - the bundled fonts (res/font, OFL — see assets/licenses);
 * - the system monospace font;
 * - one font file imported by the user, stored at `filesDir/fonts/custom.ttf|otf`.
 *
 * Commands, Settings and backups need only the names and the file, so they live here. The screen
 * turns a name into a font to draw with ([com.mtwabpin.kolahal.ui.TermFonts]).
 */
object FontFiles {
    const val SYSTEM = "system"
    const val CUSTOM = "custom"

    /** Imported fonts larger than this are rejected. */
    const val MAX_BYTES = 10L * 1024 * 1024
    private val EXTENSION = Regex("[a-z][a-z0-9]{0,4}")

    val names: List<String> get() = Keys.FONT.choices

    fun label(name: String) = when (name) {
        SYSTEM -> "System mono"
        "jetbrains-mono" -> "JetBrains Mono"
        "fira-code" -> "Fira Code"
        "ibm-plex-mono" -> "IBM Plex Mono"
        "source-code-pro" -> "Source Code Pro"
        CUSTOM -> "Custom"
        else -> name
    }

    fun dir(context: Context) = File(context.filesDir, "fonts")

    /** The imported font file, if there is one. */
    fun customFile(context: Context): File? =
        listOf("custom.ttf", "custom.otf").map { File(dir(context), it) }.firstOrNull { it.isFile }

    private val _imports = MutableStateFlow(0)

    /** Goes up after each import, so previews read the file again even if `font` was already custom. */
    val imports: StateFlow<Int> = _imports.asStateFlow()

    /** Loads [file] now. Returns null if it is not a usable font, so nothing fails later during layout. */
    fun loadTypeface(file: File): android.graphics.Typeface? = runCatching {
        // Font.Builder throws on a corrupt file. Typeface.createFromFile silently returns DEFAULT instead.
        if (Build.VERSION.SDK_INT >= 29) android.graphics.fonts.Font.Builder(file).build()
        android.graphics.Typeface.createFromFile(file)
    }.getOrNull()?.takeIf { it != android.graphics.Typeface.DEFAULT }

    /**
     * Copies the font at [uri] into the app as the custom font. The size, the file header and Android
     * itself must all accept it first.
     * Returns the file's display name if the provider gives one. Throws with a message for the log if
     * the file is not a usable font.
     */
    fun importFrom(context: Context, uri: android.net.Uri): String? {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        // An extension is the part after the last dot and starts with a letter. "Iosevka 27.3" has none;
        // ".woff2" has one.
        val named = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.matches(EXTENSION) }
        // Some providers give no display name, or a name without an extension. Then the file header decides.
        if (named != null && named != "ttf" && named != "otf") throw IllegalArgumentException("pick a .ttf or .otf file (got $name)")
        if (size > MAX_BYTES) throw IllegalArgumentException("font is larger than 10 MB")
        val dir = dir(context).apply { mkdirs() }
        val tmp = File(dir, "import.tmp")
        try {
            val input = resolver.openInputStream(uri) ?: throw java.io.IOException("cannot open the file")
            input.use { inp ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IllegalArgumentException("font is larger than 10 MB")
                        out.write(buf, 0, n)
                    }
                }
            }
            val magic = tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
            val ext = when {
                magic.contentEquals(byteArrayOf(0x4F, 0x54, 0x54, 0x4F)) -> "otf" // "OTTO": CFF outlines
                magic.contentEquals(byteArrayOf(0, 1, 0, 0)) || magic.contentEquals("true".toByteArray()) -> "ttf"
                else -> throw IllegalArgumentException("${name ?: "the file"} is not a TrueType/OpenType font")
            }
            if (loadTypeface(tmp) == null) throw IllegalArgumentException("${name ?: "the file"} is not a font Android can load")
            listOf("custom.ttf", "custom.otf").forEach { File(dir, it).delete() }
            if (!tmp.renameTo(File(dir, "custom.$ext"))) throw java.io.IOException("could not save the font")
        } finally {
            tmp.delete()
        }
        _imports.value++
        return name
    }
}
