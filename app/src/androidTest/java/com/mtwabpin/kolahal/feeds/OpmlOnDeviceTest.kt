package com.mtwabpin.kolahal.feeds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The OPML tag pattern on Android's regex engine, which isn't the one the JVM tests run on. */
@RunWith(AndroidJUnit4::class)
class OpmlOnDeviceTest {
    @Test
    fun aGreaterThanInAnAttributeIsntTheTagsEnd() {
        val xml =
            """<outline text="A > B" xmlUrl="https://a.example/"/><outline title='C/>D' xmlUrl='https://b.example/'></outline>"""
        assertEquals(
            listOf(
                Opml.Entry("A > B", "https://a.example/"),
                Opml.Entry("C/>D", "https://b.example/")
            ), Opml.parse(xml)
        )
    }

    @Test
    fun aVeryLongAttributeDoesntStopTheRest() {
        val xml =
            "<outline text=\"" + "x > ".repeat(50_000) + "\" xmlUrl=\"https://a.example/\"/><outline xmlUrl=\"https://b.example/\"/>"
        assertEquals(
            listOf("https://a.example/", "https://b.example/"),
            Opml.parse(xml).map { it.url })
    }
}
