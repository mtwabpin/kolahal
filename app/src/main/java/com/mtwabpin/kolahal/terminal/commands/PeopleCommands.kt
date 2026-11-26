/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mtwabpin.kolahal.calls.CallEvents
import com.mtwabpin.kolahal.contacts.Contact
import com.mtwabpin.kolahal.contacts.ContactsRepository
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.terminal.TerminalUi

/**
 * The `call`, `sms`, `contacts` and `calls` commands. Like the other command groups, they live
 * outside [CommandProcessor].
 *
 * The Play build has no READ_CONTACTS ([ContactsRepository.picker]). There, a name is looked up
 * among the contacts picked before. With no name, or a name it doesn't know, Android's contact
 * picker opens.
 */
class PeopleCommands(context: Context, private val ui: TerminalUi) : CommandGroup(context) {
    private val conf = Config.get(context)
    private val contacts = ContactsRepository.get(context)


    /** `calls [n]`: prints the newest calls from Android's call log, newest first, in the same form as the live call lines. */
    fun calls(args: List<String>) {
        if (!com.mtwabpin.kolahal.BuildConfig.CALL_LOG) com.mtwabpin.kolahal.extras.ExtrasLink.get(context).refreshNow()
        if (!CallEvents.hasPermission(context)) {
            // The Play build reads the call log through Kolahal Extras.
            if (com.mtwabpin.kolahal.BuildConfig.CALL_LOG) fix("Calls need call log access\nTap to allow it", "allow calls")
            else extrasNeeded("Can't read the call log", com.mtwabpin.kolahal.commands.Accesses.CALLS)
            return
        }
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 200) ?: 20
        val list = CallEvents.recent(context, n)
        if (list.isEmpty()) return info("No calls in the call log")
        // Log these as call lines, like the live ones. So they stay out of backups and are copied as sensitive.
        list.forEach {
            logs.add(
                LogKind.OUTPUT,
                CallEvents.line(it, conf, withDay = true),
                CallEvents.SOURCE,
                CallEvents.ctx(it)
            )
        }
    }

    /**
     * Finds the contact for [query] and runs [then] with it. If several contacts match and the first
     * is not an exact name match, it lists them instead.
     */
    private fun withContact(query: String, then: (Contact) -> Unit) {
        if (contacts.picker) {
            if (query.isBlank() || contacts.search(query).isEmpty()) return ui.pickContact(then)
        } else if (!contacts.hasPermission()) {
            fix("Contacts permission needed\nTap to allow it", "allow contacts")
            ui.requestContactsPermission()
            return
        }
        val matches = contacts.search(query)
        if (matches.isEmpty()) return error("No contact matches '$query'")
        if (matches.size > 1 && !matches[0].name.equals(query, ignoreCase = true)) {
            matches.take(8).forEach(::contactLine)
            info("${matches.size} contacts match\nTap one to call, long-press for more")
            return
        }
        then(matches[0])
    }

    private fun contactLine(c: Contact) = out(c.name + if (c.starred) " ★" else "", ctx(c))

    fun call(rest: String) {
        if (rest.isBlank() && !contacts.picker) return usage("call")
        if (ContactsRepository.looksLikeNumber(rest)) dial(rest) else withContact(rest) { dial(it.number) }
    }

    private fun dial(number: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        info("Dialing $number", LogContext.of(LogContext.PHONE to number))
    }

    fun sms(args: List<String>) {
        val who = args.firstOrNull()
        if (who == null && !contacts.picker) return error("Usage: sms <name|number> [message]")
        val body = args.drop(1).joinToString(" ")
        if (who != null && ContactsRepository.looksLikeNumber(who)) message(
            who,
            body
        ) else withContact(who.orEmpty()) { message(it.number, body) }
    }

    private fun message(number: String, body: String) {
        startActivity(
            Intent(
                Intent.ACTION_SENDTO,
                Uri.fromParts("smsto", number, null)
            ).putExtra("sms_body", body)
        )
        info("Message to $number", LogContext.of(LogContext.PHONE to number))
    }

    fun listContacts(query: String) {
        if (contacts.picker) return pickedContacts(query)
        if (!contacts.hasPermission()) {
            ui.requestContactsPermission()
            return
        }
        val list = if (query.isBlank()) contacts.current()
            .let { all -> all.filter { it.starred }.ifEmpty { all.take(20) } } else contacts.search(
            query,
            20
        )
        if (list.isEmpty()) return info(if (query.isBlank()) "No contacts" else "No contact matches '$query'")
        list.forEach(::contactLine)
    }

    /** `contacts` in the Play build: picks a contact, finds one picked before, or forgets one (`--forget`). */
    private fun pickedContacts(query: String) {
        when {
            query == "--forget" || query.startsWith("--forget ") -> forget(
                query.removePrefix("--forget").trim()
            )

            query.isBlank() -> ui.pickContact(::contactLine)
            else -> contacts.search(query, 20).ifEmpty { return ui.pickContact(::contactLine) }
                .forEach(::contactLine)
        }
    }

    private fun forget(name: String) {
        if (name.isBlank()) return error("Usage: contacts --forget <name>")
        val names = contacts.search(name, 50).map { it.name }.distinct()
        val target =
            names.firstOrNull { it.equals(name, ignoreCase = true) } ?: names.singleOrNull()
            ?: return error(
                if (names.isEmpty()) "No picked contact matches '$name'" else "'$name' matches ${names.size} contacts\n${
                    names.joinToString(
                        ", "
                    )
                }\nGive the whole name"
            )
        contacts.forget(target)
        info("Forgot $target")
    }

    companion object {
        /**
         * The context of a contact line. A tap dials the number. A long-press offers: call, message,
         * open the contact, or copy the number.
         */
        fun ctx(c: Contact) = LogContext.of(
            LogContext.PHONE to c.number,
            LogContext.CONTACT to c.lookupUri.toString().ifEmpty { null },
            LogContext.DESC to listOf(c.number, c.label).filter { it.isNotEmpty() }
                .joinToString(" · "),
            // The picture beside the line: the photo, or the name's first letters.
            LogContext.PERSON to c.name,
            LogContext.PHOTO to c.photoUri,
        )
    }
}
