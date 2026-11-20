package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import java.time.ZoneId

/**
 * The editor reference's widget examples ([ReferenceExamples]): each one's card, drawn by the
 * widgets page's own code at the size it has there, over its code in the script's language. A tap
 * on an example gives [onExample] its code.
 *
 * The card takes no touch: a layer over it catches each tap, and TalkBack reads it as its
 * description, with none of its buttons or rows. So it runs nothing, opens no link, and fetches
 * nothing ([LocalWidgetsShown] is false, and the picture example isn't here).
 *
 * A row of the looks ([WidgetLook]) above them draws every card in [look]: what the person who places
 * the widget can choose. [onLook] gets a tap on it. A card whose kind doesn't offer that look draws
 * Plain, and says so under it.
 */
internal fun LazyListScope.widgetExamples(lang: ScriptLang, look: WidgetLook, onLook: (WidgetLook) -> Unit, onExample: (String) -> Unit) {
    item(key = "widget-examples") {
        Text(
            "Widgets · each kind of card, and the code that fills it",
            style = Term.phoneText.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Text(
            "Each placed widget has a look, chosen on the Widgets screen. See each kind in one:",
            style = Term.phoneText.copy(color = Term.dim, fontSize = Term.phoneText.fontSize * 0.85f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            GuiTheme {
                ConnectedChoices(WidgetLook.entries.map { it.label }, look.label, tagPrefix = "example look ") { label ->
                    onLook(WidgetLook.entries.first { it.label == label })
                }
            }
        }
    }
    items(ReferenceExamples.widgets, key = { "widget-example ${it.name}" }) { ex -> WidgetExample(ex, lang, look, onExample) }
}

@Composable
private fun WidgetExample(ex: ReferenceExamples.Widget, lang: ScriptLang, look: WidgetLook, onExample: (String) -> Unit) {
    val context = LocalContext.current
    val clock24 = remember { Config.get(context)[Keys.CLOCK_24H] }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { System.currentTimeMillis() / 1000 }
    val spec = remember(ex) { ex.spec(now, zone) }
    val title = spec.base.title ?: ex.title
    val code = ex.code(lang)
    ExamplePanel("widget-example ${ex.name}", onClick = { onExample(code) }) {
        Text(ex.heading, style = Term.code.copy(color = Term.fg), modifier = Modifier.padding(horizontal = 12.dp))
        // With the panel's margin, the page's: in a sheet as wide as the screen, the card is as wide
        // as on the widgets page.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = (WidgetLayout.GAP_DP - PANEL_MARGIN_DP).dp, vertical = 6.dp)) {
            val pageDp = maxWidth.value.toInt()
            Box {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetsShown provides false, LocalWidgetLook provides look) {
                        // TalkBack reads the card as its description, with none of its buttons or rows.
                        val description = WidgetWords.description(title, spec, now, zone, clock24)
                        Box(Modifier.width(WidgetLayout.widthDp(pageDp, ex.size.span).dp).clearAndSetSemantics { contentDescription = description }) {
                            WidgetCard(
                                title = title,
                                height = WidgetSizes.heightDp(ex.size, pageDp).dp,
                                spec = spec,
                                note = null,
                                description = description,
                            )
                        }
                    }
                }
                // Over the card, so its buttons, rows and links never get a touch.
                Box(Modifier.matchParentSize().clickable(onClickLabel = "Insert this example") { onExample(code) }.testTag("widget-example-cover"))
            }
        }
        // This kind has no such look: the card above is Plain.
        val kind = WidgetJson.kindOf(spec)
        if (look.drawnFor(kind) != look) {
            val what = when (kind) {
                WidgetKind.BUTTONS -> "buttons"
                WidgetKind.TOGGLES -> "switches"
                WidgetKind.MEDIA -> "media controls"
                WidgetKind.IMAGE -> "a picture"
                else -> "this kind"
            }
            Text(
                "${look.label} changes nothing for $what, so this card is Plain.",
                style = Term.phoneText.copy(color = Term.dim, fontSize = Term.phoneText.fontSize * 0.85f),
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp),
            )
        }
        ExampleCode(code)
    }
}

