package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/** What each colour role is called in the builder, and what it colours. */
private val ROLE_TEXT = mapOf(
    "background" to ("Background" to "Behind everything"),
    "surface" to ("Surface" to "Chips, the key bar and raised panels"),
    "text" to ("Text" to "Commands and their output"),
    "muted" to ("Muted text" to "Times, hints and older output"),
    "accent" to ("Accent" to "The prompt, $ and highlights; Settings use it too"),
    "link" to ("Links" to "Links and feed items"),
    "info" to ("Info" to "Messages from Kolahal"),
    "script" to ("Script output" to "Lines your scripts write"),
    "error" to ("Errors" to "Errors and failed commands"),
    "good" to ("Good" to "Script success states: fmt.color(\"good\", …)"),
    "warn" to ("Warning" to "Script warnings: fmt.color(\"warn\", …)"),
    "personal" to ("Personal apps" to "Your own apps"),
    "work" to ("Work apps" to "Apps in a work profile, marked [W]"),
    "private" to ("Private space" to "Apps in the private space, marked [S]"),
)

/**
 * Settings → Look & feel → Your themes: make a theme, or change one made before
 * ([TerminalViewModel.themeBuilderName]). A small home screen at the top shows the colours as they
 * change; saving uses the theme.
 */
@Composable
fun ThemeBuilderScreen(vm: TerminalViewModel) {
    val context = LocalContext.current
    val editing = remember { vm.themeBuilderName.value }
    // The draft: both versions of the theme being changed, or of the theme in use. One of yours
    // made before themes had a light version starts with the light one the same as the dark.
    val draft = remember {
        mutableStateMapOf<String, Int>().apply {
            val start = editing?.let(CustomThemes::colors) ?: Palettes.themeColors(vm.config[Keys.THEME])
            putAll(start)
            if (CustomThemes.light(start) == null) CustomThemes.ROLES.forEach { put(CustomThemes.LIGHT + it, start.getValue(it)) }
        }
    }
    // Which version the colours below change; the preview shows it.
    var light by remember { mutableStateOf(false) }
    fun keyOf(role: String) = if (light) CustomThemes.LIGHT + role else role
    // Per visit, like the draft: the screen's saved state outlives a visit, and a name from the
    // last one would rename the theme being edited.
    var name by remember { mutableStateOf(editing ?: newName()) }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameError = when {
        !CustomThemes.NAME.matches(name) -> "Lowercase letters, digits and hyphens, up to 24"
        name in Keys.BUILT_IN_THEMES -> "A built-in theme has this name"
        name != editing && CustomThemes.colors(name) != null -> "You already have a theme with this name"
        else -> null
    }
    BackHandler { vm.closeScreen() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        editing?.let { vm.exportTheme(it, uri) }
    }
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it.text) } }

    GuiTheme {
        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { Text(if (editing == null) "New theme" else "Edit theme") },
                    navigationIcon = { BackButton(vm::closeScreen) },
                    actions = {
                        if (editing != null) {
                            // As saved: changes not saved yet stay out of the file.
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { export.launch("$editing.json") }) { Icon(painterResource(R.drawable.ic_download), "Export theme") }
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), "Delete theme") }
                        }
                    },
                )
            },
            bottomBar = {
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        CustomThemes.save(context, name, draft.toMap())
                        // Renamed: the old file goes, and the new name is used.
                        if (editing != null && editing != name) CustomThemes.delete(context, editing)
                        vm.setConfig(Keys.THEME, name)
                        vm.closeScreen()
                    },
                    enabled = nameError == null,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text("Save and apply") }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                ThemePreview(
                    Palettes.fromColors(if (light) CustomThemes.light(draft) ?: draft else draft),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                )
                // Every theme has a dark and a light version: the phone's dark mode picks one.
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ConnectedChoices(listOf("Dark", "Light"), if (light) "Light" else "Dark", tagPrefix = "version ") { light = it == "Light" }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    item(key = "name") {
                        TextField(
                            shape = TextFieldDefaults.roundedShape,
                            colors = fieldColors(),
                            value = name,
                            onValueChange = { name = it.lowercase().replace(' ', '-') },
                            label = { Text("Name") },
                            singleLine = true,
                            isError = nameError != null,
                            supportingText = { Text(nameError ?: "Command: theme $name") },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("theme-name"),
                        )
                    }
                    item(key = "start") {
                        GroupHeader("Start from")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (Keys.BUILT_IN_THEMES + CustomThemes.names.filter { it != editing }).forEach { other ->
                                val p = Palettes.named(other, dark = !light)
                                // Both versions: a theme's dark and light colours go together.
                                FilledTonalButton(
                                    shapes = ButtonDefaults.shapes(),
                                    onClick = { draft.putAll(Palettes.themeColors(other)) },
                                ) {
                                    Box(Modifier.size(16.dp).background(p.prompt, CircleShape).border(3.dp, p.bg, CircleShape))
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(other)
                                }
                            }
                        }
                    }
                    item(key = "h:colours") { GroupHeader("Colours") }
                    items(CustomThemes.ROLES, key = { it }) { role ->
                        val (title, help) = ROLE_TEXT.getValue(role)
                        val colour = draft.getValue(keyOf(role))
                        val index = CustomThemes.ROLES.indexOf(role)
                        Box(Modifier.padding(bottom = androidx.compose.material3.ListItemDefaults.SegmentedGap)) {
                            SegmentedListItem(
                                onClick = { picking = role },
                                colors = segColors(),
                                shapes = segShapes(index, CustomThemes.ROLES.size),
                                leadingContent = { Swatch(Color(colour), 36.dp) },
                                supportingContent = { Text(help) },
                                trailingContent = {
                                    Text(CustomThemes.hex(colour), style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace))
                                },
                                modifier = Modifier.testTag("role $role"),
                            ) { Text(title) }
                        }
                    }
                    item(key = "end") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        picking?.let { role ->
            val key = keyOf(role)
            // The same role in every built-in theme (this version first), then the draft's other colours: easy to match.
            val swatches = remember(key) {
                (Keys.BUILT_IN_THEMES.map { Palettes.colorsOf(Palettes.named(it, dark = !light)).getValue(role) } +
                    Keys.BUILT_IN_THEMES.map { Palettes.colorsOf(Palettes.named(it, dark = light)).getValue(role) } + draft.values).distinct()
            }
            androidx.compose.runtime.key(key) {
                ColourSheet(role, draft.getValue(key), swatches, onChange = { draft[key] = it }, onDismiss = { picking = null })
            }
        }

        if (confirmDelete && editing != null) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                icon = { Icon(painterResource(R.drawable.ic_delete), null) },
                title = { Text("Delete $editing?") },
                text = { Text("If it is in use, Kolahal switches to the green theme.") },
                confirmButton = {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            val inUse = vm.config[Keys.THEME] == editing
                            CustomThemes.delete(context, editing)
                            if (inUse) vm.resetConfig(Keys.THEME)
                            vm.closeScreen()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

/** "my-theme", or "my-theme-2" and so on when that is taken. */
private fun newName(): String =
    generateSequence(1) { it + 1 }.map { if (it == 1) "my-theme" else "my-theme-$it" }.first { CustomThemes.colors(it) == null }

@Composable
private fun Swatch(colour: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(colour)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
    )
}

