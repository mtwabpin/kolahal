/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.takeWhole
import com.mtwabpin.kolahal.ui.ConfirmDialog
import com.mtwabpin.kolahal.ui.DialogBars
import com.mtwabpin.kolahal.ui.ErrorTextButton
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.widgets.WidgetPage
import com.mtwabpin.kolahal.widgets.WidgetPages
import kotlinx.coroutines.delay

/**
 * The tabs of Arrange widgets, under its title: one for each widgets page ([pages]), the one being
 * arranged ([current], from 0) checked, then + that adds a page ([onAdd]) while there are fewer than
 * [WidgetPages.MAX]. A tap arranges that page ([onPick]); a long-press, or a screen reader's action,
 * opens the page's name and its removal ([onLongPress]).
 */
@Composable
internal fun PageTabs(
    pages: List<WidgetPage>,
    current: Int,
    onPick: (Int) -> Unit,
    onAdd: () -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val more = pages.size < WidgetPages.MAX
    // The buttons are one connected group: the first and the last have the round outer ends.
    val last = if (more) pages.size else pages.lastIndex
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        pages.indices.forEach { i ->
            PageTab(
                WidgetPages.title(pages, i),
                checked = i == current,
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    last -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                onClick = { onPick(i) },
                onLongPress = { onLongPress(i) },
                modifier = Modifier.weight(1f),
            )
        }
        if (more) {
            ToggleButton(
                checked = false,
                onCheckedChange = { onAdd() },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("page-add"),
            ) { Icon(painterResource(R.drawable.ic_add), "Add a page") }
        }
    }
}

/**
 * One page's tab, called [label]. A tap picks it ([onClick]). Held past the long-press time, it
 * calls [onLongPress], and the release that ends the hold picks nothing.
 */
@Composable
private fun PageTab(
    label: String,
    checked: Boolean,
    shapes: ToggleButtonShapes,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val held by rememberUpdatedState(onLongPress)
    var longPressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        longPressed = false
        delay(timeout)
        longPressed = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        held()
    }
    ToggleButton(
        checked = checked,
        onCheckedChange = {
            if (!longPressed) onClick()
            longPressed = false
        },
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = 4.dp),
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Tab
                onLongClick(label = "Rename or remove") { held(); true }
            }
            .testTag("page-tab $label"),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The name of the page at [index] of [pages], and its removal: from a long-press on its tab.
 * - The name is plain short text; [WidgetPages.nameError] says why one can't be. An empty name gives
 *   the page its number again. Save calls [onRename].
 * - Remove page, for any page but page 1, calls [onRemove]. A page with widgets ([count] of them)
 *   asks first: they move to page 1.
 */
@Composable
internal fun PageDialog(
    pages: List<WidgetPage>,
    index: Int,
    count: Int,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val title = WidgetPages.title(pages, index)
    var name by rememberSaveable(index) { mutableStateOf(pages.getOrNull(index)?.name.orEmpty()) }
    var confirm by rememberSaveable(index) { mutableStateOf(false) }
    if (confirm) {
        val first = WidgetPages.title(pages, 0)
        ConfirmDialog(
            "Remove $title?",
            if (count == 1) "Its widget moves to $first." else "Its $count widgets move to $first.",
            "Remove",
            icon = R.drawable.ic_delete,
            onDismiss = { confirm = false },
        ) {
            confirm = false
            onRemove()
        }
        return
    }
    val error = WidgetPages.nameError(pages, index, name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogBars(); Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = name,
                    // One line, without the `|` the stored list uses.
                    onValueChange = {
                        name = it.replace(Regex("[|\\r\\n]"), "").takeWhole(WidgetPages.MAX_NAME)
                    },
                    label = { Text("Name") },
                    placeholder = { Text("Page ${index + 1}") },
                    supportingText = {
                        Text(error ?: "Up to ${WidgetPages.MAX_NAME} characters. Empty: Page ${index + 1}")
                    },
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("page-name"),
                )
                if (index > 0) {
                    ErrorTextButton(onClick = { if (count > 0) confirm = true else onRemove() }) {
                        Icon(painterResource(R.drawable.ic_delete), null)
                        Spacer(Modifier.size(8.dp))
                        Text("Remove page")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                enabled = error == null,
                onClick = { onRename(name) },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * What Arrange widgets shows for a page other than page 1 that has no widgets ([title] is its name):
 * that it is empty, Add a widget ([onAdd]) and Remove page ([onRemove]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmptyPage(title: String, onAdd: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(112.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Icon(
                painterResource(R.drawable.ic_add),
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Nothing on $title yet",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            "Add a widget, or drag one here from another page.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Button(shapes = ButtonDefaults.shapes(), onClick = onAdd) { Text("Add a widget") }
            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onRemove) { Text("Remove page") }
        }
        Text(
            "An empty page also goes by itself when you leave Arrange widgets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
