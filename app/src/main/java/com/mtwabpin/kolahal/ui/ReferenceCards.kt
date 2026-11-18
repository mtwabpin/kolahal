package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetSizes
import java.time.ZoneId

/**
 * The editor reference's widget examples ([ReferenceExamples]): each one's card, drawn by the
 * widgets page's own code at the size it has there, over its code in the script's language. A tap
 * on an example gives [onExample] its code.
 *
 * The card takes no touch: a layer over it catches each tap. So it runs nothing, opens no link, and
 * fetches nothing ([LocalWidgetsShown] is false, and the picture example isn't here).
 */
internal fun LazyListScope.widgetExamples(lang: ScriptLang, onExample: (String) -> Unit) {
    item(key = "widget-examples") {
        Text(
            "Widgets · each kind of card, and the code that fills it",
            style = Term.phoneText.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(ReferenceExamples.widgets, key = { "widget-example ${it.name}" }) { ex -> WidgetExample(ex, lang, onExample) }
}

@Composable
private fun WidgetExample(ex: ReferenceExamples.Widget, lang: ScriptLang, onExample: (String) -> Unit) {
    val context = LocalContext.current
    val clock24 = remember { Config.get(context)[Keys.CLOCK_24H] }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { System.currentTimeMillis() / 1000 }
    val spec = remember(ex) { ex.spec(now, zone) }
    val title = spec.base.title ?: ex.title
    val code = ex.code(lang)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Insert this example") { onExample(code) }
            .padding(vertical = 8.dp)
            .testTag("widget-example ${ex.name}"),
    ) {
        Text(ex.heading, style = Term.code.copy(color = Term.fg), modifier = Modifier.padding(horizontal = 16.dp))
        // The page's margins: in a sheet as wide as the screen, the card is as wide as on the widgets page.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = WidgetLayout.GAP_DP.dp, vertical = 6.dp)) {
            val pageDp = maxWidth.value.toInt()
            Box {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetsShown provides false) {
                        Box(Modifier.width(WidgetLayout.widthDp(pageDp, ex.size.span).dp)) {
                            WidgetCard(
                                title = title,
                                height = WidgetSizes.heightDp(ex.size, pageDp).dp,
                                spec = spec,
                                note = null,
                                description = WidgetWords.description(title, spec, now, zone, clock24),
                            )
                        }
                    }
                }
                // Over the card, so its buttons, rows and links never get a touch.
                Box(Modifier.matchParentSize().clickable(onClickLabel = "Insert this example") { onExample(code) }.testTag("widget-example-cover"))
            }
        }
        Text(code, style = Term.code.copy(color = Term.dim, fontSize = Term.code.fontSize * 0.85f), modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/**
 * The editor reference's log examples ([ReferenceExamples.logs]): each one's lines, drawn by the
 * log's own line ([LogLine]) on the log's background, over its code in the script's language. A tap
 * on an example gives [onExample] its code.
 *
 * The lines take no touch: a layer over them catches each tap. So a line's link doesn't open, a
 * swipe doesn't copy or delete it, and its buttons run nothing.
 */
internal fun LazyListScope.logExamples(lang: ScriptLang, onExample: (String) -> Unit) {
    item(key = "log-examples") {
        Text(
            "Log lines · what log, md.log and fmt make, and the code",
            style = Term.phoneText.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(ReferenceExamples.logs, key = { "log-example ${it.name}" }) { ex -> LogExample(ex, lang, onExample) }
}

@Composable
private fun LogExample(ex: ReferenceExamples.Log, lang: ScriptLang, onExample: (String) -> Unit) {
    val context = LocalContext.current
    val config = remember { Config.get(context) }
    val times = remember { com.mtwabpin.kolahal.terminal.lineTimeFormat(config[Keys.CLOCK_24H]) }
    val itemGap = remember { config[Keys.LOG_ITEM_SPACING].dp }
    val rows = remember(ex) {
        com.mtwabpin.kolahal.terminal.buildRows(ex.entries(System.currentTimeMillis()))
            .filterIsInstance<com.mtwabpin.kolahal.terminal.LogRow.Entry>()
    }
    val indents = rememberLineIndents()
    val code = ex.code(lang)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Insert this example") { onExample(code) }
            .padding(vertical = 8.dp)
            .testTag("log-example ${ex.name}"),
    ) {
        Text(ex.heading, style = Term.code.copy(color = Term.fg), modifier = Modifier.padding(horizontal = 16.dp))
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.fillMaxWidth().background(Term.bg).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
                for (row in rows) {
                    LogLine(
                        row, TimeMode.OFF, times, dim = false, topGap = 0.dp, itemGap = if (row.itemGap) itemGap else 0.dp,
                        indents = indents, highlighted = false, apps = { emptyList() },
                        onTap = {}, onLongPress = {}, onAppTap = {}, onAppLongPress = {},
                    )
                }
            }
            // Over the lines, so their links, swipes and buttons never get a touch.
            Box(Modifier.matchParentSize().clickable(onClickLabel = "Insert this example") { onExample(code) }.testTag("log-example-cover"))
        }
        Text(code, style = Term.code.copy(color = Term.dim, fontSize = Term.code.fontSize * 0.85f), modifier = Modifier.padding(horizontal = 16.dp))
    }
}
