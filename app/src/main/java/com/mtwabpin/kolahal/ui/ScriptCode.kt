package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.commands.CommandParsing

/**
 * A script's code to read before installing it, not to edit: in the terminal font on the log's
 * background, coloured as the script editor colours it, each line whole. The text can be
 * selected, to copy a part.
 */
@Composable
internal fun ReadOnlyCode(code: String, lang: ScriptLang, modifier: Modifier = Modifier) {
    val palette = Term.palette
    val styled = remember(code, lang, palette) { SyntaxHighlighter(Syntax.of(lang), flashLine = null, matches = null).filter(AnnotatedString(code)).text }
    Surface(shape = MaterialTheme.shapes.medium, color = Term.bg, modifier = modifier.fillMaxWidth().testTag("read-only-code")) {
        // Each line whole, as in the editor: a long one scrolls sideways rather than breaking.
        SelectionContainer(Modifier.horizontalScroll(rememberScrollState())) {
            Text(styled, style = Term.code.copy(color = Term.fg, fontSize = 12.sp, lineHeight = 16.sp), softWrap = false, modifier = Modifier.padding(12.dp))
        }
    }
}

/** How a script runs, read from its code: `command · every 1h · daily 06:00 · on unlock`. This is what installing it sets up. */
internal fun scriptRuns(code: String): String = buildList {
    if (ScriptMeta.isCommand(code)) add("command")
    ScriptMeta.every(code)?.let { add("every ${CommandParsing.formatInterval(it)}") }
    ScriptMeta.at(code).takeIf { it.isNotEmpty() }?.let { add("daily ${it.joinToString(", ")}") }
    ScriptMeta.events(code).takeIf { it.isNotEmpty() }?.let { add("on ${it.joinToString(", ")}") }
}.joinToString(" · ")
