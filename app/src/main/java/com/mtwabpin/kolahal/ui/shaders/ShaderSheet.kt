/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.shaders

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.ui.Labeled
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderFit
import com.mtwabpin.kolahal.shader.ShaderSurface
import com.mtwabpin.kolahal.shader.ShaderWords
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.terminal.ShaderRow
import com.mtwabpin.kolahal.terminal.commands.ShaderCommands
import com.mtwabpin.kolahal.ui.ConfirmDialog
import com.mtwabpin.kolahal.ui.ErrorTextButton
import com.mtwabpin.kolahal.ui.GuiSheet
import com.mtwabpin.kolahal.ui.SectionTitle
import com.mtwabpin.kolahal.ui.editor.Syntax
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.ui.scripts.Fact
import com.mtwabpin.kolahal.ui.scripts.ReadOnlyCode
import com.mtwabpin.kolahal.ui.settings.PeekButton
import kotlinx.coroutines.launch

/**
 * The sheet for one shader on the Shaders screen ([name] of [rows]). Left and right swipe to the others,
 * in the list's order, as the preview is what one looks for.
 * - A preview, which stays in place over the rest where there is room, then Use or Turn off, Edit (its code in the editor, [onEdit]), Check (does it
 *   compile, and which uniforms Kolahal sets), Share, and Copy to the script folder. Holding the eye shows home with the shader, in
 *   use or not ([onPeek]).
 * - Its parameters, under the preview, which follows them ([ShaderParamRows]).
 * - Set as wallpaper, for one that can be ([WallpaperChoice]).
 * - Update, for one from Get more shaders with a newer version. An edited one asks first.
 * - Remove, for the user's own. Undo puts it back.
 * - Its code is at the end.
 *
 * One that was turned off has no preview: it may have crashed Kolahal.
 * [inUse] is the shader that is set, and [problems] why one couldn't be used. [shown] clears the sheet
 * while home is looked at through it.
 */
@Composable
internal fun ShaderSheet(
    rows: List<ShaderRow>,
    name: String,
    inUse: String,
    wallpaper: String?,
    problems: Map<String, String>,
    actions: ShaderActions,
    shown: () -> Float,
    onShow: (String) -> Unit,
    onPlay: (String) -> Unit,
    onAskNotifications: () -> Unit,
    onPeek: (String?) -> Unit,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    GuiSheet(onDismiss, alpha = shown, sides = 0.dp) {
        val pager = rememberPagerState(initialPage = rows.indexOfFirst { it.name == name }.coerceAtLeast(0)) { rows.size }
        val current by rememberUpdatedState(rows)
        // The rows of parameters that are open, kept while the sheet is: a page that is swiped away comes back as it was.
        val opened = remember { mutableStateMapOf<String, Boolean>() }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page -> current.getOrNull(page)?.let { onShow(it.name) } }
        }
        HorizontalPager(
            pager,
            key = { rows[it].name },
            verticalAlignment = Alignment.Top,
            // A swipe's leftover speed would stretch the page once it is in place.
            overscrollEffect = null,
            modifier = Modifier
                .fillMaxHeight()
                .testTag("shader-pager"),
        ) { page ->
            ShaderDetails(
                rows[page], "${page + 1} of ${rows.size}", inUse, wallpaper, problems[rows[page].name], actions,
                front = page == pager.settledPage, shown, onPlay, onAskNotifications, onPeek, onEdit, onDismiss, opened,
            )
        }
    }
}

