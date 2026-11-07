package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.eventually
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app selector's strip on the edge you choose, and its list on the other side, the whole height. */
@RunWith(AndroidJUnit4::class)
class AppSelectorTest {
    @get:Rule val compose = createComposeRule()
    private val apps = AppRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before fun setUp() {
        apps.refresh()
        eventually(5_000) { apps.apps.value.size >= 5 }
    }

    @Volatile private var launched: AppEntry? = null
    @Volatile private var pressed: AppEntry? = null

    private fun show(
        left: Boolean,
        position: String,
        list: List<AppEntry> = apps.apps.value,
        backGesture: Boolean = false,
        actions: String = "off",
        icons: Boolean = false,
        letters: Boolean = false,
        browse: String = "letters",
        filter: Boolean = false,
        rowHeightDp: Int = Keys.SELECTOR_ROW_HEIGHT.default,
        showList: Boolean = false,
    ) = compose.setContent {
        Box(Modifier.size(360.dp, 700.dp)) {
            AppSelector(
                list, emptySet(), onLaunch = { launched = it }, onLongPress = { pressed = it }, heightDp = 300, left = left,
                position = position, edgeBackGesture = backGesture, actions = actions, icons = icons, letters = letters,
                browse = browse, filter = filter, rowHeightDp = rowHeightDp, showList = showList,
            )
        }
    }

