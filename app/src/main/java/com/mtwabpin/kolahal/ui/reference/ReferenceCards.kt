/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.reference

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.ui.BackButton
import com.mtwabpin.kolahal.ui.home.LogLine
import com.mtwabpin.kolahal.ui.home.rememberLineIndents
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.tty.TtyLinesShown
import com.mtwabpin.kolahal.ui.widgets.LocalWidgetLook
import com.mtwabpin.kolahal.ui.widgets.LocalWidgetsShown
import com.mtwabpin.kolahal.ui.widgets.WidgetCard
import com.mtwabpin.kolahal.ui.widgets.WidgetWords
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSizes
import java.time.ZoneId
import kotlin.math.min

/*
 * The reference page's widget and log examples ([ReferenceExamples]): a row for each in its tab, and
 * each one's own view with its card or lines, its code, Copy and Insert.
 *
 * A card or a line here takes no touch ([Untouchable]), and TalkBack reads it as one description,
 * with none of its buttons or rows. So it runs nothing, opens no link, and fetches nothing
 * ([LocalWidgetsShown] is false, and the picture example isn't here).
 */

/** The size of a card's picture in its row, in dp. */
private const val THUMB_WIDTH_DP = 84
private const val THUMB_HEIGHT_DP = 48

/** The width of the widgets page that a row's picture is drawn for, before it is made small: a phone's. */
private const val THUMB_PAGE_DP = 344

/** Code in the terminal's font, at the size of the page's small text. */
@Composable
internal fun codeStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = Term.code.fontFamily,
    fontSize = 13.sp,
    lineHeight = 18.sp
)

/**
 * The reference page's top bar: Back, [title] and [subtitle]. [code]: the title is a name from a
 * script, in the code font.
 *
 * It is a [MediumFlexibleTopAppBar] kept collapsed, at the height of a small bar: the search and
 * the tabs under it need the room.
 */
@Composable
internal fun ReferenceBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    code: Boolean = false
) {
    val fold = with(LocalDensity.current) {
        (TopAppBarDefaults.MediumFlexibleAppBarWithSubtitleExpandedHeight - TopAppBarDefaults.MediumAppBarCollapsedHeight).toPx()
    }
    val state =
        rememberTopAppBarState(initialHeightOffsetLimit = -fold, initialHeightOffset = -fold)
    MediumFlexibleTopAppBar(
        title = {
            Text(
                title,
                fontFamily = if (code) Term.code.fontFamily else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { BackButton(onBack) },
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(state),
        colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
    )
}

/**
 * [content] that takes no touch: a layer over it catches each one, so its buttons, rows and links
 * do nothing. The layer keeps nothing, so a tap still reaches what is around it, such as its row.
 */
@Composable
private fun Untouchable(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
    }
}

/**
 * [shown]'s card at [size] on a widgets page [pageDp] wide, in [look], as the widgets page draws it.
 * TalkBack reads it as its description ([WidgetWords.description]).
 */
@Composable
private fun ExampleCard(
    shown: ReferenceExamples.Widget,
    size: WidgetSize,
    pageDp: Int,
    look: WidgetLook,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clock24 = remember { Config.get(context)[Keys.CLOCK_24H] }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { System.currentTimeMillis() / 1000 }
    val spec = remember(shown) { shown.spec(now, zone) }
    val title = spec.base.title ?: shown.title
    val description =
        remember(spec, title) { WidgetWords.description(title, spec, now, zone, clock24) }
    CompositionLocalProvider(LocalWidgetsShown provides false, LocalWidgetLook provides look) {
        Untouchable(
            modifier
                .width(WidgetLayout.widthDp(pageDp, size.span).dp)
                .clearAndSetSemantics { contentDescription = description }) {
            WidgetCard(
                title = title,
                height = WidgetSizes.heightDp(size, pageDp).dp,
                spec = spec,
                note = null,
                description = description
            )
        }
    }
}

/** [ex]'s card at its own size, made small to fit a row. TalkBack skips it: the row says what it is. */
@Composable
private fun CardThumb(ex: ReferenceExamples.Widget) {
    val width = WidgetLayout.widthDp(THUMB_PAGE_DP, ex.size.span)
    val height = WidgetSizes.heightDp(ex.size, THUMB_PAGE_DP)
    val scale = min(THUMB_WIDTH_DP.toFloat() / width, THUMB_HEIGHT_DP.toFloat() / height)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(THUMB_WIDTH_DP.dp, THUMB_HEIGHT_DP.dp)
            .clipToBounds()
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier
                .requiredSize(width.dp, height.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }) {
            ExampleCard(ex, ex.size, THUMB_PAGE_DP, WidgetLook.PLAIN)
        }
    }
}

