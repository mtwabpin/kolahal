package com.mtwabpin.kolahal.ui.assistant

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.assistant.Anthropic
import com.mtwabpin.kolahal.assistant.AssistantKeys
import com.mtwabpin.kolahal.assistant.Conversation
import com.mtwabpin.kolahal.assistant.ConversationStore
import com.mtwabpin.kolahal.assistant.Entry
import com.mtwabpin.kolahal.assistant.Google
import com.mtwabpin.kolahal.assistant.Kind
import com.mtwabpin.kolahal.assistant.KvShelf
import com.mtwabpin.kolahal.assistant.Services
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.settings.ASSISTANT_CATEGORY
import com.mtwabpin.kolahal.ui.settings.SettingsOverHome
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → Assistant: the service and its model, the key rows, the line for a missing key, the switches, deleting history. */
@RunWith(AndroidJUnit4::class)
class AssistantSettingsTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val configReset = ConfigReset()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val keys = AssistantKeys(app)
    private val store = ConversationStore(KvShelf(app))
    private lateinit var vm: TerminalViewModel

    @Before
    fun setUp() {
        store.deleteAll()
        Services.ALL.forEach { keys.set(it, null) }
    }

    @After
    fun tearDown() {
        store.deleteAll()
        Services.ALL.forEach { keys.set(it, null) }
    }

    private fun show() {
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.settingsCategory.value = ASSISTANT_CATEGORY
        }
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 700.dp).consumeWindowInsets(WindowInsets.safeDrawing)) { SettingsOverHome(vm) }
            }
        }
    }

    private fun scrollTo(key: String) = compose.onNode(hasScrollToKeyAction()).performScrollToKey(key)

    /** Another service starts on its own default model, and its models are the ones offered. */
    @Test
    fun theServiceAndItsModel() {
        config.set(Keys.ASSISTANT_MODEL, "claude-opus-5-5")
        show()
        compose.waitForText("Service, model, API keys, history")
        compose.onNodeWithTag("service google").performClick()
        compose.waitFor("not switched") { config[Keys.ASSISTANT_SERVICE] == Google.id }
        assertEquals("", config[Keys.ASSISTANT_MODEL])
        compose.waitForText(Google.models[1].name)
        // The line asking for the service's key, once read, moves the models down: bring the chip into view.
        compose.onNodeWithText(Google.models[1].name).performScrollTo()
        compose.onNodeWithText(Google.models[1].name).performClick()
        compose.waitFor("model not set") { config[Keys.ASSISTANT_MODEL] == Google.models[1].id }
        // A model typed by hand, applied with Enter.
        compose.onNodeWithTag("field assistant.model").performTextInput("gemini-9-test")
        compose.onNodeWithTag("field assistant.model").performImeAction()
        compose.waitFor("typed model not set") { config[Keys.ASSISTANT_MODEL] == "gemini-9-test" }
    }

    /** Without the service's key, the row says so and its button asks for it; saved, the line goes. */
    @Test
    fun aMissingKeyIsSaidAndAdded() {
        show()
        compose.waitForNode(hasTestTag("needs-key ${Anthropic.id}"))
        compose.onNodeWithText("Needs an Anthropic API key").assertExists()
        compose.onNodeWithTag("needs-key ${Anthropic.id}").performClick()
        compose.waitForNode(hasTestTag("key-field"))
        compose.onNodeWithTag("key-field").performTextInput("test-key-anthropic")
        compose.onNodeWithText("Save").performClick()
        compose.waitFor("not saved") { keys.has(Anthropic) }
        compose.waitFor("the line stays") { compose.onAllNodes(hasTestTag("needs-key ${Anthropic.id}")).fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf(Anthropic.host), keys.saved(Anthropic)?.hosts)
    }

    /** Each service's key row: not saved, added, saved for its host, removed. The key never shows. */
    @Test
    fun theKeyRows() {
        show()
        scrollTo("assistant:keys")
        for (s in Services.ALL) compose.waitForNode(hasTestTag("key ${s.id}") and hasText("Not saved", substring = true))
        compose.onNodeWithTag("key ${Google.id}").performClick()
        compose.waitForNode(hasTestTag("key-field"))
        compose.onNodeWithTag("key-field").performTextInput("test-key-google-1234")
        compose.onNodeWithText("Save").performClick()
        compose.waitForText("Saved, sent only to ${Google.host}. Tap to replace it")
        compose.onNodeWithText("test-key-google-1234", substring = true).assertDoesNotExist()
        assertTrue(keys.has(Google))
        assertFalse(keys.has(Anthropic))
        compose.onNodeWithTag("remove-key ${Google.id}").performClick()
        compose.waitFor("not removed") { !keys.has(Google) }
        compose.waitForNode(hasTestTag("key ${Google.id}") and hasText("Not saved", substring = true))
    }

    /** Rich answers and history are switches; delete all asks first, then empties the history. */
    @Test
    fun theSwitchesAndDeleteAll() {
        (1..2).forEach {
            store.save(Conversation("$it", it.toLong(), it.toLong(), Anthropic.id, Anthropic.default, listOf(Entry(Kind.QUESTION, "q$it"), Entry(Kind.PLAIN, "a"))))
        }
        show()
        scrollTo(Keys.ASSISTANT_RICH.key)
        compose.onNodeWithText("Rich answers").performClick()
        compose.waitFor("rich still on") { !config[Keys.ASSISTANT_RICH] }
        scrollTo("assistant:delete")
        compose.waitForText("2 kept on this phone")
        compose.onNodeWithText("Delete all conversations").performClick()
        compose.waitForText("Delete all conversations?")
        compose.onNodeWithText("Delete").performClick()
        compose.waitFor("not deleted") { store.list().isEmpty() }
        // Every value goes: headers and entries too.
        assertEquals(emptySet<String>(), KvShelf(app).keys())
        compose.waitForText("None kept")
        scrollTo(Keys.ASSISTANT_HISTORY.key)
        compose.onNodeWithText("Keep history").performClick()
        compose.waitFor("history still on") { !config[Keys.ASSISTANT_HISTORY] }
    }
}
