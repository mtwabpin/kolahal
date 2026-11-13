package com.mtwabpin.kolahal.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The controls above the Scripts tab's cards: the filter sheet with its counts and Show N, the chips
 * that are on and the badge, Sort, and that both survive a rotation; the banner about copies that
 * differ, with Review and Update all; and the note about the scripts Kolahal updated by itself.
 */
@RunWith(AndroidJUnit4::class)
class ScriptsTopTest {
    @get:Rule val vms = TestViewModels()
    @get:Rule val compose = createComposeRule()
    @get:Rule val logLines = LogLinesSince()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val library = ScriptLibrary(app)
    private val notePrefs = app.getSharedPreferences("library_updates", Context.MODE_PRIVATE)
    private val pagePrefs = app.getSharedPreferences("scripts_page", Context.MODE_PRIVATE)

    /** The test's own scripts; the search "sft-" finds only them. */
    private val names = listOf("sft-a", "sft-b", "sft-c")
    /** Library scripts the tests add. A phone that has them already skips the tests. */
    private val libraryNames = listOf("countdown", "charge-reminder")
    // The phone's note about scripts Kolahal updated, and its hidden banner: they come back after each test.
    private val noteWas = listOf("self_updated", "self_updated_in").associateWith { notePrefs.getString(it, null) }
    private val bannerWas = pagePrefs.getString("banner_hidden", null)
    private var started = false

    @Before fun setUp() {
        assumeTrue("a script by a test's name exists already", (names + libraryNames).none { scripts.byName(it) != null })
        pagePrefs.edit().remove("banner_hidden").commit()
        started = true
    }

