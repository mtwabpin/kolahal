package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptOptions
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import com.mtwabpin.kolahal.waitUntil
import com.mtwabpin.kolahal.widgets.TestScriptWidgets
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The options of one widget of a script, on the Widgets screen: "Options" in the selected widget's
 * toolbar opens them. A further widget's values are its own, the first widget's are the script's,
 * and a saved value runs that widget again.
 */
@RunWith(AndroidJUnit4::class)
class WidgetOptionsSheetTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val t = TestScriptWidgets("wopts")
    private val options = ScriptOptions(app)

    @After fun tearDown() = t.close()

    private val title = "Wopts Weather"
    private val code = "-- @title $title\n-- @widget stat 2x1\n-- @option city London The city to show\nwidget.show{ value = options.city }"
    private val declared = ScriptMeta.options(code)

    /** A view model on the Widgets screen that has read [scripts], with what the widget screens see of them. */
    private fun model(vararg scripts: Script): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.WIDGETS)
        }
        waitUntil("the view model hasn't read the scripts") { vm.widgetScripts.value?.keys?.containsAll(scripts.map { it.name }) == true }
        return vm
    }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    /**
     * Selects the widget the screen calls [name], as a tap on its card does. While a widget is
     * selected, each card has two layers that take the tap, and either one selects.
     */
    private fun select(name: String) {
        compose.waitForNode(hasClickLabel("change $name"))
        compose.onAllNodes(hasClickLabel("change $name")).onFirst().performClick()
    }

    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    /** The value the widget [ref] shows, or null. */
    private fun value(ref: WidgetRef) = (t.stored(ref).spec as? WidgetSpec.Stat)?.value

    private val city = hasText("city") and hasText("The city to show")

    /** A value set in a further widget's sheet is for that widget only, and its card is filled again with it. */
    @Test fun aFurtherWidgetHasOptionsOfItsOwn() {
        val s = t.place(t.create("-weather", code))
        val first = WidgetRef(s.name)
        val second = t.placeAnother(s)
        assertEquals("London", value(second))
        val vm = model(s)
        compose.setContent { WidgetsScreen(vm) { _, _ -> } }

        // The second widget is named by its script's title and its id.
        select("$title 2")
        compose.onNodeWithText("Options").performClick()
        compose.waitForText("Options for $title 2")
        compose.onNodeWithText(WidgetOptionWords.WIDGET).assertExists()

        // It follows the script's value until one is set for it.
        compose.onNode(city and hasText("London")).performClick()
        compose.onNodeWithText("city for ${s.name} 2").assertExists()
        inDialog("Reset").assertDoesNotExist()
        compose.onNodeWithTag("option-value").performTextReplacement("Tokyo")
        inDialog("Save").performClick()
        compose.waitFor("the widget's own value isn't stored") { options.values(s.name, declared, "2")["city"] == "Tokyo" }
        assertEquals(setOf("city"), options.changed(s.name, declared, "2"))
        // The script's own value is as it was.
        assertEquals("London", options.values(s.name, declared)["city"])
        assertEquals(emptySet<String>(), options.changed(s.name, declared))
        compose.waitForNode(city and hasText("Tokyo"))
        // The saved value asked for one run of that widget, and only of that one.
        waitUntil("the second widget wasn't filled again with its own value", 30_000) { value(second) == "Tokyo" }
        assertEquals("London", value(first))

        // Reset takes the widget's own value away: it follows the script again.
        compose.onNode(city and hasText("Tokyo")).performClick()
        inDialog("Reset").performClick()
        compose.waitFor("the widget's own value isn't gone") { options.changed(s.name, declared, "2").isEmpty() }
        compose.waitForNode(city and hasText("London"))
        waitUntil("the second widget wasn't filled again with the script's value", 30_000) { value(second) == "London" }
    }

    /** The first widget's sheet shows the script's own options, and a value set there is the script's. */
    @Test fun theFirstWidgetHasTheScriptsOptions() {
        val s = t.place(t.create("-weather", code))
        val first = WidgetRef(s.name)
        val plain = t.place(t.create("-plain", "-- @title Wopts Plain\n-- @widget stat 2x1\nwidget.show{ value = 1 }"))
        val vm = model(s, plain)
        compose.setContent { WidgetsScreen(vm) { _, _ -> } }

        // A widget whose script declares no option has nothing to set.
        select("Wopts Plain")
        compose.onNodeWithText("Square").assertExists()
        compose.onAllNodesWithText("Options").assertCountEquals(0)

        select(title)
        compose.onNodeWithText("Options").performClick()
        compose.waitForText("Options for $title")
        compose.onNodeWithText(WidgetOptionWords.SCRIPT).assertExists()
        compose.onNode(city and hasText("London")).performClick()
        compose.onNodeWithText("city for ${s.name}").assertExists()
        compose.onNodeWithTag("option-value").performTextReplacement("Paris")
        inDialog("Save").performClick()
        compose.waitFor("the script's value isn't stored") { options.values(s.name, declared)["city"] == "Paris" }
        assertEquals(setOf("city"), options.changed(s.name, declared))
        waitUntil("the first widget wasn't filled again", 30_000) { value(first) == "Paris" }
    }
}
