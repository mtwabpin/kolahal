package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.terminal.LogFilter.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterTest {
    private fun line(kind: LogKind, source: String? = null, vararg ctx: Pair<String, String>) =
        LogEntry(1, 0, kind, source, "a line", LogContext(ctx.toMap()))

    private val typed = line(LogKind.COMMAND, null, LogContext.COMMAND to "xkcd random")
    private val printed = line(LogKind.OUTPUT, null, LogContext.FROM to "7")
    private val oldOutput = line(LogKind.OUTPUT)
    private val timerComic = line(LogKind.SCRIPT, "xkcd", LogContext.SCRIPT to "xkcd")
    private val askedComic = line(LogKind.SCRIPT, "xkcd", LogContext.SCRIPT to "xkcd", LogContext.FROM to "7")
    private val scriptError = line(LogKind.ERROR, "xkcd", LogContext.SCRIPT to "xkcd")
    private val story = line(LogKind.SCRIPT, "Hacker News", com.mtwabpin.kolahal.feeds.FeedWorker.CTX_FEED to "3")
    private val message = line(LogKind.SCRIPT, "WhatsApp", com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY to "k")
    private val install = line(LogKind.INFO, "apps")
    private val call = line(LogKind.INFO, "calls")
    private val reminder = line(LogKind.SCRIPT, "remind")

    @Test fun eachLineHasItsKinds() {
        assertEquals(setOf(Kind.COMMANDS), LogFilter.kindsOf(typed))
        assertEquals(setOf(Kind.COMMANDS), LogFilter.kindsOf(printed))
        assertEquals(setOf(Kind.COMMANDS), LogFilter.kindsOf(oldOutput))
        assertEquals(setOf(Kind.SCRIPTS), LogFilter.kindsOf(timerComic))
        // What a command's script logged is the script's and the command's.
        assertEquals(setOf(Kind.SCRIPTS, Kind.COMMANDS), LogFilter.kindsOf(askedComic))
        assertEquals(setOf(Kind.SCRIPTS), LogFilter.kindsOf(scriptError))
        assertEquals(setOf(Kind.FEEDS), LogFilter.kindsOf(story))
        assertEquals(setOf(Kind.NOTIFICATIONS), LogFilter.kindsOf(message))
        assertEquals(setOf(Kind.APPS), LogFilter.kindsOf(install))
        assertEquals(setOf(Kind.CALLS), LogFilter.kindsOf(call))
        assertEquals(setOf(Kind.REMINDERS), LogFilter.kindsOf(reminder))
    }

    @Test fun aKindsWordIsTheKindAnythingElseAName() {
        assertEquals(LogFilter.OfKind(Kind.FEEDS), LogFilter.parse("@feeds"))
        assertEquals(LogFilter.OfKind(Kind.NOTIFICATIONS), LogFilter.parse("Notifications"))
        assertEquals(LogFilter.Named("Hacker News"), LogFilter.parse(" Hacker News "))
        assertNull(LogFilter.parse("@"))
        assertNull(LogFilter.parse("  "))
    }

    @Test fun aNameMatchesAnyCaseWithoutSpaces() {
        assertTrue(LogFilter.Named("hackernews").matches(story))
        assertTrue(LogFilter.Named("HACKER news").matches(story))
        assertFalse(LogFilter.Named("hacker").matches(story))
        // A script's lines, its errors and what it logged for a command alike.
        val xkcd = LogFilter.parse("xkcd")!!
        assertEquals(listOf(true, true, true, false), listOf(timerComic, askedComic, scriptError, typed).map(xkcd::matches))
        assertTrue(LogFilter.OfKind(Kind.COMMANDS).matches(askedComic))
        assertFalse(LogFilter.OfKind(Kind.COMMANDS).matches(timerComic))
    }

    /** `@starred`: the lines you starred, whatever they are; starring doesn't change what a line is. */
    @Test fun starredIsEveryStarredLine() {
        val starred = LogFilter.parse("@starred")!!
        assertEquals(LogFilter.OfKind(Kind.STARRED), starred)
        assertEquals(listOf(true, true, false), listOf(timerComic.copy(starred = true), typed.copy(starred = true), timerComic).map(starred::matches))
        assertEquals(setOf(Kind.SCRIPTS), LogFilter.kindsOf(timerComic.copy(starred = true)))
        assertEquals(LogFilter.Named("xkcd"), LogFilter.forLine(timerComic.copy(starred = true)))
        assertEquals(listOf("scripts", "starred"), LogFilter.suggestions("@s", emptyList()).map { it.word })
    }

    @Test fun longPressOffersTheLinesNameElseItsKind() {
        assertEquals(LogFilter.Named("xkcd"), LogFilter.forLine(askedComic))
        assertEquals(LogFilter.Named("Hacker News"), LogFilter.forLine(story))
        assertEquals(LogFilter.Named("WhatsApp"), LogFilter.forLine(message))
        assertEquals(LogFilter.OfKind(Kind.APPS), LogFilter.forLine(install))
        assertEquals(LogFilter.OfKind(Kind.REMINDERS), LogFilter.forLine(reminder))
        assertEquals(LogFilter.OfKind(Kind.COMMANDS), LogFilter.forLine(typed))
        assertEquals(LogFilter.OfKind(Kind.COMMANDS), LogFilter.forLine(printed))
    }

    @Test fun suggestionsAreKindsThenNames() {
        val names = listOf("releases", "Hacker News", "apps", "xkcd", "Releases")
        assertEquals(listOf("reminders", "releases"), LogFilter.suggestions("@re", names).map { it.word })
        assertEquals(listOf("hackernews"), LogFilter.suggestions("hack", names).map { it.word })
        val all = LogFilter.suggestions("", names).map { it.word }
        assertEquals(Kind.entries.map { it.word } + listOf("releases", "hackernews", "xkcd"), all)
    }
}
