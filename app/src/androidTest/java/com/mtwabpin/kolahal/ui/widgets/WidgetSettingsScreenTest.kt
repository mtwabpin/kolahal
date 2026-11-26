package com.mtwabpin.kolahal.ui.widgets

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptOptions
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import com.mtwabpin.kolahal.waitUntil
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.widgets.CardScenes
import com.mtwabpin.kolahal.widgets.MediaArt
import com.mtwabpin.kolahal.widgets.TestScriptWidgets
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetPages
import com.mtwabpin.kolahal.widgets.WidgetParts
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetShape
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings page of one of Kolahal's own cards: Settings in the chosen card's toolbar in Arrange
 * widgets opens it, and Back returns there with the card still chosen. A script widget's options are
 * there (a further widget's values are its own, the first widget's are the script's, and a saved
 * value runs that widget again), and the card's color, shape and what shows, kept for that card.
 */
@RunWith(AndroidJUnit4::class)
class WidgetSettingsScreenTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val t = TestScriptWidgets("wopts")
    private val options = ScriptOptions(app)

    /** Not a first run: right after an install, the welcome tour would open over the screens a moment after the view model starts. */
    @Before
    fun setUp() {
        app.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("intro_shown", true).commit()
    }

    @After
    fun tearDown() = t.close()

    private val title = "Wopts Weather"
    private val code =
        "-- @title $title\n-- @widget stat 2x1\n-- @option city London The city to show\nwidget.show{ value = options.city }"
    private val declared = ScriptMeta.options(code)

    /**
     * A view model on the Widgets screen that has read [scripts], and the screens as home draws them:
     * Arrange widgets, and a card's settings page once it opens.
     */
    private fun show(vararg scripts: Script): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.WIDGETS)
        }
        waitUntil("the view model hasn't read the scripts") {
            vm.widgetScripts.value?.keys?.containsAll(
                scripts.map { it.name }) == true
        }
        compose.setContent {
            // Each screen keeps what it saved while another is open, as home's screens do.
            val saved = rememberSaveableStateHolder()
            val screen = vm.screen.collectAsState().value
            saved.SaveableStateProvider(screen.name) {
                if (screen == Screen.WIDGET_SETTINGS) WidgetSettingsScreen(vm)
                else WidgetsScreen(vm) { _, _ -> }
            }
        }
        return vm
    }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    /** Chooses the widget the screen calls [name], as a tap on its card does, and opens its settings page. */
    private fun open(vm: TerminalViewModel, name: String) {
        compose.waitForNode(hasClickLabel("change $name"))
        compose.onAllNodes(hasClickLabel("change $name")).onFirst().performClick()
        compose.onNodeWithTag("widget-settings").performClick()
        compose.runOnIdle { assertEquals(Screen.WIDGET_SETTINGS, vm.screen.value) }
        compose.onNodeWithTag("widget-preview").assertExists()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun inDialog(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    /** The value the widget [ref] shows, or null. */
    private fun value(ref: WidgetRef) = (t.stored(ref).spec as? WidgetSpec.Stat)?.value

    private fun city(value: String) = hasTestTag("option city") and hasText(value)

    /** A value set on a further widget's page is for that widget only, and its card is filled again with it. */
    @Test
    fun aFurtherWidgetHasOptionsOfItsOwn() {
        val s = t.place(t.create("-weather", code))
        val first = WidgetRef(s.name)
        val second = t.placeAnother(s)
        assertEquals("London", value(second))
        val vm = show(s)

        // The second widget is named by its script's title and its id.
        open(vm, "$title 2")
        compose.waitForText(WidgetOptionWords.WIDGET)

        // It follows the script's value until one is set for it.
        compose.onNode(city("London")).performClick()
        compose.onNodeWithText("city for ${s.name} 2").assertExists()
        inDialog("Reset").assertDoesNotExist()
        compose.onNodeWithTag("option-value").performTextReplacement("Tokyo")
        inDialog("Save").performClick()
        compose.waitFor("the widget's own value isn't stored") {
            options.values(s.name, declared, "2")["city"] == "Tokyo"
        }
        assertEquals(setOf("city"), options.changed(s.name, declared, "2"))
        // The script's own value is as it was.
        assertEquals("London", options.values(s.name, declared)["city"])
        assertEquals(emptySet<String>(), options.changed(s.name, declared))
        compose.waitForNode(city("Tokyo"))
        // The saved value asked for one run of that widget, and only of that one.
        waitUntil("the second widget wasn't filled again with its own value", 30_000) { value(second) == "Tokyo" }
        assertEquals("London", value(first))

        // Reset takes the widget's own value away: it follows the script again.
        compose.onNode(city("Tokyo")).performClick()
        inDialog("Reset").performClick()
        compose.waitFor("the widget's own value isn't gone") {
            options.changed(s.name, declared, "2").isEmpty()
        }
        compose.waitForNode(city("London"))
        waitUntil("the second widget wasn't filled again with the script's value", 30_000) {
            value(second) == "London"
        }
    }

    /**
     * The first widget's page shows the script's own options, and a value set there is the script's.
     * Every widget that reads the script's value is filled again: the first, and a further one that
     * follows the script. A further widget with a value of its own keeps showing its own. A script
     * with no option says so; Back returns to Arrange widgets with the card still chosen.
     */
    @Test
    fun theFirstWidgetHasTheScriptsOptions() {
        val s = t.place(t.create("-weather", code))
        val first = WidgetRef(s.name)
        val follows = t.placeAnother(s)
        val own = t.placeAnother(s)
        options.set(s.name, "city", "Tokyo", own.id)
        t.refresh(s, own)
        assertEquals(listOf("London", "London", "Tokyo"), listOf(first, follows, own).map(::value))
        val ownTried = t.stored(own).triedAt
        val plain = t.place(
            t.create("-plain", "-- @title Wopts Plain\n-- @widget stat 2x1\nwidget.show{ value = 1 }")
        )
        val vm = show(s, plain)

        // A widget whose script declares no option says so, and has no option to set.
        open(vm, "Wopts Plain")
        compose.waitForText(WidgetOptionWords.NONE)
        compose.onAllNodesWithTag("option city").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        back()
        compose.runOnIdle { assertEquals(Screen.WIDGETS, vm.screen.value) }
        compose.onNode(isSelected() and hasClickLabel("change Wopts Plain")).assertExists()

        open(vm, title)
        compose.waitForText(WidgetOptionWords.SCRIPT)
        compose.onNode(city("London")).performClick()
        compose.onNodeWithText("city for ${s.name}").assertExists()
        compose.onNodeWithTag("option-value").performTextReplacement("Paris")
        inDialog("Save").performClick()
        compose.waitFor("the script's value isn't stored") {
            options.values(s.name, declared)["city"] == "Paris"
        }
        assertEquals(setOf("city"), options.changed(s.name, declared))
        waitUntil("the first widget wasn't filled again", 30_000) { value(first) == "Paris" }
        waitUntil("the widget that follows the script wasn't filled again", 30_000) { value(follows) == "Paris" }
        // The widget with its own value reads nothing that changed: it got no run.
        assertTrue("the queue didn't empty", t.runEnded(s.id))
        assertEquals("Tokyo", value(own))
        assertEquals(ownTried, t.stored(own).triedAt)
    }

    /**
     * A tap on a color, on a shape (in the Shape look) and on a part's switch keeps it for that card
     * at once; Reset to the card's own takes them all away and leaves the look. Undo, in the snackbar
     * that follows, puts them all back.
     */
    @Test
    fun colorShapeAndWhatShowsAreKeptAndReset() {
        val s = t.place(
            t.create(
                "-air",
                "-- @title Wopts Air\n-- @widget stat 2x2\nwidget.show{ value = 118, label = \"AQI\", icon = \"sun\" }"
            )
        )
        val key = t.key(s.name)
        t.host.setLook(key, WidgetLook.SHAPE)
        val vm = show(s)
        open(vm, "Wopts Air")
        compose.onNodeWithTag("reset-own").performScrollTo().assertIsNotEnabled()

        // Its own color is chosen until another is.
        compose.onNodeWithTag("color own").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("color link").performScrollTo().performClick()
        compose.waitFor("the color isn't kept") { t.entry(s.name)?.color == ColorRole.LINK }
        compose.onNodeWithTag("color link").assertIsSelected()

        // In the Shape look, a stat has every shape; its own is a 12-sided cookie.
        compose.onNodeWithTag("shape own").performScrollTo().assertIsSelected()
        WidgetShape.entries.filter { it != WidgetShape.COOKIE12 }
            .forEach { compose.onNodeWithTag("shape ${it.id}").assertExists() }
        compose.onNodeWithTag("shape clover8").performScrollTo().performClick()
        compose.waitFor("the shape isn't kept") { t.entry(s.name)?.shape == WidgetShape.CLOVER8 }

        // It has a name and an icon to hide.
        compose.waitForNode(hasTestTag("part ${WidgetParts.ICON}"))
        compose.onNodeWithTag("part ${WidgetParts.ICON}").performScrollTo().assertIsOn().performClick()
        compose.waitFor("the icon isn't hidden") { t.entry(s.name)?.hidden == setOf(WidgetParts.ICON) }
        compose.onNodeWithTag("part ${WidgetParts.ICON}").assertIsOff()
        compose.onNodeWithTag("part ${WidgetParts.NAME}").performScrollTo().performClick()
        compose.waitFor("the name isn't hidden") {
            t.entry(s.name)?.hidden == setOf(WidgetParts.ICON, WidgetParts.NAME)
        }

        compose.onNodeWithTag("reset-own").performScrollTo().assertIsEnabled().performClick()
        compose.waitFor("Reset didn't give the card its own") {
            t.entry(s.name)?.let { it.color == null && it.shape == null && it.hidden.isEmpty() } == true
        }
        assertEquals(WidgetLook.SHAPE, t.entry(s.name)?.look)
        compose.onNodeWithTag("color own").assertIsSelected()

        compose.waitForText(WidgetSettingsWords.resetDone("Wopts Air"))
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("Undo didn't put back the color, the shape and the hidden parts") {
            t.entry(s.name)?.let {
                it.color == ColorRole.LINK && it.shape == WidgetShape.CLOVER8 &&
                    it.hidden == setOf(WidgetParts.ICON, WidgetParts.NAME)
            } == true
        }
        compose.onNodeWithTag("color link").performScrollTo().assertIsSelected()
    }

    /**
     * A page just added in Arrange widgets, still empty, stays while a card's settings page is open:
     * the person hasn't left Arrange widgets, and Back finds the page there.
     */
    @Test
    fun anEmptyPageStaysWhileACardsPageIsOpen() {
        val before = t.host.pages.value.size
        assumeTrue("there are as many pages as there may be", before < WidgetPages.MAX)
        t.host.addTo = WidgetLocation.PANEL
        val s = t.place(
            t.create("-pages", "-- @title Wopts Pages\n-- @widget stat 2x1\nwidget.show{ value = 4 }")
        )
        val vm = show(s)
        try {
            compose.waitForNode(hasClickLabel("change Wopts Pages"))
            compose.onNodeWithTag("page-add").performClick()
            compose.waitFor("no page was added") { t.host.pages.value.size == before + 1 }
            val added = WidgetPages.title(t.host.pages.value, before)
            compose.onNodeWithTag("page-tab ${WidgetPages.title(t.host.pages.value, 0)}").performClick()

            open(vm, "Wopts Pages")
            assertEquals(before + 1, t.host.pages.value.size)
            back()
            compose.runOnIdle { assertEquals(Screen.WIDGETS, vm.screen.value) }
            assertEquals(before + 1, t.host.pages.value.size)
            compose.onNodeWithTag("page-tab $added").assertExists()
        } finally {
            t.host.addTo = WidgetLocation.PANEL
            t.host.pages.value.drop(before).forEach { t.host.removePage(it.location) }
        }
    }

    /**
     * A card's page opens at its top, also after another card's page was left scrolled to its end. The
     * screens keep what they saved while another is open, as home's do: a saved scroll position would come back.
     */
    @Test
    fun aCardsPageOpensAtItsTop() {
        fun shaped(suffix: String, title: String): Script {
            val s = t.place(t.create(suffix, "-- @title $title\n-- @widget stat 2x2\nwidget.show{ value = 118, label = \"AQI\", icon = \"sun\" }"))
            t.host.setLook(t.entry(s.name)!!.key, WidgetLook.SHAPE)
            return s
        }
        val vm = show(shaped("-air", "Wopts Air"), shaped("-rain", "Wopts Rain"))
        fun scrolledBy() = compose.onNodeWithTag("widget-settings-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()

        open(vm, "Wopts Air")
        assertEquals(0f, scrolledBy())
        compose.onNodeWithText(WidgetSettingsWords.RESET).performScrollTo()
        compose.waitForIdle()
        assertTrue("the page is too short to scroll on this screen", scrolledBy() > 0f)
        back()
        compose.runOnIdle { assertEquals(Screen.WIDGETS, vm.screen.value) }

        open(vm, "Wopts Rain")
        assertEquals(0f, scrolledBy())
    }

    /**
     * The Now playing card has album art as background under What shows, off at first. A tap turns
     * it on and keeps it for that card; another turns it off. Reset turns it off, and Undo on again.
     */
    @Test
    fun nowPlayingHasAlbumArtAsBackground() {
        assumeTrue(
            "Now playing was on the widgets page before the test",
            t.host.placeBuiltin(WidgetSource.MEDIA, WidgetSize.S4X2)
        )
        val key = "s:" + WidgetSource.builtin(WidgetSource.MEDIA)
        fun on() = t.host.widgets.value.firstOrNull { it.key == key }?.artBackground
        val vm = show()
        open(vm, WidgetSource.builtinTitle(WidgetSource.MEDIA)!!)
        compose.onNodeWithText(MediaArt.LABEL).performScrollTo().assertExists()
        compose.onNodeWithText(MediaArt.DETAIL).assertExists()
        val row = compose.onNodeWithTag(ART_BACKGROUND_TAG)
        row.performScrollTo().assertIsOff()
        assertEquals(false, on())
        compose.onNodeWithTag("reset-own").performScrollTo().assertIsNotEnabled()

        compose.onNodeWithTag(ART_BACKGROUND_TAG).performScrollTo().performClick()
        compose.waitFor("album art as background isn't kept") { on() == true }
        compose.onNodeWithTag(ART_BACKGROUND_TAG).assertIsOn()
        compose.onNodeWithTag(ART_BACKGROUND_TAG).performClick()
        compose.waitFor("album art as background isn't off again") { on() == false }
        compose.onNodeWithTag(ART_BACKGROUND_TAG).assertIsOff()

        compose.onNodeWithTag(ART_BACKGROUND_TAG).performClick()
        compose.waitFor("album art as background isn't kept") { on() == true }
        compose.onNodeWithTag("reset-own").performScrollTo().assertIsEnabled().performClick()
        compose.waitFor("Reset didn't turn album art as background off") { on() == false }
        compose.onNodeWithTag(ART_BACKGROUND_TAG).performScrollTo().assertIsOff()
        compose.waitForText(WidgetSettingsWords.resetDone(WidgetSource.builtinTitle(WidgetSource.MEDIA)!!))
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("Undo didn't turn album art as background on again") { on() == true }
    }

    /**
     * Last updated: a script's card has it under What shows, off until turned on. On, the card above
     * says when its content was set; Reset turns it off, and Undo on again. At 1×1 a line under it
     * says the card leaves it out. One of Kolahal's own cards reads live data, and has none.
     */
    @Test
    fun lastUpdatedIsOfferedOnAScriptsCard() {
        val s = t.place(
            t.create(
                "-match",
                "-- @title Wopts Match\n-- @widget versus 4x1\nwidget.show{ kind = \"versus\", score = \"2–1\", badge = \"67'\", sides = { { name = \"Harbor\" }, { name = \"Ridge\" } } }"
            )
        )
        val tiny = t.place(t.create("-tiny", "-- @title Wopts Tiny\n-- @widget stat 1x1\nwidget.show{ value = 42 }"))
        val vm = show(s, tiny)
        open(vm, "Wopts Match")
        val part = "part ${WidgetParts.UPDATED}"
        compose.onNodeWithTag(part).performScrollTo().assertIsOff()
        compose.onNodeWithText(WidgetParts.label(WidgetParts.UPDATED)).assertExists()
        compose.onNodeWithText(WidgetParts.detail(WidgetParts.UPDATED)).assertExists()
        compose.onNodeWithTag("reset-own").performScrollTo().assertIsNotEnabled()

        compose.onNodeWithTag(part).performScrollTo().performClick()
        compose.waitFor("Last updated isn't kept") { t.entry(s.name)?.shown == setOf(WidgetParts.UPDATED) }
        compose.onNodeWithTag(part).assertIsOn()
        assertTrue(t.entry(s.name)!!.hidden.isEmpty())
        // The card above says when its content was set, a versus card at 4×1 too.
        compose.waitForNode(hasText("Updated ", substring = true) and hasAnyAncestor(hasTestTag("widget-preview")))

        compose.onNodeWithTag("reset-own").performScrollTo().assertIsEnabled().performClick()
        compose.waitFor("Reset didn't turn Last updated off") { t.entry(s.name)?.shown?.isEmpty() == true }
        compose.onNodeWithTag(part).performScrollTo().assertIsOff()
        compose.waitForText(WidgetSettingsWords.resetDone("Wopts Match"))
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("Undo didn't turn Last updated on again") { t.entry(s.name)?.shown == setOf(WidgetParts.UPDATED) }

        // A 1×1 card has no room for it: the switch says so.
        back()
        open(vm, "Wopts Tiny")
        compose.onNodeWithTag(part).performScrollTo()
        compose.waitForNode(hasText("Not at 1×1", substring = true))
        back()

        // One of Kolahal's own: no Last updated.
        assumeTrue(
            "Battery and network was on the widgets page before the test",
            t.host.placeBuiltin(WidgetSource.BATTERY, WidgetSize.S2X1)
        )
        open(vm, WidgetSource.builtinTitle(WidgetSource.BATTERY)!!)
        compose.waitForNode(hasTestTag("part ${WidgetParts.NAME}"))
        compose.onNodeWithTag(part).assertDoesNotExist()
    }

    /** Out of the Shape look, the page has no shapes; Back returns to Arrange widgets. */
    @Test
    fun noShapesOutOfTheShapeLook() {
        val s = t.place(
            t.create("-plainlook", "-- @title Wopts Look\n-- @widget stat 2x1\nwidget.show{ value = 3 }")
        )
        val vm = show(s)
        open(vm, "Wopts Look")
        compose.onNodeWithTag("colors").assertExists()
        compose.onNodeWithTag("shapes").assertDoesNotExist()
        assertNull(t.entry(s.name)?.shape)
        // Album art as background is the Now playing card's alone.
        compose.onNodeWithTag(ART_BACKGROUND_TAG).assertDoesNotExist()
        // The Agenda's days are its own.
        compose.onNodeWithTag("days Auto").assertDoesNotExist()
        back()
        compose.runOnIdle { assertEquals(Screen.WIDGETS, vm.screen.value) }
    }

    /**
     * Scene, last under What shows: None at first. A tap opens the choice, with each scene; a tap on
     * one keeps it for that card and closes it. In the Split look the row says why no scene shows.
     * Reset takes it away, and Undo puts it back.
     */
    @Test
    fun aCardHasAScene() {
        val s = t.place(
            t.create("-scene", "-- @title Wopts Scene\n-- @widget list 4x2\nwidget.show{ kind = \"list\", items = { { title = \"Keyboard\", trailing = \"Today\" }, { title = \"Books\", trailing = \"Mon\" } } }")
        )
        val key = t.key(s.name)
        val vm = show(s)
        open(vm, "Wopts Scene")
        compose.onNodeWithTag(SCENE_ROW_TAG).performScrollTo().assertExists()
        compose.onNode(hasTestTag(SCENE_ROW_TAG) and hasText(CardScenes.NONE_LABEL, substring = true)).assertExists()

        compose.onNodeWithTag(SCENE_ROW_TAG).performClick()
        compose.onNodeWithTag("scene own").assertIsSelected()
        // Each of Kolahal's scenes is a choice; the first few are in view.
        compose.onNodeWithTag("scene ${CardScenes.names.first()}").assertExists()
        compose.onNodeWithTag("scene-choices").performScrollToNode(hasTestTag("scene pond"))
        compose.onNodeWithTag("scene pond").performClick()
        compose.waitFor("the scene isn't kept") { t.entry(s.name)?.scene == "pond" }
        compose.onNodeWithTag("scene-choices").assertDoesNotExist()
        compose.onNode(hasTestTag(SCENE_ROW_TAG) and hasText("Pond", substring = true)).assertExists()

        // Split keeps its tiles: the rows say why.
        compose.runOnUiThread { t.host.setLook(key, WidgetLook.SPLIT) }
        compose.waitForNode(hasText(CardScenes.why(WidgetLook.SPLIT)!!, substring = true))

        compose.onNodeWithTag("reset-own").performScrollTo().assertIsEnabled().performClick()
        compose.waitFor("Reset didn't take the scene away") {
            t.entry(s.name)?.scene == null
        }
        compose.waitForText(WidgetSettingsWords.resetDone("Wopts Scene"))
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("Undo didn't put the scene back") {
            t.entry(s.name)?.scene == "pond"
        }
    }

    /** The Agenda card's page has its days, Auto at first; a tap saves another count. Other cards have none. */
    @Test
    fun theAgendaHasItsDaysOnItsPage() {
        assumeTrue(
            "the Agenda was on the widgets page before the test",
            t.host.placeBuiltin(WidgetSource.AGENDA, WidgetSize.S4X2)
        )
        val config = com.mtwabpin.kolahal.data.Config.get(app)
        try {
            val vm = show()
            open(vm, WidgetSource.builtinTitle(WidgetSource.AGENDA)!!)
            listOf("Auto", "1", "3", "7", "14").forEach { compose.onNodeWithTag("days $it").performScrollTo().assertExists() }
            compose.onNodeWithTag("days Auto").assertIsSelected()
            compose.onNodeWithTag("days 7").performClick()
            compose.waitFor("the days aren't kept") { config[Keys.AGENDA_WIDGET_DAYS] == "7" }
            compose.onNodeWithTag("days 7").assertIsSelected()
            // A scene as on every card.
            compose.onNodeWithTag(SCENE_ROW_TAG).performScrollTo().assertExists()
        } finally {
            config.reset(Keys.AGENDA_WIDGET_DAYS)
        }
    }
}
