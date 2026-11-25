/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.scripts

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.VibrantTimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppNames
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.ConfirmDialog
import com.mtwabpin.kolahal.ui.DialogBars
import com.mtwabpin.kolahal.ui.ErrorTextButton
import com.mtwabpin.kolahal.ui.GuiSheet
import com.mtwabpin.kolahal.ui.IconLabel
import com.mtwabpin.kolahal.ui.IntervalSelector
import com.mtwabpin.kolahal.ui.Labeled
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.SectionTitle
import com.mtwabpin.kolahal.ui.SwitchRow
import com.mtwabpin.kolahal.ui.ValueRow
import com.mtwabpin.kolahal.ui.editor.EditorLogic
import com.mtwabpin.kolahal.ui.editor.RunWithDialog
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.ui.home.AppIcon
import com.mtwabpin.kolahal.ui.home.scriptColor
import com.mtwabpin.kolahal.ui.produceIo
import com.mtwabpin.kolahal.ui.relative
import com.mtwabpin.kolahal.ui.shaders.wordsFit
import com.mtwabpin.kolahal.ui.theme.Term

/** A label and its value on a script's sheet. Used for the user's scripts and for shared ones. */
@Composable
internal fun Fact(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The line that warns of the internet together with private data ([ScriptAccess.warning]): beside a
 * script's access switches, on its open card, and in the dialogs and pages where access is granted.
 * Nothing shows when [items] ([ScriptAccess.privateWithInternet]) is empty.
 * - A caution, as the card's lines about a copy: an icon in the theme's warning color, and the text
 *   in the body color. It wraps at any font size, and takes no room from a switch.
 * - A screen reader reads it as one statement.
 */
@Composable
internal fun PrivateOnlineWarning(items: List<String>, modifier: Modifier = Modifier) {
    val text = ScriptAccess.warning(items) ?: return
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(PRIVATE_ONLINE_TAG),
    ) {
        Icon(
            painterResource(R.drawable.ic_privacy_tip),
            null,
            Modifier
                .padding(top = 1.dp)
                .size(18.dp),
            tint = Term.palette.warn,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The test tag of [PrivateOnlineWarning]. */
internal const val PRIVATE_ONLINE_TAG = "private-online"

/**
 * The sheet for one script: run, edit, export, on/off, notifications, timers, library and delete.
 * A command, or a script with `@on` or `@at`, also has Run with… ([RunWithDialog]): arguments, or an
 * event to simulate.
 * It reads nothing from the script's code but its options, its events and the apps its
 * `@on notification` names: the page already read the rest ([info]).
 */
@Composable
internal fun ScriptSheet(
    vm: TerminalViewModel,
    /** The script, and what the page read from its code. */
    info: ScriptInfo,
    /** Its declared secrets that have a value, as the page read them. */
    secretsSet: List<String>,
    /** How a library or shared copy compares with its source. Null for a script the user wrote. */
    copy: ScriptCopy?,
    onEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
) {
    val s = info.script
    var confirmDelete by remember(s.id) { mutableStateOf(false) }
    // The dialog of its copy that shows: the question before Update or Reset, or Compare.
    var copyDialog by remember(s.id) { mutableStateOf<CopyDialog?>(null) }
    var times by remember(s.id, s.atTimes) { mutableStateOf(s.atTimes.joinToString(", ")) }
    var pickingTime by remember(s.id) { mutableStateOf(false) }
    var timesError by remember(s.id) { mutableStateOf(false) }
    // Typed times are set when the field loses focus or the sheet closes, as Done and the check mark
    // do. Times that do not read stay in the field, marked red, and are not set. An empty field
    // is someone retyping: only Done or the check mark clears the times. Set this way, times don't
    // switch the script on, and a script switched off or deleted while the sheet was open is left be.
    var deleted by remember(s.id) { mutableStateOf(false) }
    val applyTimes = rememberUpdatedState {
        if (!deleted && s.enabled && times.isNotBlank() && times != s.atTimes.joinToString(", ")) {
            timesError = !vm.scriptActions.setScriptTimes(s, times, switchOn = false)
        }
    }
    DisposableEffect(s.id) { onDispose { applyTimes.value() } }
    val isCommand = info.isCommand
    val desc = info.desc
    val title = info.title
    // `@version` and `@author`, if the script has them: v1.2 · by Someone
    val byLine = remember(info) {
        listOfNotNull(
            info.version?.let { "v$it" },
            info.author?.let { "By $it" }).joinToString(" · ").ifEmpty { null }
    }
    val access = info.accessItems
    val notifies = info.notifies
    val secrets = info.secrets
    var editingSecret by remember(s.id) { mutableStateOf<ScriptMeta.Secret?>(null) }
    val options = remember(s.code) { ScriptMeta.options(s.code) }
    val optionsVersion by vm.scriptActions.optionsVersion.collectAsStateWithLifecycle()
    val optionValues by produceIo(
        emptyMap<String, String>() to emptySet<String>(),
        s.code,
        s.name,
        optionsVersion,
        what = "Reading a script's options"
    ) {
        vm.scriptActions.optionValues(s)
    }
    var editingOption by remember(s.id) { mutableStateOf<ScriptMeta.Option?>(null) }
    // The apps its `@on notification` line names, by their names when installed, else by package.
    // A package means the personal profile's app: its line shows that one's icon beside its name.
    val notificationApps = remember(s.code) { ScriptMeta.parse(s.code).notificationApps }
    val context = LocalContext.current.applicationContext
    val installed by remember(context) { AppRepository.get(context).apps }.collectAsStateWithLifecycle()
    val eventApps by produceIo(
        notificationApps.orEmpty().map { EventApp(it, it, null) },
        notificationApps,
        installed,
        what = "Reading the names of a script's apps"
    ) {
        notificationApps.orEmpty().map { pkg ->
            val app = installed.firstOrNull { it.pkg == pkg && it.profile.kind == ProfileKind.PERSONAL }
            EventApp(pkg, AppNames.label(context, pkg, app?.profile?.serial) ?: pkg, app)
        }
    }
    val grants = info.grants
    var renaming by remember(s.id) { mutableStateOf(false) }
    val runWith = remember(s.code) { EditorLogic.offersRunWith(s.code) }
    var runWithOpen by remember(s.id) { mutableStateOf(false) }

    GuiSheet(onDismiss, scroll = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${s.name}.${s.lang.ext}",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = scriptColor(s.name),
                modifier = Modifier.weight(1f, fill = false),
            )
            LabeledIconButton(
                R.drawable.ic_edit,
                "Rename",
                onClick = { renaming = true },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Text(
            listOfNotNull(
                if (s.lang == ScriptLang.LUA) "Lua" else "JavaScript",
                byLine,
                copy?.addedSource?.let { "From $it" },
                if (isCommand) "Command" else null,
                desc
            )
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            s.lastRun?.let { "Last run ${relative(it)} · ${s.lastStatus.orEmpty()}" }
                ?: "Never run",
            style = MaterialTheme.typography.bodySmall,
            color = if (s.failed()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        SheetActions(
            buildList {
                add(SheetAction("Run", R.drawable.ic_play, filled = true) { vm.scriptActions.runScriptNow(s) })
                // A command takes arguments; a script with @on or @at runs on an event: the editor's long-press on Run.
                if (runWith) add(SheetAction("Run with…", R.drawable.ic_tune) { runWithOpen = true })
                add(SheetAction("Edit", R.drawable.ic_edit, run = onEdit))
                add(SheetAction("Export", R.drawable.ic_download, run = onExport))
            }
        )

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Enabled",
            when {
                info.isScreen -> "Runs from the prompt, when you type its name"
                isCommand -> "Runs on its timers and events, and from the prompt"
                else -> "Runs on its timers and events"
            },
            s.enabled,
        ) { onEnabled(it) }

        // What the script may use, in this order:
        // - internet and the pinned header;
        // - notifications, if its code posts them;
        // - what it declares with @uses.
        // The events of its @on lines follow in a separate group.
        val (events, uses) = access.partition { it in ScriptMeta.EVENTS }
        if (uses.isNotEmpty() || notifies) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Access")
            Spacer(Modifier.height(4.dp))
            val (base, declared) = uses.partition { it == ScriptAccess.INTERNET || it == ScriptAccess.HEADER }
            base.forEach { item ->
                SwitchRow(
                    ScriptAccess.label(item),
                    ScriptAccess.help(item),
                    grants.allows(item)
                ) { vm.scriptActions.setScriptAccess(s, item, it) }
            }
            if (notifies) SwitchRow(
                "Post notifications",
                "Lets the script notify you",
                s.notify
            ) { vm.scriptActions.setScriptNotify(s, it) }
            declared.forEach { item ->
                SwitchRow(
                    ScriptAccess.label(item),
                    ScriptAccess.help(item),
                    grants.allows(item)
                ) { vm.scriptActions.setScriptAccess(s, item, it) }
            }
            // The internet together with private data: it goes as soon as either side is switched off.
            PrivateOnlineWarning(info.privateOnline, Modifier.padding(top = 8.dp))
        }
        if (events.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Events")
            Spacer(Modifier.height(4.dp))
            events.forEach { item ->
                if (item == "notification" && eventApps.isNotEmpty()) {
                    NotificationAppsRow(eventApps, grants.allows(item)) { vm.scriptActions.setScriptAccess(s, item, it) }
                } else SwitchRow(
                    ScriptAccess.label(item),
                    if (item == "notification" && notificationApps != null) {
                        ScriptAccess.notificationHelp(eventApps.map { it.name })
                    } else ScriptAccess.help(item),
                    grants.allows(item)
                ) { vm.scriptActions.setScriptAccess(s, item, it) }
            }
        }

        // What it gives shaders with @gives, and which of their parameters follow it.
        val gives = remember(s.code) { ScriptMeta.gives(s.code) }
        if (gives.isNotEmpty()) {
            val followers by produceIo(emptyMap<String, List<String>>(), s.name, what = "Reading what follows a script") {
                vm.shaderActions.followers(s.name)
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("Gives shaders")
            gives.forEach { name ->
                Text(
                    "$name · " + (followers[name]?.joinToString()?.let { "followed by $it" } ?: "nothing follows it"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Keys and tokens it declares with @secret. The user sets them here, never in the code.
        if (secrets.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Secrets")
            Text(
                "Keys and tokens it needs. Kept encrypted on this phone, never in its code, backups or the log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            secrets.forEach { d ->
                SecretRow(d, isSet = d.name in secretsSet) {
                    editingSecret = d
                }
            }
        }

        // Settings it declares with @option. The user changes them here, not in the code, so
        // updates to the script keep them.
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Options")
            Text(
                "Settings it reads as it runs. Values set here are kept when the script updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            options.forEach { o ->
                OptionRow(
                    o,
                    optionValues.first[o.name] ?: o.default,
                    changed = o.name in optionValues.second
                ) { editingOption = o }
            }
        }

        // A script with a screen of its own runs only when you start it, so it is offered no timer.
        if (!info.isScreen) {
            Spacer(Modifier.height(16.dp))
            IntervalSelector(
                "Runs every",
                s.intervalMin,
                { vm.scriptActions.setScriptInterval(s, it) },
                INTERVALS
            )

            Spacer(Modifier.height(12.dp))
            TextField(
                shape = TextFieldDefaults.roundedShape,
                colors = fieldColors(),
                value = times,
                onValueChange = { times = it; timesError = false },
                label = { Text("Daily at") },
                placeholder = { Text("08:00, 18:30") },
                // Or pick a time on a clock. It is added to the times and saved at once.
                leadingIcon = {
                    LabeledIconButton(
                        R.drawable.ic_alarm,
                        "Add a time",
                        onClick = { pickingTime = true })
                },
                singleLine = true,
                isError = timesError,
                supportingText = if (timesError) {
                    { Text("Use HH:mm, separated by commas") }
                } else null,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    timesError = !vm.scriptActions.setScriptTimes(s, times)
                }),
                trailingIcon = if (times != s.atTimes.joinToString(", ")) {
                    {
                        LabeledIconButton(
                            R.drawable.ic_check,
                            "Set times",
                            onClick = { timesError = !vm.scriptActions.setScriptTimes(s, times) })
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) applyTimes.value() }
                    .testTag("daily-at"),
            )

            if (pickingTime) {
                val clock = rememberTimePickerState(
                    initialHour = 8,
                    initialMinute = 0,
                    is24Hour = vm.config[Keys.CLOCK_24H]
                )
                // The vibrant dialog has no title of its own: its title is the first line of its content.
                VibrantTimePickerDialog(
                    onDismissRequest = { pickingTime = false },
                    confirmButton = {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                            val picked = "%02d:%02d".format(clock.hour, clock.minute)
                            // Add it to the current text in the box, saved or not.
                            times =
                                (times.split(',', ' ').filter { it.isNotBlank() } + picked).distinct()
                                    .sorted().joinToString(", ")
                            timesError = !vm.scriptActions.setScriptTimes(s, times)
                            pickingTime = false
                        }) { Text("Add") }
                    },
                    dismissButton = {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = { pickingTime = false }) { Text("Cancel") }
                    },
                ) {
                    DialogBars()
                    Text(
                        "Daily at",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                    TimePicker(clock, shapes = TimePickerDefaults.shapes())
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (copy != null && copy.canRestore) {
                if (copy.lib != null) CompareButton { copyDialog = CopyDialog.COMPARE }
                // Update or Reset to the source's version. An edited copy asks first; an unedited one changes at once.
                CopyButton(copy) { restoreCopy(vm, s, copy) { copyDialog = CopyDialog.CONFIRM } }
            }
            ErrorTextButton(onClick = { confirmDelete = true }) {
                IconLabel(R.drawable.ic_delete, "Delete")
            }
        }
    }

    editingSecret?.let { d ->
        SecretDialog(
            s.name,
            d,
            d.name in secretsSet,
            onDismiss = { editingSecret = null }) { value ->
            editingSecret = null
            vm.scriptActions.setSecret(s.name, d, value)
        }
    }

    editingOption?.let { o ->
        OptionDialog(
            s.name,
            o,
            optionValues.first[o.name] ?: o.default,
            changed = o.name in optionValues.second,
            onDismiss = { editingOption = null }) { value ->
            editingOption = null
            vm.scriptActions.setOption(s.name, o.name, value)
        }
    }

    if (runWithOpen) {
        RunWithDialog(s.code, onDismiss = { runWithOpen = false }, material = true) { args, event ->
            runWithOpen = false
            vm.scriptActions.runScriptNow(s, args, event)
        }
    }

    if (renaming) {
        val all by vm.scriptList.collectAsStateWithLifecycle()
        RenameDialog(s, all.mapTo(HashSet()) { it.name }, onDismiss = { renaming = false }) { to ->
            renaming = false
            vm.scriptActions.renameScript(s, to)
        }
    }

    CopyDialogs(vm, s, copy, copyDialog, onShow = { copyDialog = it }, onExport = onExport)

    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${s.name}.${s.lang.ext}?",
            "Its timers, stored data and status line are removed too. Export it first to keep a copy.",
            "Delete",
            icon = R.drawable.ic_delete,
            onDismiss = { confirmDelete = false },
        ) { confirmDelete = false; deleted = true; onDismiss(); vm.scriptActions.deleteScript(s) }
    }
}

/** One option on a script's sheet: its name, what it is for, and its value (muted while it is the default). */
@Composable
private fun OptionRow(
    option: ScriptMeta.Option,
    value: String,
    changed: Boolean,
    onClick: () -> Unit
) {
    ValueRow(
        option.name,
        option.desc,
        value.ifEmpty { "—" },
        if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        // A value can be long (an address, a list): it gets one line, and the name keeps the rest.
        valueMaxWidth = 160.dp,
        onClick = onClick,
    )
}

/** One secret on a script's sheet: its name, what it is for, where it is sent, and whether it is set. */
@Composable
private fun SecretRow(secret: ScriptMeta.Secret, isSet: Boolean, onClick: () -> Unit) {
    ValueRow(
        secret.name,
        listOf(
            secret.desc,
            "sent only to ${secret.hosts.joinToString()}"
        ).filter { it.isNotBlank() }.joinToString(" · "),
        if (isSet) "Set" else "Not set",
        if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        onClick = onClick,
    )
}

/**
 * An app a script's `@on notification` line names: its [pkg], its [name] (its package when not
 * installed), and the personal profile's [app] when installed.
 */
private class EventApp(val pkg: String, val name: String, val app: AppEntry?)

/**
 * The notification event's switch, when its `@on notification` names apps: a first line, then a line
 * for each app in the header's order, its icon and its name. An app that is not installed is its
 * package, with the icon's place left empty, so every name starts at the same edge.
 * - The icon is half again the text's size, as the app chips' icons are, so it grows with the font
 *   size. Its place is a quarter wider than the icon: the gap before the name.
 * - A long name stays on its line, cut with an ellipsis, and the switch keeps its place at the row's end.
 * - A screen reader hears the row as one statement: the first line, then the names. The icons say nothing.
 */
@Composable
private fun NotificationAppsRow(apps: List<EventApp>, checked: Boolean, onChange: (Boolean) -> Unit) {
    val icon = with(LocalDensity.current) { (MaterialTheme.typography.bodySmall.fontSize * 1.5f).toDp() }
    SwitchRow(ScriptAccess.label("notification"), checked, onChange) {
        // A gap between the lines: the icons are taller than the text and would touch each other.
        Column(verticalArrangement = Arrangement.spacedBy(APP_LINE_GAP)) {
            Text(ScriptAccess.NOTIFICATIONS_FROM_APPS)
            apps.forEach { a ->
                Row(Modifier.testTag("$EVENT_APP_LINE_TAG ${a.pkg}"), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(icon * 1.25f, icon), contentAlignment = Alignment.CenterStart) {
                        a.app?.let { AppIcon(it, icon, Modifier.testTag("$EVENT_APP_ICON_TAG ${a.pkg}")) }
                    }
                    Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The test tags of an app's line and its icon on the notification event's switch ([NotificationAppsRow]), before a space and its package. */
/** The room between two apps' lines on the notification event's switch, and under its first line. */
private val APP_LINE_GAP = 6.dp

internal const val EVENT_APP_LINE_TAG = "event-app-line"
internal const val EVENT_APP_ICON_TAG = "event-app-icon"

/** One of the main things to do with a script, on its sheet. [filled]: Run, in the theme's color. */
private class SheetAction(val label: String, val icon: Int, val filled: Boolean = false, val run: () -> Unit)

/**
 * [actions] in an Expressive button group, as on a shader's sheet: the pressed one widens, and its
 * neighbors give way. Each has its icon and its word. Where the words don't fit on the line
 * ([wordsFit]: four of them on a phone), each is its icon alone, and says its word in a bubble on a
 * long-press and to a screen reader.
 */
@Composable
private fun SheetActions(actions: List<SheetAction>) {
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
                        colors = if (a.filled) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = PaddingValues(horizontal = WORD_PADDING),
                        interactionSource = source,
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(source),
                    ) {
                        Icon(painterResource(a.icon), null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(WORD_GAP))
                        Text(a.label, maxLines = 1, softWrap = false)
                    } else Box(
                        Modifier
                            .weight(1f)
                            .animateWidth(source)
                    ) {
                        // The bubble's own box takes no weight from the group: the box around it does.
                        Labeled(a.label, Modifier.fillMaxWidth()) {
                            FilledTonalIconButton(
                                onClick = a.run,
                                shapes = IconButtonDefaults.shapes(),
                                colors = if (a.filled) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.filledTonalIconButtonColors(),
                                interactionSource = source,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Icon(painterResource(a.icon), a.label, Modifier.size(ButtonDefaults.IconSize)) }
                        }
                    }
                },
                menuContent = { state ->
                    DropdownMenuItem(text = { Text(a.label) }, onClick = { a.run(); state.dismiss() })
                },
            )
        }
    }
}

/** The padding at each end of a button with its word, and the room between its icon and the word. */
private val WORD_PADDING = 8.dp
private val WORD_GAP = 6.dp
