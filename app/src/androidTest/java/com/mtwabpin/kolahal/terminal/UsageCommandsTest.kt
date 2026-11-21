package com.mtwabpin.kolahal.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** `data today`, `data month` and `storage`: the phone's data and storage, per app, from Android's usage data. */
@RunWith(AndroidJUnit4::class)
class UsageCommandsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val commands = CommandRunner(context)
    private val config = Config.get(context)

    @Before
    fun setUp() {
        val apps = AppRepository.get(context)
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
    }

    private fun withUsageAccess(mode: String, block: () -> Unit) {
        val before =
            Regex("GET_USAGE_STATS: (\\w+)").find(shell("appops get ${context.packageName} GET_USAGE_STATS"))?.groupValues?.get(
                1
            ) ?: "default"
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS $mode")
            block()
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS $before")
        }
    }

    private fun lines(line: String) = commands.ownLines(line)

    private val size = "(<1|\\d+|\\d+\\.\\d) (MB|GB)"

    /** Without usage access each says to allow it, with a tap that opens the page. */
    @Test
    fun theyNeedUsageAccess() = withUsageAccess("ignore") {
        for ((typed, said) in listOf(
            "data today" to "Data use",
            "data month" to "Data use",
            "storage" to "Storage per app"
        )) {
            val denied = lines(typed).single()
            assertEquals("$said needs usage access — tap to allow it", denied.text)
            assertEquals("usage-access", denied.ctx.values[LogContext.RUN])
        }
    }

    /** `data today`: the day's Wi-Fi (and mobile) data, large, then a table of the apps, or that none was used. */
    @Test
    fun dataToday() = withUsageAccess("allow") {
        val shown = lines("data today")
        val first = StyledText.plain(shown.first().text)
        assertTrue(first, Regex("($size Mobile today · )?$size Wi-Fi today").matches(first))
        val rest = shown.drop(1).map { StyledText.plain(it.text) }
        assertTrue(
            rest.toString(),
            rest.firstOrNull() == "No data used" || rest.firstOrNull()
                ?.startsWith("App\tMobile\tWi-Fi") == true
        )
    }

    /** `data month`: since the plan's day; with a plan, a bar and how much of it is used. */
    @Test
    fun dataMonthWithAPlan() = withUsageAccess("allow") {
        val plan = config[Keys.DATA_PLAN]
        try {
            config.set(Keys.DATA_PLAN, "75")
            val shown = lines("data month").map { StyledText.plain(it.text) to it }
            assertTrue(shown.first().first, shown.first().first.contains("Wi-Fi"))
            // On a phone that tells its mobile data: the bar, then the share of the plan, the pace and the days left.
            val share = shown.firstOrNull { it.first.contains("% of 75.0 GB") }
            if (share != null) assertTrue(
                share.first,
                Regex("\\d+% of 75.0 GB · on pace for $size · \\d+ days? left").matches(share.first)
            )
        } finally {
            config.set(Keys.DATA_PLAN, plan.toString())
        }
    }

    /** `storage`: free and used space, the largest apps, then the apps not opened for 30 days (each a line of its own) or that there are none. */
    @Test
    fun storage() = withUsageAccess("allow") {
        val shown = lines("storage")
        val first = StyledText.plain(shown.first().text)
        assertTrue(first, Regex("$size Free · $size Used of $size").matches(first))
        val texts = shown.map { StyledText.plain(it.text) }
        val largest = texts.indexOf("Largest apps")
        assertTrue(texts.toString(), largest > 0)
        assertTrue(texts[largest + 1], texts[largest + 1].startsWith("App\tSize\tCache"))
        assertTrue(
            texts.toString(),
            texts.any { it == "Not opened for 30 days or more" || it == "Every app you can uninstall was opened in the last 30 days" })
        // An unused app's line stands for the app: a tap opens it, a long-press offers its actions.
        val unused = texts.indexOf("Not opened for 30 days or more")
        if (unused >= 0) {
            val app = shown[unused + 1]
            assertTrue(app.ctx.pkg != null)
            assertTrue(
                app.ctx.values[LogContext.DESC].orEmpty(),
                Regex("(\\d+ days|no use recorded)( · $size)?").matches(app.ctx.values[LogContext.DESC].orEmpty())
            )
        }
    }

    /** `data` with something else says how to use it; `data` alone still opens the panel (not run here). */
    @Test
    fun dataWithAnUnknownWord() = withUsageAccess("allow") {
        assertEquals(listOf("Usage: data [today | month]"), lines("data week").map { it.text })
    }
}
