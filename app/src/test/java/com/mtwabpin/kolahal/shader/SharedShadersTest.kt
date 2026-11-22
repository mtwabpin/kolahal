package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptCatalog.State
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.shader.SharedShaders.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shaders in the sources' lists ([SharedShaders]): which entries are kept, where one stands, its record and the search. */
class SharedShadersTest {
    private val sha = "a".repeat(64)

    /** The shaders of an index.json's `shaders`, written as [entries]. */
    private fun parsed(entries: String, source: String = ScriptCatalog.SOURCE) =
        SharedShaders.parse(
            (Json.parse("""{"format":1,"scripts":[],"shaders":[$entries]}""") as Map<*, *>)["shaders"],
            source
        )

    private fun entry(name: String, path: String = "shaders/$name/$name.agsl", sha: String = this.sha) =
        """{"name":"$name","path":"$path","sha256":"$sha"}"""

    // ---- the list --------------------------------------------------------------------------

    @Test
    fun anEntryIsReadWhole() {
        val e = parsed(
            """{"name":"glow","path":"shaders/glow/glow.agsl","sha256":"${sha.uppercase()}","title":"Glow",
               "desc":"A soft glow","author":"ann","version":"1.2","license":"MIT","tags":["soft","light"],
               "animated":true,"requires":"0.5.17","later":"ignored"}""",
            "owner/shaders",
        ).single()
        assertEquals(
            SharedShader(
                "glow", "shaders/glow/glow.agsl", "Glow", "A soft glow", "ann", "1.2", "MIT",
                listOf("soft", "light"), animated = true, requires = "0.5.17", sha256 = sha,
                source = "owner/shaders",
            ),
            e,
        )
    }

    /** Only the name, the path and the checksum are needed. The title is the name then. */
    @Test
    fun whatIsLeftOutIsEmpty() {
        val e = parsed(entry("glow")).single()
        assertEquals("glow", e.title)
        assertEquals(listOf("", "", "", ""), listOf(e.desc, e.author, e.version, e.license))
        assertTrue(e.tags.isEmpty())
        assertFalse(e.animated)
        assertFalse(e.tilt)
        assertNull(e.requires)
        assertEquals(ScriptCatalog.SOURCE, e.source)
    }

    /** `tilt` says that it follows the phone's lean. Only true counts; a list that doesn't have it means no. */
    @Test
    fun tiltIsReadWhenTheListHasIt() {
        fun tilt(value: String) = parsed(entry("glow").dropLast(1) + ""","tilt":$value}""").single().tilt
        assertTrue(tilt("true"))
        for (other in listOf("false", "null", "1", "\"true\"", "[]")) assertFalse(other, tilt(other))
    }

    /** An index without shaders, as every index was before, has none; nor has one with another thing there. */
    @Test
    fun anIndexWithoutShadersHasNone() {
        assertTrue(SharedShaders.parse(null, "x").isEmpty())
        for (other in listOf("\"no\"", "{}", "7", "null", "[1,\"a\",null,[]]")) {
            val root = Json.parse("""{"shaders":$other}""") as Map<*, *>
            assertTrue(other, SharedShaders.parse(root["shaders"], "x").isEmpty())
        }
    }

    /** One bad entry is left out; the good ones stay. */
    @Test
    fun aBadEntryIsLeftOut() {
        assertEquals(
            listOf("good", "fine"),
            parsed(listOf(entry("good"), """{"name":"half"}""", entry("fine")).joinToString()).map { it.name },
        )
    }

    /** A name a shader can't have: not one word, too long, or a word of the shader command. */
    @Test
    fun aNameAShaderCantHaveIsLeftOut() {
        for (name in listOf("", "My Glow", "Glow", "-a", "a/b", "..", "a".repeat(41), "off", "get", "update")) {
            assertTrue(name, parsed(entry(name)).isEmpty())
        }
    }

    /** Only the file in the shader's own folder, however the path is written. */
    @Test
    fun aPathThatIsntItsOwnFileIsLeftOut() {
        for (path in listOf(
            "shaders/glow.agsl",
            "shaders/other/glow.agsl",
            "shaders/glow/glow.lua",
            "shaders/glow/glow.AGSL",
            "scripts/glow/glow.agsl",
            "/shaders/glow/glow.agsl",
            "../shaders/glow/glow.agsl",
            "shaders/glow/../glow/glow.agsl",
            "shaders/./glow/glow.agsl",
            "shaders//glow/glow.agsl",
            "shaders/glow/glow.agsl?x=1",
            "shaders/glow/glow.agsl#x",
            "https://example.com/shaders/glow/glow.agsl",
            "",
        )) assertTrue(path, parsed(entry("glow", path)).isEmpty())
    }

    @Test
    fun aChecksumThatIsntASha256IsLeftOut() {
        for (bad in listOf("", "a".repeat(63), "a".repeat(65), "g".repeat(64), "a".repeat(64) + " ")) {
            assertTrue(bad, parsed(entry("glow", sha = bad)).isEmpty())
        }
    }

    /** The same name twice is the first one: the screen's list needs one of each. */
    @Test
    fun aNameTwiceIsTheFirst() {
        val other = "b".repeat(64)
        assertEquals(listOf(sha), parsed(entry("glow") + "," + entry("glow", sha = other)).map { it.sha256 })
    }