/**
 * The editor reference's log examples ([ReferenceExamples.logs]): each one's lines ([LinesPreview]),
 * over its code in the script's language. A tap on an example gives [onExample] its code.
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

/** Opens nothing: the links of an example's lines only show. */
private object NoLinks : UriHandler {
    override fun openUri(uri: String) = Unit
}

@Composable
private fun LogExample(ex: ReferenceExamples.Log, lang: ScriptLang, onExample: (String) -> Unit) {
    val code = ex.code(lang)
    ExamplePanel("log-example ${ex.name}", onClick = { onExample(code) }) {
        Text(ex.heading, style = Term.code.copy(color = Term.fg), modifier = Modifier.padding(horizontal = 12.dp))
        LinesPreview(ex, "Insert this example", cover = "log-example-cover", modifier = Modifier.padding(vertical = 6.dp)) { onExample(code) }
        ExampleCode(code)
    }
}

/**
 * [ex]'s lines as the log draws them, on the log's background, only to show: a layer over them
 * gives every tap to [onClick] ([clickLabel]), their links open nothing, and TalkBack reads them as
 * one text. So a line's link doesn't open, a swipe doesn't copy or delete it, and its buttons run
 * nothing. [source]: the first line ends with its script's name, as in the log.
 */
@Composable
internal fun LinesPreview(
    ex: ReferenceExamples.Log,
    clickLabel: String,
    cover: String,
    modifier: Modifier = Modifier,
    source: Boolean = true,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val config = remember { Config.get(context) }
    val times = remember { com.mtwabpin.kolahal.terminal.lineTimeFormat(config[Keys.CLOCK_24H]) }
    val itemGap = remember { config[Keys.LOG_ITEM_SPACING].dp }
    val rows = remember(ex, source) {
        com.mtwabpin.kolahal.terminal.buildRows(ex.entries(System.currentTimeMillis()))
            .filterIsInstance<com.mtwabpin.kolahal.terminal.LogRow.Entry>()
            .map { if (source) it else it.copy(showSource = false) }
    }
    val indents = rememberLineIndents()
    // What a screen reader says for the lines: each title and body, as plain text.
    val spoken = remember(ex) { ex.lines.joinToString("\n") { l -> listOfNotNull(l.text, l.body).joinToString("\n") { StyledText.plain(it) } } }
    Box(modifier.fillMaxWidth()) {
        // A link in a line opens through LocalUriHandler: here it opens nothing.
        CompositionLocalProvider(LocalUriHandler provides NoLinks) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Term.bg)
                    .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                    .clearAndSetSemantics { contentDescription = spoken },
            ) {
                for (row in rows) {
                    LogLine(
                        row, TimeMode.OFF, times, dim = false, topGap = 0.dp, itemGap = if (row.itemGap) itemGap else 0.dp,
                        indents = indents, highlighted = false, apps = { emptyList() },
                        onTap = {}, onLongPress = {}, onAppTap = {}, onAppLongPress = {},
                    )
                }
            }
        }
        // Over the lines, so their links, swipes and buttons never get a touch.
        Box(Modifier.matchParentSize().clickable(onClickLabel = clickLabel, onClick = onClick).testTag(cover))
    }
}

/** The space around an example's panel, in dp. */
private const val PANEL_MARGIN_DP = 4

/**
 * One example, on a shaded panel of its own with space around it, so its name, its preview and its
 * code read as one. A tap anywhere on it calls [onClick]. [tag] is its test tag.
 */
@Composable
private fun ExamplePanel(tag: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PANEL_MARGIN_DP.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Term.fg.copy(alpha = 0.06f))
            .clickable(onClickLabel = "Insert this example", onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(tag),
    ) { content() }
}

/** An example's code, small and dim, under its preview. */
@Composable
private fun ExampleCode(code: String) =
    Text(code, style = Term.code.copy(color = Term.dim, fontSize = Term.code.fontSize * 0.85f), modifier = Modifier.padding(horizontal = 12.dp))
