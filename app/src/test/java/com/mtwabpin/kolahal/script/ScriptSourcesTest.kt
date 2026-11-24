package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The sources of Get more scripts ([ScriptSources]): their addresses, their order, a backup's list,
 * and the list of a Kolahal version.
 */
class ScriptSourcesTest {
    private fun ok(input: String) = (ScriptSources.parse(input) as ScriptSources.Parsed.Ok).source
    private fun bad(input: String) = (ScriptSources.parse(input) as ScriptSources.Parsed.Bad).why

    private val alice =
        ScriptSources.Source("alice/scripts", "https://alice.github.io/scripts/index.json")
    private val bob = ScriptSources.Source("bob/bob.github.io", "https://bob.github.io/index.json")

    private fun entry(name: String, source: String) = ScriptCatalog.Entry(
        name,
        ScriptLang.LUA,
        "scripts/$name/$name.lua",
        name,
        "",
        "",
        "1.0",
        emptyList(),
        emptyList(),
        emptyList(),
        false,
        null,
        emptyList(),
        null,
        "sha-$source-$name",
        source,
    )

    /** GitHub's owner/name reads that repository's Pages site; a repository named owner.github.io is the site's root. */
    @Test
    fun ownerAndNameReadGitHubPages() {
        assertEquals(alice, ok("alice/scripts"))
        assertEquals(alice, ok("  Alice/scripts/ "))
        assertEquals(bob, ok("bob/bob.github.io"))
        assertEquals(bob, ok("Bob/Bob.github.io"))
        // A link to the repository on github.com means the same.
        assertEquals(alice, ok("https://github.com/alice/scripts"))
        assertEquals(alice, ok("github.com/alice/scripts.git"))
        assertEquals("https://alice.github.io/scripts/", alice.base)
    }

    /** Any https link to an index.json is read as it is; its folder holds the scripts. */
    @Test
    fun aLinkToAnIndexIsReadAsItIs() {
        val s = ok("https://Example.com/kolahal/index.json")
        assertEquals("https://example.com/kolahal/index.json", s.index)
        assertEquals("example.com/kolahal", s.name)
        assertEquals("https://example.com/kolahal/", s.base)
        assertEquals(
            "https://example.com/kolahal/index.json",
            ok("https://example.com/kolahal/").index
        )
        assertEquals("example.com", ok("https://example.com").name)
        // A GitHub Pages link gets the owner/name it would get typed that way.
        assertEquals(alice, ok("https://alice.github.io/scripts/index.json"))
        assertEquals(bob, ok("https://bob.github.io/index.json"))
        assertEquals("alice.github.io/a/b", ok("https://alice.github.io/a/b/index.json").name)
        // Nothing else about the address is checked.
        assertEquals(
            "https://192.168.1.2:8443/index.json",
            ok("https://192.168.1.2:8443/index.json").index
        )
    }

    /** Only https, only an index.json, and only names GitHub allows. */
    @Test
    fun whatIsntASourceSaysWhy() {
        assertEquals("Only https:// links", bad("http://example.com/index.json"))
        assertEquals("Only https:// links", bad("ftp://example.com/index.json"))
        assertEquals("The link must end in index.json", bad("https://example.com/list.json"))
        assertEquals("The link must end in index.json", bad("https://example.com/index.json?x=1"))
        assertTrue(bad("").startsWith("Enter owner/name"))
        assertTrue(bad("just-a-word").startsWith("Enter owner/name"))
        assertTrue(bad("a/b/c").startsWith("Enter owner/name"))
        assertTrue(bad("-alice/scripts").startsWith("Enter owner/name"))
        assertTrue(bad("alice/..").startsWith("Enter owner/name"))
    }

    /** kolahal-scripts is first and is never added twice; each source is there once, in the order added. */
    @Test
    fun kolahalScriptsComesFirst() {
        assertEquals(listOf(ScriptSources.DEFAULT), ScriptSources.list(emptyList()))
        assertEquals(ScriptSources.DEFAULT, ok("mtwabpin/kolahal-scripts"))
        val added = ScriptSources.adding(ScriptSources.adding(emptyList(), alice), bob)
        assertEquals(listOf(alice.index, bob.index), added)
        assertSame(added, ScriptSources.adding(added, alice))
        assertEquals(added, ScriptSources.adding(added, ScriptSources.DEFAULT))
        assertEquals(listOf(ScriptSources.DEFAULT, alice, bob), ScriptSources.list(added))
        assertEquals(
            listOf(ScriptSources.DEFAULT, alice, bob),
            ScriptSources.list(added + alice.index)
        )
    }

