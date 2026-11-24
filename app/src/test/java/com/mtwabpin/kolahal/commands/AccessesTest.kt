package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `allow`: the accesses it lists in each build, the word for each, and what each word does. */
class AccessesTest {
    private val words = listOf(
        "usage", "send-notifications", "read-notifications", "lock", "dnd", "settings", "contacts", "calendar", "calls", "alarms", "battery",
    )

    /**
     * One lowercase word each, none the start of another, so typing one in full names only it. They are
     * short, but for the two about notifications, which say which way they go: sending and reading.
     */
    @Test
    fun oneShortWordForEachAccess() {
        assertEquals(words, Accesses.ALL.map { it.word })
        val long = setOf("send-notifications", "read-notifications")
        assertTrue(words.all { it.matches(Regex("[a-z]{3,8}")) || it in long })
        for (a in words) for (b in words) if (a != b) assertFalse("$a starts $b", b.startsWith(a))
        assertEquals("usage", Accesses.find("USAGE")?.word)
        assertNull(Accesses.find("usage-access"))
    }

    /** A word is found by its start or by the start of its second half: `notif` finds both about notifications. */
    @Test
    fun aWordIsFoundByEitherHalf() {
        fun found(typed: String) = Accesses.matching(typed, Accesses.ALL).map { it.word }
        assertEquals(listOf("send-notifications", "read-notifications"), found("notif"))
        assertEquals(listOf("read-notifications"), found("read"))
        assertEquals(listOf("send-notifications", "settings"), found("se"))
        assertEquals(words, found(""))
        assertEquals(emptyList<String>(), found("nope"))
        assertEquals(listOf("usage"), found("USA"))
    }

    /** The two about notifications say which is which: sending them, and reading yours. */
    @Test
    fun theTwoAboutNotificationsDiffer() {
        val notify = Accesses.find("send-notifications")!!
        val notifs = Accesses.find("read-notifications")!!
        assertEquals("Notifications", notify.title)
        assertEquals("Notification access", notifs.title)
        assertTrue(notify.short, notify.short.startsWith("send"))
        assertTrue(notifs.short, notifs.short.startsWith("read"))
    }

    @Test
    fun theFullBuildListsEveryAccess() {
        assertEquals(words, Accesses.offered(34) { true }.map { it.word })
    }

    /** The Play build has no screen lock service, no call log and no contacts permission. */
    @Test
    fun thePlayBuildLeavesThreeOut() {
        assertEquals(
            listOf("usage", "send-notifications", "read-notifications", "dnd", "settings", "calendar", "alarms", "battery"),
            Accesses.offered(34) { false }.map { it.word },
        )
        val lock = Accesses.find("lock")!!
        assertFalse(Accesses.inBuild(lock) { false })
        assertTrue(Accesses.inBuild(lock) { true })
        assertTrue(Accesses.inBuild(Accesses.find("usage")!!) { false })
    }

    /** Android asks for exact alarms from Android 12 on; before that they are always allowed, and not listed. */
    @Test
    fun alarmsFromAndroid12() {
        assertFalse(Accesses.offered(30) { true }.any { it.word == "alarms" })
        assertTrue(Accesses.offered(31) { true }.any { it.word == "alarms" })
        assertEquals(words.size - 1, Accesses.offered(28) { true }.size)
    }

    @Test
    fun thisBuildOffersWhatItsSwitchesSay() {
        val offered = Accesses.OFFERED.map { it.word }
        assertEquals(BuildConfig.LOCK_SERVICE, "lock" in offered)
        assertEquals(BuildConfig.CALL_LOG, "calls" in offered)
        assertEquals(BuildConfig.CONTACTS, "contacts" in offered)
    }

    /** Each line says whether it is allowed now, and a tap runs `allow <word>`. */
    @Test
    fun theListSaysWhatIsAllowed() {
        val given = setOf("usage", "dnd")
        val list = Accesses.listing(Accesses.offered(34) { true }) { it.word in given }
        assertEquals(words, list.map { it.access.word })
        assertEquals(given, list.filter { it.allowed }.map { it.access.word }.toSet())
        assertEquals(words.map { "allow $it" }, list.map { it.run })
        assertTrue(Accesses.listing(Accesses.offered(34) { true }) { false }.none { it.allowed })
    }

    /** `allow` is offered in every build; `help allow` lists this build's words, each with what it is for. */
    @Test
    fun allowIsInEveryBuild() {
        assertTrue("allow" in CommandCatalog.OFFERED_NAMES)
        assertTrue("allow" !in CommandCatalog.UNAVAILABLE)
        val spec = CommandCatalog.COMMANDS.single { it.name == "allow" }
        assertEquals(Accesses.OFFERED.joinToString(" · ") { it.word }, spec.args)
        assertEquals(listOf("allow") + Accesses.OFFERED.map { "allow ${it.word}" }, spec.uses.map { it.first })
        assertTrue("allow" in CommandCatalog.HELP_SECTIONS.getValue("device"))
    }