    // ---- where one stands --------------------------------------------------------------------

    private val code = "uniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }"
    private val glow = parsed(entry("glow")).single()
    private val installed = Origin.of(glow, code)

    private fun state(e: SharedShader, mine: String?, origin: Origin?, kolahal: String = "0.5.17") =
        SharedShaders.state(e, mine, origin, kolahal)

    @Test
    fun itsStateFollowsTheFileAndItsRecord() {
        assertEquals(State.NOT_INSTALLED, state(glow, null, null))
        assertEquals(State.INSTALLED, state(glow, code, installed))
        assertEquals(State.UPDATE_AVAILABLE, state(glow.copy(sha256 = "b".repeat(64)), code, installed))
        assertEquals(State.MODIFIED, state(glow, "$code\n// mine", installed))
        // Edited and a newer version: still never replaced without asking.
        assertEquals(State.MODIFIED, state(glow.copy(sha256 = "b".repeat(64)), "$code\n// mine", installed))
    }

    /** The user's own file, one from another source, and the name of a shader that comes with Kolahal. */
    @Test
    fun aNameInUseIsTaken() {
        assertEquals(State.NAME_TAKEN, state(glow, code, null))
        assertEquals(State.NAME_TAKEN, state(glow.copy(source = "owner/shaders"), code, installed))
        assertEquals(State.NAME_TAKEN, state(glow.copy(name = "crt"), null, null))
    }

    @Test
    fun oneForANewerKolahalSaysSo() {
        val later = glow.copy(requires = "0.6")
        assertEquals(State.NEEDS_NEWER, state(later, null, null))
        assertEquals(State.NEEDS_NEWER, state(later, code, installed))
        assertEquals(State.NOT_INSTALLED, state(later, null, null, kolahal = "0.6.0"))
        // Not a version: as for scripts, it keeps nobody out.
        assertEquals(State.NOT_INSTALLED, state(glow.copy(requires = "soon"), null, null))
    }

    /** What the Shaders screen asks: edited, and whether its own source has a newer version it can have. */
    @Test
    fun aCopyIsComparedWithItsSource() {
        val next = glow.copy(sha256 = "b".repeat(64))
        fun compare(mine: String, latest: SharedShader?) =
            SharedShaders.compare(mine, installed, latest, "0.5.17")
        assertEquals(ScriptCatalog.Copy(edited = false, newer = false), compare(code, glow))
        assertEquals(ScriptCatalog.Copy(edited = false, newer = true), compare(code, next))
        assertEquals(ScriptCatalog.Copy(edited = true, newer = true), compare("$code ", next))
        assertFalse(compare(code, null).newer)
        assertFalse(compare(code, next.copy(source = "owner/shaders")).newer)
        assertFalse(compare(code, next.copy(requires = "9.0")).newer)
    }

    // ---- the record --------------------------------------------------------------------------

    /** A record survives being stored, and holds no code: only its hash. */
    @Test
    fun aRecordIsStoredAndRead() {
        assertEquals(installed, Origin.read(installed.text()))
        assertFalse(installed.text(), "content.eval" in installed.text())
        assertTrue(installed.isCode(code))
        assertFalse(installed.isCode("$code\n"))
        assertNotEquals(installed, Origin.of(glow, "$code\n"))
    }

    @Test
    fun whatIsntARecordIsNone() {
        for (raw in listOf(null, "", "x", "[]", "{}", """{"sha256":"a","code":"b"}""")) {
            assertNull(raw, Origin.read(raw))
        }
    }

    // ---- names and the search ----------------------------------------------------------------

    /** `shader get` picks as `script get` does: the first source's, or the one named. */
    @Test
    fun getPicksByNameAndSource() {
        val other = (ScriptSources.parse("owner/shaders") as ScriptSources.Parsed.Ok).source
        val theirs = glow.copy(source = other.name)
        val all = listOf(glow, theirs)
        val sources = listOf(ScriptSources.DEFAULT, other)
        assertEquals(glow, ScriptSources.pick(all, "glow", sources))
        assertEquals(theirs, ScriptSources.pick(all, "owner/shaders/glow", sources))
        assertNull(ScriptSources.pick(all, "nobody/here/glow", sources))
        assertNull(ScriptSources.pick(all, "tide", sources))
        assertEquals("glow", ScriptSources.ref(all, glow))
        assertEquals("owner/shaders/glow", ScriptSources.ref(all, theirs))
    }

    @Test
    fun theSearchNeedsEveryWord() {
        val rain = glow.copy(
            name = "storm", title = "Night storm", desc = "Drops behind the text", author = "bo",
            tags = listOf("weather"),
        )
        val all = listOf(glow, rain)
        fun found(q: String) = SharedShaders.found(all, q).map { it.name }
        assertEquals(listOf("glow", "storm"), found("  "))
        assertEquals(listOf("storm"), found("weather NIGHT"))
        assertEquals(listOf("storm"), found("drops bo"))
        assertEquals(listOf("glow"), found("glow"))
        assertEquals(emptyList<String>(), found("glow weather"))
    }
}