    /** A source is removed by its name, its address or what added it. kolahal-scripts stays. */
    @Test
    fun removing() {
        val added = listOf(alice.index, bob.index)
        assertEquals(listOf(bob.index), ScriptSources.removing(added, "alice/scripts"))
        assertEquals(listOf(alice.index), ScriptSources.removing(added, bob.index))
        assertEquals(added, ScriptSources.removing(added, "kolahal-scripts"))
        val all = ScriptSources.list(added)
        assertEquals(alice, ScriptSources.find(all, "Alice/scripts"))
        assertEquals(bob, ScriptSources.find(all, "bob/bob.github.io"))
        assertEquals(ScriptSources.DEFAULT, ScriptSources.find(all, "mtwabpin/kolahal-scripts"))
        assertNull(ScriptSources.find(all, "carol/scripts"))
    }

    /**
     * `script get <name>` takes the first source that has the name; `source/name` picks one. The
     * list calls a script by the name, or by source/name when another source has it first.
     */
    @Test
    fun theFirstSourceWinsAName() {
        val sources = ScriptSources.list(listOf(alice.index, bob.index))
        val tideK = entry("tide", "kolahal-scripts")
        val tideA = entry("tide", "alice/scripts")
        val moon = entry("moon", "alice/scripts")
        val moonB = entry("moon", "bob/bob.github.io")
        val entries = listOf(tideK, tideA, moon, moonB)
        assertSame(tideK, ScriptSources.pick(entries, "tide", sources))
        assertSame(tideA, ScriptSources.pick(entries, "alice/scripts/tide", sources))
        assertSame(tideK, ScriptSources.pick(entries, "kolahal-scripts/tide", sources))
        assertSame(tideK, ScriptSources.pick(entries, "mtwabpin/kolahal-scripts/tide", sources))
        assertSame(moon, ScriptSources.pick(entries, "moon", sources))
        assertSame(moonB, ScriptSources.pick(entries, "bob/bob.github.io/moon", sources))
        assertNull(ScriptSources.pick(entries, "bob/bob.github.io/tide", sources))
        assertNull(ScriptSources.pick(entries, "carol/x/tide", sources))
        assertNull(ScriptSources.pick(entries, "sun", sources))
        assertEquals(
            listOf("tide", "alice/scripts/tide", "moon", "bob/bob.github.io/moon"),
            entries.map { ScriptSources.ref(entries, it) })
    }

    /** A backup holds the added sources' addresses. An import adds those it doesn't have, after its own, and skips what isn't a source. */
    @Test
    fun aBackupAddsTheSourcesItDoesntHave() {
        val saved: List<Any?> = listOf(alice.index, bob.index)
        assertEquals(saved, ScriptSources.fromBackup(emptyList(), saved))
        val skipped = ArrayList<String>()
        val carol = "https://carol.example/k/index.json"
        val restored = ScriptSources.fromBackup(
            listOf(carol, bob.index),
            saved + listOf("http://plain.example/index.json", null, ScriptSources.DEFAULT.index),
            skipped
        )
        assertEquals(listOf(carol, bob.index, alice.index), restored)
        assertEquals(
            listOf("script source http://plain.example/index.json", "script source null"),
            skipped
        )
    }

    // ---- the list of a version -------------------------------------------------------------

    /** Each source has a folder for each version, beside its index.json, whatever its address. */
    @Test
    fun aVersionsListIsInAFolderBesideTheIndex() {
        assertEquals(
            "https://mtwabpin.github.io/kolahal-scripts/0.5.17/index.json",
            ScriptSources.DEFAULT.folderIndex("0.5.17")
        )
        assertEquals("https://alice.github.io/scripts/0.5.17/index.json", alice.folderIndex("0.5.17"))
        assertEquals("https://bob.github.io/0.5.17/index.json", bob.folderIndex("0.5.17"))
        assertEquals(
            "https://example.com/kolahal/0.5.17/index.json",
            ok("https://example.com/kolahal/index.json").folderIndex("0.5.17")
        )
        assertEquals(
            "https://example.com/k/0.5.17/index.json",
            ok("https://example.com/k/").folderIndex("0.5.17")
        )
        assertEquals("https://example.com/0.5.17/index.json", ok("https://example.com").folderIndex("0.5.17"))
        // The files of a list are under its folder; those of the general list under the source's.
        assertEquals("https://alice.github.io/scripts/0.5.17/", alice.folderBase("0.5.17"))
        assertEquals(alice.base, alice.folderBase(null))
    }

    /** A folder is three numbers, so it can't lead out of the source. */
    @Test
    fun aFolderIsOnlyAVersion() {
        for (bad in listOf("..", "../x", "0.5", "0.5.17/..", "0.5.17/../x", "a.b.c", "", "0.5.17.1", "/0.5.17")) {
            assertThrows(bad, IllegalArgumentException::class.java) { alice.folderBase(bad) }
        }
    }

