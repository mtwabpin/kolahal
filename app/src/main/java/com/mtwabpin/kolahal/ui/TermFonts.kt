package com.mtwabpin.kolahal.ui

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Keys
import java.io.File

/**
 * Terminal fonts:
 * - the bundled fonts (res/font, OFL — see assets/licenses);
 * - the system monospace font;
 * - one font file imported by the user, stored at `filesDir/fonts/custom.ttf|otf`.
 */
object TermFonts {
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

    private val bundled: Map<String, FontFamily> by lazy {
        fun family(regular: Int, bold: Int) = FontFamily(Font(regular, FontWeight.Normal), Font(bold, FontWeight.Bold))
        mapOf(
            "jetbrains-mono" to family(R.font.jetbrains_mono_regular, R.font.jetbrains_mono_bold),
            "fira-code" to family(R.font.fira_code_regular, R.font.fira_code_bold),
            "ibm-plex-mono" to family(R.font.ibm_plex_mono_regular, R.font.ibm_plex_mono_bold),
            "source-code-pro" to family(R.font.source_code_pro_regular, R.font.source_code_pro_bold),
        )
    }

    fun dir(context: Context) = File(context.filesDir, "fonts")

    /** The imported font file, if there is one. */
    fun customFile(context: Context): File? =
        listOf("custom.ttf", "custom.otf").map { File(dir(context), it) }.firstOrNull { it.isFile }

    /** Loads [file] now. Returns null if it is not a usable font, so nothing fails later during layout. */
    fun loadTypeface(file: File): android.graphics.Typeface? = runCatching {
        // Font.Builder throws on a corrupt file. Typeface.createFromFile silently returns DEFAULT instead.
        if (Build.VERSION.SDK_INT >= 29) android.graphics.fonts.Font.Builder(file).build()
        android.graphics.Typeface.createFromFile(file)
    }.getOrNull()?.takeIf { it != android.graphics.Typeface.DEFAULT }

    private class Loaded(val path: String, val stamp: Long, val family: FontFamily)

    @Volatile private var customCache: Loaded? = null

    /** Increased after an import, so previews read the file again even if `font` was already custom. */
    var revision by mutableIntStateOf(0)

    /** The imported font as a family, or null if it is missing or unreadable. Cached per file version. */
    fun customFamily(context: Context): FontFamily? {
        val file = customFile(context) ?: return null
        val stamp = file.lastModified() xor file.length()
        customCache?.let { if (it.path == file.path && it.stamp == stamp) return it.family }
        val tf = loadTypeface(file) ?: return null
        return FontFamily(androidx.compose.ui.text.font.Typeface(tf))
            .also { customCache = Loaded(file.path, stamp, it) }
    }

    /** The font [name] as a family. Returns null only for a custom font that cannot be loaded. */
    fun family(context: Context, name: String): FontFamily? = when (name) {
        CUSTOM -> customFamily(context)
        else -> bundled[name] ?: FontFamily.Monospace
    }

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
        return name
    }
}
