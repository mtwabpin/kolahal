package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cards set no bold by hand where the theme has the same style as an Emphasized role
 * (`labelLargeEmphasized`, `titleSmallEmphasized` and the others): a label's or a small or medium
 * title's bold is that role. The screens of `ui/widgets` (Arrange widgets, the toolbar, a card's
 * page, the picker) are left to `ExpressiveOnlyTest`. Left as they are, as the roles differ there:
 * a body's Medium (`bodyMediumEmphasized` has wider letters, `bodyLargeEmphasized` narrower) and
 * Bold on a title or a headline that is larger (their Emphasized roles are Medium).
 */
class CardEmphasisTest {
    private val screens = setOf(
        "WidgetsScreen.kt",
        "WidgetsToolbar.kt",
        "WidgetPageTabs.kt",
        "WidgetSettingsScreen.kt",
        "PageDots.kt",
        "WidgetPickerScreen.kt",
        "WidgetsArrangedPage.kt",
        "HostedWidgets.kt",
        "ShortcutsEditor.kt",
    )

    private val boldByHand =
        Regex("""\b(label(Large|Medium|Small)|title(Medium|Small))\.copy\(fontWeight = FontWeight\.Bold\b""")

    @Test
    fun theCardsUseTheEmphasizedRoles() {
        val dir = sourceFile("src/main/java/com/mtwabpin/kolahal/ui/widgets")
        val cards = dir.listFiles().orEmpty().filter { it.extension == "kt" && it.name !in screens }
        assertTrue(cards.size > 40)
        val found = cards.flatMap { f ->
            val text = f.readText()
            boldByHand.findAll(text).map { m ->
                "${f.name}:${text.substring(0, m.range.first).count { it == '\n' } + 1}: ${m.value}"
            }.toList()
        }
        assertEquals(emptyList<String>(), found)
    }
}
