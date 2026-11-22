/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.commands.CommandCatalog.RESERVED_NAMES
import com.mtwabpin.kolahal.data.LogContext

/** The `alias` and `unalias` commands. [CommandProcessor] expands the aliases ([Aliases.expand]). */
class AliasCommands(context: Context) : CommandGroup(context) {
    private val prefs = AppPrefs.get(context)

    fun alias(rest: String) {
        when (val request = Aliases.parse(rest, RESERVED_NAMES)) {
            Aliases.Request.ShowAll -> {
                val all = prefs.aliases()
                if (all.isEmpty()) info("No aliases — e.g. alias m = open maps")
                all.forEach { (k, v) -> out("$k = $v", LogContext.of(LogContext.COMMAND to k)) }
            }

            is Aliases.Request.Show -> prefs.alias(request.name)
                ?.let { out("${request.name} = $it") } ?: error("No alias '${request.name}'")

            Aliases.Request.Usage -> error("Usage: alias <name> = <command>")
            is Aliases.Request.BuiltIn -> error("'${request.name}' is a built-in command and can't be an alias")
            is Aliases.Request.Define -> {
                prefs.setAlias(request.name, request.expansion)
                info("Alias ${request.name} = ${request.expansion}")
            }
        }
    }

    fun unalias(args: List<String>) {
        val name = args.firstOrNull() ?: return usage("unalias")
        if (prefs.alias(name) == null) return error("No alias '$name'")
        prefs.setAlias(name, null)
        info("Removed alias $name")
    }
}
