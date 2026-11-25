/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap

/** The Todos card's Next look of [t], as a card draws it in place of its list ([WidgetCard]'s `body`). */
internal fun nextTodosBody(t: OwnDesigns.NextTodos): OwnBody = { box, onTap -> NextTodosBody(t, box, onTap) }

/**
 * The Todos card in the Next look ([com.mtwabpin.kolahal.widgets.WidgetLook.NEXT]): it does the job,
 * the next todo first, large, with a check to tap. A tap on the check marks it done and the one
 * after it comes up; the check's shape changes and settles ([rememberSettle]). Undo opens the one
 * ticked last again, until the page is turned away.
 * - 2×2: a ring of the ticked ones with "3/7", "Todos" and "4 left" beside it, then "Next" and the
 *   todo in two lines with its check, then Undo and "+3 more".
 * - 4×2 and taller: the 4×1 row on top, its line without the count, then the todos after the two it
 *   names as rows with checkboxes, as many as fit, then "+3 more". No todo is there twice.
 * - 4×1: the check, the todo with "then Renew the passport · +2" under it, and the ring.
 * - 2×1: the check, and the todo in two lines with "+3 more" under it. No ring: no room.
 * - 1×1: the ring with "3/7" alone.
 * "+3 more", the line under a 4×1's todo and a 1×1's ring open the whole list ([opensList]).
 */
@Composable
private fun NextTodosBody(t: OwnDesigns.NextTodos, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val role = cardRoleColor()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(ownRoom(box))
    ) {
        // Copied first: a Row or Column inside can't read them.
        val width = maxWidth
        val height = maxHeight
        when {
            box == WidgetSizes.Box.LARGE && width < 300.dp -> NextSquare(t, role, onTap)
            box == WidgetSizes.Box.LARGE -> NextTall(t, role, onTap)
            box == WidgetSizes.Box.WIDE -> NextRow(t, role, min(height, 56.dp), ring = true, onTap)
            width >= 150.dp -> NextRow(t, role, min(height, 48.dp), ring = false, onTap)
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .opensList(),
                contentAlignment = Alignment.Center
            ) { RingCount(t, role, min(width, height)) }
        }
    }
}

/** The 2×2: the ring and the count at the top, the next todo under them with its check at the end. */
@Composable
private fun NextSquare(t: OwnDesigns.NextTodos, role: Color, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val muted = lookColors().muted
    val next = t.next
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingCount(t, role, 48.dp)
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    WidgetSource.builtinTitle(WidgetSource.TODOS).orEmpty(),
                    style = type.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    OwnDesigns.leftWords(t),
                    style = type.bodyMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (next != null) {
                    Text("Next", style = type.labelMedium, color = muted, maxLines = 1)
                    Text(
                        cardText(next.text),
                        style = type.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Text(
                        "Nothing open",
                        style = type.titleMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                NextFoot(t.undo, more = t.left - 1, role, onTap, Modifier.padding(top = 4.dp))
            }
            if (next != null) CheckShape(
                next,
                t.done,
                role,
                52.dp,
                onTap,
                Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * A 4×2 or taller card: the 4×1's row at the top ([NextRow]), whose line names the todo after the next
 * one without a count, then the todos after those two as rows with checkboxes ([OwnDesigns.followRows]),
 * as many as fit in the room under the top row as it is drawn, then "+3 more" for the ones left out.
 * No todo is on the card twice, and Undo is on the top row's line.
 */
@Composable
private fun NextTall(t: OwnDesigns.NextTodos, role: Color, onTap: ((WidgetTap) -> Unit)?) {
    val rows = OwnDesigns.followRows(t)
    val lineDp = linesDp(MaterialTheme.typography.bodyMedium)
    Column(Modifier.fillMaxSize()) {
        NextRow(t, role, 56.dp, ring = true, onTap, lines = 2, rest = false)
        Spacer(Modifier.height(8.dp))
        // The room that is left under the top row, whether its todo takes one line or two.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val room = maxHeight.value.toInt()
            val shown = OwnDesigns.followShown(rows.size, room, lineDp)
            val more = OwnDesigns.followMore(t, shown)
            val rowDp = OwnDesigns.followHeightDp(shown, room, lineDp, more > 0)
            Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp)) {
                rows.take(shown).forEachIndexed { i, todo ->
                    key(todo.id) { FollowRow(todo, rowDp.dp, segmentShape(i, shown), role, onTap) }
                }
                NextFoot(null, more, role, onTap, Modifier.height(OwnDesigns.FOOT_DP.dp))
            }
        }
    }
}

/**
 * One row: the next todo's check ([side] across), the todo with a line under it, and with [ring]
 * the ring at the end. The line under it is "then Renew the passport · +2" beside a ring, else
 * "+3 more"; Undo comes first on it while there is one. With nothing open, "Nothing open" stands
 * where the todo is. The todo has [lines] at most. Without [rest], the line has no count: a tall
 * card's, whose rows go on from there.
 */
@Composable
private fun NextRow(
    t: OwnDesigns.NextTodos,
    role: Color,
    side: Dp,
    ring: Boolean,
    onTap: ((WidgetTap) -> Unit)?,
    lines: Int = if (ring) 1 else 2,
    rest: Boolean = true,
) {
    val type = MaterialTheme.typography
    val muted = lookColors().muted
    val next = t.next
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (next != null) CheckShape(next, t.done, role, side, onTap, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (next != null) cardText(next.text) else cardText("Nothing open"),
                style = if (next != null) type.titleMediumEmphasized else type.titleMedium,
                color = if (next != null) MaterialTheme.colorScheme.onSurface else muted,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis
            )
            val then = if (ring) OwnDesigns.thenWords(t, rest) else null
            if (then != null || t.undo != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    t.undo?.let { UndoText(it, role, onTap, Modifier.padding(end = 12.dp)) }
                    if (then != null) Text(
                        cardText(then),
                        style = type.bodyMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .opensList()
                    )
                }
            } else if (!ring) {
                NextFoot(t.undo, more = t.left - 1, role, onTap)
            }
        }
        if (ring) Box(Modifier.padding(start = 12.dp)) { RingCount(t, role, min(side, 48.dp)) }
    }
}