/** One shader's page of the sheet. Only the one in [front] moves, and its preview is the only one that may. */
@Composable
private fun ShaderDetails(
    row: ShaderRow,
    place: String,
    inUse: String,
    wallpaper: String?,
    problem: String?,
    actions: ShaderActions,
    front: Boolean,
    shown: () -> Float,
    onPlay: (String) -> Unit,
    onAskNotifications: () -> Unit,
    onPeek: (String?) -> Unit,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
    opened: MutableMap<String, Boolean>,
) {
    var confirmReplace by remember(row.name) { mutableStateOf(false) }
    // Checked again only when asked: a changed file has no result yet.
    var check by remember(row.name, row.code) { mutableStateOf<Shaders.Check?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val on = row.name == inUse && !row.turnedOff
    val drawable = Shaders.supported && !row.turnedOff && problem == null

    // The preview stays in place while what is under it scrolls: what one changes there shows in it. Where the
    // sheet hasn't room for both, such as on a phone on its side, it scrolls too. The room without the keyboard
    // decides, so the preview doesn't jump while one types; the keyboard makes it smaller.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown() }
    ) {
      val fixed = maxHeight >= PINNED_ROOM
      BoxWithConstraints(
          Modifier
              .fillMaxSize()
              .imePadding()
      ) {
      val high = maxOf(maxHeight * 0.36f, 140.dp)
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    place,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A message of the page would be behind the sheet.
            if (drawable) PeekButton(
                onHold = { holding -> onPeek(row.name.takeIf { holding }) },
                onTap = { Toast.makeText(context, "Tap and hold the eye to see the home screen", Toast.LENGTH_SHORT).show() },
            )
        }
        if (drawable && fixed) ShaderPreview(
            row.name, row.code, Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), moves = front, size = Modifier.height(high),
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
        if (drawable && !fixed) ShaderPreview(row.name, row.code, Modifier.padding(top = 12.dp), moves = front)
        row.meta.description?.let {
            if (drawable && !fixed) Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        if (row.meta.params.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ShaderParamRows(row.name, row.meta.params, actions, opened = opened)
        }
        if (row.meta.once != null) ShaderWordsField(row.name, actions, onAskNotifications, Modifier.padding(top = 16.dp))
        if (row.meta.suits.isNotEmpty()) SuitsRow(row.meta.suits, Modifier.padding(top = 16.dp))
        if (row.turnedOff) SheetError(ShaderPage.turnedOff(row))
        problem?.let { SheetError("Can't be used: $it") }
        ShaderActions.wallpaperWarning(row.meta, row.name, actions.wallpaper())?.let { SheetError(it) }
        if (!Shaders.supported) SheetError(Shaders.NEEDS)

        Spacer(Modifier.height(16.dp))
        val size = ButtonDefaults.MediumContainerHeight
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val wide = Modifier
                .weight(1f)
                .heightIn(size)
            if (row.meta.once != null) Button(
                onClick = { onDismiss(); onPlay(row.name) },
                shapes = ButtonDefaults.shapesFor(size),
                enabled = drawable,
                contentPadding = ButtonDefaults.contentPaddingFor(size),
                modifier = wide,
            ) { Text("Play", style = ButtonDefaults.textStyleFor(size)) }
            else if (on) FilledTonalButton(
                onClick = { actions.off() },
                shapes = ButtonDefaults.shapesFor(size),
                contentPadding = ButtonDefaults.contentPaddingFor(size),
                modifier = wide,
            ) { Text("Turn off", style = ButtonDefaults.textStyleFor(size)) }
            else Button(
                onClick = { actions.use(row.name) },
                shapes = ButtonDefaults.shapesFor(size),
                enabled = Shaders.supported,
                contentPadding = ButtonDefaults.contentPaddingFor(size),
                modifier = wide,
            ) { Text("Use", style = ButtonDefaults.textStyleFor(size)) }
            if (row.origin?.newer == true) FilledTonalButton(
                onClick = {
                    if (row.origin.edited) confirmReplace = true
                    else row.latest?.let { actions.install(it) }
                },
                shapes = ButtonDefaults.shapesFor(size),
                contentPadding = ButtonDefaults.contentPaddingFor(size),
                modifier = wide,
            ) { Text(ShaderPage.update(row), style = ButtonDefaults.textStyleFor(size)) }
        }
        if (drawable && WallpaperChoice.problem(row.name, row.meta) == null) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { actions.setWallpaper(row.name) },
                shapes = ButtonDefaults.shapes(),
                enabled = row.name != wallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("set-wallpaper"),
            ) {
                Icon(painterResource(R.drawable.ic_wallpaper), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (row.name == wallpaper) "Your wallpaper" else "Set as wallpaper")
            }
        }
        Spacer(Modifier.height(8.dp))
        SheetActions(
            buildList {
                // As a script's sheet does, Edit closes the sheet. One that comes with Kolahal becomes yours when it is saved.
                add(SheetAction("Edit", R.drawable.ic_edit) { onDismiss(); onEdit(row.name) })
                add(SheetAction("Check", R.drawable.ic_info) { scope.launch { check = actions.check(row.name) } })
                add(SheetAction("Share", R.drawable.ic_share) { share(context, row) })
                // Its message is on the page, behind the sheet.
                add(SheetAction("Copy", R.drawable.ic_folder, "Copy to script folder") { onDismiss(); actions.copyToFolder(row.name) })
                if (row.yours) add(SheetAction("Remove", R.drawable.ic_delete, error = true) { onDismiss(); actions.remove(row.name) })
            }
        )
        // An edited copy with nothing newer goes back to the shared version.
        if (row.origin?.edited == true && !row.origin.newer && row.latest != null) {
            ErrorTextButton(onClick = { confirmReplace = true }) { Text("Reset to shared version") }
        }
        check?.let { CheckResult(it) }

        Spacer(Modifier.height(16.dp))
        Text(
            ShaderPage.byLine(row),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Fact("From", from(row))
        row.meta.once?.let { Fact("Plays for", if (it % 1f == 0f) "${it.toInt()} s" else "$it s") } ?: Fact("Moves", if (row.meta.animated) "yes" else "no")
        if (row.meta.tilt) Fact("Uses tilt", "yes")
        row.meta.license?.let { Fact("License", it) }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Code")
        Spacer(Modifier.height(8.dp))
        ReadOnlyCode(row.code, Syntax.AGSL)
        Spacer(Modifier.height(16.dp))
        }
      }
      }
    }

    val latest = row.latest
    if (confirmReplace && latest != null) {
        ConfirmDialog(
            "Replace ${row.name}.${ShaderCatalog.EXT}?",
            "Your copy differs from the shared one. Its code is replaced and your changes are lost.",
            "Replace",
            onDismiss = { confirmReplace = false },
        ) { confirmReplace = false; actions.install(latest, replace = true) }
    }
}

