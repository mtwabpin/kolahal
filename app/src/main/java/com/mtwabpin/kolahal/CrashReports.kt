package com.mtwabpin.kolahal

import com.mtwabpin.kolahal.data.Stores
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.shortTime
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reports that stay on the phone until the user shares them. There is no crash reporting service.
 * - An uncaught exception in either process is written to `filesDir/crash` before the process ends.
 * - On Android 11+, the exit reasons that Android recorded add what that handler can't see: freezes
 *   (not responding) and crashes in native code, such as the script engines.
 * - Each new report gets one log line. A tap shares it. `crashes` lists all reports.
 */
object CrashReports {
    /** The share sheet with a crash report: email, a chat, or any app that accepts text. */
    fun shareIntent(report: String): android.content.Intent =
        android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "Kolahal crash report")
                .putExtra(android.content.Intent.EXTRA_TEXT, report),
            "Share the crash report",
        )

    private const val DIR = "crash"
    private const val KEEP = 10
    private const val MAX_BODY = 64 * 1024

    enum class Kind(val key: String, val label: String) {
        CRASH("crash", "crash"),
        FREEZE("freeze", "not responding"),
        NATIVE("native", "native crash"),
        START("start", "start-up failure"),
    }

    class Report(val id: String, val kind: Kind, private val file: File) {
        /** The file name without .txt. [announce] remembers this key. */
        val key: String get() = file.name.removeSuffix(".txt")
        val time: Long get() = file.lastModified()
        fun delete() = file.delete()
        fun text(): String = runCatching { file.readText() }.getOrDefault("")
        /** The error's first line with the short exception name, for the log. */
        val summary: String get() = CrashText.shortName(CrashText.summary(text()))
        val inScriptProcess: Boolean get() = text().lineSequence().take(8).any { it.startsWith("What:") && it.contains(":script") }
    }

    /** Call this first in [LauncherApp.onCreate], in both processes. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { save(app, Kind.CRASH, processName(), "thread ${thread.name}", e.stackTraceToString(), System.currentTimeMillis()) }
            // Android's own handler shows the "stopped" dialog and ends the process.
            if (previous != null) previous.uncaughtException(thread, e) else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun processName(): String = runCatching { android.app.Application.getProcessName() }.getOrNull() ?: "?"

    private fun dir(context: Context) = File(context.filesDir, DIR)

    private fun save(context: Context, kind: Kind, process: String, where: String, body: String, time: Long) {
        val dir = dir(context).apply { mkdirs() }
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))
        var file = File(dir, "$id-${kind.key}.txt")
        var n = 2
        while (file.exists()) file = File(dir, "$id-${n++}-${kind.key}.txt")
        file.writeText(
            CrashText.format(
                app = "Kolahal ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})",
                device = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}",
                time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(time)),
                what = "${kind.label} in $process, $where",
                body = body.take(MAX_BODY),
            ),
        )
        file.setLastModified(time)
        // Keep only the newest few reports.
        list(context).drop(KEEP).forEach { it.delete() }
    }

    /** Returns the reports, newest first. */
    fun list(context: Context): List<Report> =
        dir(context).listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
            .mapNotNull { f ->
                val stem = f.name.removeSuffix(".txt")
                val kind = Kind.entries.firstOrNull { stem.endsWith("-" + it.key) } ?: return@mapNotNull null
                Report(stem.removeSuffix("-" + kind.key), kind, f)
            }
            .sortedByDescending { it.time }

    /** Deletes every report and returns how many there were. */
    fun clear(context: Context): Int {
        val files = dir(context).listFiles().orEmpty()
        files.forEach { it.delete() }
        return files.size
    }

    /**
     * Saves what Android recorded about earlier exits (Android 11+). Then logs one line for each report
     * that wasn't announced yet. It runs in the main process, off the main thread, on start and on
     * each return to home. So a crash in the script process shows up without a restart.
     */
    @Synchronized
    fun announce(context: Context) {
        val prefs = Stores.CRASH.open(context)
        if (Build.VERSION.SDK_INT >= 30) runCatching { collectExits(context, prefs) }
        val reports = list(context)
        val announced = prefs.getStringSet("announced", null).orEmpty()
        val fresh = reports.filter { it.key !in announced }.sortedBy { it.time }
        if (fresh.isEmpty()) return
        // Save the list before logging, so a second caller can't announce the same report again.
        prefs.edit(commit = true) { putStringSet("announced", reports.map { it.key }.toSet()) }
        val logs = LogRepository.get(context)
        for (r in fresh) {
            val stopped = if (r.kind == Kind.FREEZE) "stopped responding" else "stopped unexpectedly"
            val detail = r.summary.take(160) + if (r.inScriptProcess) " (script process)" else ""
            logs.add(
                LogKind.ERROR,
                "Kolahal $stopped ${whenText(context, r.time)} · tap to share the report\n$detail",
                ctx = LogContext.of(LogContext.RUN to "crashes share ${r.id}"),
            )
        }
    }

    @androidx.annotation.RequiresApi(30)
    private fun collectExits(context: Context, prefs: android.content.SharedPreferences) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 16)
        val newest = exits.maxOfOrNull { it.timestamp } ?: 0L
        // The first time, only remember where Android's record ends. Older exits happened before this feature.
        if (!prefs.contains("exits_seen")) {
            prefs.edit(commit = true) { putLong("exits_seen", newest) }
            return
        }
        val seen = prefs.getLong("exits_seen", 0L)
        for (info in exits.filter { it.timestamp > seen }) {
            val kind = when (info.reason) {
                ApplicationExitInfo.REASON_ANR -> Kind.FREEZE
                ApplicationExitInfo.REASON_CRASH_NATIVE -> Kind.NATIVE
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> Kind.START
                // The handler already saved Java crashes, with the whole stack. The other reasons
                // (memory pressure, updates, force stop) aren't bugs.
                else -> continue
            }
            val trace = if (kind == Kind.FREEZE) {
                runCatching { info.traceInputStream?.bufferedReader()?.use { r -> CrashText.mainThread(r.lineSequence().take(20_000)) } }.getOrNull()
            } else null
            // For a native crash, Android's status is the signal number (11 is a bad memory access).
            val signal = if (kind == Kind.NATIVE && info.status > 0) CrashText.signal(info.status) else null
            val body = listOfNotNull(signal, info.description?.takeIf { it.isNotBlank() }, trace).joinToString("\n\n").ifEmpty { "(Android gave no details)" }
            runCatching { save(context, kind, info.processName, "pid ${info.pid}", body, info.timestamp) }
        }
        if (newest > seen) prefs.edit(commit = true) { putLong("exits_seen", newest) }
    }

    /** Returns "at 19:55" for today, else "on 20 Oct at 19:55". The time follows `clock.24h`, like the log. */
    fun whenText(context: Context, time: Long): String {
        val clock = com.mtwabpin.kolahal.data.Config.get(context).shortTime(time)
        val day = SimpleDateFormat("yyyyMMdd", Locale.US)
        return if (day.format(Date(time)) == day.format(Date())) "at $clock"
        else "on " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time)) + " at $clock"
    }
}

