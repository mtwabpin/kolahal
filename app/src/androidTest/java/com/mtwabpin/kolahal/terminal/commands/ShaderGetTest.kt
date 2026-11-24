package com.mtwabpin.kolahal.terminal.commands

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.ListedShader
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.SharedShaderLists
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.shader.ShaderStore
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `shader get` and `shader get --update`, and installing ([ShaderStore]), from a list kept as if just downloaded. Nothing goes online. */
@RunWith(AndroidJUnit4::class)
class ShaderGetTest {
    @get:Rule
    val configReset = ConfigReset()

    @get:Rule
    val lines = LogLinesSince()

    @get:Rule
    val lists = SharedShaderLists()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val commands = CommandRunner(context)

    private val code = "uniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }"
    private val glow = ListedShader("sg-glow", "Soft glow", "A glow", "ann", "1.0", code)
    private val later = ListedShader("sg-later", "Later", "", "di", "1.0", "$code\n// later", requires = "99.0")

    @After
    fun tearDown() {
        Shaders.remove(context, "sg-own")
    }

    /** Runs [line], whose work is in the background, and waits for the line that starts with [text]. */
    private fun run(line: String, text: String): LogEntry {
        val from = logs.maxId()
        commands.run(line)
        var found: LogEntry? = null
        waitUntil("a line '$text' after $line") {
            found = logs.recent(400, afterId = from).firstOrNull { StyledText.plain(it.text).startsWith(text) }
            found != null
        }
        return found!!
    }

    /** The list names each with a tap that installs it; one another source has first is called by its source. */
    @Test
    fun getListsTheSharedShaders() {
        lists.listed(glow, later)
        lists.listed(glow.copy(title = "Other glow"), source = lists.other)
        val from = logs.maxId()
        run("shader get", "sg-later needs Kolahal 99.0")
        waitUntil("the second source's") {
            logs.recent(400, afterId = from)
                .any { it.ctx.values[LogContext.COMMAND] == "shader get ${lists.other.name}/sg-glow" }
        }
        val first = logs.recent(400, afterId = from).first { it.text == "sg-glow" }
        assertEquals("shader get sg-glow", first.ctx.values[LogContext.COMMAND])
        assertEquals("Soft glow · v1.0 · by ann · A glow", first.ctx.values[LogContext.DESC])
    }

    /** What can't be installed says why; what is installed says so. */
    @Test
    fun getSaysWhyItDoesntInstall() {
        Shaders.save(context, "sg-own", code)
        lists.installed(glow)
        lists.listed(glow, later, glow.copy(name = "sg-own"))
        run("shader get sg-glow", "sg-glow is installed and up to date")
        run("shader get sg-later", "sg-later needs Kolahal 99.0 or newer")
        run("shader get sg-own", "You have your own shader named sg-own")
        val noSource = run("shader get nobody/here/sg-glow", "No source 'nobody/here'\n")
        assertEquals("script sources", noSource.ctx.values[LogContext.COPY])
        Shaders.save(context, "sg-glow", "$code\n// mine")
        val edited = run("shader get sg-glow", "You edited sg-glow\n")
        assertEquals(
            "You edited sg-glow\n'shader get sg-glow --replace' replaces it\n'shader save sg-glow' keeps a copy",
            StyledText.plain(edited.text),
        )
    }

    /** Only what was installed from a list is updated: a shader of the user's own isn't. */
    @Test
    fun updateNeedsAnInstalledShader() {
        Shaders.save(context, "sg-own", code)
        run("shader get --update", "No shader was installed with 'shader get'")
    }

    // ---- installing ------------------------------------------------------------------------

    private fun entry(name: String) = lists.store.cached().first { it.name == name }

    /** Installing saves the file and where it is from; removing it forgets that. */
    @Test
    fun removingForgetsWhereItIsFrom() {
        lists.installed(glow)
        assertEquals(code, Shaders.own(context, "sg-glow"))
        assertEquals(ScriptCatalog.State.INSTALLED, lists.store.state(entry("sg-glow")))
        assertEquals(ScriptCatalog.SOURCE, lists.store.installedFrom("sg-glow"))
        Shaders.remove(context, "sg-glow")
        assertNull(lists.store.recorded("sg-glow"))
        assertEquals(ScriptCatalog.State.NOT_INSTALLED, lists.store.state(entry("sg-glow")))
    }

    /** A file that can't be used, or that needs a newer Kolahal than its list says, isn't installed. */
    @Test
    fun whatCantBeUsedIsntInstalled() {
        lists.listed(glow)
        val e = entry("sg-glow")
        val none = assertThrows(IllegalStateException::class.java) { lists.store.install(e, "half4 main() {}") }
        assertTrue(none.message, "uniform shader content" in none.message!!)
        assertThrows(IllegalStateException::class.java) { lists.store.install(e, "// @requires 99\n$code") }
        assertNull(Shaders.own(context, "sg-glow"))
        assertNull(lists.store.recorded("sg-glow"))
    }

    /** An edited copy stays until it may be replaced, and one of another source always. */
    @Test
    fun anEditedCopyStays() {
        lists.installed(glow)
        Shaders.save(context, "sg-glow", "$code\n// mine")
        val e = entry("sg-glow")
        assertThrows(IllegalStateException::class.java) { lists.store.install(e, code) }
        assertEquals("$code\n// mine", Shaders.own(context, "sg-glow"))
        lists.store.install(e, code, replace = true)
        assertEquals(code, Shaders.own(context, "sg-glow"))
        lists.listed(glow, source = lists.other)
        val theirs = lists.store.cached().first { it.source == lists.other.name }
        assertThrows(IllegalStateException::class.java) { lists.store.install(theirs, code, replace = true) }
    }

    /** A source's list for this version installs as any other, with each file read from the version's folder. */
    @Test
    fun aVersionsListReadsItsFilesFromItsFolder() {
        val folder = checkNotNull(ScriptSources.folderOf(BuildConfig.VERSION_NAME))
        lists.listed(glow, source = lists.other, folder = folder)
        val e = lists.store.cached().first { it.source == lists.other.name }
        assertEquals(folder, e.folder)
        assertEquals(
            "${lists.other.base}$folder/shaders/sg-glow/sg-glow.agsl",
            lists.catalog.address(e, e.path)
        )
        lists.store.install(e, code)
        assertEquals(code, Shaders.own(context, "sg-glow"))
        assertEquals(lists.other.name, lists.store.installedFrom("sg-glow"))
    }

    /** The scripts of a list with shaders are read as ever, and the shaders of one with scripts. */
    @Test
    fun oneListHasBoth() {
        val script = """{"name":"sg-script","lang":"lua","path":"scripts/sg-script/sg-script.lua","sha256":"${"d".repeat(64)}"}"""
        lists.listed(glow)
        lists.catalog.keep(
            ScriptSources.DEFAULT,
            """{"format":1,"scripts":[$script],"shaders":[${glow.json()},{"name":"../x"}]}"""
        )
        val listing = lists.catalog.kept().first { it.source == ScriptSources.DEFAULT }
        assertEquals(listOf("sg-script"), listing.entries.map { it.name })
        assertEquals(listOf("sg-glow"), listing.shaders.map { it.name })
    }
}