/** The height a shader's details need to keep the preview in place over the rest: about a third for it, the rest for what scrolls. */
private val PINNED_ROOM = 520.dp

private fun from(row: ShaderRow): String = when {
    row.origin != null -> row.origin.source
    row.yours && row.bundled -> "you, used instead of the one that comes with Kolahal"
    row.yours -> "you"
    else -> "Kolahal"
}

@Composable
private fun SheetError(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(top = 4.dp)
)

/** What Check found: the problem, or that it compiles and its uniforms; and the header's lines that can't be read. */
@Composable
private fun CheckResult(check: Shaders.Check) {
    Column(
        Modifier
            .padding(top = 12.dp)
            .testTag("shader-check")
    ) {
        val problem = check.problem
        if (problem != null) SheetError(problem.replaceFirstChar { it.uppercase() })
        else ShaderCommands.checkLines(check).forEach { line ->
            Text(
                line.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Header lines that are left out: the shader works without them.
        check.warnings.forEach { SheetError(it) }
    }
}

/**
 * The words that play the one-off shader [name] when a notification has one ([ShaderWords]), kept as they are
 * typed. Without notification access, a button asks for it.
 */
@Composable
private fun ShaderWordsField(name: String, actions: ShaderActions, onAskNotifications: () -> Unit, modifier: Modifier = Modifier) {
    var text by remember(name) { mutableStateOf(ShaderWords.words.value[name].orEmpty()) }
    // Read again on the way back from Android's screen.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val sees = remember(lifecycle) { actions.seesNotifications() }
    Column(modifier) {
        SectionTitle("Plays on a notification")
        Spacer(Modifier.height(8.dp))
        TextField(
            value = text,
            onValueChange = {
                text = it.take(ShaderWords.MAX_CHARS)
                actions.setWords(name, text)
            },
            label = { Text("Words, with commas between") },
            supportingText = { Text("Plays when a notification has one of them, such as a name") },
            singleLine = true,
            shape = TextFieldDefaults.roundedShape,
            colors = fieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shader-words"),
        )
        if (!sees && text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            SheetError("Kolahal can't see your notifications")
            FilledTonalButton(onClick = onAskNotifications, shapes = ButtonDefaults.shapes()) { Text("Allow notification access") }
        }
    }
}

/** How well a shader works over a dark theme, a light one and a wallpaper: a card for each that is rated. */
@Composable
private fun SuitsRow(suits: Map<ShaderSurface, ShaderFit>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rated = ShaderSurface.entries.filter { it in suits }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rated.forEachIndexed { i, surface ->
            val fit = suits.getValue(surface)
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (i == 0) 16.dp else 4.dp, bottomStart = if (i == 0) 16.dp else 4.dp,
                    topEnd = if (i == rated.lastIndex) 16.dp else 4.dp, bottomEnd = if (i == rated.lastIndex) 16.dp else 4.dp,
                ),
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .testTag("suits ${surface.word}")
                    .clearAndSetSemantics { contentDescription = "${surface.label}: ${fit.label}" },
            ) {
                Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(
                            when (surface) {
                                ShaderSurface.DARK -> R.drawable.wi_moon
                                ShaderSurface.LIGHT -> R.drawable.wi_sun
                                ShaderSurface.WALLPAPER -> R.drawable.ic_wallpaper
                            }
                        ),
                        null, Modifier.size(20.dp), tint = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(surface.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    Text(
                        fit.label, style = MaterialTheme.typography.titleSmall,
                        color = when (fit) {
                            ShaderFit.GOOD -> colors.primary
                            ShaderFit.FAIR -> colors.onSurface
                            ShaderFit.POOR -> colors.error
                        },
                    )
                }
            }
        }
    }
}

