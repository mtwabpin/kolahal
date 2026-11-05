package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.workDataOf
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** How runs of a script go, whichever way they start. */
@RunWith(AndroidJUnit4::class)
class ScriptRunTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)
    private val before = logs.maxId()

    @After fun tearDown() {
        scripts.byName("srtest")?.let { scripts.delete(it.id) }
        logs.recent(100).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    /**
     * Two runs of one script started together (its timer while you run it, a daily time meeting an
     * interval) go one after the other: each sees its store as the other left it.
     */
    @Test fun twoRunsOfOneScriptGoOneAfterTheOther() {
        val code = """
            local was = store.get("busy")
            store.set("busy", "1")
            sleep(400)
            store.set("busy", nil)
            store.set("runs", tostring((tonumber(store.get("runs")) or 0) + 1))
            if was then log("srtest overlapped") end
        """.trimIndent()
        val s = scripts.create("srtest", ScriptLang.LUA, code)
        val data = workDataOf(ScriptWorker.KEY_SCRIPT_ID to s.id, ScriptWorker.KEY_INTERACTIVE to true)
        runBlocking(Dispatchers.IO) {
            (1..3).map { async { ScriptRun.run(context, data) } }.awaitAll()
        }
        val store = KvStore(context, ScriptRepository.scopeOf(s.id))
        assertEquals("3", store.get("runs"))
        assertEquals(emptyList<String>(), logs.recent(100).filter { it.id > before && it.text == "srtest overlapped" }.map { it.text })
    }
}