    /**
     * The app launched by touching the strip's middle, sliding [slideDp] down it at 40dp a second
     * (slow: under the aim speed), then off the edge and lifting.
     */
    private fun launchAfterSlowSlide(slideDp: Int): AppEntry? {
        launched = null
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            repeat(slideDp / 2) { moveBy(Offset(0f, 2.dp.toPx()), delayMillis = 50) }
            moveBy(Offset(-60.dp.toPx(), 0f), delayMillis = 16)
            up()
        }
        compose.waitForIdle()
        return launched
    }

    /** Plan C: a slow slide aims, an app per selector.aim dp, where a glide would run through many more. */
    @Test fun aSlowSlideAims() {
        val list = apps.apps.value
        val order = SelectorMath.build(list.filter { !it.hidden }, { it.label }, { it.key }, { false }, { it.profile.kind.ordinal }).map { it.item.key }
        var aimDp by mutableIntStateOf(40)
        compose.setContent {
            Box(Modifier.size(360.dp, 700.dp)) {
                AppSelector(list, emptySet(), onLaunch = { launched = it }, onLongPress = {}, heightDp = 300, edgeBackGesture = false, aimDp = aimDp, browse = "apps")
            }
        }
        val start = order.indexOf(launchAfterSlowSlide(0)!!.key)
        val aimed = order.indexOf(launchAfterSlowSlide(80)!!.key) - start
        compose.runOnIdle { aimDp = 0 }
        val glided = order.indexOf(launchAfterSlowSlide(80)!!.key) - order.indexOf(launchAfterSlowSlide(0)!!.key)
        assertTrue("aimed $aimed apps", aimed in 1..3)
        assertTrue("glided $glided apps, aimed $aimed", glided > aimed + 1)
    }

    /** The app launched by touching the strip [atTop] or at its bottom, and picking it there. */
    private fun launchAtEnd(atTop: Boolean): AppEntry? {
        launched = null
        compose.onNodeWithTag("app-selector").performTouchInput {
            val y = if (atTop) 2f else height - 2f
            down(Offset(centerX, y))
            moveTo(Offset(centerX - 60.dp.toPx(), y))
            up()
        }
        compose.waitForIdle()
        return launched
    }

    /** Plan F: with the strip at the bottom of the edge, favourites come last, where the thumb rests; elsewhere first. */
    @Test fun favouritesSitWhereTheThumbRests() {
        val list = apps.apps.value.filter { !it.hidden }
        val favourite = list.sortedBy { it.label.lowercase() }[list.size / 2]
        var position by mutableStateOf("bottom")
        compose.setContent {
            Box(Modifier.size(360.dp, 700.dp)) {
                AppSelector(list, setOf(favourite.key), onLaunch = { launched = it }, onLongPress = {}, heightDp = 300, position = position, edgeBackGesture = false)
            }
        }
        assertEquals(favourite.key, launchAtEnd(atTop = false)?.key)
        compose.runOnIdle { position = "top" }
        assertEquals(favourite.key, launchAtEnd(atTop = true)?.key)
    }

    private fun lettersShown() = compose.onAllNodesWithTag("app-selector-letter", useUnmergedTree = true).fetchSemanticsNodes()

    /** selector.letters: every letter that has apps, top to bottom in the list's order, and only those; under the thumb the wave takes over. */
    @Test fun lettersShowAlongTheEdge() {
        val list = apps.apps.value
        val expected = SelectorMath.build(list.filter { !it.hidden }, { it.label }, { it.key }, { false }, { it.profile.kind.ordinal })
            .filter { it.sectionStart }.map { it.section.toString() }
        show(left = false, position = "bottom", list = list, letters = true)
        val shown = lettersShown().sortedBy { it.boundsInRoot.top }
            .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") }
        assertEquals(expected, shown)
        compose.onNodeWithTag("app-selector").performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(0, lettersShown().size)
    }

    /** selector.letters is on by default; off, the edge shows only its handle. */
    @Test fun lettersByDefaultAndNoneWhenOff() {
        assertEquals(true, Keys.SELECTOR_LETTERS.default)
        show(left = false, position = "bottom", letters = false)
        assertEquals(0, lettersShown().size)
    }

    private fun iconsShown() = compose.onAllNodesWithTag("app-selector-icon", useUnmergedTree = true).fetchSemanticsNodes().size

    /** Plan E: with selector.icons on (the default), each row starts with the app's icon; off, none. */
    @Test fun rowsShowIconsWhenTurnedOn() {
        show(left = false, position = "bottom", icons = true)
        compose.onNodeWithTag("app-selector").performTouchInput { down(center) }
        compose.waitForIdle()
        assertTrue("icons in the list", iconsShown() > 0)
    }

    @Test fun iconsByDefaultAndNoneWhenOff() {
        assertEquals(true, Keys.SELECTOR_ICONS.default)
        show(left = false, position = "bottom", icons = false)
        compose.onNodeWithTag("app-selector").performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(0, iconsShown())
    }

    /** Plan B: holding still while picking launches the app once its row has filled, without lifting. */
    @Test fun holdingStillLaunchesTheApp() {
        show(left = false, position = "bottom")
        compose.onNodeWithTag("app-selector").performTouchInput { down(center); moveTo(Offset(centerX - 60.dp.toPx(), centerY)) }
        compose.waitUntil(3_000) { launched != null }
        assertEquals(null, pressed)
        // Lifting afterwards launches nothing more.
        val first = launched
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.runOnIdle { assertEquals(first, launched); assertEquals(null, pressed) }
    }

    /** B2: lifting on the panel's far side opens the actions; nearer the strip it launches. */
    @Test fun theFarSideOpensTheActions() {
        show(left = false, position = "bottom", actions = "far-side")
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveTo(Offset(centerX - 60.dp.toPx(), centerY))
            moveTo(Offset(-290.dp.toPx(), centerY))
            up()
        }
        compose.runOnIdle { assertTrue(pressed != null); assertEquals(null, launched) }
        pressed = null
        compose.onNodeWithTag("app-selector").performTouchInput { down(center); moveTo(Offset(centerX - 60.dp.toPx(), centerY)); up() }
        compose.runOnIdle { assertTrue(launched != null); assertEquals(null, pressed) }
    }

    /** On the far side a hold launches nothing: lifting there opens the actions. */
    @Test fun onTheFarSideAHoldDoesNotLaunch() {
        show(left = false, position = "bottom", actions = "far-side")
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveTo(Offset(centerX - 60.dp.toPx(), centerY))
            moveTo(Offset(-290.dp.toPx(), centerY))
        }
        Thread.sleep(1_100)
        compose.mainClock.advanceTimeBy(1_100)
        compose.runOnIdle { assertEquals(null, launched) }
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.runOnIdle { assertTrue(pressed != null); assertEquals(null, launched) }
    }

    private fun height(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    /** Plan A: with gesture navigation only the middle 200dp of the strip starts the selector, and the handle shows it. */
    @Test fun withGestureNavigationOnlyTheMiddleStarts() {
        show(left = false, position = "bottom", backGesture = true)
        val strip = compose.onNodeWithTag("app-selector-strip").getUnclippedBoundsInRoot()
        val zone = compose.onNodeWithTag("app-selector").getUnclippedBoundsInRoot()
        assertEquals(300f, height("app-selector-strip"), 1f)
        assertEquals(200f, height("app-selector"), 1f)
        assertEquals(strip.top.value + 50f, zone.top.value, 1f)
        assertEquals(200f, height("app-selector-handle"), 1f)
        // Near the strip's top end the edge is Android's: nothing opens.
        compose.onNodeWithTag("app-selector-strip").performTouchInput { down(Offset(centerX, 20.dp.toPx())); up() }
        compose.onNodeWithTag("app-selector-panel").assertDoesNotExist()
        compose.onNodeWithTag("app-selector").performTouchInput { down(center) }
        compose.onNodeWithTag("app-selector-panel").assertExists()
    }

    /**
     * Started in the middle, the thumb still reaches the first letter at the strip's top end and the last at its bottom,
     * and picks one of its apps there. Picking starts 32dp off the edge: from the 36dp strip's centre, a 60dp slide is
     * well past it.
     */
    @Test fun onceDownTheThumbSlidesTheWholeLength() {
        show(left = false, position = "bottom", backGesture = true)
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveTo(Offset(centerX, -48.dp.toPx()))
            moveTo(Offset(centerX - 60.dp.toPx(), -48.dp.toPx()))
            up()
        }
        compose.runOnIdle { assertTrue("${launched?.label}", launched?.key in letterApps(last = false)) }
        launched = null
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveTo(Offset(centerX, height + 48.dp.toPx()))
            moveTo(Offset(centerX - 60.dp.toPx(), height + 48.dp.toPx()))
            up()
        }
        compose.runOnIdle { assertTrue("${launched?.label}", launched?.key in letterApps(last = true)) }
    }

    /**
     * Gliding (apps): started in the middle, the thumb still reaches the first app at the strip's top end and the last at its
     * bottom. Picking starts 32dp off the edge: from the 36dp strip's centre, a 60dp slide is well past it.
     */
    @Test fun onceDownTheThumbGlidesTheWholeLength() {
        val list = apps.apps.value
        val entries = SelectorMath.build(list.filter { !it.hidden }, { it.label }, { it.key }, { false }, { it.profile.kind.ordinal })
        show(left = false, position = "bottom", list = list, backGesture = true, browse = "apps")
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveTo(Offset(centerX, -48.dp.toPx()))
            moveTo(Offset(centerX - 60.dp.toPx(), -48.dp.toPx()))
            up()
        }
        compose.runOnIdle { assertEquals(entries.first().item.key, launched?.key) }
        launched = null
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveTo(Offset(centerX, height + 48.dp.toPx()))
            moveTo(Offset(centerX - 60.dp.toPx(), height + 48.dp.toPx()))
            up()
        }
        compose.runOnIdle { assertEquals(entries.last().item.key, launched?.key) }
    }

    /** Three-button navigation: the whole strip starts it, as before, with the short handle. */
    @Test fun withButtonsTheWholeStripStarts() {
        show(left = false, position = "bottom", backGesture = false)
        assertEquals(300f, height("app-selector"), 1f)
        assertEquals(64f, height("app-selector-handle"), 1f)
        compose.onNodeWithTag("app-selector").performTouchInput { down(Offset(centerX, 20.dp.toPx())) }
        compose.onNodeWithTag("app-selector-panel").assertExists()
    }

    /** The app you're at sits level with the thumb: a short list (it can't scroll) with the strip high up too. */
    @Test fun theListComesLevelWithTheFingerEvenWhenItCantScroll() {
        show(left = false, position = "top", list = apps.apps.value.take(5))
        val strip = compose.onNodeWithTag("app-selector")
        strip.performTouchInput { down(center) }
        val finger = strip.getUnclippedBoundsInRoot().let { (it.top + it.bottom) / 2 }
        val panel = compose.onNodeWithTag("app-selector-panel").getUnclippedBoundsInRoot()
        assertTrue("the panel (${panel.top}..${panel.bottom}) covers the finger ($finger)", panel.top <= finger && finger <= panel.bottom)
    }

    @Test fun offUnlessTurnedOn() {
        assertEquals(false, Keys.SELECTOR_RAIL.default)
        assertEquals("right", Keys.SELECTOR_SIDE.default)
        assertEquals("bottom", Keys.SELECTOR_POSITION.default)
    }

    @Test fun onTheRightAtTheBottomTheListOpensOnTheLeft() {
        show(left = false, position = "bottom")
        val strip = compose.onNodeWithTag("app-selector").getUnclippedBoundsInRoot()
        assertEquals(360f, strip.right.value, 1f)
        // Low on the edge: 72dp above the bottom, 300dp long.
        assertEquals(628.dp.value, strip.bottom.value, 1f)
        assertEquals(300.dp.value, (strip.bottom - strip.top).value, 1f)
        compose.onNodeWithTag("app-selector").performTouchInput { down(center) }
        val list = compose.onNodeWithTag("app-selector-panel").getUnclippedBoundsInRoot()
        assertEquals(0f, list.left.value, 1f)
        assertTrue("the list ends before the strip", list.right < strip.left)
    }

    @Test fun onTheLeftAtTheTopTheListOpensOnTheRight() {
        show(left = true, position = "top")
        val strip = compose.onNodeWithTag("app-selector").getUnclippedBoundsInRoot()
        assertEquals(0f, strip.left.value, 1f)
        assertEquals(8.dp.value, strip.top.value, 1f)
        compose.onNodeWithTag("app-selector").performTouchInput { down(center) }
        val list = compose.onNodeWithTag("app-selector-panel").getUnclippedBoundsInRoot()
        assertEquals(360.dp.value, list.right.value, 1f)
        assertTrue("the list starts after the strip", list.left > strip.right)
    }

    @Test fun inTheMiddleTheStripIsCentred() {
        show(left = false, position = "middle")
        val strip = compose.onNodeWithTag("app-selector").getUnclippedBoundsInRoot()
        // Between the top gap (8dp) and the bottom one (72dp from the bottom).
        assertEquals(((8 + 628) / 2f), ((strip.top + strip.bottom) / 2).value, 1f)
    }
    private fun bubble() = compose.onNodeWithTag("app-selector-bubble", useUnmergedTree = true).fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")

    /** selector.row-height: left open, the list's names are a row's height apart. */
    @Test fun rowHeightSpacesOutTheList() {
        show(left = false, position = "bottom", rowHeightDp = 56)
        compose.onNodeWithTag("app-selector").performTouchInput { down(center) }
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.waitForIdle()
        val labels = entries.map { it.item.label }
        val shown = labels.withIndex().filter { (_, l) ->
            labels.count { it == l } == 1 && compose.onAllNodesWithText(l, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val (a, b) = shown.zipWithNext().first { (x, y) -> y.index == x.index + 1 }
        val top = { l: String -> compose.onNodeWithText(l, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value }
        assertEquals("${a.value} to ${b.value}", 56f, top(b.value) - top(a.value), 1f)
    }

    /** The widest gap between the list's rows against a plain one: above 1 where the fisheye spreads them. */
    private fun magnified(): Float {
        val rows = compose.onNodeWithTag("app-selector-panel").onChildren()
        val tops = rows.fetchSemanticsNodes().indices.map { i -> rows[i].getUnclippedBoundsInRoot().top.value }.sorted()
        val gaps = tops.zipWithNext { a, b -> b - a }
        return gaps.max() / gaps.sorted()[gaps.size / 2]
    }

    /** Lifted on the edge, the list stays open and its fisheye shrinks back, rather than the rows jumping flat. */
    @Test fun theFisheyeShrinksBackWhenTheThumbLifts() {
        show(left = false, position = "bottom", browse = "apps")
        compose.onNodeWithTag("app-selector").performTouchInput { down(center); moveBy(Offset(0f, 4.dp.toPx())) }
        compose.waitForIdle()
        assertTrue("sliding: ${magnified()}", magnified() > 1.3f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(80)
        val easing = magnified()
        assertTrue("just lifted: $easing", easing > 1.05f && easing < 1.3f)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(1f, magnified(), 0.01f)
        compose.onNodeWithTag("app-selector-panel").assertIsDisplayed()
    }

    /** Settings' row height preview: the list stands open without a touch, and the idle close leaves it. */
    @Test fun thePreviewShowsTheListOpen() {
        show(left = false, position = "bottom", showList = true)
        compose.waitForIdle()
        compose.onNodeWithTag("app-selector-panel").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.onNodeWithTag("app-selector-panel").assertIsDisplayed()
    }

    private val entries get() = SelectorMath.build(apps.apps.value.filter { !it.hidden }, { it.label }, { it.key }, { false }, { it.profile.kind.ordinal })

    /** The keys of the apps under the first or the last letter. */
    private fun letterApps(last: Boolean): Set<String> {
        val all = entries
        val section = if (last) all.last().sectionIndex else 0
        return all.filter { it.sectionIndex == section }.map { it.item.key }.toSet()
    }

    /**
     * Letters (the default): the strip's top end is the first letter and its bottom end the last, shown big
     * in the bubble; sliding off the edge there picks one of that letter's apps.
     */
    @Test fun aSlideGoesALetterAtATime() {
        val first = entries.first().section.toString()
        val last = entries.last().section.toString()
        show(left = false, position = "bottom")
        compose.onNodeWithTag("app-selector").performTouchInput { down(Offset(centerX, 2f)) }
        compose.waitForIdle()
        assertEquals(first, bubble())
        compose.onNodeWithTag("app-selector").performTouchInput { moveTo(Offset(centerX, height - 2f)) }
        compose.waitForIdle()
        assertEquals(last, bubble())
        compose.onNodeWithTag("app-selector").performTouchInput { moveTo(Offset(centerX - 60.dp.toPx(), height - 2f)); up() }
        compose.runOnIdle { assertTrue("${launched?.label} is a $last app", launched?.key in letterApps(last = true)) }
        // Picking, and after, no bubble.
        compose.onAllNodesWithTag("app-selector-bubble", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun panelRows() = (height("app-selector-panel") / Keys.SELECTOR_ROW_HEIGHT.default).let { kotlin.math.round(it).toInt() }

    /** selector.filter: the list holds only the letter's apps, and stays so when you lift on the edge. */
    @Test fun withTheFilterTheListHoldsOnlyTheLettersApps() {
        val firstApps = letterApps(last = false)
        show(left = false, position = "bottom", filter = true)
        compose.onNodeWithTag("app-selector").performTouchInput { down(Offset(centerX, 2f)) }
        compose.waitForIdle()
        assertEquals(firstApps.size, panelRows())
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.waitForIdle()
        compose.onAllNodesWithTag("app-selector-bubble", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(firstApps.size, panelRows())
    }

    /** selector.filter: picking from a letter's apps alone launches one of them. */
    @Test fun withTheFilterAPickIsOneOfTheLettersApps() {
        show(left = false, position = "bottom", filter = true)
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(Offset(centerX, height - 2f))
            moveTo(Offset(centerX - 60.dp.toPx(), height - 2f))
            up()
        }
        compose.runOnIdle { assertTrue("${launched?.label}", launched?.key in letterApps(last = true)) }
    }

    /** Letters without the filter: the whole list, every app still there to pick. */
    @Test fun withoutTheFilterTheWholeListIsThere() {
        show(left = false, position = "bottom")
        compose.onNodeWithTag("app-selector").performTouchInput { down(Offset(centerX, 2f)) }
        compose.waitForIdle()
        assertTrue("more than the first letter's apps", panelRows() > letterApps(last = false).size)
    }

    /** Gliding (apps) shows the bubble too: the letter of the app at the thumb. */
    @Test fun glidingShowsTheLetterToo() {
        show(left = false, position = "bottom", browse = "apps")
        compose.onNodeWithTag("app-selector").performTouchInput { down(Offset(centerX, 2f)) }
        compose.waitForIdle()
        assertEquals(entries.first().section.toString(), bubble())
    }

    /**
     * While the thumb is down home dims and the list narrows, so the wave swings out clear of it; left open, the list
     * widens nearly to the strip, so a thumb at the edge reaches every row.
     */
    @Test fun homeDimsAndTheListNarrowsWhileSliding() {
        show(left = false, position = "bottom")
        compose.onAllNodesWithTag("app-selector-scrim").assertCountEquals(0)
        compose.onNodeWithTag("app-selector").performTouchInput { down(center) }
        compose.waitForIdle()
        compose.onNodeWithTag("app-selector-scrim").assertExists()
        val sliding = compose.onNodeWithTag("app-selector-panel").getUnclippedBoundsInRoot().let { (it.right - it.left).value }
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.waitForIdle()
        val open = compose.onNodeWithTag("app-selector-panel").getUnclippedBoundsInRoot().let { (it.right - it.left).value }
        // 55% of the width, but at least 200dp; open, all but the 36dp strip and an 8dp gap.
        assertEquals(200f, sliding, 1f)
        assertEquals(360f - 36f - 8f, open, 1f)
    }

    /** Keyboard up (inactive): nothing shows and an open list closes; down again, it is back as it was. */
    @Test fun inactiveItHidesAndClosesItsList() {
        var active by mutableStateOf(true)
        val list = apps.apps.value
        compose.setContent {
            Box(Modifier.size(360.dp, 700.dp)) {
                AppSelector(list, emptySet(), onLaunch = {}, onLongPress = {}, heightDp = 300, edgeBackGesture = false, active = active)
            }
        }
        compose.onNodeWithTag("app-selector").performTouchInput { down(center); up() }
        compose.onNodeWithTag("app-selector-panel").assertExists()
        compose.runOnIdle { active = false }
        compose.onNodeWithTag("app-selector").assertDoesNotExist()
        compose.onNodeWithTag("app-selector-panel").assertDoesNotExist()
        compose.runOnIdle { active = true }
        compose.onNodeWithTag("app-selector").assertExists()
        compose.onNodeWithTag("app-selector-panel").assertDoesNotExist()
    }

    /** The font the row showing [label] is drawn in. */
    private fun fontOf(label: String): androidx.compose.ui.text.font.FontFamily? {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.fontFamily
    }

    /** selector.system-font: the list in the phone's font; off, in the terminal's. */
    @Test fun theListCanUseThePhonesFont() {
        val first = entries.first().item.label
        var systemFont by mutableStateOf(true)
        val list = apps.apps.value
        compose.setContent {
            Box(Modifier.size(360.dp, 700.dp)) {
                AppSelector(list, emptySet(), onLaunch = {}, onLongPress = {}, heightDp = 300, edgeBackGesture = false, systemFont = systemFont)
            }
        }
        compose.onNodeWithTag("app-selector").performTouchInput { down(Offset(centerX, 2f)) }
        compose.waitForIdle()
        assertEquals(androidx.compose.ui.text.font.FontFamily.Default, fontOf(first))
        compose.runOnIdle { systemFont = false }
        compose.waitForIdle()
        assertEquals(Term.text.fontFamily, fontOf(first))
        assertTrue("the terminal's font is another", Term.text.fontFamily != androidx.compose.ui.text.font.FontFamily.Default)
    }

    /** The centre of the row showing [label], from the top (dp). */
    private fun rowCentre(label: String) = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot().let { (it.top + it.bottom).value / 2 }

    /** Sliding to another letter, its apps glide to their place beside it rather than jumping there. */
    @Test fun theListGlidesToANewLetter() {
        // Few enough to fit: the panel itself moves, and the first app's row is always there to measure.
        val list = apps.apps.value.filter { !it.hidden }.take(10)
        val first = SelectorMath.build(list, { it.label }, { it.key }, { false }, { it.profile.kind.ordinal }).first().item.label
        show(left = false, position = "bottom", list = list)
        compose.onNodeWithTag("app-selector").performTouchInput { down(Offset(centerX, height - 2f)) }
        compose.waitForIdle()
        val from = rowCentre(first)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("app-selector").performTouchInput { moveTo(Offset(centerX, 2f)) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val on = rowCentre(first)
        compose.mainClock.advanceTimeBy(1_000)
        val to = rowCentre(first)
        compose.mainClock.autoAdvance = true
        assertTrue("on its way from $from to $to: $on", on in minOf(from, to) + 1f..maxOf(from, to) - 1f)
    }

    /**
     * Tapping a letter: its first app starts level with it, and stays there once the thumb lifts, even
     * at the list's start (the panel had moved down to put it there); the list still scrolls to its end.
     */
    @Test fun aTappedLettersAppsStayBesideIt() {
        val all = entries
        val letters = all.last().sectionIndex + 1
        val first = all.first().item.label
        show(left = false, position = "bottom")
        val strip = compose.onNodeWithTag("app-selector-strip").getUnclippedBoundsInRoot()
        val letterY = strip.top.value + 300f * (SelectorMath.DEAD + 0.5f / letters * (1 - 2 * SelectorMath.DEAD))
        compose.onNodeWithTag("app-selector").performTouchInput { down(Offset(centerX, 2f)) }
        compose.waitForIdle()
        assertEquals("while touching", letterY, rowCentre(first), 2f)
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("after the tap", letterY, rowCentre(first), 2f)
        compose.onNodeWithTag("app-selector-panel").performScrollToIndex(all.size - 1)
        compose.onNodeWithText(all.last().item.label, useUnmergedTree = true).assertIsDisplayed()
    }
}
