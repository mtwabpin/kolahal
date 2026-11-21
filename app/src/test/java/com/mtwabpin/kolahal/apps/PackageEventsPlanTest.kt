package com.mtwabpin.kolahal.apps

import com.mtwabpin.kolahal.apps.PackageEvents.Pkg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageEventsPlanTest {
    private fun pkg(label: String, profile: String = "", removedAt: Long = 0L) =
        Pkg(label, "1.0", 1L, 100L, profile, removedAt)

    private val maps = pkg("Maps")
    private val gmail = pkg("Gmail")
    private val workGmail = pkg("Gmail", "W")
    private val workSlack = pkg("Slack", "W")

    private fun kinds(plan: PackageEvents.Companion.Plan) =
        plan.events.map { (kind, key, _) -> "$kind $key" }

    @Test
    fun profileOffWhenKolahalFirstRanIsSilentTheFirstTimeItIsOn() {
        // The snapshot was taken with the work profile (serial 12) paused: none of its apps are in it.
        val plan = PackageEvents.plan(
            before = mapOf("maps#0" to maps),
            now = mapOf("maps#0" to maps, "gmail#12" to workGmail, "slack#12" to workSlack),
            paused = emptySet(), present = setOf(0L, 12L), seen = setOf(0L), t = 1_000L,
        )
        assertTrue(plan.events.isEmpty())
        assertEquals(setOf("maps#0", "gmail#12", "slack#12"), plan.next.keys)
        assertEquals(setOf(0L, 12L), plan.seen)
    }

    @Test
    fun anAppInstalledInAKnownProfileIsStillLogged() {
        val plan = PackageEvents.plan(
            before = mapOf("maps#0" to maps, "gmail#12" to workGmail),
            now = mapOf("maps#0" to maps, "gmail#12" to workGmail, "slack#12" to workSlack),
            paused = emptySet(), present = setOf(0L, 12L), seen = setOf(0L, 12L), t = 1_000L,
        )
        assertEquals(listOf("installed slack#12"), kinds(plan))
    }

    @Test
    fun anAppStillInstalledWithoutItsIconIsNotUninstalled() {
        // Disabled in Android's settings: gone from the launcher past the grace period, still installed.
        val hidden = PackageEvents.plan(
            before = mapOf("maps#0" to maps, "gmail#0" to gmail.copy(removedAt = 1_000L)),
            now = mapOf("maps#0" to maps),
            paused = emptySet(),
            present = setOf(0L),
            seen = setOf(0L),
            t = 60_000L,
            installed = { it == "gmail#0" },
        )
        assertTrue(hidden.events.isEmpty())
        assertEquals(PackageEvents.HIDDEN, hidden.next.getValue("gmail#0").removedAt)
        // Enabled again: back without an "installed" line.
        val back = PackageEvents.plan(
            before = hidden.next, now = mapOf("maps#0" to maps, "gmail#0" to gmail),
            paused = emptySet(), present = setOf(0L), seen = setOf(0L), t = 90_000L,
        )
        assertTrue(back.events.isEmpty())
        // Uninstalled while hidden: that is logged.
        val removed = PackageEvents.plan(
            before = hidden.next, now = mapOf("maps#0" to maps),
            paused = emptySet(), present = setOf(0L), seen = setOf(0L), t = 90_000L,
        )
        assertEquals(listOf("uninstalled gmail#0"), kinds(removed))
    }

    @Test
    fun aPausedProfileKeepsItsApps() {
        val plan = PackageEvents.plan(
            before = mapOf("maps#0" to maps, "gmail#12" to workGmail),
            now = mapOf("maps#0" to maps),
            paused = setOf(12L), present = setOf(0L, 12L), seen = setOf(0L, 12L), t = 1_000L,
        )
        assertTrue(plan.events.isEmpty())
        assertEquals(workGmail, plan.next["gmail#12"])
        assertNull(plan.checkInMs)
    }

    @Test
    fun aProfileThatIsGoneIsForgottenWithoutUninstallLines() {
        val gone = PackageEvents.plan(
            before = mapOf("maps#0" to maps, "gmail#12" to workGmail),
            now = mapOf("maps#0" to maps),
            paused = emptySet(), present = setOf(0L), seen = setOf(0L, 12L), t = 1_000L,
        )
        assertTrue(gone.events.isEmpty())
        assertEquals(setOf("maps#0"), gone.next.keys)
        assertEquals(setOf(0L), gone.seen)
        // Back again (a private space visible again): new to the snapshot, still no lines.
        val back = PackageEvents.plan(
            before = gone.next, now = mapOf("maps#0" to maps, "gmail#12" to workGmail),
            paused = emptySet(), present = setOf(0L, 12L), seen = gone.seen, t = 2_000L,
        )
        assertTrue(back.events.isEmpty())
    }

    @Test
    fun aRealUninstallIsLoggedAfterTheGracePeriod() {
        val first = PackageEvents.plan(
            before = mapOf("maps#0" to maps, "gmail#0" to gmail), now = mapOf("maps#0" to maps),
            paused = emptySet(), present = setOf(0L), seen = setOf(0L), t = 1_000L,
        )
        assertTrue(first.events.isEmpty())
        assertEquals(20_000L, first.checkInMs)
        val later = PackageEvents.plan(
            before = first.next, now = mapOf("maps#0" to maps),
            paused = emptySet(), present = setOf(0L), seen = first.seen, t = 22_000L,
        )
        assertEquals(listOf("uninstalled gmail#0"), kinds(later))
    }

    @Test
    fun aQuickReinstallIsOneUpdate() {
        val plan = PackageEvents.plan(
            before = mapOf("gmail#0" to pkg("Gmail", removedAt = 1_000L)),
            now = mapOf("gmail#0" to gmail),
            paused = emptySet(),
            present = setOf(0L),
            seen = setOf(0L),
            t = 5_000L,
        )
        assertEquals(listOf("updated gmail#0"), kinds(plan))
    }
}
