package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sources of Get more scripts ([ScriptSources]): their addresses, their order and a backup's list. */
class ScriptSourcesTest {
    private fun ok(input: String) = (ScriptSources.parse(input) as ScriptSources.Parsed.Ok).source
    private fun bad(input: String) = (ScriptSources.parse(input) as ScriptSources.Parsed.Bad).why

    private val alice = ScriptSources.Source("alice/scripts", "https://alice.github.io/scripts/index.json")
    private val bob = ScriptSources.Source("bob/bob.github.io", "https://bob.github.io/index.json")

    private fun entry(name: String, source: String) = ScriptCatalog.Entry(
        name, ScriptLang.LUA, "scripts/$name/$name.lua", name, "", "", "1.0", emptyList(), emptyList(), emptyList(),
        false, null, emptyList(), null, "sha-$source-$name", source,
    )

    /** GitHub's owner/name reads that repository's Pages site; a repository named owner.github.io is the site's root. */
    @Test fun ownerAndNameReadGitHubPages() {
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
    @Test fun aLinkToAnIndexIsReadAsItIs() {
        val s = ok("https://Example.com/kolahal/index.json")
        assertEquals("https://example.com/kolahal/index.json", s.index)
        assertEquals("example.com/kolahal", s.name)
        assertEquals("https://example.com/kolahal/", s.base)
        assertEquals("https://example.com/kolahal/index.json", ok("https://example.com/kolahal/").index)
        assertEquals("example.com", ok("https://example.com").name)
        // A GitHub Pages link gets the owner/name it would get typed that way.
        assertEquals(alice, ok("https://alice.github.io/scripts/index.json"))
        assertEquals(bob, ok("https://bob.github.io/index.json"))
        assertEquals("alice.github.io/a/b", ok("https://alice.github.io/a/b/index.json").name)
        // Nothing else about the address is checked.
        assertEquals("https://192.168.1.2:8443/index.json", ok("https://192.168.1.2:8443/index.json").index)
    }

    /** Only https, only an index.json, and only names GitHub allows. */
    @Test fun whatIsntASourceSaysWhy() {
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
    @Test fun kolahalScriptsComesFirst() {
        assertEquals(listOf(ScriptSources.DEFAULT), ScriptSources.list(emptyList()))
        assertEquals(ScriptSources.DEFAULT, ok("mtwabpin/kolahal-scripts"))
        val added = ScriptSources.adding(ScriptSources.adding(emptyList(), alice), bob)
        assertEquals(listOf(alice.index, bob.index), added)
        assertSame(added, ScriptSources.adding(added, alice))
        assertEquals(added, ScriptSources.adding(added, ScriptSources.DEFAULT))
        assertEquals(listOf(ScriptSources.DEFAULT, alice, bob), ScriptSources.list(added))
        assertEquals(listOf(ScriptSources.DEFAULT, alice, bob), ScriptSources.list(added + alice.index))
    }

    /** A source is removed by its name, its address or what added it. kolahal-scripts stays. */
    @Test fun removing() {
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
    @Test fun theFirstSourceWinsAName() {
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
        assertEquals(listOf("tide", "alice/scripts/tide", "moon", "bob/bob.github.io/moon"), entries.map { ScriptSources.ref(entries, it) })
    }

    /** A backup holds the added sources' addresses. An import adds those it doesn't have, after its own, and skips what isn't a source. */
    @Test fun aBackupAddsTheSourcesItDoesntHave() {
        val saved: List<Any?> = listOf(alice.index, bob.index)
        assertEquals(saved, ScriptSources.fromBackup(emptyList(), saved))
        val skipped = ArrayList<String>()
        val carol = "https://carol.example/k/index.json"
        val restored = ScriptSources.fromBackup(listOf(carol, bob.index), saved + listOf("http://plain.example/index.json", null, ScriptSources.DEFAULT.index), skipped)
        assertEquals(listOf(carol, bob.index, alice.index), restored)
        assertEquals(listOf("script source http://plain.example/index.json", "script source null"), skipped)
    }
}
