package com.mtwabpin.kolahal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** A database copied from another phone (Android's transfer) loses what belonged to that phone; this install's stays. */
@RunWith(AndroidJUnit4::class)
class ThisInstallTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aDatabaseFromAnotherPhoneLosesThatPhonesState() {
        val scopes = Stores.THIS_PHONE
        val kept = scopes.associateWith { KvStore(context, it).all() }
        try {
            ThisInstall.forget()
            ThisInstall.check(context)
            val mine = KvStore(context, Stores.INSTALL).get("id")
            KvStore(context, "calls").set("ti-test", "1")
            KvStore(context, Stores.SHARED_SETTINGS).set("ti-test", "1")

            // Written by this install: nothing changes.
            ThisInstall.forget()
            ThisInstall.check(context)
            assertEquals("1", KvStore(context, "calls").get("ti-test"))

            // Written by another: the old phone's scopes are cleared, the copy of settings stays.
            KvStore(context, Stores.INSTALL).set("id", "another-phone")
            ThisInstall.forget()
            ThisInstall.check(context)
            assertNull(KvStore(context, "calls").get("ti-test"))
            assertEquals("1", KvStore(context, Stores.SHARED_SETTINGS).get("ti-test"))
            assertEquals(mine, KvStore(context, Stores.INSTALL).get("id"))
        } finally {
            for (scope in scopes) {
                val store = KvStore(context, scope)
                store.all().keys.forEach { store.set(it, null) }
                kept.getValue(scope).forEach { (k, v) -> store.set(k, v) }
            }
            ThisInstall.forget()
        }
    }
}
