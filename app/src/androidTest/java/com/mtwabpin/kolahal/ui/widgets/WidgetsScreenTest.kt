package com.mtwabpin.kolahal.ui.widgets

import android.app.Application
import android.graphics.Bitmap
import android.os.Process
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForText
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.TestWidgets
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetOrder
import com.mtwabpin.kolahal.widgets.WidgetPlacement
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Settings → Widgets → Arrange widgets: placed widgets live, where they are, their look and size, and the ones to add again. */
@RunWith(AndroidJUnit4::class)
class WidgetsScreenTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val t = TestWidgets()
    private val widgets = t.host

    @After
    fun tearDown() {
        config.reset(Keys.WIDGETS_SHOW)
        config.reset(Keys.AGENDA_WIDGET_DAYS)
        t.close()
        widgets.releaseViews(compose.activity)
    }

    /**
     * The screen, as it opens. A widget just placed is chosen at once, unless [fresh] is false.
     * [onSettings] gets the widget whose Widget settings was asked for.
     */
    private fun show(
        fresh: Boolean = false,
        onSettings: (PlacedWidget) -> Unit = {},
        onReAdd: (ProviderEntry, PlacedWidget) -> Unit = { _, _ -> }
    ): TerminalViewModel {
        if (!fresh) widgets.chosen()
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.WIDGETS)
        }
        compose.setContent { WidgetsScreen(vm, onSettings = onSettings, onReAdd = onReAdd) }
        return vm
    }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun card(label: String) = compose.onNode(hasClickLabel("change $label"))

    /** A tap on the widget called [label]: its toolbar comes up. Another widget's toolbar is put away first, so it hides nothing. */
    private fun choose(label: String) {
        if (compose.onAllNodes(hasContentDescription("Done")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Done").performClick()
        }
        card(label).performClick()
        compose.onNodeWithContentDescription("Done").assertExists()
    }

    /** A chosen card: selected, and no tab. The tab of the page being arranged is selected too. */
    private fun isChosen() = isSelected() and !SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /** The chosen widget as drawn, in dp. */
    private fun chosenCard() = compose.onNode(isChosen()).getUnclippedBoundsInRoot()

    private fun panel() = WidgetOrder.inLocation(widgets.widgets.value, WidgetLocation.PANEL)

    /** Runs the screen reader's action [label] on this card. */
    private fun SemanticsNodeInteraction.act(label: String) {
        val action =
            fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    /**
     * A clock on the page, Kolahal allowed to bind it, made full width (a new one starts at its own
     * width); and what the screen calls it.
     */
    private fun placeClock(index: Int = 0): Pair<PlacedWidget, String> {
        val clocks = t.clocks()
        assumeTrue("only ${clocks.size} clock widgets on this device", clocks.size > index)
        val clock = clocks[index]
        t.allowBinding(clock)
        val placed = t.place(clock)
        widgets.resizeTo(placed.key, WidgetLayout.COLUMNS, placed.heightDp)
        return t.get(placed.key)!! to t.label(clock)
    }

    /** One of Kolahal's own widgets on the page, at [size]; and what the screen calls it. */
    private fun placeBuiltin(
        id: String,
        size: WidgetSize = WidgetSize.S2X2
    ): Pair<PlacedWidget, String> {
        assumeTrue("$id was on the widgets page before the test", widgets.placeBuiltin(id, size))
        return t.get("s:" + WidgetSource.builtin(id))!! to WidgetSource.builtinTitle(id)!!
    }

    /** With the widgets page off, the screen says so, rather than "swipe sideways", and turns it on in a tap. */
    @Test
    fun withThePageOffItSaysSoAndTurnsItOn() {
        config.set(Keys.WIDGETS_SHOW, "off")
        show()
        compose.onNodeWithText("The widgets page is off").assertExists()
        compose.onNodeWithText("Turn on the widgets page").performClick()
        compose.waitForIdle()
        assertEquals(true, config[Keys.WIDGETS_SHOW])
        compose.onNodeWithText("Turn on the widgets page").assertDoesNotExist()
        compose.onNodeWithText("The widgets page is off").assertDoesNotExist()
    }

    /** No widgets yet: the screen says where they will show, and a tap opens the picker. Nothing to close up. */
    @Test
    fun anEmptyPageOffersToAddOne() {
        assumeTrue("widgets were placed before the test", widgets.widgets.value.isEmpty())
        val vm = show()
        compose.onNodeWithText("On the widgets page beside the terminal").assertExists()
        compose.onNodeWithText("No widgets yet. They appear on the widgets page beside the terminal: swipe sideways on the home screen to reach it.")
            .assertExists()
        compose.onNodeWithContentDescription("Close gaps").assertDoesNotExist()
        compose.onNodeWithText("Add a widget").performClick()
        compose.runOnIdle { assertEquals(Screen.WIDGET_PICKER, vm.screen.value) }
    }

    /** Add widget in the top bar opens the picker. */
    @Test
    fun addWidgetOpensThePicker() {
        val vm = show()
        compose.onNodeWithContentDescription("Add widget").performClick()
        compose.runOnIdle { assertEquals(Screen.WIDGET_PICKER, vm.screen.value) }
    }

    /** "Added Clock", said as the picker hands over to this screen, shows here once. */
    @Test
    fun aWaitingMessageShowsOnceTheScreenOpens() {
        widgets.message("Added Clock")
        show()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Added Clock").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(widgets.messages.value.isEmpty())
    }

    /** A widget the picker just placed is chosen as the screen opens, so it can be moved at once. */
    @Test
    fun aNewWidgetIsChosenAtOnce() {
        val (_, label) = placeBuiltin(WidgetSource.BATTERY)
        show(fresh = true)
        compose.waitForIdle()
        compose.onNode(isSelected() and hasClickLabel("change $label")).assertExists()
        compose.onNodeWithContentDescription("Done").assertExists()
        assertNull(widgets.toChoose.value)
    }

    /** The Agenda has the Now look. Its days are on its settings page, not in the toolbar. */
    @Test
    fun theAgendaHasTheNowLookAndItsDaysOnItsPage() {
        val (_, agenda) = placeBuiltin(WidgetSource.AGENDA)
        show()
        choose(agenda)
        // An agenda's own look, beside the five it shares.
        compose.onNodeWithTag("look Now").assertExists()
        compose.onNodeWithTag("days Auto").assertDoesNotExist()
    }

    /** One of Kolahal's own widgets has a Look row and a Size row; an app's widget has steps and Square. */
    @Test
    fun ownWidgetsHaveLooksAndSizesAppWidgetsHaveSteps() {
        val (_, own) = placeBuiltin(WidgetSource.BATTERY)
        val (_, clock) = placeClock()
        show()
        choose(own)
        // Battery is a progress card: every look but Poster, which is for a stat, a countdown and a clock, and Now, an agenda's.
        (WidgetLook.entries - WidgetLook.POSTER - WidgetLook.ownOnly - WidgetLook.NOW).forEach { compose.onNodeWithTag("look ${it.label}").assertExists() }
        compose.onNodeWithTag("look Poster").assertDoesNotExist()
        compose.onNodeWithTag("look Now").assertDoesNotExist()
        compose.onNodeWithTag("look Plain").assertIsOn()
        listOf("1×1", "2×1", "2×2", "4×1", "4×2", "4×4").forEach {
            compose.onNodeWithTag("size $it").assertExists()
        }
        compose.onNodeWithTag("size 2×2").assertIsOn()
        compose.onNodeWithText("2×2 · Plain").assertExists()
        compose.onNodeWithText("Drag to move. Drag an edge to resize.").assertExists()
        compose.onNodeWithText("Square").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase height").assertDoesNotExist()
        // Its Settings opens its page; color and shape are there, not in the toolbar.
        compose.onNodeWithTag("widget-settings").assertExists()
        compose.onNodeWithTag("color own").assertDoesNotExist()

        choose(clock)
        compose.onNodeWithTag("widget-settings").assertDoesNotExist()
        compose.onNodeWithTag("look Plain").assertDoesNotExist()
        compose.onNodeWithTag("size 2×2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase height").assertExists()
        compose.onNodeWithText("Square").assertExists()
    }

    /** A tap on a look gives the widget that look, at once; the others stay as they were. */
    @Test
    fun aLookTapChangesTheLook() {
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        show()
        choose(label)
        compose.onNodeWithTag("look Tonal").performClick()
        compose.waitForIdle()
        assertEquals(WidgetLook.TONAL, t.get(w.key)!!.look)
        compose.onNodeWithTag("look Tonal").assertIsOn()
        compose.onNodeWithTag("look Plain").assertIsOff()
        compose.onNodeWithText("2×2 · Tonal").assertExists()
        compose.onNodeWithTag("look Shape").performClick()
        compose.waitForIdle()
        assertEquals(WidgetLook.SHAPE, t.get(w.key)!!.look)
    }

    /**
     * The Look row offers only the looks that change the widget's kind: Shortcuts (buttons) has no
     * Bold. A Bold stored for it is kept, but the card draws Plain and the row says so.
     */
    @Test
    fun theLookRowOffersOnlyLooksThatChangeTheKind() {
        val (w, label) = placeBuiltin(WidgetSource.SHORTCUTS, WidgetSize.S4X1)
        widgets.setLook(w.key, WidgetLook.BOLD)
        show()
        choose(label)
        compose.onNodeWithTag("look Bold").assertDoesNotExist()
        listOf("Plain", "Tonal", "Split", "Shape").forEach {
            compose.onNodeWithTag("look $it").assertExists()
        }
        compose.onNodeWithTag("look Plain").assertIsOn()
        compose.onNodeWithText("4×1 · Plain").assertExists()
        assertEquals(WidgetLook.BOLD, t.get(w.key)!!.look)
    }

    /** A tap on a size gives the widget that width and height, drawn so. */
    @Test
    fun aSizeTapChangesSpanAndHeight() {
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        show()
        choose(label)
        val half = chosenCard().width.value
        compose.onNodeWithTag("size 4×1").performClick()
        compose.waitForIdle()
        val now = t.get(w.key)!!
        assertEquals(WidgetLayout.COLUMNS, now.span)
        assertEquals(WidgetSize.S4X1, widgets.sizeOf(now))
        assertTrue("the row is shorter than 2×2", now.heightDp < w.heightDp)
        compose.onNodeWithTag("size 4×1").assertIsOn()
        compose.onNodeWithText("4×1 · Plain").assertExists()
        val gap = WidgetLayout.GAP_DP.toFloat()
        assertEquals((half + gap) * 2 - gap, chosenCard().width.value, 1f)
        assertEquals(now.heightDp.toFloat(), chosenCard().height.value, 1f)
    }

    /** Each − or + makes the chosen widget 8dp shorter or taller, on the page as in the toolbar. */
    @Test
    fun heightStepsByEightWithMinusAndPlus() {
        val (w, label) = placeClock()
        show()
        compose.onNodeWithText("${panel().size} on the widgets page").assertExists()
        choose(label)
        val h = w.heightDp
        val drawn = chosenCard().height.value
        compose.onNodeWithText("Full width · $h dp tall").assertExists()
        compose.onNodeWithContentDescription("Increase height").performClick()
        compose.onNodeWithText("Full width · ${h + 8} dp tall").assertExists()
        assertEquals(h + 8, t.get(w.key)!!.heightDp)
        assertEquals(drawn + 8, chosenCard().height.value, 1f)
        compose.onNodeWithContentDescription("Decrease height").performClick()
        compose.onNodeWithContentDescription("Decrease height").performClick()
        compose.onNodeWithText("Full width · ${h - 8} dp tall").assertExists()
        assertEquals(h - 8, t.get(w.key)!!.heightDp)
        assertEquals(drawn - 8, chosenCard().height.value, 1f)
    }

    /** A full-width widget can't be wider; − takes off a twelfth of the page, drawn narrower. */
    @Test
    fun widthStepsByTwelfthsOfThePage() {
        val (w, label) = placeClock()
        show()
        choose(label)
        val page = chosenCard().width.value
        compose.onNodeWithContentDescription("Increase width").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Decrease width").performClick()
        compose.onNodeWithText("11/12 wide · ${w.heightDp} dp tall").assertExists()
        assertEquals(11, t.get(w.key)!!.span)
        val gap = WidgetLayout.GAP_DP.toFloat()
        assertEquals((page + gap) * 11 / 12 - gap, chosenCard().width.value, 1f)
        compose.onNodeWithContentDescription("Increase width").assertIsEnabled().performClick()
        compose.onNodeWithText("Full width · ${w.heightDp} dp tall").assertExists()
    }

    /** Square makes the widget as tall as the page is wide. */
    @Test
    fun squareMakesItAsTallAsItIsWide() {
        val (w, label) = placeClock()
        show()
        choose(label)
        compose.onNodeWithText("Square").performClick()
        compose.waitForIdle()
        val card = chosenCard()
        assertEquals(card.width.value, card.height.value, 1.5f)
        val h = t.get(w.key)!!.heightDp
        compose.onNodeWithText("Full width · $h dp tall").assertExists()
    }

    /**
     * A screen reader moves a widget a column at a time, and a row down: drawn there too. Move left
     * isn't offered at the start edge.
     */
    @Test
    fun aScreenReaderMovesAWidget() {
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        widgets.moveTo(w.key, 0, t.get(w.key)!!.topDp)
        show()
        val left = card(label).getUnclippedBoundsInRoot().left
        val labels =
            card(label).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertFalse("Move left at the start edge: $labels", "Move left" in labels)
        assertTrue(
            labels.containsAll(
                listOf(
                    "Move down",
                    "Move right",
                    "Wider",
                    "Narrower",
                    "Taller",
                    "Shorter",
                    "Remove"
                )
            )
        )
        card(label).act("Move right")
        assertEquals(1, t.get(w.key)!!.column)
        assertTrue("drawn further right", card(label).getUnclippedBoundsInRoot().left > left)
        val top = t.get(w.key)!!.topDp
        card(label).act("Move down")
        assertTrue("a row down", t.get(w.key)!!.topDp >= top + widgets.rowDp())
        card(label).act("Wider")
        assertEquals(7, t.get(w.key)!!.span)
    }

    /** The screen reader's actions on the card called [label]. */
    private fun actionsOf(label: String) =
        card(label).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    /** Waits until the card called [label] is named so: its app's name loads off the main thread. */
    private fun waitForCard(label: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasClickLabel("change $label")).fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * An app's widget whose setup may run again has Widget settings in its toolbar and for a screen
     * reader; each hands the widget to the screen's caller, which opens its setup.
     */
    @Test
    fun widgetSettingsOpenAnAppWidgetsSetupAgain() {
        val p = t.withSetup(again = true)
        t.allowBinding(p)
        val w = t.place(p)
        val label = t.label(p)
        val asked = mutableListOf<String>()
        show(onSettings = { asked += it.key })
        waitForCard(label)
        compose.waitUntil(5_000) { "Widget settings" in actionsOf(label) }
        // Before Remove, as in the toolbar.
        val actions = actionsOf(label)
        assertEquals(actions.indexOf("Remove") - 1, actions.indexOf("Widget settings"))
        choose(label)
        compose.onNodeWithContentDescription("Widget settings").performClick()
        compose.runOnIdle { assertEquals(listOf(w.key), asked) }
        card(label).act("Widget settings")
        assertEquals(listOf(w.key, w.key), asked)
    }

    /**
     * No Widget settings for an app's widget whose setup runs only as it is placed, nor for one with
     * no setup screen. A card that holds something has its own edit as a screen reader's action too.
     */
    @Test
    fun widgetSettingsOnlyWhereTheSetupMayRunAgain() {
        val again = t.withSetup(again = true)
        val once = t.withSetup(again = false)
        val clock = t.clocks(unconfigured = true).first()
        t.allowBinding(clock)
        t.place(again)
        t.place(once)
        t.place(clock)
        val (_, shortcuts) = placeBuiltin(WidgetSource.SHORTCUTS)
        show()
        listOf(again, once, clock).forEach { waitForCard(t.label(it)) }
        // Once the first one has it, the screen has read which can run their setup again.
        compose.waitUntil(5_000) { "Widget settings" in actionsOf(t.label(again)) }
        listOf(once, clock).forEach {
            val label = t.label(it)
            assertFalse(label, "Widget settings" in actionsOf(label))
            choose(label)
            compose.onNodeWithContentDescription("Widget settings").assertDoesNotExist()
        }
        // One of Kolahal's own cards has Settings, which opens its page (Edit shortcuts is there).
        assertTrue("Settings" in actionsOf(shortcuts))
        assertFalse("Widget settings" in actionsOf(shortcuts))
    }

    /**
     * Widget settings need the widget's profile listed and running ([isRunning]). A private space that
     * Android hides while it is locked isn't listed: its widget opens nothing.
     */
    @Test
    fun widgetSettingsNeedTheProfileListedAndRunning() {
        val me = Process.myUserHandle()
        val serial = app.getSystemService(UserManager::class.java).getSerialNumberForUser(me)
        val personal = Profile(me, serial, ProfileKind.PERSONAL, quiet = false)
        assertTrue(listOf(personal).isRunning(serial))
        assertFalse(listOf(personal.copy(quiet = true)).isRunning(serial))
        assertFalse("not listed", listOf(personal).isRunning(serial + 1))
        assertFalse("not read yet", emptyList<Profile>().isRunning(serial))
    }

    /**
     * A widget's profile: paused or locked ([pausedKind]) and its kind ([kindOf]) as listed. A profile
     * the list misses is a locked private space only when Android says so (`lockedPrivate`, asked of
     * [com.mtwabpin.kolahal.widgets.WidgetHost.isLockedPrivate]), and only then is Android asked.
     */
    @Test
    fun aProfileTheListMissesIsAskedOfAndroid() {
        val me = Process.myUserHandle()
        val serial = app.getSystemService(UserManager::class.java).getSerialNumberForUser(me)
        val personal = Profile(me, serial, ProfileKind.PERSONAL, quiet = false)
        val space = Profile(me, serial + 7, ProfileKind.PRIVATE, quiet = true)
        val asked = mutableListOf<Long>()
        val hidden: (Long) -> Boolean = { asked += it; it == serial + 9 }
        val listed = listOf(personal, space)
        assertEquals(null, listed.pausedKind(serial, hidden))
        assertEquals(ProfileKind.PRIVATE, listed.pausedKind(serial + 7, hidden))
        assertEquals(ProfileKind.WORK, listOf(personal.copy(kind = ProfileKind.WORK, quiet = true)).pausedKind(serial, hidden))
        assertTrue("Android isn't asked for a listed profile", asked.isEmpty())
        assertEquals("hidden while locked", ProfileKind.PRIVATE, listed.pausedKind(serial + 9, hidden))
        assertEquals(null, listed.pausedKind(serial + 8, hidden))
        assertEquals(ProfileKind.PERSONAL, listed.kindOf(serial, hidden))
        assertEquals(ProfileKind.PRIVATE, listed.kindOf(serial + 9, hidden))
        assertEquals("can't be found", null, listed.kindOf(serial + 8, hidden))
        // The widget's name then: none in a hidden private space; kept where the profile can't be found.
        assertFalse(namesApp(listed.kindOf(serial + 9, hidden)))
        assertTrue(namesApp(listed.kindOf(serial + 8, hidden)))
    }

    /**
     * A row under "Restored from backup" whose profile is a private space shows neither the app's
     * package nor its name, and still adds the widget again in one tap.
     */
    @Test
    fun aRestoredPrivateSpaceWidgetIsNotNamed() {
        val clock = t.clocks().first()
        val repo = com.mtwabpin.kolahal.apps.AppRepository.get(app)
        val listed = repo.profiles.value.ifEmpty {
            listOf(Profile(Process.myUserHandle(), clock.serial, ProfileKind.PERSONAL, quiet = false))
        }
        val space = Profile(Process.myUserHandle(), 4247, ProfileKind.PRIVATE, quiet = true)
        try {
            repo.standInForTests(emptyList(), listed + space)
            t.restore(clock.flat, 4247, height = 160)
            show()
            compose.onNodeWithText("Restored from backup").performScrollTo()
            compose.waitForText("Private space widget")
            compose.onNodeWithText(t.label(clock)).assertDoesNotExist()
            compose.onAllNodesWithText(clock.packageName, substring = true).assertCountEquals(0)
            compose.onNodeWithText("Add again").assertIsEnabled()
        } finally {
            repo.standInForTests(null)
        }
    }

    /**
     * A row whose profile isn't on this phone (a work profile on the phone the backup came from, say)
     * keeps its package and name: Android doesn't know the profile, so nothing says it was a private space.
     */
    @Test
    fun aRestoredWidgetOfAProfileNotHereKeepsItsName() {
        val clock = t.clocks().first()
        t.restore(clock.flat, 4249, height = 160)
        show()
        compose.onNodeWithText("Restored from backup").performScrollTo()
        compose.waitForText(t.label(clock))
        compose.onAllNodesWithText(clock.packageName, substring = true).assertCountEquals(1)
        compose.onNodeWithText("Private space widget").assertDoesNotExist()
    }

    /** After Close gaps the snackbar offers Undo, which puts every widget back where it was. */
    @Test
    fun undoAfterCloseGapsPutsThePageBack() {
        val (w, _) = placeBuiltin(WidgetSource.BATTERY)
        val (o, _) = placeBuiltin(WidgetSource.TODOS)
        widgets.moveTo(w.key, 0, 0)
        widgets.moveTo(o.key, 6, t.get(w.key)!!.heightDp + 200)
        show()
        val before = panel().map { it.key to it.topDp }
        compose.onNodeWithContentDescription("Close gaps").performClick()
        compose.waitForText("Gaps closed")
        assertNotEquals(before, panel().map { it.key to it.topDp })
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("Undo didn't put the page back") { panel().map { it.key to it.topDp } == before }
        compose.onNodeWithText("Gaps closed").assertDoesNotExist()
    }

    /**
     * After a drop that moved a widget the snackbar offers Undo: the widget, and the ones it pushed,
     * go back. A second change takes the first one's place: only it goes back.
     */
    @Test
    fun undoAfterAMoveTakesBackThatMoveOnly() {
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        // A gap above it, for Close gaps to close.
        widgets.moveTo(w.key, 0, 200)
        show()
        compose.onNodeWithContentDescription("Close gaps").performClick()
        compose.waitForText("Gaps closed")
        val packed = panel().map { it.key to (it.column to it.topDp) }
        card(label).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            repeat(10) { moveBy(Offset(width / 10f, 0f)) }
            up()
        }
        compose.waitForText("Widget moved")
        assertEquals(6, t.get(w.key)!!.column)
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("Undo didn't move the widget back") { t.get(w.key)!!.column == 0 }
        assertEquals(packed, panel().map { it.key to (it.column to it.topDp) })
    }

    /** Close gaps moves every widget up as far as it goes. */
    @Test
    fun closeGapsPacksThePage() {
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        show()
        val top = t.get(w.key)!!.topDp
        widgets.moveTo(w.key, t.get(w.key)!!.column, top + 400)
        compose.waitForIdle()
        val down = card(label).getUnclippedBoundsInRoot().top
        val packed = WidgetPlacement.closeGaps(widgets.widgets.value)
        compose.onNodeWithContentDescription("Close gaps").performClick()
        compose.waitForIdle()
        assertEquals(
            packed.filter(WidgetPlacement::onPage).map { it.key to it.topDp },
            widgets.widgets.value.filter(WidgetPlacement::onPage).map { it.key to it.topDp })
        assertTrue("the widget is drawn higher", card(label).getUnclippedBoundsInRoot().top < down)
        assertTrue(t.get(w.key)!!.topDp <= top)
    }

    /** A long-press lifts a widget, and a drag to the side moves it there when it is let go. */
    @Test
    fun aLongPressAndDragMovesAWidget() {
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        widgets.moveTo(w.key, 0, t.get(w.key)!!.topDp)
        show()
        card(label).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            // The width of the card to the side: half the page.
            repeat(10) { moveBy(Offset(width / 10f, 0f)) }
        }
        compose.waitForIdle()
        // While it is held, Remove takes the top bar's place.
        compose.onNodeWithText("Remove").assertExists()
        card(label).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(6, t.get(w.key)!!.column)
        compose.onNode(isChosen()).assertExists()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }

    /**
     * A lone widget dragged far down lands at the foot of the page as home shows it, with empty space
     * above it, not just under where it was; and home shows it there, at the bottom, with nothing scrolled.
     */
    @Test
    fun aLoneWidgetDropsAtTheFootOfThePage() {
        assumeTrue("widgets were on page 1 before the test", panel().isEmpty())
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        widgets.moveTo(w.key, 0, 0)
        widgets.chosen()
        val measured = widgets.measuredPageHeightDp
        try {
            lateinit var vm: TerminalViewModel
            compose.runOnUiThread { vm = vms.terminal(app) }
            val home = mutableStateOf(true)
            // Home lays the page out first: Arrange widgets reads how tall it shows.
            widgets.measuredPageHeightDp = 0
            compose.setContent { if (home.value) WidgetPanel(vm) else WidgetsScreen(vm, onReAdd = { _, _ -> }) }
            compose.waitFor("home didn't lay the page out") { widgets.measuredPageHeightDp > 0 }
            val shownDp = widgets.measuredPageHeightDp
            compose.runOnUiThread {
                vm.openScreen(Screen.WIDGETS)
                home.value = false
            }
            compose.waitForIdle()
            card(label).performTouchInput {
                down(center)
                advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
                // Far below the screen: it lands at the foot, no further.
                repeat(20) { moveBy(Offset(0f, 100.dp.toPx())) }
                up()
            }
            compose.waitForText("Widget moved")
            val foot = WidgetPlacement.lowestDrop(panel(), w.key, w.heightDp, shownDp)
            assertTrue("the foot $foot is under where it was", foot > WidgetLayout.GAP_DP)
            assertEquals(0 to foot, t.get(w.key)!!.let { it.column to it.topDp })
            compose.runOnUiThread { home.value = true }
            compose.waitForIdle()
            val page = compose.onNodeWithTag("widgets-page").getUnclippedBoundsInRoot()
            val card = compose.onNodeWithTag("widget:" + WidgetSource.builtin(WidgetSource.BATTERY), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            assertTrue("card $card on page $page", card.bottom <= page.bottom && card.top > page.top + page.height / 2)
            // Only the navigation bar and a step at most under it.
            assertTrue("card $card on page $page", page.bottom - card.bottom < 96.dp)
        } finally {
            widgets.measuredPageHeightDp = measured
        }
    }

    /** Dragging the chosen widget's end edge makes it wider by columns; its bottom edge, taller by 8 dp steps. */
    @Test
    fun aHandleDragResizes() {
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        widgets.moveTo(w.key, 0, t.get(w.key)!!.topDp)
        show()
        choose(label)
        card(label).performTouchInput {
            down(Offset(width + 4.dp.toPx(), height / 2f))
            repeat(5) { moveBy(Offset(width / 10f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertTrue("wider: ${t.get(w.key)!!.span}", t.get(w.key)!!.span in 8..10)
        val h = t.get(w.key)!!.heightDp
        card(label).performTouchInput {
            down(Offset(width / 2f, height + 4.dp.toPx()))
            repeat(4) { moveBy(Offset(0f, 10.dp.toPx())) }
            up()
        }
        compose.waitForIdle()
        // 40 dp down, to the nearest 8 dp step.
        assertEquals((h + 40).toFloat(), t.get(w.key)!!.heightDp.toFloat(), 4f)
        compose.onNode(isChosen()).assertExists()
    }

    /**
     * In a page longer than the screen, the chosen widget stays in view as it moves: each Move down
     * scrolls it back, with its top on the screen and above the toolbar.
     */
    @Test
    fun aMovedWidgetStaysInView() {
        val clocks = t.clocks()
        assumeTrue("no clock widget on this device", clocks.isNotEmpty())
        t.allowBinding(clocks[0])
        val placed = List(5) { t.place(clocks[0]) }
        // Tall, so five of them are much longer than the screen.
        placed.forEach { widgets.resize(it.key, 300) }
        val label = t.label(clocks[0])
        show()
        compose.onAllNodes(hasClickLabel("change $label"))[0].performClick()
        compose.waitForIdle()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        repeat(placed.size - 1) { step ->
            compose.onNode(isChosen()).act("Move down")
            compose.waitForIdle()
            val card = chosenCard()
            val bar = compose.onNodeWithContentDescription("Done").getUnclippedBoundsInRoot()
            assertTrue(
                "step $step: card $card, screen $screen",
                card.top >= screen.top && card.bottom > screen.top
            )
            assertTrue("step $step: card $card, toolbar $bar", card.top < bar.top)
        }
    }

    /** Remove asks first; Cancel keeps the widget, Remove deletes it and frees its id. */
    @Test
    fun removeAsksFirst() {
        val (w, label) = placeClock()
        show()
        choose(label)
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove $label?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove $label?").assertDoesNotExist()
        assertNotNull(t.get(w.key))
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()
        assertNull(t.get(w.key))
        assertFalse(w.id in t.ids())
        card(label).assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove").assertDoesNotExist()
    }

    /**
     * With two pages: a tab for each, the chosen widget's Page row moves it to the other page in one
     * tap, the screen goes with it, and the first tab no longer shows it. To a screen reader each tab
     * is a tab, "Selected" on the page being arranged, and + is a button, neither checked nor not.
     */
    @Test
    fun thePageRowMovesAWidgetToAnotherPage() {
        widgets.addTo = WidgetLocation.PANEL
        val (w, label) = placeBuiltin(WidgetSource.BATTERY)
        assertEquals(WidgetLocation.PANEL, w.location)
        val second = widgets.addPage()
        assertNotNull(second)
        show()
        compose.onNodeWithTag("page-tab Page 1").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        compose.onNodeWithTag("page-tab Page 2").assertIsNotSelected()
        compose.onNodeWithTag("page-add")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        choose(label)
        compose.onNodeWithTag("page Page 2").performClick()
        compose.waitUntil(5_000) { t.get(w.key)?.location == second }
        compose.onNodeWithTag("page-tab Page 2").assertIsSelected()
        card(label).assertExists()
        compose.onNodeWithTag("page-tab Page 1").performClick()
        card(label).assertDoesNotExist()
    }

    /** Back puts the toolbar away before it leaves the screen. */
    @Test
    fun backPutsTheToolbarAwayBeforeLeaving() {
        val (_, label) = placeClock()
        val vm = show()
        choose(label)
        compose.onNodeWithContentDescription("Increase height").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Increase height").assertDoesNotExist()
        compose.onNode(isChosen()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Add widget").assertExists()
        assertEquals(Screen.WIDGETS, vm.screen.value)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(Screen.TERMINAL, vm.screen.value) }
    }

    /**
     * A widget whose id Android no longer has says it's unavailable, and a tap still reaches its
     * controls. What the card shows is drawn, not read out: a screen reader hears its name once.
     */
    @Test
    fun anUnavailableWidgetSaysSoAndCanStillBeChanged() {
        val clock = t.clocks().first()
        // An id given out and never bound, as one whose app took it back.
        val id = widgets.allocate()
        widgets.place(id, clock.info)
        val fallback = clock.flat.substringAfterLast('.')
        show()
        compose.onNodeWithText("Widget unavailable").assertDoesNotExist()
        // Named by its provider's class, with no app to ask for a name.
        card(fallback).assertExists()
        compose.onNodeWithText(fallback).assertDoesNotExist()
        card(fallback).performClick()
        val placed = t.get("w$id")!!
        compose.onNodeWithText(WidgetLayout.sizeLine(placed.span, placed.heightDp)).assertExists()
        // In the toolbar.
        compose.onAllNodesWithText(fallback).assertCountEquals(1)
    }

    /**
     * A screen reader stops once on each card, on its name: an app widget's own view, and its
     * buttons, are hidden from it here.
     */
    @Test
    fun anAppWidgetsButtonsAreHiddenFromAScreenReader() {
        val (w, label) = placeClock()
        show()
        compose.waitForIdle()
        card(label).assertExists()
        fun hosted(): android.view.View? = compose.runOnUiThread {
            fun find(v: android.view.View): android.view.View? = when {
                v is android.appwidget.AppWidgetHostView && v.appWidgetId == w.id -> v
                v is android.view.ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
                else -> null
            }
            find(compose.activity.window.decorView)
        }
        assertEquals(
            android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            checkNotNull(hosted()) { "the clock's view isn't shown" }.importantForAccessibility
        )
    }

    /** A widget restored from a backup waits under "Restored from backup"; Add again hands it to the add flow. */
    @Test
    fun aRestoredWidgetIsAddedAgainInOneTap() {
        val clock = t.clocks().first()
        val pending = t.restore(clock.flat, clock.serial, height = 160)
        var readded: Pair<ProviderEntry, PlacedWidget>? = null
        show { e, w -> readded = e to w }
        compose.onNodeWithText("Restored from backup").performScrollTo()
        compose.onNodeWithText("Full width · 160 dp tall").assertExists()
        compose.onNodeWithText(t.label(clock)).assertExists()
        compose.onNodeWithText("Add again").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(clock.flat, readded?.first?.flat)
            assertEquals(pending.key, readded?.second?.key)
        }
    }

    /** One whose app isn't installed can't be added again, only forgotten. */
    @Test
    fun aRestoredWidgetWhoseAppIsGoneCanOnlyBeForgotten() {
        val serial = widgets.serialOf(android.os.Process.myUserHandle())
        val pending = t.restore("com.example.kolahal.gone/.ClockWidget", serial, height = 120)
        show()
        compose.onNodeWithText("App not installed").performScrollTo()
        compose.onNodeWithText("Add again").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Forget").performClick()
        compose.waitForIdle()
        assertNull(t.get(pending.key))
        compose.onNodeWithText("App not installed").assertDoesNotExist()
        compose.onNodeWithText("Restored from backup").assertDoesNotExist()
    }

    /**
     * Pictures of the screen, to look at: a widget chosen, one held in a drag, and an app's widget
     * chosen. They go to the app's files, in `screens/`.
     */
    @Test
    fun picturesOfTheScreen() {
        val out = checkNotNull(app.getExternalFilesDir("screens")) { "no external files" }
        out.listFiles()?.forEach { it.delete() }
        listOf(
            WidgetSource.BATTERY to WidgetSize.S2X2,
            WidgetSource.TODOS to WidgetSize.S2X2,
            WidgetSource.SHORTCUTS to WidgetSize.S4X1
        )
            .forEach { (id, size) -> widgets.placeBuiltin(id, size) }
        val battery = t.get("s:" + WidgetSource.builtin(WidgetSource.BATTERY))!!
        widgets.setLook(battery.key, WidgetLook.TONAL)
        val clock = t.clocks().firstOrNull()?.also { t.allowBinding(it) }
        val placedClock = clock?.let { t.place(it) }
        placedClock?.let { widgets.resizeTo(it.key, 6, 184) }
        show()
        fun save(name: String) {
            compose.waitForIdle()
            File(out, "$name.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }

        val label = WidgetSource.builtinTitle(WidgetSource.BATTERY)!!
        choose(label)
        save("chosen")
        card(label).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            repeat(8) { moveBy(Offset(width / 16f, width / 6f)) }
        }
        save("dragging")
        card(label).performTouchInput { up() }
        save("dropped")
        if (clock != null) {
            choose(t.label(clock))
            save("app-widget")
        }
        assertNotEquals(0, out.listFiles()?.size ?: 0)
    }
}