/** One of the smaller things to do with a shader. [says] is what a screen reader hears, when it is more than [label]. */
private class SheetAction(val label: String, val icon: Int, val says: String = label, val error: Boolean = false, val run: () -> Unit)

/**
 * Whether [count] buttons with their words fit side by side in [room]: all are as wide as the one with
 * the longest word ([widest], the width of its button with its icon and padding), with [gap] between
 * two. A pressed button widens by [grows] of its width and takes that from a neighbor, which must
 * still hold its word. All in dp.
 */
internal fun wordsFit(room: Float, widest: Float, count: Int, gap: Float, grows: Float): Boolean {
    if (count <= 0) return true
    val each = (room - gap * (count - 1)) / count
    return each * (1f - grows) >= widest
}

/**
 * [actions] in an Expressive button group: the pressed one widens, and its neighbors give way. Each has
 * its icon and its word. Where the words don't fit on the line ([wordsFit]: four or five of them on a phone),
 * each is its icon alone, and says its word in a bubble on a long-press and to a screen reader.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetActions(actions: List<SheetAction>) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val labels = actions.map { it.label }
    // The widest word, as a button draws it.
    val widest = remember(labels, style, measurer, density) {
        with(density) { labels.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }?.toDp() ?: 0.dp }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A button's own parts beside its word: the padding at both ends, the icon and the room after it.
        val words = wordsFit(
            room = maxWidth.value,
            widest = (widest + WORD_PADDING * 2 + ButtonDefaults.IconSize + WORD_GAP).value,
            count = actions.size,
            gap = ButtonGroupDefaults.HorizontalArrangement.spacing.value,
            grows = ButtonGroupDefaults.ExpandedRatio,
        )
        ButtonGroup(
            overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menu) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (a in actions) customItem(
                buttonGroupContent = {
                    val source = remember { MutableInteractionSource() }
                    if (words) FilledTonalButton(
                        onClick = a.run,
                        shapes = ButtonDefaults.shapes(),
                        colors = if (a.error) ButtonDefaults.filledTonalButtonColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer)
                        else ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = PaddingValues(horizontal = WORD_PADDING),
                        interactionSource = source,
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(source)
                            .semantics { if (a.says != a.label) contentDescription = a.says },
                    ) {
                        Icon(painterResource(a.icon), null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(WORD_GAP))
                        Text(a.label, maxLines = 1, softWrap = false)
                    } else Box(Modifier.weight(1f).animateWidth(source)) {
                        // The bubble's own box takes no weight from the group: the box around it does.
                        Labeled(a.says, Modifier.fillMaxWidth()) {
                            FilledTonalIconButton(
                                onClick = a.run,
                                shapes = IconButtonDefaults.shapes(),
                                colors = if (a.error) IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer)
                                else IconButtonDefaults.filledTonalIconButtonColors(),
                                interactionSource = source,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Icon(painterResource(a.icon), a.says, Modifier.size(ButtonDefaults.IconSize)) }
                        }
                    }
                },
                menuContent = { state ->
                    DropdownMenuItem(text = { Text(a.says) }, onClick = { a.run(); state.dismiss() })
                },
            )
        }
    }
}

/** The padding at each end of a button with its word, and the room between its icon and the word. */
private val WORD_PADDING = 8.dp
private val WORD_GAP = 6.dp

/** Offers the shader's code, as text, to another app. Without one that takes it, nothing happens. */
private fun share(context: Context, row: ShaderRow) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "${row.name}.${ShaderCatalog.EXT}")
        .putExtra(Intent.EXTRA_TEXT, row.code)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
