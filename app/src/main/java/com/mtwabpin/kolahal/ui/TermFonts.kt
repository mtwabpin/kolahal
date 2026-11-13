package com.mtwabpin.kolahal.ui

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.FontFiles

/**
 * The terminal fonts as Compose draws them: a font name ([FontFiles.names]) becomes a [FontFamily].
 * The names, the imported file and the import itself are in [FontFiles].
 */
object TermFonts {
    private val bundled: Map<String, FontFamily> by lazy {
        fun family(regular: Int, bold: Int) = FontFamily(Font(regular, FontWeight.Normal), Font(bold, FontWeight.Bold))
        mapOf(
            "jetbrains-mono" to family(R.font.jetbrains_mono_regular, R.font.jetbrains_mono_bold),
            "fira-code" to family(R.font.fira_code_regular, R.font.fira_code_bold),
            "ibm-plex-mono" to family(R.font.ibm_plex_mono_regular, R.font.ibm_plex_mono_bold),
            "source-code-pro" to family(R.font.source_code_pro_regular, R.font.source_code_pro_bold),
        )
    }

    private class Loaded(val path: String, val stamp: Long, val family: FontFamily)

    @Volatile private var customCache: Loaded? = null

    /** The imported font as a family, or null if it is missing or unreadable. Cached per file version. */
    fun customFamily(context: Context): FontFamily? {
        val file = FontFiles.customFile(context) ?: return null
        val stamp = file.lastModified() xor file.length()
        customCache?.let { if (it.path == file.path && it.stamp == stamp) return it.family }
        val tf = FontFiles.loadTypeface(file) ?: return null
        return FontFamily(androidx.compose.ui.text.font.Typeface(tf))
            .also { customCache = Loaded(file.path, stamp, it) }
    }

    /** The font [name] as a family. Returns null only for a custom font that cannot be loaded. */
    fun family(context: Context, name: String): FontFamily? = when (name) {
        FontFiles.CUSTOM -> customFamily(context)
        else -> bundled[name] ?: FontFamily.Monospace
    }
}
