package com.mtwabpin.kolahal.ui.reference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.ui.DocsExamples
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * The editor reference draws each widget example from a card made in Kotlin
 * ([ReferenceExamples.Widget.spec]), not by running the example. Here each example's code runs, in
 * Lua and in JavaScript, and must hand `widget.show` that same card. So the card beside the code
 * is what the code makes. The variants ([ReferenceExamples.variants]) run too.
 */
@RunWith(AndroidJUnit4::class)
class ReferenceCardsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun eachExampleDrawsTheCardItsCodeMakes() {
        val zone = ZoneId.systemDefault()
        for (ex in ReferenceExamples.widgets + ReferenceExamples.variants) for (lang in ScriptLang.entries) {
            val before =
                (if (lang == ScriptLang.LUA) DocsExamples.luaBefore else DocsExamples.jsBefore)[ex.name].orEmpty()
            val shown = DocsExamples.shown(
                context,
                ex.name,
                ex.kind,
                ex.size,
                lang,
                before + ex.code(lang),
                DocsExamples.optionsOf[ex.name].orEmpty()
            )
            assertEquals(
                "${ex.name} in $lang",
                ex.spec(System.currentTimeMillis() / 1000, zone),
                shown
            )
        }
    }
}