/** A small home screen in [p]'s colours: the header's line, a command and its output, every kind of line, chips and the prompt. */
@Composable
fun ThemePreview(p: Palette, modifier: Modifier = Modifier) {
    val mono = Term.fontFamily
    val line = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 17.sp)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.bg)
            .border(1.dp, p.surface, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("theme-preview"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The header's line: the time and date, the battery at the end.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("9:41", style = line.copy(color = p.fg, fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(8.dp))
            Text("Thu 22 Oct", style = line.copy(color = p.dim))
            Spacer(Modifier.weight(1f))
            BatteryGlyph(82, charging = false, color = p.fg, height = 12.dp)
            Spacer(Modifier.width(3.dp))
            Text("82%", style = line.copy(color = p.fg, fontWeight = FontWeight.Bold))
        }
        Row {
            Box(Modifier.width(2.dp).height(34.dp).background(p.prompt))
            Spacer(Modifier.width(8.dp))
            Column {
                Row {
                    Text("$ ", style = line.copy(color = p.prompt, fontWeight = FontWeight.Bold))
                    Text("= 5 km in mi", style = line.copy(color = p.fg, fontWeight = FontWeight.Bold))
                }
                Text("5 km = 3.1069 mi", style = line.copy(color = p.fg))
            }
        }
        Text("Show HN: a terminal launcher · Hacker News", style = line.copy(color = p.link), maxLines = 1)
        Text("weather · Pune 27°C", style = line.copy(color = p.script))
        Text("3 feeds refreshed", style = line.copy(color = p.info))
        Text("Command not found: fo", style = line.copy(color = p.error))
        Text("Older output, dimmed · 9:30", style = line.copy(color = p.dim))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Maps" to p.personal, "W Gmail" to p.work, "S Bank" to p.private).forEach { (label, colour) ->
                Text(label, style = line.copy(color = colour), modifier = Modifier.background(p.surface, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", style = line.copy(color = p.prompt, fontWeight = FontWeight.Bold, fontSize = 14.sp))
            Text("ma", style = line.copy(color = p.fg, fontSize = 14.sp))
            Box(Modifier.width(2.dp).height(16.dp).background(p.prompt))
        }
    }
}