    @After fun tearDown() {
        if (!started) return
        (names + libraryNames).forEach { n -> scripts.byName(n)?.let { ScriptScheduler.cancel(app, it.id); scripts.delete(it.id) } }
        libraryNames.forEach(library::forget)
        notePrefs.edit().apply { noteWas.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) } }.commit()
        pagePrefs.edit().apply { if (bannerWas == null) remove("banner_hidden") else putString("banner_hidden", bannerWas) }.commit()
    }

    private fun card(file: String) = hasTestTag("script-card") and hasText(file)

    private val chosen = hasAnyAncestor(hasTestTag("chosen-filters"))

    private fun shown(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The file names on the cards shown, top to bottom. */
    private fun cards(): List<String> = compose.onAllNodes(hasTestTag("script-card")).fetchSemanticsNodes()
        .sortedBy { it.boundsInRoot.top }
        .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }?.firstOrNull { it.endsWith(".lua") || it.endsWith(".js") } }

    /** The Scripts tab, its search set to [query] when given. With [restoration], it can be torn down and restored as after a rotation. */
    private fun show(query: String? = null, restoration: StateRestorationTester? = null) {
        val vm = vms.terminal(app)
        if (restoration != null) restoration.setContent { ScriptsScreen(vm) }
        else compose.setContent { ScriptsScreen(vm) }
        compose.waitUntil(5_000) { vm.scriptList.value.isNotEmpty() }
        query?.let { compose.onNodeWithTag("script-search").performTextInput(it) }
    }

    /**
     * Each chip counts the scripts it would show with the chips of the other groups. In a group, any
     * chip; across groups, all. Show N closes the sheet; the chips that are on stay under the search,
     * each with ×, and the badge counts them.
     */
    @Test fun theSheetsChipsCountTheirScriptsAndShowThem() {
        scripts.create("sft-a", ScriptLang.LUA, "-- @command\nreturn 1")
        scripts.create("sft-b", ScriptLang.JS, "// @command\nreturn 1", enabled = false)
        scripts.create("sft-c", ScriptLang.LUA, "return 1").also { scripts.setInterval(it.id, 60) }
        show("sft-")
        compose.waitFor("only the test's scripts") { cards() == listOf("sft-a.lua", "sft-b.js", "sft-c.lua") }
        compose.onNodeWithContentDescription("Filters").performClick()
        // Nothing on: each count is what a tap shows.
        compose.waitForNode(hasTestTag("filter:command") and hasText("Command 2"))
        compose.onNode(hasTestTag("filter:timer") and hasText("Timer 1")).assertExists()
        compose.onNode(hasTestTag("filter:off") and hasText("Off 1")).assertExists()
        compose.onNodeWithTag("filter:js").performScrollTo().assert(hasText("JavaScript 1"))
        compose.onNodeWithTag("filter:command").performScrollTo().performClick()
        compose.waitForText("Show 2 scripts")
        // Another group narrows: Command and JavaScript. Timer counts with JavaScript only: none, greyed but there.
        compose.onNodeWithTag("filter:js").performScrollTo().performClick()
        compose.waitForText("Show 1 script")
        compose.onNodeWithTag("filter:lua").performScrollTo().assert(hasText("Lua 1"))
        compose.onNodeWithTag("filter:timer").performScrollTo().assert(hasText("Timer 0"))
        // The same group adds: Lua or JavaScript.
        compose.onNodeWithTag("filter:lua").performClick()
        compose.waitForText("Show 2 scripts")
        compose.onNodeWithTag("filter:lua").performClick()
        compose.waitForText("Show 1 script")
        compose.onNodeWithText("Show 1 script").performClick()
        compose.waitFor("the sheet closed") { !shown(hasText("Show 1 script")) }
        compose.waitFor("sft-b alone") { cards() == listOf("sft-b.js") }
        compose.onNode(hasContentDescription("Filters") and hasText("2")).assertExists()
        compose.onNodeWithText("1 of ", substring = true).assertExists()
        // × on a chip turns it off; Clear turns off the rest.
        compose.onNode(hasText("JavaScript") and chosen).performClick()
        compose.waitFor("both commands") { cards() == listOf("sft-a.lua", "sft-b.js") }
        compose.onNode(hasContentDescription("Filters") and hasText("1")).assertExists()
        compose.onNode(hasText("Clear") and chosen).performClick()
        compose.waitFor("all three") { cards().size == 3 }
        compose.onNodeWithTag("chosen-filters").assertDoesNotExist()
    }

    /** Sort by last run puts the latest run first and those that never ran last; it and the chips survive a rotation. */
    @Test fun sortAndFiltersSurviveARotation() {
        scripts.create("sft-a", ScriptLang.LUA, "return 1")
        scripts.create("sft-b", ScriptLang.JS, "return 1")
        scripts.create("sft-c", ScriptLang.LUA, "return 1").also { scripts.recordRun(it.id, "ok") }
        val restoration = StateRestorationTester(compose)
        show("sft-", restoration)
        compose.waitFor("by name") { cards() == listOf("sft-a.lua", "sft-b.js", "sft-c.lua") }
        compose.onNodeWithText("Sorted by name").assertExists()
        compose.onNodeWithContentDescription("Sort").performClick()
        compose.onNodeWithText("Last run").performClick()
        compose.waitFor("the last run first") { cards() == listOf("sft-c.lua", "sft-a.lua", "sft-b.js") }
        compose.onNodeWithText("Sorted by last run").assertExists()
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.onNodeWithTag("filter:lua").performScrollTo().performClick()
        compose.onNodeWithText("Show 2 scripts").performClick()
        compose.waitFor("Lua only") { cards() == listOf("sft-c.lua", "sft-a.lua") }

        restoration.emulateSavedInstanceStateRestore()
        compose.waitFor("still Lua only, by last run") { cards() == listOf("sft-c.lua", "sft-a.lua") }
        compose.onNodeWithText("Sorted by last run").assertExists()
        compose.onNode(hasText("Lua") and chosen).assertExists()
    }

    /**
     * When copies differ from the library, a banner says so. Review turns on New version and Edited.
     * Update all ticks the new versions, not an edited copy with nothing newer; Update N updates the
     * ticked ones, with Undo. × hides the banner while the same copies differ.
     */
    @Test fun theBannerReviewsAndUpdatesTheCopiesThatDiffer() {
        val (countdown, charge) = libraryNames.map { n -> library.entries.single { it.name == n } }
        // countdown: edited by you, nothing newer. charge-reminder: an older copy, never edited.
        val edited = library.install(countdown)
        val mine = library.code(countdown) + "\n-- mine"
        scripts.saveCode(edited.id, mine, byUser = true)
        val old = "-- an older charge reminder\nreturn 1"
        val older = scripts.create(charge.name, charge.lang, old)
        library.record(charge.name, old)
        show()
        val banner = hasTestTag("copies-banner")
        compose.waitForNode(hasText("differ from the library", substring = true) and hasAnyAncestor(banner))

        compose.onNode(hasText("Review") and hasAnyAncestor(banner)).performClick()
        compose.waitForNode(hasText("New version") and chosen)
        compose.onNode(hasText("Edited") and chosen).assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(card("countdown.lua"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(card("charge-reminder.lua"))

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(banner)
        compose.onNode(hasText("Update all…") and hasAnyAncestor(banner)).performClick()
        compose.onNodeWithText("Update from the library").assertExists()
        compose.onNodeWithTag("update:charge-reminder").assertIsOn()
        compose.onNodeWithTag("update:countdown").assertIsOff()
        // Only the test's copies: any other copy on this phone is left as it is.
        val others = SemanticsMatcher("another copy's row") { n ->
            n.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("update:") && it.removePrefix("update:") !in libraryNames } == true
        } and isOn()
        compose.onAllNodes(others).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            .forEach { compose.onNodeWithTag(it).performScrollTo().performClick() }
        // Ticked, an edited copy says its edits go.
        compose.onNodeWithTag("update:countdown").performClick()
        compose.onNode(hasTestTag("update:countdown") and hasText("your edits will be lost", substring = true)).assertExists()
        compose.onNodeWithTag("update:countdown").performClick()
        compose.onNode(hasText("Update 1") and hasAnyAncestor(isDialog())).performClick()
        compose.waitFor("the new version") { scripts.byId(older.id)!!.code == library.code(charge) }
        assertEquals(mine, scripts.byId(edited.id)!!.code)
        compose.waitForText("charge-reminder.lua is updated")
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("the older copy back") { scripts.byId(older.id)!!.code == old }

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(banner)
        compose.onNodeWithContentDescription("Hide the banner").performClick()
        compose.waitFor("the banner hidden") { !shown(banner) }
        assertNotNull(pagePrefs.getString("banner_hidden", null))
    }

    /**
     * After Kolahal updated library copies by itself, one line says so; their cards say "Updated in"
     * the version. Show filters the list to them, and × closes the note for good.
     */
    @Test fun theNoteSaysWhatKolahalUpdatedAndShowsThem() {
        val countdown = library.entries.single { it.name == "countdown" }
        val old = "-- @command\n-- @title Countdowns\nprint('older countdown')\n"
        scripts.create("countdown", countdown.lang, old)
        library.record("countdown", old)
        scripts.create("sft-a", ScriptLang.LUA, "return 1")
        library.dismissSelfUpdate()
        assertTrue("countdown" in library.updateUnedited())
        val version = BuildConfig.VERSION_NAME
        show()
        val note = hasTestTag("self-update-note")
        compose.waitForNode(hasText("Kolahal $version updated", substring = true) and hasAnyAncestor(note))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(card("countdown.lua"))
        compose.onNode(card("countdown.lua") and hasText("Updated in $version", substring = true)).assertExists()

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(note)
        compose.onNode(hasText("Show") and hasAnyAncestor(note)).performClick()
        compose.waitForNode(hasText("Updated in $version") and chosen)
        compose.waitFor("only what it updated") { "countdown.lua" in cards() && "sft-a.lua" !in cards() }

        compose.onNodeWithContentDescription("Close the note").performClick()
        compose.waitFor("the note closed") { !shown(note) }
        assertNull(library.selfUpdate())
        compose.onNodeWithTag("chosen-filters").assertDoesNotExist()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(card("countdown.lua"))
        compose.onNode(card("countdown.lua") and hasText("Updated in", substring = true)).assertDoesNotExist()
    }
}
