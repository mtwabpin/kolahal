package com.mtwabpin.kolahal.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `fetch` (and `neofetch`, `fastfetch`): this phone and Kolahal, lined up to fit the log. */
@RunWith(AndroidJUnit4::class)
class FetchCommandTest {
    @get:Rule val logLines = LogLinesSince()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val commands = CommandRunner(context)

    private fun run(line: String) = commands.lines(line)

    @Test fun fetchShowsThePhoneAndKolahal() {
        val lines = run("fetch")
        val text = lines.map { StyledText.plain(it.text) }
        assertTrue(text.any { "kolahal@" in it })
        assertTrue(text.any { "OS" in it && "Android" in it })
        assertTrue(text.any { "Kolahal" in it && BuildConfig.VERSION_NAME in it })
        // In the terminal font, to keep its columns, and narrow enough not to wrap.
        assertTrue(lines.all { LogContext.MONO in it.ctx.values })
        val cols = Fetch.columns(context)
        assertTrue(text.joinToString("\n"), text.all { it.length <= cols })
    }

    @Test fun fetchHasYourName() {
        val conf = Config.get(context)
        try {
            conf.set(Keys.USER_NAME, "alex")
            assertTrue(run("fetch").any { StyledText.plain(it.text).contains("alex@") })
        } finally {
            conf.reset(Keys.USER_NAME)
        }
    }

    @Test fun neofetchAndFastfetchAreFetch() {
        // By what they print, not how many lines: rows such as Apps join once they've loaded, after a cold start.
        for (name in listOf("neofetch", "fastfetch")) {
            val text = run(name).map { StyledText.plain(it.text) }
            assertTrue(name, text.any { "kolahal@" in it } && text.any { "OS" in it && "Android" in it } && text.any { "Kolahal" in it && BuildConfig.VERSION_NAME in it })
        }
    }
}
