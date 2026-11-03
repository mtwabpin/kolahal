package com.mtwabpin.kolahal.ui

import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.R
import java.io.File
import java.io.RandomAccessFile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** `font import`: the file it takes in as the custom font, the ones it refuses, and how that font loads. */
@RunWith(AndroidJUnit4::class)
class FontImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fonts = TermFonts.dir(context)
    private val names = listOf("custom.ttf", "custom.otf")
    /** The custom font there was before the test, put back after it. */
    private val before = names.mapNotNull { n -> File(fonts, n).takeIf { it.isFile }?.let { n to it.readBytes() } }
    /** Files the test made to pick from, each in a folder of its own. */
    private val made = ArrayList<File>()
    /** The folders those are in, and the fonts', gone again after the test if they weren't there before. */
    private val folders = listOf(fonts, File(context.cacheDir, "updates")).filter { !it.exists() }

    private val jetbrains = bytes(R.font.jetbrains_mono_regular)
    private val fira = bytes(R.font.fira_code_regular)

    @Before fun setUp() = names.forEach { File(fonts, it).delete() }

    @After fun tearDown() {
        made.forEach { it.deleteRecursively() }
        (names + "import.tmp").forEach { File(fonts, it).delete() }
        folders.forEach { it.delete() }
        before.forEach { (name, bytes) -> File(fonts.apply { mkdirs() }, name).writeBytes(bytes) }
    }

    private fun bytes(font: Int) = context.resources.openRawResource(font).use { it.readBytes() }

    private fun file(name: String, bytes: ByteArray?, size: Long?, dir: File): File {
        val folder = File(dir, "font-import-${System.nanoTime()}").apply { mkdirs() }
        made += folder
        return File(folder, name).apply {
            writeBytes(bytes ?: ByteArray(0))
            // Sparse: as long as a big font, with nothing written.
            if (size != null) RandomAccessFile(this, "rw").use { it.setLength(size) }
        }
    }

    /** [name] as a file picker gives it: from a provider, which tells its name and size. */
    private fun picked(name: String, bytes: ByteArray? = null, size: Long? = null): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file(name, bytes, size, File(context.cacheDir, "updates")))

    /** A file with nothing to tell but its bytes, as some providers give them. */
    private fun unnamed(bytes: ByteArray? = null, size: Long? = null): Uri = Uri.fromFile(file("font", bytes, size, context.cacheDir))

    private fun saved(name: String) = File(fonts, name)

    /** The message [uri]'s import is refused with; it fails the test if it is taken in. */
    private fun refusal(uri: Uri): String {
        try {
            TermFonts.importFrom(context, uri)
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        fail("taken in")
        throw AssertionError()
    }

    private fun assertNothingSaved() {
        assertNull(TermFonts.customFile(context))
        assertFalse("the copy was left", saved("import.tmp").exists())
    }

    /** A font picked is copied in whole as the custom font, which then loads; its name is what the log says. */
    @Test fun aFontPickedBecomesTheCustomFont() {
        assertNull(TermFonts.customFamily(context))
        assertNull(TermFonts.family(context, TermFonts.CUSTOM))

        assertEquals("Mono.TTF", TermFonts.importFrom(context, picked("Mono.TTF", jetbrains)))
        assertEquals(saved("custom.ttf"), TermFonts.customFile(context))
        assertArrayEquals(jetbrains, saved("custom.ttf").readBytes())
        assertFalse(saved("import.tmp").exists())
        assertNotNull(TermFonts.customFamily(context))
        assertNotNull(TermFonts.family(context, TermFonts.CUSTOM))
    }

    /** The file's header, not its name, says which kind it is saved as; it takes the place of the font before. */
    @Test fun theHeaderSaysTheKindAndTheNewFontReplacesTheOld() {
        fonts.mkdirs()
        saved("custom.otf").writeBytes(byteArrayOf(0x4F, 0x54, 0x54, 0x4F))
        TermFonts.importFrom(context, picked("Mono.otf", jetbrains))
        assertFalse(saved("custom.otf").exists())
        assertArrayEquals(jetbrains, saved("custom.ttf").readBytes())
    }

    /** With no name from the provider, a font is still taken in by its header. */
    @Test fun aFontWithNoNameIsTakenByItsHeader() {
        assertNull(TermFonts.importFrom(context, unnamed(jetbrains)))
        assertArrayEquals(jetbrains, saved("custom.ttf").readBytes())
    }

    /** Named, only .ttf and .otf files are taken. */
    @Test fun onlyTtfAndOtfFilesAreTaken() {
        assertEquals("pick a .ttf or .otf file (got notes.txt)", refusal(picked("notes.txt", jetbrains)))
        assertNothingSaved()
    }

    /** A name with no extension says nothing of the kind: the header decides, as with no name. */
    @Test fun aNameWithNoExtensionIsTakenByItsHeader() {
        assertEquals("Mono is not a TrueType/OpenType font", refusal(picked("Mono", "hello, world".toByteArray())))
        assertNothingSaved()
        assertEquals("Mono", TermFonts.importFrom(context, picked("Mono", jetbrains)))
        assertArrayEquals(jetbrains, saved("custom.ttf").readBytes())
        // A dot in a version is no extension either.
        assertEquals("Iosevka 27.3", TermFonts.importFrom(context, picked("Iosevka 27.3", jetbrains)))
    }

    /** Over 10 MB is refused by the size the provider gives, or else part way through the copy. */
    @Test fun aFontOverTenMegabytesIsRefused() {
        assertEquals("font is larger than 10 MB", refusal(picked("Big.ttf", size = TermFonts.MAX_BYTES + 1)))
        assertNothingSaved()
        assertEquals("font is larger than 10 MB", refusal(unnamed(jetbrains, size = TermFonts.MAX_BYTES + 1)))
        assertNothingSaved()
    }

    /** Exactly 10 MB is let through to be read: what decides then is the header. */
    @Test fun tenMegabytesIsNotTooBig() {
        assertEquals("Big.ttf is not a TrueType/OpenType font", refusal(picked("Big.ttf", size = TermFonts.MAX_BYTES)))
        assertNothingSaved()
    }

    /** A file whose header isn't a font's is refused, by name when there is one; an empty one too. */
    @Test fun aFileThatIsntAFontIsRefused() {
        assertEquals("Mono.ttf is not a TrueType/OpenType font", refusal(picked("Mono.ttf", "hello, world".toByteArray())))
        assertEquals("the file is not a TrueType/OpenType font", refusal(unnamed("hello, world".toByteArray())))
        assertEquals("Mono.ttf is not a TrueType/OpenType font", refusal(picked("Mono.ttf", ByteArray(0))))
        assertNothingSaved()
    }

    /** A font's header with no font after it is refused before it can fail at layout. */
    @Test fun aFontAndroidCantLoadIsRefused() {
        val truetype = byteArrayOf(0, 1, 0, 0) + ByteArray(4096) { 0x5A }
        val opentype = byteArrayOf(0x4F, 0x54, 0x54, 0x4F) + ByteArray(4096) { 0x5A }
        assertEquals("Mono.ttf is not a font Android can load", refusal(picked("Mono.ttf", truetype)))
        assertEquals("the file is not a font Android can load", refusal(unnamed(opentype)))
        assertNothingSaved()
    }

    /** A file refused leaves the font imported before it as it was. */
    @Test fun aRefusedFileKeepsTheFontThereWas() {
        TermFonts.importFrom(context, picked("Mono.ttf", jetbrains))
        refusal(picked("Mono.ttf", "hello, world".toByteArray()))
        refusal(picked("Big.ttf", size = TermFonts.MAX_BYTES + 1))
        assertArrayEquals(jetbrains, saved("custom.ttf").readBytes())
        assertNotNull(TermFonts.customFamily(context))
    }

    /** The custom font is read once per version of its file: again after a new import, and not at all once broken. */
    @Test fun theCustomFontIsReadOncePerVersion() {
        TermFonts.importFrom(context, picked("Mono.ttf", jetbrains))
        val first = TermFonts.customFamily(context)
        assertNotNull(first)
        assertSame(first, TermFonts.customFamily(context))

        TermFonts.importFrom(context, picked("Fira.ttf", fira))
        val second = TermFonts.customFamily(context)
        assertNotNull(second)
        assertNotSame(first, second)

        saved("custom.ttf").writeBytes(byteArrayOf(0, 1, 0, 0) + ByteArray(64))
        assertNull(TermFonts.customFamily(context))
        assertNull(TermFonts.family(context, TermFonts.CUSTOM))
    }

    /** A file Android can't read as a font loads as nothing, not as the default font. */
    @Test fun onlyARealFontLoads() {
        assertNotNull(TermFonts.loadTypeface(file("a.ttf", jetbrains, null, context.cacheDir)))
        assertNull(TermFonts.loadTypeface(file("b.ttf", ByteArray(0), null, context.cacheDir)))
        assertNull(TermFonts.loadTypeface(file("c.ttf", "hello, world".toByteArray(), null, context.cacheDir)))
        assertNull(TermFonts.loadTypeface(File(context.cacheDir, "font-import-missing.ttf")))
    }

    /** Each bundled font is a family of its own; the system's, or a name not known, is the system monospace. */
    @Test fun eachBundledFontIsItsOwnFamily() {
        val bundled = TermFonts.names - TermFonts.SYSTEM - TermFonts.CUSTOM
        val families = bundled.map { TermFonts.family(context, it) }
        assertTrue(families.none { it == null || it == FontFamily.Monospace })
        assertEquals(families.size, families.toSet().size)
        assertEquals(FontFamily.Monospace, TermFonts.family(context, TermFonts.SYSTEM))
        assertEquals(FontFamily.Monospace, TermFonts.family(context, "comic-mono"))
    }
}
