package com.mtwabpin.kolahal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrashTextTest {
    private val report = CrashText.format(
        app = "Kolahal 0.3.1 (9, play)",
        device = "Android 16 (API 36) · samsung SM-S911B",
        time = "2026-10-21 19:55:02 +0530",
        what = "crash in com.mtwabpin.kolahal, thread main",
        body = "java.lang.IllegalStateException: boom\n\tat a.b.c(SourceFile:1)\n",
    )

    @Test
    fun reportHasHeaderThenBody() {
        val lines = report.lines()
        assertEquals("Kolahal crash report", lines[0])
        assertEquals("Kolahal 0.3.1 (9, play)", lines[1])
        assertEquals("What: crash in com.mtwabpin.kolahal, thread main", lines[4])
        assertEquals("", lines[5])
        assertEquals("java.lang.IllegalStateException: boom", lines[6])
    }

    @Test
    fun summaryIsTheErrorsFirstLine() {
        assertEquals("java.lang.IllegalStateException: boom", CrashText.summary(report))
        assertEquals("(no details)", CrashText.summary("Kolahal crash report\nno body"))
    }

    @Test
    fun shortNameDropsThePackage() {
        assertEquals(
            "IllegalStateException: boom",
            CrashText.shortName("java.lang.IllegalStateException: boom")
        )
        assertEquals(
            "CrashedByAdbException: shell-induced crash",
            CrashText.shortName("android.app.RemoteServiceException\$CrashedByAdbException: shell-induced crash")
        )
        assertEquals("c", CrashText.shortName("a.b.c"))
        assertEquals(
            "Signal 11 (SIGSEGV): crash",
            CrashText.shortName("Signal 11 (SIGSEGV): crash")
        )
    }

    @Test
    fun mainThreadIsCutFromTheDump() {
        val dump = """
            ----- pid 4148 at 2026-10-21 19:55:02 -----
            Cmd line: com.mtwabpin.kolahal

            "Signal Catcher" daemon prio=10 tid=2 Runnable
              at x.y(Native method)

            "main" prio=5 tid=1 Sleeping
              | group="main" sCount=1
              at java.lang.Thread.sleep(Native method)
              at com.mtwabpin.kolahal.Foo.bar(SourceFile:12)

            "Binder:4148_1" prio=5 tid=9 Native
        """.trimIndent()
        assertEquals(
            "\"main\" prio=5 tid=1 Sleeping\n  | group=\"main\" sCount=1\n  at java.lang.Thread.sleep(Native method)\n  at com.mtwabpin.kolahal.Foo.bar(SourceFile:12)",
            CrashText.mainThread(dump.lineSequence()),
        )
        assertNull(CrashText.mainThread(sequenceOf("no threads here")))
    }
}
