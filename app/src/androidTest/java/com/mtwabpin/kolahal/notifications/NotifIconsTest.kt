package com.mtwabpin.kolahal.notifications

import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A notification's line ends in its status-bar icon, saved once as a small picture; the oldest go past 400. */
@RunWith(AndroidJUnit4::class)
class NotifIconsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, "notif-icons")
    private val ours = Icon.createWithResource(context, R.drawable.ic_stat_terminal)

    @After fun tearDown() {
        dir.listFiles { f -> f.name.startsWith("kn-") }?.forEach { it.delete() }
    }

    private fun png(key: String) = File(dir, "$key.png")

    private fun version() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** Only a resource icon gets a key, made of its app, the resource and the app's version; one with no app named is the poster's. */
    @Test fun onlyResourceIconsGetAKey() {
        val hex = Integer.toHexString(R.drawable.ic_stat_terminal)
        assertNull(NotifIcons.key(context, "com.example.app", null))
        assertNull(NotifIcons.key(context, "com.example.app", Icon.createWithBitmap(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))))
        assertEquals("${context.packageName}_${hex}_${version()}", NotifIcons.key(context, "com.example.app", ours))
        assertEquals(
            "${context.packageName}_${hex}_${version()}",
            NotifIcons.key(context, context.packageName, Icon.createWithResource("", R.drawable.ic_stat_terminal)),
        )
    }

    /** Saved once, an icon loads as a 48-pixel picture and stays in memory; saving it again leaves the file be. */
    @Test fun anIconIsSavedOnceAndLoads() {
        val key = "kn-icon-${System.nanoTime()}"
        assertFalse(NotifIcons.has(context, key))
        NotifIcons.save(context, key, ours)
        assertTrue(NotifIcons.has(context, key))
        assertTrue(png(key).exists())

        val image = NotifIcons.load(context, key)
        assertNotNull(image)
        assertEquals(48, image!!.width)
        assertEquals(48, image.height)
        assertTrue(NotifIcons.cached(key) === image)

        val old = 1_500_000_000_000L
        png(key).setLastModified(old)
        NotifIcons.save(context, key, ours)
        assertEquals(old, png(key).lastModified())
    }

    /** An icon that can't be drawn (its app is gone) isn't saved: the line falls back to the app's name. */
    @Test fun anIconThatCantBeDrawnIsntSaved() {
        val key = "kn-gone-${System.nanoTime()}"
        NotifIcons.save(context, key, Icon.createWithResource("com.example.kolahal.gone", 0x7f010001))
        assertFalse(NotifIcons.has(context, key))
        assertFalse(png(key).exists())
        assertNull(NotifIcons.load(context, key))
        assertNull(NotifIcons.cached(key))
    }

    /**
     * Past 400 saved icons the oldest go, and only they: the one just saved and the newer ones stay.
     * The oldest of all was saved as a notification's is, so it's known, and once gone it isn't.
     */
    @Test fun theOldestGoPastFourHundred() {
        NotifIcons.preload(context)
        dir.mkdirs()
        val others = dir.listFiles { f -> f.name.endsWith(".png") }.orEmpty().map { it.name }
        val oldest = "kn-trim-oldest-${System.nanoTime()}"
        NotifIcons.save(context, oldest, ours)
        assertTrue(NotifIcons.has(context, oldest))
        png(oldest).setLastModified(1_400_000_000_000L)
        val old = (0 until 399).map { i ->
            png("kn-trim-$i").apply {
                writeBytes(byteArrayOf(1))
                setLastModified(1_500_000_000_000L + i * 1_000L)
            }
        }
        val key = "kn-trim-new-${System.nanoTime()}"
        NotifIcons.save(context, key, ours)

        val left = dir.listFiles { f -> f.name.endsWith(".png") }.orEmpty().map { it.name }.toSet()
        assertEquals(400, left.size)
        assertTrue("$key.png" in left)
        assertTrue(NotifIcons.has(context, key))
        assertTrue(left.containsAll(others))
        // The oldest went first, and from what is known; then as many of the rest as were too many.
        assertFalse(png(oldest).exists())
        assertFalse(NotifIcons.has(context, oldest))
        val gone = old.filter { !it.exists() }
        assertEquals(old.take(others.size), gone)
    }
}