/**
 * Picks [colour] for [role]: hue, saturation and lightness, a hex code, or a swatch. Changes go
 * out at once ([onChange]) and nothing dims the screen, so the preview above shows them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColourSheet(role: String, colour: Int, swatches: List<Int>, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val start = remember(role) { FloatArray(3).also { ColorUtils.colorToHSL(colour, it) } }
    var h by remember(role) { mutableFloatStateOf(start[0]) }
    var s by remember(role) { mutableFloatStateOf(start[1]) }
    var l by remember(role) { mutableFloatStateOf(start[2]) }
    var hex by remember(role) { mutableStateOf(CustomThemes.hex(colour)) }
    fun fromSliders() {
        val c = ColorUtils.HSLToColor(floatArrayOf(h, s, l))
        hex = CustomThemes.hex(c)
        onChange(c)
    }
    fun take(c: Int, typed: Boolean = false) {
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(c, it) }
        h = hsl[0]; s = hsl[1]; l = hsl[2]
        if (!typed) hex = CustomThemes.hex(c)
        onChange(c)
    }
    val (title, help) = ROLE_TEXT.getValue(role)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(Color(colour), 40.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            GradientSlider("Hue", h, 0f..360f, Brush.horizontalGradient((0..6).map { Color.hsl(it * 60f, 1f, 0.5f) })) { h = it; fromSliders() }
            GradientSlider("Saturation", s, 0f..1f, Brush.horizontalGradient(listOf(Color.hsl(h, 0f, l), Color.hsl(h, 1f, l)))) { s = it; fromSliders() }
            GradientSlider("Lightness", l, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, Color.hsl(h, s, 0.5f), Color.White))) { l = it; fromSliders() }
            TextField(
                shape = TextFieldDefaults.roundedShape,
                colors = fieldColors(),
                value = hex,
                onValueChange = { typed -> hex = typed; CustomThemes.parseHex(typed)?.let { take(it, typed = true) } },
                label = { Text("Hex") },
                singleLine = true,
                isError = CustomThemes.parseHex(hex) == null,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.forEach { c ->
                    Box(Modifier.clip(CircleShape).clickable { take(c) }) { Swatch(Color(c), 32.dp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(shapes = ButtonDefaults.shapes(), onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
        }
    }
}

/** A labelled slider drawn over [brush], so the track shows what moving it does. */
@Composable
private fun GradientSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, brush: Brush, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val state = remember(label) { SliderState(value, 0, range) }
    state.value = value
    Slider(
        state = state,
        onValueChange = onChange,
        track = { Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(brush)) },
    )
}
