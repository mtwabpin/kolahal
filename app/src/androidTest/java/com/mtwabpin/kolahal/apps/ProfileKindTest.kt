package com.mtwabpin.kolahal.apps

import android.content.pm.LauncherApps
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Kolahal takes another profile for: a clone isn't work, on Android 15 and later by its type and
 * below by Android's own name for its apps ("Clone Chrome"); a work profile is work either way.
 */
@RunWith(AndroidJUnit4::class)
class ProfileKindTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val created = ArrayList<Int>()

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    /** The ids of this phone's users of [type], from what Android reports of them. */
    private fun usersOfType(type: String): List<Int> =
        Regex("UserInfo\\{(\\d+):[^\n]*\n(?:[^\n]*\n){0,3}?\\s*Type: \\Q$type\\E").findAll(shell("dumpsys user")).map { it.groupValues[1].toInt() }.toList()

    /**
     * A profile of this user of [type], started, as a launcher sees it: a new one, or the one there is
     * already where a phone allows only one (a clone); null where the phone can't make one.
     */
    private fun profile(type: String, name: String): UserHandle? {
        val made = Regex("created user id (\\d+)").find(shell("pm create-user --profileOf 0 --user-type $type $name"))?.groupValues?.get(1)?.toInt()
        made?.let { created += it }
        val id = made ?: usersOfType(type).firstOrNull() ?: return null
        shell("am start-user -w $id")
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            launcherApps.profiles.firstOrNull { it.hashCode() == id }?.let { return it }
            Thread.sleep(200)
        }
        return null
    }

    @After fun removeProfiles() {
        created.forEach { shell("pm remove-user $it") }
    }

    @Test fun aCloneIsntWork() {
        val clone = profile("android.os.usertype.profile.CLONE", "clone")
        assumeTrue("this phone can't make a clone profile", clone != null)
        assertEquals(ProfileKind.CLONE, AppRepository.kindOf(context, launcherApps, clone!!, Process.myUserHandle()))
    }

    @Test fun aWorkProfileIsWork() {
        val work = profile("android.os.usertype.profile.MANAGED", "work")
        assumeTrue("this phone can't make a work profile", work != null)
        assertEquals(ProfileKind.WORK, AppRepository.kindOf(context, launcherApps, work!!, Process.myUserHandle()))
    }

    /** The kept app list follows a profile coming and going (from Android 14 a clone too), with no return home to reload it. */
    @Test fun theAppListFollowsAProfileComingAndGoing() {
        val repo = AppRepository.get(context).also { it.start() }
        val users = context.getSystemService(android.os.UserManager::class.java)
        val id = Regex("created user id (\\d+)").find(shell("pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE clone"))?.groupValues?.get(1)?.toInt()
        assumeTrue("this phone can't make another clone profile", id != null)
        created += id!!
        shell("am start-user -w $id")
        val user = (0 until 50).firstNotNullOfOrNull { launcherApps.profiles.firstOrNull { it.hashCode() == id } ?: run { Thread.sleep(200); null } }
        assumeTrue("the launcher isn't shown this profile", user != null)
        val serial = users.getSerialNumberForUser(user!!)
        fun listed() = repo.apps.value.any { it.profile.serial == serial }
        fun waitFor(shown: Boolean): Boolean {
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                if (listed() == shown) return true
                Thread.sleep(200)
            }
            return false
        }
        assumeTrue("the clone has no apps to list", waitFor(shown = true))
        shell("pm remove-user $id")
        created -= id
        assertEquals("its apps leave the list", true, waitFor(shown = false))
    }
}
