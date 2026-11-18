package com.mtwabpin.kolahal.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.ScriptEngine
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.buildRows
import com.mtwabpin.kolahal.terminal.lineTimeFormat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * The website's pictures of the scripting guide's log examples ([DocsExamples.logs]). Each
 * example's Lua code runs as a script, and its lines are drawn as the log draws them, in
 * Kolahal's default theme (green, dark), as the website's other screenshots are. Nothing is
 * fetched: the picture line's picture is drawn here.
 *
 * The pictures go to the app's own files on the device, as `log-<name>.png`, with `manifest.json`:
 *
 *     adb pull /sdcard/Android/data/com.mtwabpin.kolahal.debug/files/gallery-log
 */
@RunWith(AndroidJUnit4::class)
class LogGalleryTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val paletteBefore = Term.palette
    private val name = "example"
    private val id = 920_000_000L + System.nanoTime() % 1_000_000

    @Before fun setUp() {
        Term.palette = Palettes.green
        pictureFile(DocsExamples.COMIC).outputStream().use { comic().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @After fun tearDown() {
        Term.palette = paletteBefore
        pictureFile(DocsExamples.COMIC).delete()
        lines().forEach { logs.delete(it.id) }
        KvStore(context, ScriptRepository.scopeOf(id)).let { s -> s.all().keys.forEach { s.set(it, null) } }
        LogImages.trim()
    }

    private fun lines(): List<LogEntry> = logs.recent(limit = 200).filter { it.source == name }.sortedBy { it.id }

    /** Runs [ex]'s code as a script, and returns the lines it logged. */
    private fun run(ex: DocsExamples.Log): List<LogEntry> {
        lines().forEach { logs.delete(it.id) }
        val host = HostApi(context, name, scriptId = id, notifyEnabled = false)
        runBlocking { ScriptEngine.forLang(ScriptLang.LUA).run(ex.before + ex.code.trimIndent(), name, host, 10_000, false, emptyList(), null) }
        host.flush()
        return lines()
    }

    private var shown by mutableStateOf<List<LogRow.Entry>>(emptyList())

    @Test fun theGuidesLogExamplesAreDrawnAndSaved() {
        val out = checkNotNull(context.getExternalFilesDir("gallery-log")) { "no external files" }
        out.listFiles()?.forEach { it.delete() }
        val times = lineTimeFormat(clock24 = true)
        compose.setContent {
            KolahalTheme {
                val indents = rememberLineIndents()
                Column(Modifier.testTag(SHOT).width(WIDTH_DP.dp).background(Term.bg).padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                    for (row in shown) key(row.entry.id) {
                        LogLine(
                            row, TimeMode.OFF, times, dim = false, topGap = 0.dp, itemGap = if (row.itemGap) 5.dp else 0.dp,
                            indents = indents, highlighted = false, apps = { emptyList() },
                            onTap = {}, onLongPress = {}, onAppTap = {}, onAppLongPress = {},
                        )
                    }
                }
            }
        }
        val pictures = ArrayList<String>()
        for (ex in DocsExamples.logs) {
            val entries = run(ex)
            assertTrue("${ex.name} logged nothing", entries.isNotEmpty())
            shown = buildRows(entries).filterIsInstance<LogRow.Entry>()
            compose.waitForIdle()
            // A picture line loads its picture off the main thread.
            if (entries.any { it.ctx.values.containsKey("image") }) {
                Thread.sleep(1_000)
                compose.waitForIdle()
            }
            val picture = compose.onNodeWithTag(SHOT).captureToImage().asAndroidBitmap()
            val file = File(out, "log-${ex.name}.png")
            file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("${file.name} is empty", file.length() > 0)
            pictures += """{"name":"log-${ex.name}","w":$WIDTH_DP}"""
        }
        val density = context.resources.displayMetrics.density
        File(out, "manifest.json").writeText("""{"density":$density,"lines":[${pictures.joinToString(",")}]}""")
    }

    /** Where [LogImages] keeps [url]'s download: put there, nothing is fetched. */
    private fun pictureFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "log-images").apply { mkdirs() }, hash)
    }

    /** A strip with two stick figures, as a comic has them: a stand-in for the xkcd picture. */
    private fun comic(): Bitmap {
        val p = Palettes.green
        val picture = Bitmap.createBitmap(720, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        canvas.drawColor(lerp(p.surface, p.fg, 0.08f).toArgb())
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.fg.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        for (x in listOf(230f, 490f)) {
            canvas.drawCircle(x, 110f, 26f, ink)
            canvas.drawLine(x, 136f, x, 215f, ink)
            canvas.drawLine(x - 34f, 172f, x + 34f, 160f, ink)
            canvas.drawLine(x, 215f, x - 26f, 270f, ink)
            canvas.drawLine(x, 215f, x + 26f, 270f, ink)
        }
        canvas.drawLine(280f, 50f, 440f, 50f, ink)
        canvas.drawLine(300f, 72f, 400f, 72f, ink)
        canvas.drawRect(12f, 12f, 708f, 288f, ink)
        return picture
    }

    private companion object {
        const val SHOT = "log-gallery-shot"

        /** A phone's log is about this wide, in dp. */
        const val WIDTH_DP = 360
    }
}
