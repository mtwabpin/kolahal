package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository

/** Base class for each group of commands. It gives them ways to print to the log and to start other screens. */
abstract class CommandGroup(protected val context: Context) {
    protected val logs = LogRepository.get(context)

    protected fun out(text: String, ctx: LogContext = LogContext.EMPTY) { logs.add(LogKind.OUTPUT, text, ctx = ctx) }
    protected fun info(text: String, ctx: LogContext = LogContext.EMPTY) { logs.add(LogKind.INFO, text, ctx = ctx) }
    protected fun error(text: String, ctx: LogContext = LogContext.EMPTY) { logs.add(LogKind.ERROR, text, ctx = ctx) }

    /** Prints "Usage: " and the usage lines of the commands [names] as an error. The lines come from [CommandCatalog], the same ones that help shows. */
    protected fun usage(vararg names: String) = error("Usage: " + names.joinToString(" · ") { CommandCatalog.COMMAND_USAGES.getValue(it) })

    protected fun startActivity(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
