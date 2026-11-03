package com.mtwabpin.kolahal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** `/words` at the prompt: what [LogRepository.search] finds. */
@RunWith(AndroidJUnit4::class)
class LogSearchTest {
    private val logs = LogRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val token = "zq${System.nanoTime()}"
    private val added = ArrayList<Long>()

    private fun add(text: String, source: String? = null, ctx: LogContext = LogContext.EMPTY) {
        added += logs.add(LogKind.OUTPUT, text, source, ctx)
    }

    @After fun tearDown() = added.forEach(logs::delete)

    private fun texts(vararg words: String) = logs.search(words.toList()).entries.map { it.text }.toSet()

    @Test fun wordsAreFoundInTextSourceAndLink() {
        add("$token one")
        add("two", source = "$token-feed")
        add("three", ctx = LogContext.of(LogContext.URL to "https://example.com/$token"))
        add("four")
        assertEquals(setOf("$token one", "two", "three"), texts(token))
        assertEquals(3, logs.search(listOf(token)).total)
    }

    @Test fun aScriptLinesBodyIsSearchedToo() {
        added += logs.add(LogKind.SCRIPT, "title", "s", LogContext.of(LogContext.TITLE to "1", LogContext.BODY to "$token in the body"))
        assertEquals(setOf("title"), texts(token))
    }

    @Test fun everyWordMustMatchIgnoringCase() {
        add("$token Battery LOW")
        add("$token battery full")
        assertEquals(setOf("$token Battery LOW"), texts(token.uppercase(), "low", "battery"))
    }

    @Test fun newestFirstAndCapped() {
        repeat(5) { add("$token $it") }
        val found = logs.search(listOf(token), limit = 2)
        assertEquals(5, found.total)
        assertEquals(listOf("$token 4", "$token 3"), found.entries.map { it.text })
    }

    @Test fun skippedLinesAreLeftOut() {
        add("$token personal")
        add("$token private", ctx = LogContext.of(LogContext.USER to "77"))
        val found = logs.search(listOf(token), skip = { it.ctx.userSerial == 77L })
        assertEquals(listOf("$token personal"), found.entries.map { it.text })
        assertTrue(found.searched > 0)
    }

    // The database rules most lines out before they are read (a LIKE per word): what that must not miss.

    /** Styled text matches as it reads: a word across a style's edge is still the word. */
    @Test fun aWordAcrossAStyleEdgeIsFound() {
        add("$token ab" + StyledText.wrap(StyledText.Style.BOLD, "cd") + "ef")
        assertEquals(1, logs.search(listOf(token, "bcde")).total)
        added += logs.add(LogKind.SCRIPT, "t", "s", LogContext.of(LogContext.TITLE to "1", LogContext.BODY to "$token xy" + StyledText.wrap(StyledText.Style.ITALIC, "z") + "w"))
        assertEquals(1, logs.search(listOf(token, "yzw")).total)
    }

    /** JSON escapes the slashes in a link. */
    @Test fun aLinkWithSlashesIsFound() {
        add("three", ctx = LogContext.of(LogContext.URL to "https://example.com/$token/path"))
        assertEquals(setOf("three"), texts("com/$token/path"))
    }

    @Test fun likeWildcardsAreLiteral() {
        add("$token 100%")
        add("$token 100x")
        add("$token a_b")
        add("$token axb")
        // With the token too: any other line in the log with "100%" in it would be found.
        assertEquals(setOf("$token 100%"), texts(token, "100%"))
        assertEquals(setOf("$token a_b"), texts(token, "a_b"))
    }

    @Test fun nonAsciiWordsIgnoreCase() {
        add("$token Éclair")
        assertEquals(setOf("$token Éclair"), texts(token, "éclair"))
        assertEquals(setOf("$token Éclair"), texts(token, "ÉCLAIR"))
    }

    @Test fun everyLineCountsAsSearched() {
        repeat(3) { add("$token $it") }
        val found = logs.search(listOf(token))
        assertEquals(3, found.total)
        assertTrue(found.searched >= 3)
        assertTrue(logs.search(listOf("zz${token}zz")).searched >= 3)
    }
}
