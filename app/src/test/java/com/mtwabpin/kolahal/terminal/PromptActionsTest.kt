package com.mtwabpin.kolahal.terminal

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.commands.InstantRun
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prompt's typing, Enter, history and touch keys, with a view model that only writes down what it is asked. */
class PromptActionsTest {
    private class FakeHost : PromptActions.Host {
        /** The blocks put on the command queue, not run yet. */
        val queued = mutableListOf<suspend () -> Unit>()
        val events = mutableListOf<UiEvent>()
        val ran = mutableListOf<String>()

        /** The lines instant launch was asked about. */
        val asked = mutableListOf<String>()

        /** What instant launch runs: a line for each typed text that should run. */
        var instant: Map<String, String> = emptyMap()
        var aliases: Map<String, String> = emptyMap()
        var preview: LivePreview? = null
        var suggestions: List<Suggestion> = emptyList()

        override fun io(block: suspend () -> Unit) {
            queued += block
        }

        override fun emit(event: UiEvent) {
            events += event
        }

        override fun instantTarget(text: String): InstantRun.Target<AppEntry>? {
            asked += text
            return instant[text]?.let { InstantRun.Target.Command(it) }
        }

        override fun runsOnTap(line: String) = false
        override fun abbreviation(word: String) = aliases[word]
        override fun livePreview(line: String) = preview
        override fun suggestions(text: String) = suggestions
        override suspend fun run(line: String) {
            ran += line
        }

        override suspend fun launch(app: AppEntry) {}

        /** Runs what is on the queue, in order. */
        fun drain() = runBlocking {
            while (queued.isNotEmpty()) queued.removeAt(0)()
        }
    }

    private val host = FakeHost()
    private val prompt = PromptActions(host)

    /** Types [text] one character at a time, at the end of the line. */
    private fun type(text: String) = text.forEach { c ->
        val next = prompt.input.text + c
        prompt.onInputChange(TextFieldValue(next, TextRange(next.length)))
    }

    @Test
    fun enterRunsTheLineAndEmptiesThePrompt() {
        type("echo hi")
        prompt.submit()
        assertEquals("", prompt.input.text)
        assertEquals(listOf(UiEvent.ScrollToBottom), host.events)
        host.drain()
        assertEquals(listOf("echo hi"), host.ran)
    }

    @Test
    fun enterOnAnEmptyLineRunsNothing() {
        prompt.submit()
        assertTrue(host.queued.isEmpty())
        assertTrue(host.events.isEmpty())
    }

    @Test
    fun theLooksPreviewStaysUntilTheCommandHasRun() {
        host.preview = LivePreview(theme = "dark")
        type("theme dark")
        prompt.submit()
        assertEquals(LivePreview(theme = "dark"), prompt.entered)
        host.drain()
        assertNull(prompt.entered)
    }

    @Test
    fun enterInASearchHidesTheKeyboardAndKeepsTheLine() {
        type("/battery")
        prompt.submit()
        assertEquals("/battery", prompt.input.text)
        assertEquals(listOf(UiEvent.HideKeyboard), host.events)
        assertTrue(host.queued.isEmpty())
    }

    @Test
    fun instantLaunchRunsWhatTheTypedLineCanOnlyMean() {
        host.instant = mapOf("tor" to "torch")
        type("tor")
        assertEquals("", prompt.input.text)
        assertEquals(listOf(UiEvent.ScrollToBottom), host.events)
        host.drain()
        assertEquals(listOf("torch"), host.ran)
    }

    @Test
    fun instantLaunchIsNotAskedInASearchNorForAPastedWord() {
        type("/ab")
        prompt.setText("")
        prompt.onInputChange(TextFieldValue("maps", TextRange(4)))
        assertTrue(host.asked.isEmpty())
    }

    @Test
    fun instantLaunchIsNotAskedWhenTypingInTheMiddle() {
        prompt.setText("ab")
        prompt.onInputChange(TextFieldValue("axb", TextRange(2)))
        assertTrue(host.asked.isEmpty())
    }

    @Test
    fun anAliasExpandsOnTheSpaceAfterIt() {
        host.aliases = mapOf("m" to "open maps")
        type("m ")
        assertEquals("open maps ", prompt.input.text)
        assertEquals(TextRange(10), prompt.input.selection)
    }

    @Test
    fun upAndDownWalkTheHistoryAndKeepTheDraft() {
        prompt.history = listOf("newest", "older")
        type("dra")
        prompt.historyUp()
        assertEquals("newest", prompt.input.text)
        prompt.historyUp()
        assertEquals("older", prompt.input.text)
        prompt.historyUp()
        assertEquals("older", prompt.input.text)
        prompt.historyDown()
        assertEquals("newest", prompt.input.text)
        prompt.historyDown()
        assertEquals("dra", prompt.input.text)
        prompt.historyDown()
        assertEquals("dra", prompt.input.text)
    }

    @Test
    fun typingStartsTheHistoryAgain() {
        prompt.history = listOf("newest", "older")
        prompt.historyUp()
        type("x")
        prompt.historyUp()
        assertEquals("newest", prompt.input.text)
    }

    @Test
    fun tabTakesTheFirstSuggestion() {
        host.suggestions =
            listOf(Suggestion.Completion("battery", "battery"), Suggestion.Completion("bat", "bat"))
        type("bat")
        prompt.complete()
        assertEquals("battery", prompt.input.text)
    }

    @Test
    fun aCompletionWithItsArgumentsRunsWhenInstantLaunchSaysSo() {
        host.instant = mapOf("torch on" to "torch on")
        host.suggestions = listOf(Suggestion.Completion("on", "torch on"))
        prompt.complete()
        assertEquals("", prompt.input.text)
        host.drain()
        assertEquals(listOf("torch on"), host.ran)
    }

    @Test
    fun touchKeysEditAtTheCursor() {
        prompt.setText("helo")
        prompt.onKeyBar(KeyBarKey.LEFT)
        prompt.insert("l")
        assertEquals("hello", prompt.input.text)
        assertEquals(TextRange(4), prompt.input.selection)
        prompt.onKeyBar(KeyBarKey.END)
        prompt.onKeyBar(KeyBarKey.BKSP)
        assertEquals("hell", prompt.input.text)
        prompt.onKeyBar(KeyBarKey.HOME)
        prompt.onKeyBar(KeyBarKey.BKSP)
        assertEquals("hell", prompt.input.text)
        prompt.onKeyBar(KeyBarKey.ESC)
        assertEquals("", prompt.input.text)
    }

    @Test
    fun theKeyboardKeyAsksForThePrompt() {
        prompt.onKeyBar(KeyBarKey.KBD)
        assertEquals(listOf(UiEvent.FocusPrompt), host.events)
    }
}
