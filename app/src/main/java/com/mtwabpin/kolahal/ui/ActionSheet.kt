/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.terminal.ActionIcon
import com.mtwabpin.kolahal.terminal.ActionPlace
import com.mtwabpin.kolahal.terminal.LogAction
import com.mtwabpin.kolahal.terminal.MenuLayout
import com.mtwabpin.kolahal.ui.home.BatteryGlyph
import com.mtwabpin.kolahal.ui.theme.Term

/**
 * A long-press menu.
 * - The actions use the system font, like any menu.
 * - The [title] (the pressed line) uses the terminal font, as in the log. With [systemFont] it uses
 *   the system font instead (quick actions, where the title is a heading).
 * - [url] is the line's link. It shows under the title, so the user sees where Open link goes.
 */
data class ActionMenu(
    val title: String,
    val actions: List<LogAction>,
    val entryId: Long?,
    val systemFont: Boolean = false,
    val url: String? = null,
    /** Where the line came from and when, to the second ([com.mtwabpin.kolahal.terminal.lineDetail]). */
    val detail: String? = null,
    /**
     * The quick actions (`$`), not a long-press menu: in the tty home style, `tty.quick-actions`
     * chooses how it is drawn, and `tty.menu` chooses for the others.
     */
    val quick: Boolean = false,
)

/**
 * A bottom sheet in the terminal's style: its surface color, rounded only at the top, and no drag
 * handle. It opens fully, and hides the bars that home hides ([DialogBars]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TermSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberExpandedSheetState(),
        containerColor = Term.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        DialogBars()
        content()
    }
}

/**
 * Long-press actions in a compact bottom sheet. The line is on top, with its details and link. Then,
 * for a line's or an app's actions (each with its [ActionPlace], [MenuLayout]):
 * - the row of icons, a word under each, in fixed places: what every line has (Copy, Fresh,
 *   Star, Only this, Delete), or what every app has, held in a list (Open, App info, Favorite, Hide,
 *   Uninstall);
 * - a group of chips for each thing the line names, under a heading that names it: the script, the
 *   app, the link. A group's destructive actions are red chips at its end.
 * Actions without a place (quick actions, the editor's menu) are a list of words, destructive ones
 * last, in red. A screen reader hears each action's full words ([LogAction.label]).
 */
@Composable
fun ActionSheet(menu: ActionMenu, onDismiss: () -> Unit) {
    TermSheet(onDismiss) {
        // Scroll when the actions don't fit, so the user can reach the last one.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            // The size follows the terminal's text size, like the icons.
            val menuStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (Term.fontSize + 2).sp,
                lineHeight = ((Term.fontSize + 2) * 1.4f).sp,
            )
            Text(
                menu.title,
                style = (if (menu.systemFont) menuStyle else Term.text).copy(color = Term.dim),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Where the line came from and when. The log may not show this on the line.
            menu.detail?.let { detail ->
                Text(
                    detail,
                    style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .testTag("menu-detail"),
                )
            }
            menu.url?.let { url ->
                Text(
                    url,
                    style = Term.text.copy(color = Term.link, fontSize = (Term.fontSize - 1).sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            val layout = remember(menu.actions) { MenuLayout.of(menu.actions) }
            val run = { action: LogAction -> onDismiss(); action.run() }
            layout.row?.let { IconRow(it, run) }
            layout.groups.forEach { (group, actions) ->
                Text(
                    group.heading,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = (Term.fontSize - 1).sp),
                    color = Term.prompt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .semantics { heading() },
                )
                // A chip is 40 dp tall and takes touches over 48 dp, so the rows sit 8 dp apart and
                // two chips' touch areas never overlap.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    actions.forEach { ActionChip(it, run) }
                }
            }
            WordRows(layout.words, menuStyle, divided = layout.row != null || layout.groups.isNotEmpty(), run)
        }
    }
}

/** An action's icon color: as the header colors it, red for a destructive one, else [plain]. */
private fun tint(action: LogAction, plain: Color): Color {
    val icon = action.icon as? ActionIcon.Drawable
    return when {
        action.destructive || icon?.alert == true -> Term.error
        icon?.profile != null -> Term.profileColor(icon.profile)
        else -> plain
    }
}

/**
 * The row of icons with a word under each, in [ActionPlace.SLOTS] equal places. An empty place
 * stays empty, so every action keeps its place from line to line.
 */
@Composable
private fun IconRow(row: List<LogAction?>, run: (LogAction) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        row.forEach { action ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (action != null) {
                    val color = if (action.destructive) Term.error else Term.fg
                    TextButton(
                        onClick = { run(action) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(contentColor = color),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .semantics { contentDescription = action.label },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            (action.icon as? ActionIcon.Drawable)?.let {
                                Icon(
                                    painterResource(it.id),
                                    contentDescription = null,
                                    tint = tint(action, color),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                action.short,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = (Term.fontSize - 2).sp),
                                color = if (action.destructive) Term.error else Term.dim,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A chip: an outlined button 40 dp tall with the action's icon and its short words. A destructive one is red. */
@Composable
private fun ActionChip(action: LogAction, run: (LogAction) -> Unit) {
    val size = ButtonDefaults.MinHeight
    val color = if (action.destructive) Term.error else Term.fg
    OutlinedButton(
        onClick = { run(action) },
        shapes = ButtonDefaults.shapesFor(size),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.dp, if (action.destructive) Term.error.copy(alpha = 0.6f) else Term.dim.copy(alpha = 0.4f)),
        contentPadding = ButtonDefaults.contentPaddingFor(size, hasStartIcon = action.icon != null),
        modifier = Modifier
            .heightIn(min = size)
            .semantics { contentDescription = action.label },
    ) {
        (action.icon as? ActionIcon.Drawable)?.let {
            Icon(
                painterResource(it.id),
                contentDescription = null,
                tint = tint(action, Term.dim),
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
            )
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
        }
        Text(
            action.short,
            style = ButtonDefaults.textStyleFor(size).copy(fontSize = Term.fontSize.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Actions as a list of words, one 48 dp row each, with the icon (if any) in a column as wide as the
 * header's battery icon, so the labels line up. Destructive ones come last, in red, after a line;
 * so does the first word after a row or groups above ([divided]).
 */
@Composable
private fun WordRows(actions: List<LogAction>, menuStyle: TextStyle, divided: Boolean, run: (LogAction) -> Unit) {
    var dividerShown = false
    // The header's icon size.
    val iconSize = (Term.fontSize + 3).dp
    val iconColumn = actions.any { it.icon != null }
    actions.forEachIndexed { i, action ->
        if ((action.destructive || (divided && i == 0)) && !dividerShown) {
            HorizontalDivider(
                color = Term.dim.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            dividerShown = true
        }
        val color = if (action.destructive) Term.error else Term.fg
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { run(action) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (iconColumn) {
                Box(Modifier.width(iconSize * 1.6f), contentAlignment = Alignment.Center) {
                    when (val icon = action.icon) {
                        is ActionIcon.Drawable -> Image(
                            painter = painterResource(icon.id),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(
                                when {
                                    icon.alert -> Term.error
                                    icon.profile != null -> Term.profileColor(icon.profile)
                                    else -> Term.dim
                                },
                            ),
                            modifier = Modifier.size(iconSize),
                        )

                        is ActionIcon.Battery -> BatteryGlyph(
                            icon.pct,
                            icon.charging,
                            when {
                                icon.charging -> Term.prompt
                                icon.pct <= 15 -> Term.error
                                else -> Term.dim
                            },
                            iconSize,
                        )

                        null -> Unit
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(action.label, style = menuStyle.copy(color = color))
        }
    }
}
