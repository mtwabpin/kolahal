package com.mtwabpin.kolahal.ui.assistant

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.assistant.Anthropic
import com.mtwabpin.kolahal.assistant.AssistantKeys
import com.mtwabpin.kolahal.assistant.ConversationStore
import com.mtwabpin.kolahal.assistant.Google
import com.mtwabpin.kolahal.assistant.KvShelf
import com.mtwabpin.kolahal.assistant.Problem
import com.mtwabpin.kolahal.assistant.Role
import com.mtwabpin.kolahal.assistant.Services
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The Assistant's page with a fake service: the thread, rich and plain answers, rejected ones, history and keys. */
@RunWith(AndroidJUnit4::class)
class AssistantPageTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val configReset = ConfigReset()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val keys = AssistantKeys(app)
    private val store = ConversationStore(KvShelf(app))
    private var settingsOpened = 0
    private lateinit var focusManager: androidx.compose.ui.focus.FocusManager

    @Before
    fun setUp() {
        store.deleteAll()
        Services.ALL.forEach { keys.set(it, null) }
        keys.set(Anthropic, "test-key-anthropic")
    }

    @After
    fun tearDown() {
        store.deleteAll()
        Services.ALL.forEach { keys.set(it, null) }
    }

    // Without the keyboard's inset: the keyboard comes up in an animation, which moves the key card's Save
    // as a click lands, and the click is missed.
    // [visible] false takes the page away, as leaving it does.
    private fun show(asker: FakeAsker, visible: () -> Boolean = { true }) {
        compose.setContent {
            focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            KolahalTheme {
                Box(Modifier.size(360.dp, 720.dp).consumeWindowInsets(WindowInsets.ime)) {
                    if (visible()) AssistantPage(config, onSettings = { settingsOpened++ }, asker = asker)
                }
            }
        }
    }

    private val thread get() = compose.onNodeWithTag("assistant-thread")

    private fun ask(text: String) {
        compose.onNodeWithTag("assistant-ask").performTextInput(text)
        compose.onNodeWithText("Ask").performClick()
        // The keyboard away, so the thread has the page's height and the answer shows on it.
        compose.runOnUiThread { focusManager.clearFocus() }
    }

    private fun waitIdle(asker: FakeAsker, n: Int) {
        compose.waitFor("asked $n times") { asker.asked.size == n }
        compose.waitFor("not answered $n times", ms = 10_000) { asker.answered == n }
        // The answer is drawn on the main thread after it comes back.
        compose.waitForIdle()
    }

    /** A question, its drawn answer; a follow-up that changes it shows it once, changed, where it was. */
    @Test
    fun aRichAnswerIsDrawnAndChangedInPlace() {
        val asker = FakeAsker({ Answers.DASHBOARD }, { Answers.DASHBOARD_TITLE })
        show(asker)
        compose.onNodeWithText(sentLine(Anthropic)).assertIsDisplayed()
        ask("How much energy did we use?")
        waitIdle(asker, 1)
        compose.waitForText("Home energy this week")
        thread.performScrollToIndex(0)
        compose.onNodeWithText("How much energy did we use?").assertExists()
        compose.onNodeWithTag("surface s1").assertExists()
        compose.onNodeWithText("Heat pump").assertExists()
        val first = asker.asked[0]
        assertEquals(Anthropic, first.service)
        assertEquals(Anthropic.default, first.model)
        assertTrue(first.json)
        assertTrue("A2UI" in first.system)
        assertEquals(listOf(Role.USER), first.turns.map { it.role })

        ask("Change the title")
        waitIdle(asker, 2)
        compose.waitForText("Home energy, updated")
        compose.onNodeWithText("Changed the answer above").assertExists()
        assertEquals(1, compose.onAllNodesWithTag("surface s1").fetchSemanticsNodes().size)
        // The whole conversation went with the follow-up, the answer as it came.
        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.USER), asker.asked[1].turns.map { it.role })
        assertEquals(Answers.DASHBOARD, asker.asked[1].turns[1].text)
    }

    /** With rich answers off, the model is asked for text, and its Markdown shows as text. */
    @Test
    fun aPlainAnswerIsText() {
        config.set(Keys.ASSISTANT_RICH, "off")
        config.set(Keys.ASSISTANT_INSTRUCTIONS, "Answer like a pirate")
        val asker = FakeAsker({ "Here are **two** ideas:\n- a walk\n- a book" })
        show(asker)
        ask("Ideas for Sunday?")
        waitIdle(asker, 1)
        thread.performScrollToKey("item1")
        compose.waitForNode(hasTestTag("assistant-text") and hasText("two", substring = true), ms = 10_000)
        compose.onNodeWithText("a walk", substring = true).assertExists()
        assertFalse(asker.asked[0].json)
        assertFalse("A2UI" in asker.asked[0].system)
        assertTrue(asker.asked[0].system.endsWith("Answer like a pirate"))
    }

    /** An answer that isn't A2UI, and one the renderer rejects: each is said, with the answer as it came, and the page goes on. */
    @Test
    fun aRejectedAnswerIsShownNotACrash() {
        val asker = FakeAsker({ "Sorry, I can only answer in words." }, { Answers.UNKNOWN_COMPONENT }, { Answers.LIST })
        show(asker)
        ask("First")
        waitIdle(asker, 1)
        compose.waitForText("The answer isn't one the app can draw")
        compose.onNodeWithText("Sorry, I can only answer in words.").assertExists()
        ask("Second")
        waitIdle(asker, 2)
        compose.waitForText("The answer couldn't be drawn", substring = true)
        // What the renderer said goes to the model with the next question.
        ask("Third")
        waitIdle(asker, 3)
        assertTrue(asker.asked[2].turns.last().text.contains("\"error\""))
        // A list of cards with a list inside: drawn whole, inside the scrolling thread.
        compose.waitForText("Three easy dinners")
        compose.onNodeWithText("Zest the lemon first").assertExists()
    }

    /** A service's error reads plainly; a wrong key offers to enter another. */
    @Test
    fun aWrongKeyAsksForAnother() {
        val asker = FakeAsker({ throw Anthropic.fail(Problem.BAD_KEY) })
        show(asker)
        ask("Hello")
        waitIdle(asker, 1)
        compose.waitForText("Anthropic didn't accept the API key")
        compose.onNodeWithText("Enter another key").performClick()
        compose.waitForNode(hasTestTag("assistant-key"))
        // The question that failed isn't part of the conversation: the next one goes alone.
        asker.add { Answers.DASHBOARD }
        ask("Again")
        waitIdle(asker, 2)
        assertEquals(1, asker.asked[1].turns.size)
    }

    /** Without a key, the page asks for one, and saving it is all it takes. */
    @Test
    fun withoutAKeyThePageAsksForOne() {
        keys.set(Anthropic, null)
        show(FakeAsker())
        compose.waitForNode(hasTestTag("assistant-key"))
        compose.onNodeWithText("Anthropic API key").assertExists()
        compose.onNodeWithTag("assistant-key").performTextInput("sk-test-0000")
        compose.onNodeWithText("Save").performClick()
        compose.waitFor("the key isn't saved", ms = 10_000) { keys.has(Anthropic) }
        compose.waitFor("the key field stays", ms = 10_000) { compose.onAllNodesWithTag("assistant-key").fetchSemanticsNodes().isEmpty() }
        assertTrue(keys.has(Anthropic))
        assertEquals(listOf(Anthropic.host), keys.saved(Anthropic)?.hosts)
    }

    /** One tap switches the service: its own models show, and the next question goes to its default. */
    @Test
    fun oneTapSwitchesTheService() {
        keys.set(Google, "test-key-google")
        val asker = FakeAsker({ Answers.DASHBOARD })
        config.set(Keys.ASSISTANT_MODEL, "claude-opus-5-5")
        show(asker)
        compose.onNodeWithTag("service google").performClick()
        compose.waitFor("not switched") { config[Keys.ASSISTANT_SERVICE] == "google" }
        assertEquals("", config[Keys.ASSISTANT_MODEL])
        compose.waitForText(Google.models.first().name)
        ask("Hi")
        waitIdle(asker, 1)
        assertEquals(Google, asker.asked[0].service)
        assertEquals(Google.default, asker.asked[0].model)
        compose.onNodeWithContentDescription("Assistant settings").performClick()
        assertEquals(1, settingsOpened)
    }

    /** Conversations are kept: a new one starts empty, an earlier one opens where it was, and one can be deleted. */
    @Test
    fun historyKeepsOpensAndDeletes() {
        val asker = FakeAsker({ Answers.COMPARISON }, { Answers.DASHBOARD })
        show(asker)
        ask("Train or car?")
        waitIdle(asker, 1)
        compose.waitFor("not kept") { store.list().size == 1 }
        val kept = store.list().single()
        assertEquals("Train or car?", kept.title)
        assertEquals(Anthropic.id, kept.service)

        compose.onNodeWithContentDescription("New conversation").performClick()
        compose.waitFor("the thread isn't empty") { compose.onAllNodesWithTag("surface s3").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithContentDescription("Earlier conversations").performClick()
        compose.waitForNode(hasTestTag("conversation ${kept.id}"))
        compose.onNodeWithTag("conversation ${kept.id}").performClick()
        compose.waitForText("Train or car to the coast")
        thread.performScrollToIndex(0)
        compose.onNodeWithText("Train or car?").assertExists()
        // It goes on: the next question carries it.
        ask("And by bus?")
        waitIdle(asker, 2)
        assertEquals(3, asker.asked[1].turns.size)
        compose.waitFor("not saved again") { store.load(kept.id)?.entries?.size == 4 }
        // Each entry a value of its own, beside the conversation's header and the index.
        assertEquals(
            setOf("index", "h:${kept.id}") + (0..3).map { ConversationStore.entryKey(kept.id, it) },
            KvShelf(app).keys(),
        )

        compose.onNodeWithContentDescription("Earlier conversations").performClick()
        compose.waitForNode(hasTestTag("delete ${kept.id}"))
        compose.onNodeWithTag("delete ${kept.id}").performClick()
        compose.waitFor("not deleted") { store.list().isEmpty() }
        assertNull(store.load(kept.id))
        assertEquals(setOf("index"), KvShelf(app).keys())
        compose.waitForText("No conversations kept yet")
    }

    /** Leaving the page and coming back finds the conversation as it was, its answer drawn again. */
    @Test
    fun theConversationOutlivesThePage() {
        val asker = FakeAsker({ Answers.DASHBOARD })
        var visible by mutableStateOf(true)
        show(asker) { visible }
        ask("How much energy did we use?")
        waitIdle(asker, 1)
        compose.waitForText("Home energy this week")

        compose.runOnUiThread { visible = false }
        compose.waitFor("the page is still there") { compose.onAllNodesWithTag("assistant-thread").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { visible = true }
        compose.waitForText("Home energy this week", ms = 10_000)
        thread.performScrollToIndex(0)
        compose.onNodeWithText("How much energy did we use?").assertExists()
    }

    /** While a question waits, no earlier conversation can be opened and no new one started: the answer belongs to this one. */
    @Test
    fun nothingElseOpensWhileAQuestionWaits() {
        val gate = CountDownLatch(1)
        val asker = FakeAsker({ Answers.COMPARISON }, {
            gate.await(20, TimeUnit.SECONDS)
            Answers.DASHBOARD
        })
        show(asker)
        ask("Train or car?")
        waitIdle(asker, 1)
        compose.waitFor("not kept") { store.list().size == 1 }

        ask("And by bus?")
        compose.waitFor("not asked") { asker.asked.size == 2 }
        compose.waitForNode(hasContentDescription("Waiting for the answer"), ms = 10_000)
        compose.onNodeWithContentDescription("Earlier conversations").assertIsNotEnabled()
        compose.onNodeWithContentDescription("New conversation").assertIsNotEnabled()

        gate.countDown()
        waitIdle(asker, 2)
        compose.waitFor("still waiting") { compose.onAllNodes(hasContentDescription("Waiting for the answer")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Earlier conversations").assertIsEnabled()
        // Kept as one conversation: both questions and both answers, under the first one's id.
        compose.waitFor("not saved with the answer") { store.list().singleOrNull()?.let { store.load(it.id)?.entries?.size } == 4 }
    }

    /** With history off, nothing is kept and the list is hidden. */
    @Test
    fun historyOffKeepsNothing() {
        config.set(Keys.ASSISTANT_HISTORY, "off")
        val asker = FakeAsker({ Answers.DASHBOARD })
        show(asker)
        compose.onNodeWithContentDescription("Earlier conversations").assertDoesNotExist()
        ask("Hello")
        waitIdle(asker, 1)
        compose.waitForText("Home energy this week")
        assertTrue(store.list().isEmpty())
    }
}
