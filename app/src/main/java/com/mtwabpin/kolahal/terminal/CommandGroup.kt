package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository

/** What each family of commands does with the phone: prints to the log, and starts other screens. */
abstract class CommandGroup(protected val context: Context) {
    protected val logs = LogRepository.get(context)

    protected fun out(text: String, ctx: LogContext = LogContext.EMPTY) { logs.add(LogKind.OUTPUT, text, ctx = ctx) }
    protected fun info(text: String, ctx: LogContext = LogContext.EMPTY) { logs.add(LogKind.INFO, text, ctx = ctx) }
    protected fun error(text: String, ctx: LogContext = LogContext.EMPTY) { logs.add(LogKind.ERROR, text, ctx = ctx) }

    /** "Usage: " and the usage lines of the commands [names] from [CommandCatalog], the ones help shows, as an error. */
    protected fun usage(vararg names: String) = error("Usage: " + names.joinToString(" · ") { CommandCatalog.COMMAND_USAGES.getValue(it) })

    protected fun startActivity(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
