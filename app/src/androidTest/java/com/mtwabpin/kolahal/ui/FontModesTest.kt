package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** `font.sans`: which parts of home draw in the phone's font, and that indents still line up. */
@RunWith(AndroidJUnit4::class)
class FontModesTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val terminalFont = Term.fontFamily

    @After fun tearDown() {
        Term.sans = Term.Sans.OFF
    }

    /** Home on a phone-sized frame, with [sans] as MainActivity sets it from `font.sans`. */
    private fun show(sans: Term.Sans) {
        Term.sans = sans
        val vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 700.dp).consumeWindowInsets(WindowInsets.safeDrawing)) {
                    TerminalScreen(vm, onPickFolder = {})
                }
            }
        }
    }

    private fun line(text: String): SemanticsNodeInteraction {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithText(text, substring = true)
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    private fun SemanticsNodeInteraction.fontFamily() = layout().layoutInput.style.fontFamily

    @Test fun promptModeChangesOnlyThePrompt() {
        logs.add(LogKind.OUTPUT, "cptest prompt mode")
        show(Term.Sans.PROMPT)
        assertEquals(terminalFont, line("cptest prompt mode").fontFamily())
        assertEquals(FontFamily.Default, compose.onNodeWithTag("prompt", useUnmergedTree = true).fontFamily())
    }

    @Test fun allModeKeepsAlignedListsInTheTerminalFont() {
        logs.add(LogKind.OUTPUT, "cptest plain line")
        logs.add(LogKind.OUTPUT, "cptest  aligned   line", ctx = LogContext(LogContext.ALIGNED))
        show(Term.Sans.ALL)
        assertEquals(FontFamily.Default, line("cptest plain line").fontFamily())
        assertEquals(terminalFont, line("cptest  aligned   line").fontFamily())
        assertEquals(FontFamily.Default, compose.onNodeWithTag("prompt", useUnmergedTree = true).fontFamily())
    }

    /** A wrapped line and the dimmed line under it start at the same indent, set by layout, not spaces. */
    @Test fun detailLinesLineUpInThePhonesFont() {
        val first = "cptest heading " + "that is long enough to wrap onto a second line in the frame ".repeat(2)
        logs.add(LogKind.INFO, "$first\ncptest detail")
        show(Term.Sans.ALL)
        val layout = line("cptest heading").layout()
        // Where each line's first character is drawn (getLineLeft leaves the paragraph indent out).
        fun start(line: Int) = layout.getHorizontalPosition(layout.getLineStart(line), usePrimaryDirection = true)
        val wrapped = start(1)
        val detail = start(layout.lineCount - 1)
        assertTrue("wrapped line starts at $wrapped", wrapped > 0f)
        assertTrue("wrapped line at $wrapped, detail line at $detail", abs(wrapped - detail) < 1f)
    }
}
