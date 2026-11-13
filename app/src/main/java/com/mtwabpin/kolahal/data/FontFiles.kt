package com.mtwabpin.kolahal.data

import android.content.Context
import java.io.File

/**
 * Where an imported terminal font is kept (`font import`). The screen loads it ([com.mtwabpin.kolahal.ui.TermFonts]),
 * and a backup carries it ([Backup]). It lives here, so the backup doesn't depend on the screen's code.
 */
object FontFiles {
    fun dir(context: Context) = File(context.filesDir, "fonts")

    /** The imported font file, if there is one. */
    fun customFile(context: Context): File? =
        listOf("custom.ttf", "custom.otf").map { File(dir(context), it) }.firstOrNull { it.isFile }
}