    /** A build reads the three numbers of its version, as `@requires` does: a -dev build reads its release's. */
    @Test
    fun aBuildReadsTheNumbersOfItsVersion() {
        assertEquals("0.5.17", ScriptSources.folderOf("0.5.17"))
        assertEquals("0.5.17", ScriptSources.folderOf("0.5.17-dev"))
        assertEquals("0.5.17", ScriptSources.folderOf("v0.5.17"))
        assertEquals("1.0.0", ScriptSources.folderOf("1.0.0-lite"))
        for (none in listOf("0.5", "0.5.17.1", "dev", "", "0.x.17")) {
            assertNull(none, ScriptSources.folderOf(none))
        }
    }

    /** Answers each address from [pages]; an address not there is missing (404). Each address asked is in [asked]. */
    private class Server(val pages: Map<String, String>, val fails: Map<String, IOException> = emptyMap()) {
        val asked = ArrayList<String>()
        fun get(url: String): String {
            asked += url
            fails[url]?.let { throw it }
            return pages[url] ?: throw ScriptSources.Missing("HTTP 404 from ${url.substringAfter("://").substringBefore('/')}")
        }
    }

    /** A source with a list for this version: that list is read, and nothing else. */
    @Test
    fun theVersionsListComesFirst() {
        val server = Server(mapOf(alice.folderIndex("0.5.17") to "mine", alice.index to "general"))
        assertEquals(ScriptSources.Read("mine", "0.5.17"), ScriptSources.read(alice, "0.5.17-dev", server::get))
        assertEquals(listOf(alice.folderIndex("0.5.17")), server.asked)
    }

    /** A source without one (404 or 410) gives its general list, as every Kolahal up to 0.5.16 reads it. */
    @Test
    fun aSourceWithoutOneGivesItsGeneralList() {
        assertTrue(ScriptSources.missing(404))
        assertTrue(ScriptSources.missing(410))
        for (other in listOf(200, 301, 403, 429, 500, 503)) assertFalse("$other", ScriptSources.missing(other))
        val server = Server(mapOf(bob.index to "general"))
        assertEquals(ScriptSources.Read("general", null), ScriptSources.read(bob, "0.5.17", server::get))
        assertEquals(listOf(bob.folderIndex("0.5.17"), bob.index), server.asked)
        // A 410 the same way.
        val gone = Server(mapOf(bob.index to "general"), mapOf(bob.folderIndex("0.5.17") to ScriptSources.Missing("HTTP 410 from bob.github.io")))
        assertEquals(ScriptSources.Read("general", null), ScriptSources.read(bob, "0.5.17", gone::get))
        // A version without three numbers has no folder: only the general list.
        val plain = Server(mapOf(bob.index to "general"))
        assertEquals(ScriptSources.Read("general", null), ScriptSources.read(bob, "dev", plain::get))
        assertEquals(listOf(bob.index), plain.asked)
    }

    /** Any other failure of the version's list is the source's failure: the general list isn't read in its place. */
    @Test
    fun anotherFailureDoesntFallBack() {
        for (why in listOf("HTTP 500 from alice.github.io", "HTTP 403 from alice.github.io", "timeout")) {
            val server = Server(
                mapOf(alice.index to "general"),
                mapOf(alice.folderIndex("0.5.17") to IOException(why))
            )
            val thrown = assertThrows(IOException::class.java) { ScriptSources.read(alice, "0.5.17", server::get) }
            assertEquals(why, thrown.message)
            assertEquals(listOf(alice.folderIndex("0.5.17")), server.asked)
        }
        // With neither list, the general list's failure is the source's, as before.
        val none = Server(emptyMap())
        val thrown = assertThrows(IOException::class.java) { ScriptSources.read(alice, "0.5.17", none::get) }
        assertEquals("HTTP 404 from alice.github.io", thrown.message)
    }

    /** A list is kept with the folder it came from, and read back from it. */
    @Test
    fun aKeptListHasItsFolder() {
        val list = """{"format":1,"scripts":[]}"""
        val mine = ScriptSources.kept(ScriptSources.keptText(ScriptSources.Read(list, "0.5.17"), "0.5.17-dev"))
        assertEquals("0.5.17", mine.folder)
        assertEquals("0.5.17-dev", mine.by)
        assertEquals(1, (mine.list["format"] as Number).toInt())
        val general = ScriptSources.kept(ScriptSources.keptText(ScriptSources.Read(list, null), "0.5.17"))
        assertNull(general.folder)
        assertEquals("0.5.17", general.by)
    }

    /** A list kept by a Kolahal up to 0.5.16 is the list alone: it came from the general list. */
    @Test
    fun anOlderKeptListIsTheGeneralOne() {
        val old = ScriptSources.kept("""{"format":1,"scripts":[],"list":"{}","folder":"0.5.17"}""")
        assertNull(old.folder)
        assertNull(old.by)
        assertEquals(1, (old.list["format"] as Number).toInt())
        // A kept list whose folder isn't a version's isn't read at all.
        assertThrows(IOException::class.java) {
            ScriptSources.kept("""{"list":"{\"format\":1}","by":"0.5.17","folder":"../x"}""")
        }
        assertThrows(IOException::class.java) { ScriptSources.kept("not a list") }
    }
}
