package com.mtwabpin.kolahal.widgets

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.eventually
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptRun
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.ScriptWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue

/**
 * Scripts with a widget of their own, for the device tests: made, placed on the widgets page, and
 * everything they left taken away again ([close]). Every script made here has a name that starts
 * with [prefix], so what a run that stopped half way left behind is found and taken away too.
 *
 * Placing a script's widget queues a real run of it in the `:script` process. That run writes the
 * widget's content and times beside the test. So [place] waits for it to end before the test goes on.
 */
class TestScriptWidgets(private val prefix: String) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    val host: WidgetHost = WidgetHost.get(context)
    val scripts: ScriptRepository = ScriptRepository.get(context)
    private val work = WorkManager.getInstance(context)
    /** Every name a widget's content was kept under: a script's first name, and the ones it was renamed to. */
    private val names = mutableSetOf<String>()
    private val hadKeys: Set<String>

    init {
        clear(had = null)
        hadKeys = host.widgets.value.map { it.key }.toSet()
    }

    /** The name of the script made with [suffix]. */
    fun name(suffix: String): String = (prefix + suffix).also { names += it }

    /** A new script, switched on. */
    fun create(suffix: String, code: String, lang: ScriptLang = ScriptLang.LUA): Script = scripts.create(name(suffix), lang, code)

    /** The source of [name]'s own widget, and its key in the placed list. */
    fun source(name: String): String = WidgetSource.script(name)

    fun key(name: String): String = "s:" + source(name)

    /** [name]'s entry on the widgets page, or null. */
    fun entry(name: String): PlacedWidget? = host.widgets.value.firstOrNull { it.source == source(name) }

    /** What is kept for [name]'s widget. */
    fun stored(name: String): WidgetState = WidgetStates.read(context, name)

    /**
     * Places [s]'s widget as the picker does, and waits for what follows: its access is on, and the
     * run that placing queued has ended. Returns the script as it is then.
     */
    fun place(s: Script, size: WidgetSize = ScriptMeta.widget(s.code)!!.size): Script {
        assertTrue("${s.name}'s widget was placed already", host.placeScript(s, size))
        return placed(s)
    }

    /** Waits for what follows placing [s]'s widget, here or on a screen ([place]). Returns the script as it is then. */
    fun placed(s: Script): Script {
        host.settle()
        assertTrue("the run that placing ${s.name} queued didn't end", runEnded(s.id, queued = true))
        return scripts.byId(s.id)!!
    }

    /**
     * Waits for the run that placing queued for script [id] to end; one that doesn't end in time is
     * cancelled. Whether it ended by itself. With [queued], a run must be there to begin with.
     * A widget's runs and a pinned line's refreshes share the queue, so this waits for both kinds.
     */
    fun runEnded(id: Long, queued: Boolean = false): Boolean {
        val unique = ScriptScheduler.refreshQueue(id)
        val ended = eventually(30_000) {
            val runs = work.getWorkInfosForUniqueWork(unique).get()
            (runs.isNotEmpty() || !queued) && runs.all { it.state.isFinished }
        }
        if (!ended) work.cancelUniqueWork(unique).result.get()
        return ended
    }

    /** Runs [s] here, as its timer would, and waits for the run. With [widget], as the widgets page asks for it. */
    fun run(s: Script, widget: Boolean = false) {
        val data = if (widget) {
            workDataOf(
                ScriptWorker.KEY_SCRIPT_ID to s.id, ScriptWorker.KEY_SCHEDULED to true,
                ScriptWorker.KEY_EVENT to "{\"name\":\"${ScriptScheduler.WIDGET}\"}",
            )
        } else {
            workDataOf(ScriptWorker.KEY_SCRIPT_ID to s.id)
        }
        runBlocking { ScriptRun.run(context, data) }
    }

    /** Takes away what the test added: the widgets it placed, its scripts, what their widgets kept, and the messages. */
    fun close() = clear(hadKeys)

    /**
     * Takes away these scripts and their widgets, and with [had] every widget placed since then.
     * The placed list is then written to its file before this returns. The app writes it a moment
     * later, and a test run's process ends before that: the next run would find the widgets still placed.
     */
    private fun clear(had: Set<String>?) {
        val mine = scripts.all().filter { it.name.startsWith(prefix) }
        // A run still on its way would write after the clean-up.
        mine.forEach { runEnded(it.id) }
        host.widgets.value
            .filter { w -> WidgetSource.scriptName(w.source)?.startsWith(prefix) == true || (had != null && w.key !in had) }
            .forEach { host.remove(it.key) }
        host.settle()
        Stores.WIDGETS.open(context).edit().commit()
        val worker = KvStore(context, Stores.WORKER)
        mine.forEach { s ->
            ScriptScheduler.cancel(context, s.id)
            // The error a quiet run left, for its first widget and for each further one.
            worker.set("refresh-error:${s.id}", null)
            worker.deleteStartingWith("refresh-error:${s.id}#")
            scripts.delete(s.id)
        }
        (names + mine.map { it.name }).forEach { WidgetStates.forgetScript(context, it) }
        // The rows that say which widgets of a name are placed. Removing a widget takes its name's row
        // away; a row a test wrote for a name that is not on the page is still there.
        KvStore(context, Stores.WIDGET_PLACED).deleteStartingWith(prefix)
        // Messages left for a widget screen aren't the next test's.
        while (host.takeMessage() != null) Unit
    }
}
