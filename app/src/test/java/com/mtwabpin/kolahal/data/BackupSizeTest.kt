package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** A backup's size is counted, not copied to measure: it must come to what the file holds. */
class BackupSizeTest {
    @Test fun countedAsUtf8Encodes() {
        val samples = listOf("", "plain", "café · naïve", "नमस्ते", "😀 emoji", "\uD83D lone high", "lone low \uDE00", "end high \uD83D")
        for (s in samples) assertEquals(s, s.toByteArray().size.toLong(), utf8Size(s))
        val r = Random(3)
        val pool = "aé·न😀😀\u0000߿ࠀ￿"
        repeat(2_000) {
            val s = String(CharArray(r.nextInt(0, 40)) { pool[r.nextInt(pool.length)] })
            assertEquals(s.toByteArray().size.toLong(), utf8Size(s))
        }
    }
}