    /**
     * The old forms were sensitive (`calls --allow` by its argument, the others by name). `allow` is
     * too: it never runs without Enter, not even as a whole line, and a script can't send it.
     */
    @Test
    fun allowWaitsForEnter() {
        assertTrue(InstantRun.isSensitive("allow"))
        assertTrue(InstantRun.isSensitive("allow usage"))
        assertTrue(InstantRun.isSensitive("allow dnd|head"))
        assertFalse(InstantRun.wholeLine("allow dnd", CommandCatalog.COMMAND_USAGES, emptyMap()))
        assertFalse(InstantRun.canRun("allow", CommandCatalog.COMMAND_USAGES, emptyMap(), emptyList()))
        assertFalse(InstantRun.canRun("allow calls", CommandCatalog.COMMAND_USAGES, emptyMap(), emptyList(), tapped = true))
    }

    /**
     * `allow` can be turned off: every "tap to allow" line, the setup checklist and the tour run it as
     * Kolahal's own, and Settings → Setup checklist always opens. So nobody is left without a way.
     */
    @Test
    fun allowCanBeTurnedOff() {
        assertTrue("allow" in CommandsOff.CHOICES)
        assertEquals("allow", CommandsOff.parse("allow"))
    }

    /** The commands and sub-forms that asked for an access before `allow` are gone. */
    @Test
    fun theOldFormsAreGone() {
        for (name in listOf("usage-access", "battery", "accessibility", "notifications-feed")) {
            assertFalse(name, name in CommandCatalog.RESERVED_NAMES)
        }
        // `notifications` is the notification feed's other name.
        assertEquals("notifs", CommandCatalog.SYNONYMS["notifications"])
        assertEquals("notifs", InstantRun.canonical("notifications"))
        val gone = listOf(
            "notifs" to "enable",
            "contacts" to "--allow",
            "calls" to "--allow",
            "calls" to "--settings",
            "agenda" to "--allow",
            "agenda" to "--settings",
            "dnd" to "access",
            "brightness" to "access",
            "remind" to "exact",
        )
        for ((command, word) in gone) {
            val spec = CommandCatalog.COMMANDS.single { it.name == command }
            val text = spec.usage + " " + spec.args + " " + spec.uses.joinToString(" ") { it.first }
            assertFalse("$command $word", Regex("(?<![\\w-])${Regex.escape(word)}(?![\\w-])").containsMatchIn(text))
            assertFalse("$command $word", word in ControlSpecs.suggest(command, ""))
        }
    }

    /** The source of a `when (access.word)` branch for each word: from `Accesses.X ->` to the next branch. A constant is named as its word, with `_` for `-`. */
    private fun branches(path: String): Map<String, String> {
        val text = sourceFile(path).readText()
        val starts = Regex("""Accesses\.([A-Z_]+) ->""").findAll(text).toList()
        return starts.mapIndexed { i, m ->
            val end = starts.getOrNull(i + 1)?.range?.first ?: text.length
            m.groupValues[1].lowercase().replace('_', '-') to text.substring(m.range.last + 1, end)
        }.toMap()
    }

    /** Each word asks for what its old form asked for, and the setup checklist knows whether each is given. */
    @Test
    fun eachWordReachesWhatItsOldFormReached() {
        // The word, the old form, and what both run: Android's page or Kolahal's request.
        val reaches = listOf(
            Triple("usage", "usage-access", "UsageAccess.intent()"),
            Triple("send-notifications", "notifications", "notifications()"),
            Triple("read-notifications", "notifs enable", "askNotificationAccess()"),
            Triple("lock", "accessibility", "LockService.settingsIntent()"),
            Triple("dnd", "dnd access", "Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS"),
            Triple("settings", "brightness access", "Settings.ACTION_MANAGE_WRITE_SETTINGS"),
            Triple("contacts", "contacts --allow", "ui::requestContactsPermission"),
            Triple("calendar", "agenda --allow", "ui::requestCalendarPermission"),
            Triple("calls", "calls --allow", "ui::requestCallLogPermission"),
            Triple("alarms", "remind exact", "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM"),
            Triple("battery", "battery", "Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS"),
        )
        assertEquals(words, reaches.map { it.first })
        val asks = branches("src/main/java/com/mtwabpin/kolahal/terminal/commands/AllowCommands.kt")
        val given = branches("src/main/java/com/mtwabpin/kolahal/terminal/SetupChecks.kt")
        for ((word, old, target) in reaches) {
            assertTrue("allow $word (was $old) doesn't run $target", asks[word].orEmpty().contains(target))
            assertTrue("the setup checklist can't tell whether $word is given", given[word].orEmpty().isNotBlank())
        }
        // `send-notifications` asks while Android can, and opens Kolahal's notification settings when it can't.
        val notify = sourceFile("src/main/java/com/mtwabpin/kolahal/terminal/commands/AllowCommands.kt").readText()
            .substringAfter("private fun notifications()").substringBefore("private fun appSettings")
        assertTrue(notify.contains("ui.requestNotificationPermission()"))
        assertTrue(notify.contains("Settings.ACTION_APP_NOTIFICATION_SETTINGS"))
    }
}