/** A thin line under a row of the reference, so each row reads as one at a glance. */
@Composable
internal fun RowDivider() =
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )

/**
 * A row of the Widgets tab, or of the search's results: a small picture of [ex]'s card, its [name]
 * and a [line] under it, and a chevron. A tap opens its view ([onOpen]).
 */
@Composable
internal fun WidgetExampleRow(
    ex: ReferenceExamples.Widget,
    name: AnnotatedString,
    line: AnnotatedString,
    onOpen: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Open", onClick = onOpen)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            .testTag("widget-row ${ex.name}"),
    ) {
        CardThumb(ex)
        Column(Modifier.weight(1f)) {
            Text(name, style = codeStyle().copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    RowDivider()
}

/**
 * A row of the Log lines tab, or of the search's results: [ex]'s [name], and under it its first
 * lines as the log draws them, or [line] when there is one. [tty]: the lines as the TTY style's
 * log draws them, on the grid. A tap opens its view ([onOpen]).
 */
@Composable
internal fun LogExampleRow(
    ex: ReferenceExamples.Log,
    name: AnnotatedString,
    line: AnnotatedString?,
    tty: Boolean,
    onOpen: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Open", onClick = onOpen)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            .testTag("log-row ${ex.name}"),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = codeStyle().copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            if (line != null) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                // The first line only, so the list stays easy to scan; a tall one (a table) is cut
                // at the bottom. TalkBack reads the name.
                Box(
                    Modifier
                        .heightIn(max = 88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clearAndSetSemantics { }) {
                    LinesPreview(
                        ex,
                        Modifier.wrapContentHeight(Alignment.Top, unbounded = true),
                        source = false,
                        first = 1,
                        tty = tty
                    )
                }
            }
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    RowDivider()
}

/**
 * A widget example's own view: its card, large, and what an author can try on it, each one tap,
 * then its code, with Copy and Insert at the bottom.
 * - Example: the guide's example and its variants ([ReferenceExamples.variantsOf]), [variant]
 *   chosen first. Only for an example with variants.
 * - Size: the sizes its kind is made for ([com.mtwabpin.kolahal.widgets.WidgetKind.sizes]).
 * - Look: the looks its kind offers ([WidgetLook.offeredFor]).
 * The card, the code, Copy and Insert follow what is chosen. [onInsert] gets the code.
 */
@Composable
internal fun WidgetExampleView(
    ex: ReferenceExamples.Widget,
    variant: Int,
    lang: ScriptLang,
    onBack: () -> Unit,
    onInsert: (String) -> Unit
) {
    val versions = remember(ex) { listOf(ex) + ReferenceExamples.variantsOf(ex.name) }
    var chosen by remember(ex) { mutableIntStateOf(variant.coerceIn(versions.indices)) }
    val shown = versions[chosen]
    var size by remember(ex) { mutableStateOf(ex.size) }
    val looks = remember(ex) { WidgetLook.offeredFor(ex.kind) }
    var look by remember(ex) { mutableStateOf(WidgetLook.PLAIN) }
    val code = shown.code(lang)
    Scaffold(
        topBar = { ReferenceBar(ex.heading, ex.line, onBack, code = true) },
        bottomBar = { CopyInsertBar(code, "${ex.name} example") { onInsert(code) } },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("widget-view ${ex.name}"),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            PreviewFrame {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ExampleCard(shown, size, maxWidth.value.toInt(), look)
                }
            }
            if (versions.size > 1) {
                ChoiceLabel("Example")
                ExampleChoices(
                    versions.map { it.variant ?: GUIDE },
                    shown.variant ?: GUIDE,
                    "variant ${ex.name} "
                ) { chosen = it }
            }
            ChoiceLabel("Size")
            ExampleChoices(ex.kind.sizes.map { it.label }, size.label, "size ${ex.name} ") {
                size = ex.kind.sizes[it]
            }
            ChoiceLabel("Look")
            ExampleChoices(looks.map { it.label }, look.label, "look ${ex.name} ") {
                look = looks[it]
            }
            ChoiceLabel("Code")
            CodeBlock(code)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * A log example's own view: its lines as the log draws them, then its code, with Copy and Insert at
 * the bottom.
 * - Home style: the lines as the GUI style's log draws them, or as the TTY style's does, on the
 *   grid ([tty]). A tap picks one ([onTty]); the setting stays as it is.
 */
@Composable
internal fun LogExampleView(
    ex: ReferenceExamples.Log,
    lang: ScriptLang,
    tty: Boolean,
    onTty: (Boolean) -> Unit,
    onBack: () -> Unit,
    onInsert: (String) -> Unit
) {
    val code = ex.code(lang)
    Scaffold(
        topBar = { ReferenceBar(ex.heading, ReferenceSearch.Tab.LOGS.label, onBack, code = true) },
        bottomBar = { CopyInsertBar(code, "${ex.name} example") { onInsert(code) } },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("log-view ${ex.name}"),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            PreviewFrame { LinesPreview(ex, Modifier.clip(RoundedCornerShape(16.dp)), tty = tty) }
            ChoiceLabel("Home style")
            ExampleChoices(STYLES, STYLES[if (tty) 1 else 0], "style ${ex.name} ") { onTty(it == 1) }
            ChoiceLabel("Code")
            CodeBlock(code)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The frame around an example's card or lines in its view, a shade off the page. */
@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .testTag("example-preview"),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The name over a row of choices, or over the code. */
@Composable
private fun ChoiceLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/**
 * [choices] as one row of connected buttons, [selected] checked. A tap picks one ([onPick] with its
 * index). Each takes room by the length of its words, so a long one ("As in the guide") isn't cut.
 * Each is tagged [tagPrefix] and its words.
 */
@Composable
private fun ExampleChoices(
    choices: List<String>,
    selected: String,
    tagPrefix: String,
    onPick: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth()
    ) {
        choices.forEachIndexed { i, choice ->
            ToggleButton(
                checked = choice == selected,
                onCheckedChange = { onPick(i) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(choice.length + 4f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton }
                    .testTag(tagPrefix + choice),
            ) {
                Text(choice, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** An example's code, in a rounded box. A line too long for the screen wraps: a line that scrolled
 * sideways hid its end with nothing to say there was more. */
@Composable
private fun CodeBlock(code: String) {
    Text(
        code,
        style = codeStyle().copy(fontSize = 12.sp, lineHeight = 18.sp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("example-code"),
    )
}

/**
 * Copy and Insert, at the bottom of an example's view, as medium buttons. Copy puts [code] on the
 * clipboard (as [clipLabel]); Insert calls [onInsert].
 */
@Composable
private fun CopyInsertBar(code: String, clipLabel: String, onInsert: () -> Unit) {
    val context = LocalContext.current
    val size = ButtonDefaults.MediumContainerHeight
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            FilledTonalButton(
                shapes = ButtonDefaults.shapesFor(size),
                contentPadding = ButtonDefaults.contentPaddingFor(size, hasStartIcon = true),
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(clipLabel, code))
                    // Android 13 and later show their own "copied" confirmation.
                    if (Build.VERSION.SDK_INT < 33) Toast.makeText(
                        context,
                        "Copied",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = size)
                    .testTag("example-copy"),
            ) { SizedIconLabel(R.drawable.ic_content_copy, "Copy", size) }
            Button(
                shapes = ButtonDefaults.shapesFor(size),
                contentPadding = ButtonDefaults.contentPaddingFor(size, hasStartIcon = true),
                onClick = onInsert,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = size)
                    .testTag("example-insert"),
            ) { SizedIconLabel(R.drawable.ic_add, "Insert", size) }
        }
    }
}

/** A button's icon and word, at the icon size, spacing and text style of a button [size] tall. */
@Composable
private fun SizedIconLabel(icon: Int, text: String, size: Dp) {
    Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.iconSizeFor(size)))
    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
    Text(text, style = ButtonDefaults.textStyleFor(size))
}

/** Opens nothing: the links of an example's lines only show. */
private object NoLinks : UriHandler {
    override fun openUri(uri: String) = Unit
}

/**
 * [ex]'s lines as the log draws them, on the log's background, only to show: they take no touch
 * ([Untouchable]), their links open nothing, and TalkBack reads them as one text. So a line's link
 * doesn't open, a swipe doesn't copy or delete it, and its buttons run nothing. A tap on them still
 * reaches what is around them. [source]: the first line ends with its script's name, as in the log.
 * [first]: only that many lines. [tty]: as the TTY style's log draws them, on the grid
 * ([TtyLinesShown]).
 */
@Composable
internal fun LinesPreview(
    ex: ReferenceExamples.Log,
    modifier: Modifier = Modifier,
    source: Boolean = true,
    first: Int = Int.MAX_VALUE,
    tty: Boolean = false
) {
    val context = LocalContext.current
    val config = remember { Config.get(context) }
    val times = remember { com.mtwabpin.kolahal.terminal.lineTimeFormat(config[Keys.CLOCK_24H]) }
    val itemGap = remember { config[Keys.LOG_ITEM_SPACING].dp }
    val rows = remember(ex, source, first) {
        com.mtwabpin.kolahal.terminal.buildRows(ex.entries(System.currentTimeMillis()))
            .filterIsInstance<com.mtwabpin.kolahal.terminal.LogRow.Entry>()
            .take(first)
            .map { if (source) it else it.copy(showSource = false) }
    }
    val indents = rememberLineIndents()
    // What a screen reader says for the lines: each title and body, as plain text.
    val spoken = remember(ex) {
        ex.lines.joinToString("\n") { l ->
            listOfNotNull(
                l.text,
                l.body
            ).joinToString("\n") { StyledText.plain(it) }
        }
    }
    // A link in a line opens through LocalUriHandler: here it opens nothing.
    CompositionLocalProvider(LocalUriHandler provides NoLinks) {
        Untouchable(modifier.fillMaxWidth()) {
            if (tty) TtyLinesShown(
                rows,
                config,
                times,
                Modifier
                    .fillMaxWidth()
                    .background(Term.bg)
                    .padding(vertical = 6.dp)
                    .testTag("tty-lines")
                    .clearAndSetSemantics { contentDescription = spoken },
            ) else Column(
                Modifier
                    .fillMaxWidth()
                    .background(Term.bg)
                    .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                    .clearAndSetSemantics { contentDescription = spoken },
            ) {
                for (row in rows) {
                    LogLine(
                        row,
                        TimeMode.OFF,
                        times,
                        dim = false,
                        topGap = 0.dp,
                        itemGap = if (row.itemGap) itemGap else 0.dp,
                        indents = indents,
                        highlighted = false,
                        apps = { emptyList() },
                        onTap = {},
                        onLongPress = {},
                        onAppTap = {},
                        onAppLongPress = {},
                    )
                }
            }
        }
    }
}

/** The choice of a guide's example among its variants: the example as the scripting guide has it. */
private const val GUIDE = "As in the guide"

/** The choices of a log example's home style: `home.style gui` and `home.style tty`. */
private val STYLES = listOf("GUI", "TTY")
