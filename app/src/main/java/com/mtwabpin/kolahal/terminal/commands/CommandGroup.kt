/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.content.Intent
import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository

/** What went wrong, for a line the user reads: the error's message, or the error's class name when it has none. */
internal fun Throwable.reason(): String = message ?: javaClass.simpleName

/** Base class for each group of commands. It gives them ways to print to the log and to start other screens. */
abstract class CommandGroup(protected val context: Context) {
    protected val logs = LogRepository.get(context)

    protected fun out(text: String, ctx: LogContext = LogContext.EMPTY) {
        logs.add(LogKind.OUTPUT, text, ctx = ctx)
    }

    protected fun info(text: String, ctx: LogContext = LogContext.EMPTY) {
        logs.add(LogKind.INFO, text, ctx = ctx)
    }

    protected fun error(text: String, ctx: LogContext = LogContext.EMPTY) {
        logs.add(LogKind.ERROR, text, ctx = ctx)
    }

    /** Prints "Usage: " and the usage lines of the commands [names] as an error. The lines come from [CommandCatalog], the same ones that help shows. */
    protected fun usage(vararg names: String) =
        error("Usage: " + names.joinToString(" · ") { CommandCatalog.COMMAND_USAGES.getValue(it) })

    /** Prints [text] as an error that a tap fixes: the tap runs [command] ("… needs usage access", and "Tap to allow it" under it). */
    protected fun fix(text: String, command: String) =
        error(text, LogContext.of(LogContext.RUN to command))

    /**
     * A line for a feature this build gets through Kolahal Extras ([com.mtwabpin.kolahal.extras.ExtrasLink]), which
     * can't give it now: what happened ([first]), what's missing, and a tap that runs `allow <word>` (the access
     * [word]), which opens Extras or its page in the guide. Off the main thread: it asks Extras first.
     */
    protected fun extrasNeeded(first: String, word: String) {
        val feature = com.mtwabpin.kolahal.commands.Accesses.find(word)?.needs ?: return
        val link = com.mtwabpin.kolahal.extras.ExtrasLink.get(context).also { it.refreshNow() }
        val status = link.status(feature)
        val words = com.mtwabpin.kolahal.extras.ExtrasWords
        fix("$first\n${words.problem(status, feature)}\n${words.hint(status)}", "allow $word")
    }

    protected fun startActivity(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** A line that stands for [app]: a tap opens it, and a long-press offers its actions (info, uninstall…). */
internal fun appContext(app: com.mtwabpin.kolahal.apps.AppEntry) = LogContext.of(
    LogContext.PKG to app.pkg,
    LogContext.CLS to app.component.className,
    LogContext.USER to app.profile.serial.toString(),
)