/** The ring of the ticked todos, [side] across, with "3/7" in it. It is no item for a screen reader: the card says the count. */
@Composable
private fun RingCount(t: OwnDesigns.NextTodos, role: Color, side: Dp) {
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        Ring(t.fraction, false, role, side)
        BigText(
            t.count,
            if (side >= 72.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            Modifier.padding(horizontal = side / 6)
        )
    }
}

/**
 * The next todo's check: a Material shape in the role's container with a check in the role's color,
 * moved just far enough to read on it ([valueInk]).
 * A tap marks [todo] done, as a tap on its row does in the other looks: it is a checkbox for a
 * screen reader, named by the todo. The shape is the next in turn after each tick ([rankPolygon] by
 * [done]), and settles into it. Smaller than 48 dp (a low 2×1), its touches still reach 48 dp
 * (Compose's minimum), as [opensList]'s do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckShape(
    todo: Todo,
    done: Int,
    role: Color,
    side: Dp,
    onTap: ((WidgetTap) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val polygon = rankPolygon(done)
    val settle = rememberSettle(polygon)
    val row = remember(todo.id, todo.text) {
        WidgetSpec.Row(todo.text, tap = OwnDesigns.doneTap(todo), done = false)
    }
    Box(
        modifier
            .size(side)
            .settledShape(settle, roleContainer(role), clip = false)
            .clip(polygon.toShape())
            .then(rowPress(row, onTap))
            .semantics { contentDescription = todo.text }
            .testTag("todo-check"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.wi_check),
            contentDescription = null,
            tint = valueInk(role, roleContainer(role)),
            modifier = Modifier.size(side * 0.46f)
        )
    }
}

/** A todo after the next one, in a row as the Plain card has them: its checkbox, and a tap marks it done. */
@Composable
private fun FollowRow(
    todo: Todo,
    height: Dp,
    shape: Shape,
    role: Color,
    onTap: ((WidgetTap) -> Unit)?
) {
    val row = remember(todo.id, todo.text) {
        WidgetSpec.Row(todo.text, tap = OwnDesigns.doneTap(todo), done = false)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(segmentColor())
            .then(rowPress(row, onTap))
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowCheck(false, role, Modifier.padding(horizontal = 8.dp))
        Text(
            cardText(todo.text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The line under the todos: Undo while there is one ([undo]), then "+3 more" for [more] open todos not shown. Nothing without either. */
@Composable
private fun NextFoot(
    undo: Todo?,
    more: Int,
    role: Color,
    onTap: ((WidgetTap) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (undo == null && more <= 0) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (undo != null) UndoText(undo, role, onTap, Modifier.padding(end = 12.dp))
        if (more > 0) Text(
            "+$more more",
            style = MaterialTheme.typography.labelMedium,
            color = lookColors().muted,
            maxLines = 1,
            modifier = Modifier.opensList()
        )
    }
}

/**
 * Undo, in the role's color: a tap opens [todo] again. A screen reader hears which todo. Its
 * touches reach 48 dp (Compose's minimum), more than its line is tall, as [opensList]'s do.
 */
@Composable
private fun UndoText(todo: Todo, role: Color, onTap: ((WidgetTap) -> Unit)?, modifier: Modifier = Modifier) {
    val tap = remember(todo.id) { OwnDesigns.undoTap(todo) }
    Text(
        "Undo",
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = role,
        maxLines = 1,
        modifier = modifier
            .then(
                if (onTap == null) Modifier
                else Modifier.clickable(onClickLabel = tap.label) { onTap(tap) }
            )
            .semantics { contentDescription = "Undo: ${todo.text}" }
            .testTag("todo-undo")
    )
}