/** The text part of [CrashReports]. It uses no Android APIs, so it can be unit-tested. */
internal object CrashText {
    fun format(app: String, device: String, time: String, what: String, body: String): String =
        "Kolahal crash report\n$app\n$device\nWhen: $time\nWhat: $what\n\n$body\n"

    /** Returns the error's first line: the exception and its message, or the first line Android gave. */
    fun summary(report: String): String =
        report.substringAfter("\n\n", "").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "(no details)"

    /** Shortens the class name: `java.lang.IllegalStateException: boom` → `IllegalStateException: boom`, `Outer$Inner` → `Inner`. */
    fun shortName(line: String): String {
        val name = line.substringBefore(':')
        if (name.isEmpty() || name.any { it.isWhitespace() }) return line
        return name.substringAfterLast('.').substringAfterLast('$') + line.substring(name.length)
    }

    fun signal(n: Int): String {
        val name = mapOf(4 to "SIGILL", 6 to "SIGABRT", 7 to "SIGBUS", 8 to "SIGFPE", 11 to "SIGSEGV")[n]
        return if (name != null) "Signal $n ($name)" else "Signal $n"
    }

    /** Returns only the main thread from a "not responding" thread dump, because the freeze is there. */
    fun mainThread(lines: Sequence<String>): String? {
        val out = mutableListOf<String>()
        for (line in lines) {
            if (out.isEmpty()) {
                if (line.startsWith("\"main\"")) out += line
            } else {
                if (line.isBlank()) break
                out += line
                if (out.size >= 80) break
            }
        }
        return out.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
