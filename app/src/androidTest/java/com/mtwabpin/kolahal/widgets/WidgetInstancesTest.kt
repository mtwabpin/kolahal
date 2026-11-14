package com.mtwabpin.kolahal.widgets

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogKept
import com.mtwabpin.kolahal.data.Backup
import com.mtwabpin.kolahal.data.FullBackup
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Several widgets of one script on the widgets page. Each is filled by a run of its own, with its
 * own options. Each is removed by itself, and all follow a rename and come back from a backup.
 *
 * The script shows its `city` option as the value, and which widget the run was for as the label:
 * the id of a further widget, or "first".
 */
@RunWith(AndroidJUnit4::class)
class WidgetInstancesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val t = TestScriptWidgets("winst")
    private val host = t.host
    private val scripts = t.scripts
    private val options = ScriptOptions(context)
    // Only the backup test replaces the log, and only it has the log put back.
    @get:Rule val logKept = LogKept(eachTest = false)

    @After fun tearDown() = t.close()

    private val code = "-- @widget stat 2x1\n-- @option city London The city to show\n" +
        "widget.show{ value = options.city, label = (event and event.instance) or 'first' }"
    private val declared = ScriptMeta.options(code)
    private val nothing = WidgetState(null, false)

    private fun now(s: Script) = scripts.byId(s.id)!!

    /** Whether [s] may keep a widget, as stored now. */
    private fun on(s: Script) = now(s).grants.allows(ScriptAccess.WIDGET)

    /** What the widget [ref] shows: its value and its label. Null when it shows nothing. */
    private fun shown(ref: WidgetRef): Pair<String, String?>? = (t.stored(ref).spec as? WidgetSpec.Stat)?.let { it.value to it.label }

    /** What is set in the option scope of the widget [ref]. */
    private fun scope(ref: WidgetRef) = KvStore(context, ScriptOptions.scope(ref.script, ref.id)).all()

    /** Two widgets of one script, each filled by a real run in the `:script` process, each with its own option value. */
    @Test fun twoWidgetsOfOneScriptShowTwoOptionValues() {
        val s = t.place(t.create("-a", code))
        val first = WidgetRef(s.name)
        val second = t.placeAnother(s)
        assertEquals(WidgetRef(s.name, "2"), second)
        // Each is an entry of its own, and the database knows both.
        assertEquals(listOf("script:${s.name}", "script:${s.name}#2"), host.placedOf(s.name).map { WidgetSource.script(it) })
        assertEquals("s:script:${s.name}#2", t.entry(second)!!.key)
        assertEquals(setOf("", "2"), WidgetStates.placedIds(context, s.name))
        assertTrue(on(s))
        // The run that placing asked for filled the widget it was for. The second follows the script's options.
        assertEquals("London" to "first", shown(first))
        assertEquals("London" to "2", shown(second))
        assertTrue(t.stored(second).triedAt > 0)

        // A value set for the second widget is for that widget only.
        options.set(s.name, "city", "Tokyo", second.id)
        t.refresh(s, second)
        t.refresh(s, first)
        assertEquals("Tokyo" to "2", shown(second))
        assertEquals("London" to "first", shown(first))

        // The script's own value: the first widget has it, and the second keeps its own.
        options.set(s.name, "city", "Paris")
        t.refresh(s, first)
        t.refresh(s, second)
        assertEquals("Paris" to "first", shown(first))
        assertEquals("Tokyo" to "2", shown(second))
        // Reset for the widget: it follows the script again.
        options.set(s.name, "city", null, second.id)
        t.refresh(s, second)
        assertEquals("Paris" to "2", shown(second))

        // A run on the script's timer fills the first widget only, with the script's options.
        options.set(s.name, "city", "Oslo")
        val before = t.stored(second)
        t.run(s)
        assertEquals("Oslo" to "first", shown(first))
        assertEquals(before, t.stored(second))

        // `widgets ls` tells them apart.
        val listed = host.listing(context).map { it.first.substringAfter(". ") }
        assertTrue(listed.toString(), listed.containsAll(listOf(s.name, "${s.name} 2")))
    }

    /** A run that fails for one widget says so on that widget's card only, and its error is logged once for that widget. */
    @Test fun aFailedRunIsNotedForItsOwnWidget() {
        val s = t.place(t.create("-f", "-- @widget stat 2x1\nif event and event.instance then error('no second') end\nwidget.show{ value = 1 }"))
        val first = WidgetRef(s.name)
        val second = t.placeAnother(s)
        assertEquals("1", shown(first)?.first)
        assertNull(t.stored(first).error)
        assertNull(shown(second))
        assertTrue(t.stored(second).error.orEmpty(), t.stored(second).error.orEmpty().contains("no second"))
        val worker = KvStore(context, Stores.WORKER)
        assertNull(worker.get("refresh-error:${s.id}"))
        assertTrue(worker.get("refresh-error:${s.id}#2").orEmpty().contains("no second"))
    }

    /** Removing one widget leaves the other as it is. A further widget takes the options set for it with it. */
    @Test fun removingOneLeavesTheOther() {
        val s = t.place(t.create("-b", code))
        val first = WidgetRef(s.name)
        val second = t.placeAnother(s)
        options.set(s.name, "city", "Paris")
        options.set(s.name, "city", "Tokyo", second.id)
        t.refresh(s, first)
        t.refresh(s, second)
        assertEquals("Tokyo" to "2", shown(second))

        host.remove(t.entry(second)!!.key)
        host.settle()
        assertNull(t.entry(second))
        assertEquals(nothing, t.stored(second))
        assertEquals(emptyMap<String, String>(), scope(second))
        // The first widget, its content and the script's own options stay.
        assertEquals(setOf(""), WidgetStates.placedIds(context, s.name))
        assertTrue(on(s))
        assertEquals("Paris" to "first", shown(first))
        assertEquals(mapOf("city" to "Paris"), scope(first))
        // A run for the widget that is gone keeps nothing.
        t.run(s, widget = true, id = "2")
        assertEquals(nothing, t.stored(second))

        // The next widget gets the free id, and starts clean: it follows the script.
        val again = t.placeAnother(s)
        assertEquals(second, again)
        assertEquals("Paris" to "2", shown(again))

        // Now the first one goes, and the second stays. The script still has a widget, so its access stays on.
        host.remove(t.entry(first)!!.key)
        host.settle()
        assertNull(t.entry(first))
        assertEquals(nothing, t.stored(first))
        assertEquals(setOf("2"), WidgetStates.placedIds(context, s.name))
        assertTrue(on(s))
        assertEquals(mapOf("city" to "Paris"), scope(first))
        // A timer's run is for the first widget, which is not there: nothing is kept for it.
        t.run(s)
        assertEquals(nothing, t.stored(first))
        t.refresh(s, again)
        assertEquals("Paris" to "2", shown(again))

        // The last one goes: the access goes off, and the name has no row.
        host.remove(t.entry(again)!!.key)
        host.settle()
        assertFalse(on(s))
        assertNull(WidgetStates.placedIds(context, s.name))
        assertEquals(nothing, t.stored(again))
    }

    /** One script may have six widgets. The seventh is refused, and nothing changes. */
    @Test fun aScriptHasSixWidgetsAtMost() {
        val s = t.place(t.create("-m", code))
        val refs = (2..WidgetRef.MAX_PER_SCRIPT).map { host.placeAnother(s, WidgetSize.S2X1) }
        assertEquals((2..6).map { WidgetRef(s.name, it.toString()) }, refs)
        assertNull(host.placeAnother(s, WidgetSize.S2X1))
        t.placed(s)
        assertEquals(6, host.placedOf(s.name).size)
        assertEquals(setOf("", "2", "3", "4", "5", "6"), WidgetStates.placedIds(context, s.name))
        // Every one got its own run.
        assertEquals(listOf("first", "2", "3", "4", "5", "6"), host.placedOf(s.name).map { shown(it)?.second })
        // One is removed: its id is free again.
        host.remove(t.entry(WidgetRef(s.name, "4"))!!.key)
        assertEquals(WidgetRef(s.name, "4"), host.placeAnother(s, WidgetSize.S2X1))
        t.placed(s)
    }

    /** Renamed, the script keeps both widgets: the entries, what each shows, and the second one's own options. */
    @Test fun aRenameKeepsBoth() {
        val s = t.place(t.create("-c", code))
        val second = t.placeAnother(s)
        options.set(s.name, "city", "Tokyo", second.id)
        t.refresh(s, second)
        val entries = listOf(t.entry(WidgetRef(s.name))!!, t.entry(second)!!)
        val to = t.name("-c2")

        scripts.rename(s.id, to)
        host.settle()
        val first2 = WidgetRef(to)
        val second2 = WidgetRef(to, "2")
        assertEquals(emptyList<WidgetRef>(), host.placedOf(s.name))
        assertEquals(listOf(entries[0].copy(provider = "script:$to"), entries[1].copy(provider = "script:$to#2")), listOf(t.entry(first2), t.entry(second2)))
        // What each showed, and the copies of the list, are under the new name.
        assertEquals("London" to "first", shown(first2))
        assertEquals("Tokyo" to "2", shown(second2))
        assertEquals(nothing, t.stored(WidgetRef(s.name)))
        assertEquals(nothing, t.stored(second))
        assertEquals(setOf("", "2"), WidgetStates.placedIds(context, to))
        assertNull(WidgetStates.placedIds(context, s.name))
        assertTrue(on(s))
        // The second widget's own options moved with it.
        assertEquals(mapOf("city" to "Tokyo"), scope(second2))
        assertEquals(emptyMap<String, String>(), scope(second))
        assertEquals("Tokyo", options.values(to, declared, "2")["city"])
        // A run under the new name fills the same widgets.
        options.set(to, "city", "Rome", "2")
        t.refresh(now(s), second2)
        assertEquals("Rome" to "2", shown(second2))
        assertEquals("London" to "first", shown(first2))
    }

    /** A script whose name is the start of another's keeps its own: nothing of `name_x` moves or goes with `name`. */
    @Test fun aLongerNameIsAnotherScript() {
        val s = t.place(t.create("-u", code))
        val other = t.place(t.create("-u_x", code))
        val second = t.placeAnother(s)
        val otherSecond = t.placeAnother(other)
        options.set(other.name, "city", "Lima", otherSecond.id)
        t.refresh(other, otherSecond)

        scripts.rename(s.id, t.name("-u9"))
        host.settle()
        assertEquals("Lima" to "2", shown(otherSecond))
        assertEquals(mapOf("city" to "Lima"), scope(otherSecond))
        assertEquals(2, host.placedOf(other.name).size)
        scripts.delete(s.id)
        host.settle()
        assertEquals("Lima" to "2", shown(otherSecond))
        assertEquals(mapOf("city" to "Lima"), scope(otherSecond))
        assertEquals(nothing, t.stored(WidgetRef(t.name("-u9"), second.id)))
    }

    /** A backup brings both widgets back, and the second one's own options with them. What they showed stays behind. */
    @Test fun aBackupBringsBothBackWithTheirOptions() {
        logKept.keep()
        val s = t.place(t.create("-d", code))
        val first = WidgetRef(s.name)
        val second = t.placeAnother(s)
        options.set(s.name, "city", "Paris")
        options.set(s.name, "city", "Tokyo", second.id)
        val entries = listOf(t.entry(first)!!, t.entry(second)!!)
        val file = File(context.cacheDir, "kolahal-backup-winst.json")
        try {
            Backup(context).export(Uri.fromFile(file))
            // The file has both entries by their sources, and the widget's own option scope. The
            // phone's copies of the placed list and what the widgets show stay out of it.
            val text = file.readText()
            assertTrue(text.contains("\"script:${s.name}#2\""))
            val kv = (FullBackup.export(context)["tables"] as Map<*, *>)["kv"] as List<*>
            val scopes = kv.map { (it as Map<*, *>)["scope"] }.toSet()
            assertTrue(scopes.toString(), ScriptOptions.scope(s.name, "2") in scopes)
            assertTrue(scopes.none { it == Stores.WIDGET_PLACED || it == Stores.WIDGET || it == Stores.WIDGET_META })

            // Both widgets go, and with the second its options.
            entries.forEach { host.remove(it.key) }
            host.settle()
            options.set(s.name, "city", null)
            assertEquals(emptyList<WidgetRef>(), host.placedOf(s.name))
            assertEquals(emptyMap<String, String>(), scope(second))
            assertFalse(on(s))

            Backup(context).import(Uri.fromFile(file))
            host.settle()
            assertEquals(listOf(first, second), host.placedOf(s.name))
            assertEquals(entries.map { it.heightDp to it.span }, listOf(t.entry(first)!!, t.entry(second)!!).map { it.heightDp to it.span })
            assertEquals(mapOf("city" to "Paris"), scope(first))
            assertEquals(mapOf("city" to "Tokyo"), scope(second))
            // The list wrote its copies again, so the script may fill both.
            assertEquals(setOf("", "2"), WidgetStates.placedIds(context, s.name))
            assertTrue(on(s))
            t.refresh(now(s), first)
            t.refresh(now(s), second)
            assertEquals("Paris" to "first", shown(first))
            assertEquals("Tokyo" to "2", shown(second))
        } finally {
            file.delete()
        }
    }

    /**
     * A widget placed by a version from before a script could have several: its entry is
     * `script:<name>`, its script's `widget` access is on, and the name has no `widget.placed` row.
     * It is the script's first widget, and it still fills. The next start writes its row.
     */
    @Test fun anEntryFromBeforeStillFills() {
        val s = t.place(t.create("-e", code))
        val first = WidgetRef(s.name)
        val rows = KvStore(context, Stores.WIDGET_PLACED)
        rows.set(s.name, null)
        WidgetStates.forget(context, s.name)
        assertNull(WidgetStates.placedIds(context, s.name))
        assertTrue(on(s))

        // A run on its timer and a run for the widget both fill it, in the `:script` process too.
        t.run(s)
        assertEquals("London" to "first", shown(first))
        WidgetStates.forget(context, s.name)
        t.refresh(s, first)
        assertEquals("London" to "first", shown(first))
        // With no row, no further widget counts as placed.
        t.run(s, widget = true, id = "2")
        assertEquals(nothing, t.stored(WidgetRef(s.name, "2")))

        // At start the list writes the row, and nothing else changes.
        host.syncAccess()
        host.settle()
        assertEquals(setOf(""), WidgetStates.placedIds(context, s.name))
        assertTrue(on(s))
        assertEquals("London" to "first", shown(first))
    }

    /**
     * A widget can wait on the page for a script. When the only widget of a name is `#2`, the script
     * that takes the name fills `#2`, and not a first widget that is not there.
     */
    @Test fun aScriptThatTakesANameWithOnlyASecondWidgetFillsThatOne() {
        val old = t.place(t.create("-g", code))
        val first = WidgetRef(old.name)
        val second = t.placeAnother(old)
        host.remove(t.entry(first)!!.key)
        scripts.delete(old.id)
        host.settle()
        // The second widget waits on the page with nothing to show. Its name keeps its row.
        assertEquals(listOf(second), host.placedOf(old.name))
        assertEquals(nothing, t.stored(second))
        assertEquals(setOf("2"), WidgetStates.placedIds(context, old.name))

        val next = t.create("-g", code)
        assertTrue("a widget of its name is placed, so its access starts on", on(next))
        // A run on its timer is for the first widget, which is not on the page: nothing is kept.
        t.run(next)
        assertEquals(nothing, t.stored(first))
        assertEquals(nothing, t.stored(second))
        // A run for the second widget fills it, here and in the `:script` process.
        t.run(next, widget = true, id = "2")
        assertEquals("London" to "2", shown(second))
        WidgetStates.forget(context, second.key)
        t.refresh(next, second)
        assertEquals("London" to "2", shown(second))
        assertEquals(nothing, t.stored(first))

        // The same for a script that is renamed onto the name.
        scripts.delete(next.id)
        host.settle()
        val renamed = t.create("-h", code)
        assertFalse(on(renamed))
        scripts.rename(renamed.id, old.name)
        host.settle()
        assertTrue(on(renamed))
        assertEquals(setOf("2"), WidgetStates.placedIds(context, old.name))
        t.run(now(renamed))
        assertEquals(nothing, t.stored(first))
        t.refresh(now(renamed), second)
        assertEquals("London" to "2", shown(second))
    }

    /** `script deny <name> widget` is refused as before when the script has several widgets: the placed list sets the access. */
    @Test fun theSwitchIsStillNotSetByHand() {
        val s = t.place(t.create("-k", code))
        t.placeAnother(s)
        val commands = CommandRunner(context)
        val refusal = "${s.name}'s widget is on while it is placed: add or remove it on the widgets page"
        assertEquals(listOf(refusal), commands.run("script deny ${s.name} widget"))
        assertTrue(on(s))
    }
}
