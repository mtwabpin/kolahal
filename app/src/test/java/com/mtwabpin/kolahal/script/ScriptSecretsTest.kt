package com.mtwabpin.kolahal.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `@secret NAME site[,site] description` lines, and where a value may go. */
class ScriptSecretsTest {
    @Test
    fun nameSitesAndDescription() {
        val s =
            ScriptMeta.secrets("-- @secret OWM_KEY api.openweathermap.org Your OpenWeather API key")
                .single()
        assertEquals("OWM_KEY", s.name)
        assertEquals(listOf("api.openweathermap.org"), s.hosts)
        assertEquals("Your OpenWeather API key", s.desc)
    }

    @Test
    fun severalSitesWithOrWithoutSpaces() {
        for (line in listOf(
            "// @secret K a.com,B.com the key",
            "-- @secret K a.com, b.com the key",
            "-- @secret K a.com ,b.com the key"
        )) {
            val s = ScriptMeta.secrets(line).single()
            assertEquals(line, listOf("a.com", "b.com"), s.hosts)
            assertEquals(line, "the key", s.desc)
        }
        // A comma in the description doesn't make it a site.
        assertEquals(
            listOf("a.com"),
            ScriptMeta.secrets("-- @secret K a.com Yours, from the site").single().hosts
        )
    }

    @Test
    fun badLinesAreSkippedAndTheFirstOfANameWins() {
        val code = """
            -- @secret 9KEY a.com bad name
            -- @secret NOSITE
            -- @secret K first.com first
            -- @secret K second.com second
            local x = "-- @secret NOT_A_LINE x.com"
        """.trimIndent()
        val all = ScriptMeta.secrets(code)
        assertEquals(listOf("K"), all.map { it.name })
        assertEquals(listOf("first.com"), all.single().hosts)
    }

    @Test
    fun sitesAndTheirSubdomainsOnly() {
        val s = ScriptMeta.Secret("K", listOf("api.github.com"), "")
        assertTrue(s.allows("api.github.com"))
        assertTrue(s.allows("API.GitHub.com"))
        assertTrue(s.allows("eu.api.github.com"))
        assertFalse(s.allows("github.com"))
        assertFalse(s.allows("evilapi.github.com"))
        assertFalse(s.allows("api.github.com.evil.net"))
        assertTrue(ScriptMeta.Secret("K", listOf("*"), "").allows("anything.example"))
    }
}
