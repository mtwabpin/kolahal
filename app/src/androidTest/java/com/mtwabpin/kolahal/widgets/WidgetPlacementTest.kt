package com.mtwabpin.kolahal.widgets

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.script.ScriptAccess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A script's own widget on the widgets page. Placing it turns the script's `widget` access on;
 * removing it turns that off and forgets what it showed. The entry follows a rename and outlasts a
 * delete, and the access is never switched by hand.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPlacementTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val t = TestScriptWidgets("wplace")
    private val host = t.host
    private val scripts = t.scripts
    private val commands = CommandRunner(context)
    @get:Rule val logLines = LogLinesSince()

    @After fun tearDown() = t.close()

    private val code = "-- @widget stat 2x1\nwidget.show{ value = 7, label = 'kept' }"
    private val seven = WidgetSpec.Stat(WidgetBase(), "7", label = "kept")
    private val nothing = WidgetState(null, false)

    private fun now(s: Script) = scripts.byId(s.id)!!

    /** Whether [s] may keep a widget, as stored now. */
    private fun on(s: Script) = now(s).grants.allows(ScriptAccess.WIDGET)

    /** The placed list as saved, which the next start reads. */
    private fun saved() = WidgetCodec.decode(Stores.WIDGETS.open(context).getString("placed", null))

    /** Placing adds one entry at the end of the page, at the size of the `@widget` line, and the run it asks for fills the widget. */
    @Test fun placingAddsAnEntryAndTurnsTheAccessOn() {
        val s = t.create("-a", code)
        assertFalse(on(s))
        assertNull(t.entry(s.name))
        val last = WidgetOrder.nextOrder(host.widgets.value)

        val placed = t.place(s)
        val w = t.entry(s.name)!!
        assertEquals(
            PlacedWidget(id = null, provider = "script:${s.name}", profileSerial = host.serialOf(Process.myUserHandle()), heightDp = w.heightDp, order = last, span = 6),
            w,
        )
        assertEquals("s:script:${s.name}", w.key)
        assertFalse("it needs no id, so it never waits to be added again", w.pending)
        assertTrue("${w.heightDp}dp", w.heightDp >= WidgetSizes.MIN_ROW_DP)
        assertTrue(host.isPlaced(t.source(s.name)))
        assertEquals(listOf(w), saved().filter { it.source == w.source })
        assertTrue(host.listing(context).toString(), host.listing(context).any { (text, detail) -> text.endsWith(". ${s.name}") && detail.endsWith(" · script") })

        assertTrue(on(placed))
        assertEquals(true, placed.access[ScriptAccess.WIDGET])
        // The run found the access on.
        assertEquals(seven, t.stored(s.name).spec)

        // A script's widget is placed once.
        assertFalse(host.placeScript(placed, WidgetSize.S4X2))
        host.settle()
        assertEquals(listOf(w), host.widgets.value.filter { it.source == w.source })
    }

    @Test fun removingTurnsTheAccessOffAndForgetsWhatItShowed() {
        val s = t.place(t.create("-b", code))
        t.run(s, widget = true)
        assertEquals(seven, t.stored(s.name).spec)
        assertTrue(t.stored(s.name).triedAt > 0)

        host.remove(t.key(s.name))
        host.settle()
        assertNull(t.entry(s.name))
        assertTrue(saved().none { it.source == t.source(s.name) })
        assertFalse(on(s))
        assertEquals(false, now(s).access[ScriptAccess.WIDGET])
        // The content and its times.
        assertEquals(nothing, t.stored(s.name))
        // A run after that keeps nothing.
        t.run(s)
        assertEquals(nothing, t.stored(s.name))

        // Placed again, it is filled again.
        assertTrue(on(t.place(now(s))))
        assertEquals(seven, t.stored(s.name).spec)
    }

    /** Renamed, the script keeps its widget: the entry, its size and place, the access and what it shows. */
    @Test fun aRenameMovesTheEntryAndWhatItShows() {
        val s = t.place(t.create("-c", code))
        t.run(s)
        val before = t.entry(s.name)!!
        val to = t.name("-c2")

        scripts.rename(s.id, to)
        host.settle()
        assertNull(t.entry(s.name))
        assertEquals(before.copy(provider = "script:$to"), t.entry(to))
        assertEquals(listOf(t.entry(to)), saved().filter { it.source == t.source(to) })
        assertTrue(on(s))
        assertEquals(seven, t.stored(to).spec)
        assertEquals(nothing, t.stored(s.name))
        // A run under the new name fills the same widget.
        scripts.saveCode(s.id, "-- @widget stat 2x1\nwidget.show{ value = 8 }")
        t.run(s)
        assertEquals("8", (t.stored(to).spec as WidgetSpec.Stat).value)
    }

    /** Deleted, the script leaves its entry on the page with nothing to show. A script that takes the name has the widget. */
    @Test fun aDeleteKeepsTheEntry() {
        val s = t.place(t.create("-d", code))
        t.run(s)
        val w = t.entry(s.name)!!

        scripts.delete(s.id)
        host.settle()
        assertEquals(w, t.entry(s.name))
        assertEquals(listOf(w), saved().filter { it.source == w.source })
        assertEquals(nothing, t.stored(s.name))

        val next = t.create("-d", code)
        assertTrue("its access starts on: the placed list decides it", on(next))
        t.run(next)
        assertEquals(seven, t.stored(next.name).spec)
        // A script of that name without a widget doesn't.
        scripts.delete(next.id)
        assertFalse(on(t.create("-d", "return 1")))
    }

    /**
     * A widget can wait on the page for a script: a deleted script leaves its own there. A script that
     * gets the name later has the widget, whichever way it gets there: by a rename, or with a
     * `@widget` line added to its code.
     */
    @Test fun aWaitingWidgetIsTheScriptsAfterARenameOrAnEdit() {
        // Leaves a widget on the page under the name made with the suffix, with no script.
        fun waiting(suffix: String): PlacedWidget {
            val s = t.place(t.create(suffix, code))
            scripts.delete(s.id)
            host.settle()
            return t.entry(s.name)!!
        }

        // A rename onto the name.
        val first = waiting("-i")
        val renamed = t.create("-j", code)
        assertFalse(on(renamed))
        scripts.rename(renamed.id, t.name("-i"))
        host.settle()
        assertTrue("renamed onto a placed widget's name, it may fill it", on(renamed))
        assertEquals(first, t.entry(t.name("-i")))
        assertNull(t.entry(t.name("-j")))
        t.run(renamed)
        assertEquals(seven, t.stored(t.name("-i")).spec)

        // A `@widget` line added to a script that had none. Its access is on as soon as the code is saved.
        val second = waiting("-k")
        val plain = t.create("-k", "return 1")
        assertFalse(on(plain))
        scripts.saveCode(plain.id, code, byUser = true)
        assertTrue("with its `@widget` line, it may fill the widget of its name", on(plain))
        assertEquals(true, now(plain).access[ScriptAccess.WIDGET])
        assertEquals(second, t.entry(plain.name))
        t.run(plain)
        assertEquals(seven, t.stored(plain.name).spec)

        // The same for code from outside: a library update, then a file or a backup.
        val third = waiting("-l")
        val updated = t.create("-l", "return 1")
        scripts.saveCode(updated.id, code)
        assertTrue(on(updated))
        assertEquals(third, t.entry(updated.name))
        waiting("-n")
        val replaced = t.create("-n", "return 1")
        scripts.replaceCode(replaced.id, code)
        assertEquals(true, now(replaced).access[ScriptAccess.WIDGET])

        // A script whose widget isn't placed gets nothing from the line.
        val unplaced = t.create("-m", "return 1")
        scripts.saveCode(unplaced.id, code, byUser = true)
        assertFalse(on(unplaced))
        assertNull(now(unplaced).access[ScriptAccess.WIDGET])
    }

    /** `script allow|deny <name> widget` is refused, and says where the widget is added and removed. */
    @Test fun theAccessIsNotSwitchedByHand() {
        val s = t.create("-e", code)
        val refusal = "${s.name}'s widget is on while it is placed: add or remove it on the widgets page"
        assertEquals(listOf(refusal), commands.run("script allow ${s.name} widget"))
        assertFalse(on(s))
        assertNull(now(s).access[ScriptAccess.WIDGET])

        val placed = t.place(s)
        assertEquals(listOf(refusal), commands.run("script deny ${s.name} widget"))
        assertTrue(on(placed))
        // Its switches don't list it.
        val listed = commands.run("script access ${s.name}")
        assertTrue(listed.toString(), listed.none { it.startsWith("widget") })
    }

    /** Where the two came apart (a restore, an older version), the access follows the placed list. */
    @Test fun theAccessFollowsThePlacedList() {
        val off = t.create("-f", code)
        scripts.setAccess(off.id, ScriptAccess.WIDGET, true)
        val placed = t.place(t.create("-g", code))
        scripts.setAccess(placed.id, ScriptAccess.WIDGET, false)
        host.syncAccess()
        host.settle()
        assertFalse(on(off))
        assertTrue(on(placed))

        // A backup's widget of a script is placed as it comes, and its script may fill it.
        val restored = t.create("-h", code)
        host.importWidgets(listOf(mapOf("provider" to t.source(restored.name), "profile" to 0, "height" to 120, "order" to 90, "span" to 6)))
        host.settle()
        val w = t.entry(restored.name)!!
        assertFalse(w.pending)
        assertEquals(120 to 6, w.heightDp to w.span)
        assertTrue(on(restored))
    }
}
