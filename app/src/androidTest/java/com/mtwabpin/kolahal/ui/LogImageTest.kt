package com.mtwabpin.kolahal.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** A script's image under its log line, decoded for the log's width. */
@RunWith(AndroidJUnit4::class)
class LogImageTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val url = "https://example.com/log-image-${System.nanoTime()}.png"

    @After fun tearDown() {
        download().delete()
    }

    /** Where [LogImages] keeps [url]'s download: put there, nothing is fetched. */
    private fun download(): File {
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "log-images").apply { mkdirs() }, name)
    }

    /** The phone turned, so the log is wider: the image is decoded again for it, not stretched. */
    @Test fun aWiderLogDecodesItAgain() {
        download().outputStream().use { Bitmap.createBitmap(2000, 500, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        var width by mutableStateOf(200.dp)
        compose.setContent { KolahalTheme { Box(Modifier.width(width).testTag("log")) { LogImage(url) } } }
        fun px() = compose.onNodeWithTag("log").fetchSemanticsNode().size.width
        val narrow = px()
        compose.waitUntil(5_000) { LogImages.cached(url, narrow) != null }
        compose.runOnIdle { width = 400.dp }
        compose.waitForIdle()
        val wide = px()
        assertTrue("$narrow, then $wide", wide > narrow)
        compose.waitUntil(5_000) { LogImages.cached(url, wide) != null }
    }
}
