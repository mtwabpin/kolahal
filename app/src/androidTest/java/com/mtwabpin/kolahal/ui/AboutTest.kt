package com.mtwabpin.kolahal.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → About: what the guide and the emails to the developer hand to other apps. */
@RunWith(AndroidJUnit4::class)
class AboutTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Keeps what would be started; [refuse] actions act as if no app handled them. */
    private class Catching(base: Context, private val refuse: Set<String> = emptySet()) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            if (intent.action in refuse) throw ActivityNotFoundException(intent.action)
            started += intent
        }
    }

    @Test fun emailIsADraftToTheDeveloper() {
        val phone = Catching(context)
        assertTrue(emailDeveloper(phone, "Kolahal 0.3.1: Crash at 9:41", "the report"))
        val mail = phone.started.single()
        assertEquals(Intent.ACTION_SENDTO, mail.action)
        assertEquals("mailto:", mail.dataString)
        assertArrayEquals(arrayOf("mtwabpin@gmail.com"), mail.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Kolahal 0.3.1: Crash at 9:41", mail.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("the report", mail.getStringExtra(Intent.EXTRA_TEXT))
    }

    /** No mail app: any app that sends text, still addressed to the developer. */
    @Test fun withoutAMailAppAnyAppThatSendsText() {
        val phone = Catching(context, refuse = setOf(Intent.ACTION_SENDTO))
        assertTrue(emailDeveloper(phone, "subject", "body"))
        val chooser = phone.started.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertArrayEquals(arrayOf("mtwabpin@gmail.com"), send.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("body", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun noAppAtAllIsReported() {
        val phone = Catching(context, refuse = setOf(Intent.ACTION_SENDTO, Intent.ACTION_CHOOSER))
        assertFalse(emailDeveloper(phone, "subject", "body"))
    }

    @Test fun guideOpensTheWebsite() {
        val phone = Catching(context)
        assertTrue(openGuide(phone))
        val view = phone.started.single()
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("https://mtwabpin.github.io/kolahal/", view.dataString)
    }

    /** The share sheet with all of the report, to no one in particular. */
    @Test fun aCrashReportGoesToTheShareSheet() {
        val chooser = crashReportShare("the report")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("the report", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Kolahal crash report", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertFalse(send.hasExtra(Intent.EXTRA_EMAIL))
    }

    /** Under the message someone writes: what the developer needs to know about the phone. */
    @Test fun phoneDetailsFollowTheMessage() {
        val details = aboutThisPhone()
        assertTrue(details, details.startsWith("\n\n--\n"))
        assertTrue(details, details.contains(VERSION_TEXT) && details.contains(Build.MODEL) && details.contains("API ${Build.VERSION.SDK_INT}"))
    }
}
