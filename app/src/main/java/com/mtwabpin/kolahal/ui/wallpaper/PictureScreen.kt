/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.terminal.PictureRequest
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.ConnectedChoices
import com.mtwabpin.kolahal.ui.DialogBars
import com.mtwabpin.kolahal.ui.ErrorTextButton
import com.mtwabpin.kolahal.ui.GroupHeader
import com.mtwabpin.kolahal.ui.GuiListPage
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.shaders.ShaderParamRows
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

/**
 * Lab's picture composer ([com.mtwabpin.kolahal.data.Lab.Feature.PICTURE]): a still picture of the scene asked for
 * ([PictureRequest]), as large as the phone can draw, to save to the gallery, share or set as the wallpaper.
 * Nothing shows on an Android older than 13: shaders need it, and no way in is offered there.
 */
@Composable
fun PictureScreen(vm: TerminalViewModel) {
    val name by PictureRequest.shader.collectAsStateWithLifecycle()
    val shader = name
    if (shader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(Unit) { vm.closeScreen() }
        return
    }
    GuiTheme {
        PictureComposer(shader, vm.config, onBack = vm::closeScreen) {
            vm.closeScreen()
            vm.openShaderEditor(shader)
        }
    }
}

/** What keeps a picture from being made ([PictureComposer]'s error), in the words it shows. */
internal sealed class PictureProblem(val text: String) {
    data object TooLarge : PictureProblem("This phone can't draw a picture this large\nPick a smaller size")
    data object NoMemory : PictureProblem("There isn't enough memory for a picture this large\nUse 2K")
    class Compile(why: String) : PictureProblem("The shader doesn't compile\n${why.replaceFirstChar(Char::uppercase)}")
    class Failed(why: String) : PictureProblem("The picture couldn't be made\n${why.replaceFirstChar(Char::uppercase)}")
}

/** The picture Kolahal saved last ([uri]), and what it was made with ([key]): Share and Set as wallpaper use it while nothing changed. */
private class Saved(val uri: Uri, val key: Any)

/** The message after a save, with Open and Undo. */
private class SavedVisuals(val uri: Uri) : SnackbarVisuals {
    override val message = "Saved to Pictures/${PictureFiles.FOLDER}"
    override val actionLabel = "Undo"
    override val withDismissAction = false
    override val duration = SnackbarDuration.Long
}

/** The question before the app sets the wallpaper itself, where the phone has no screen of its own for it. */
private class Asking(val uri: Uri, val live: Boolean)

/**
 * The composer (C1 in the drafts): the picture at the top in the phone's proportions, drawn live as the controls
 * change (a tap shows it on the whole screen); under it, scrolling, the scene's parameters (the picture's own:
 * home's stay as they are), Frame, Time of day and Arrangement where the scene has them, Dark or Light, and the size;
 * Save to gallery and Set as wallpaper pinned at the bottom, Share and Full screen in the top bar. While the picture is
 * drawn at full size the buttons wait, and Back cancels it.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PictureComposer(name: String, config: Config, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val version by config.version.collectAsStateWithLifecycle()
    val code by produceState<String?>(null, name) { value = withContext(Dispatchers.IO) { Shaders.source(app, name) } }
    val meta = remember(code) { code?.let(ShaderMeta::parse) }
    // The preview's shader, compiled off the main thread. A failure is the compiler's error, with Edit.
    val compiled by produceState<Result<PictureShader>?>(null, code) {
        val c = code ?: return@produceState
        value = withContext(Dispatchers.Default) { PictureShader.compile(c) }
    }
    val maxSide by produceState<Int?>(null) { value = withContext(Dispatchers.Default) { PictureRender.maxSide() } }
    // The whole screen, as the wallpaper fills it, in pixels.
    val screen = remember { context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds }
    val cell = remember(version) { cellOf(context, config) }
    val systemDark = isSystemInDarkTheme()

    var dark by remember(name) { mutableStateOf(config[Keys.THEME_ALWAYS_DARK] || systemDark) }
    var time by remember(name) { mutableFloatStateOf(-1f) }
    LaunchedEffect(meta) { if (meta != null && time < 0f) time = PictureMath.startTime(meta) }
    var minute by remember(name) { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    var seed by remember(name) { mutableIntStateOf(0) }
    var sizeId by remember(name) { mutableStateOf<String?>(null) }
    val params = remember(name) { PictureParams(app, name) { LocalTime.of(minute / 60, minute % 60) } }
    val set by params.values.collectAsStateWithLifecycle()
    val follows by params.follows.collectAsStateWithLifecycle()
    val formulas by params.formulas.collectAsStateWithLifecycle()
    val phone = remember { ShaderActions.phone(app) }
    val theme = remember(version) { config[Keys.THEME] }
    val colors = remember(theme, dark) { WallpaperWriter.colors(Palettes.named(theme, dark)) }
    val look = remember(meta, set, follows, formulas, time, minute, dark, seed, colors, cell) {
        meta?.let { m ->
            PictureLook(
                colors, dark, params.now(m.params), time.coerceAtLeast(0f), PictureMath.date(LocalDate.now(), minute),
                seed.toFloat(), screen.width(), cell.first, cell.second, phone.battery, phone.charging,
            )
        }
    }
    val sizes = remember(maxSide) { PictureMath.sizes(screen.width(), screen.height(), maxSide) }
    val size = sizes.firstOrNull { it.id == sizeId && it.why == null } ?: PictureMath.firstSize(sizes)

    var job by remember { mutableStateOf<Job?>(null) }
    var problem by remember { mutableStateOf<PictureProblem?>(null) }
    var saved by remember { mutableStateOf<Saved?>(null) }
    var full by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<Asking?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val say: (String) -> Unit = { text ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    val preview = compiled?.getOrNull()
    val compileError = compiled?.exceptionOrNull()?.message?.let { PictureProblem.Compile(it) }
    val shown = compileError ?: problem
    val busy = job != null
    val ready = look != null && preview != null && size != null && code != null && !busy

    // Draws the picture at full size, off the main thread, and hands it to [then] with what it was made of.
    fun draw(then: suspend (Bitmap, Any) -> Unit) {
        val c = code ?: return
        val l = look ?: return
        val s = size ?: return
        if (job != null) return
        problem = null
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val r = PictureRender.draw(c, l, s.width, s.height) { !isActive }
                    if (!isActive && r is PictureResult.Drawn) {
                        r.bitmap.recycle()
                        PictureResult.Failed("cancelled")
                    } else r
                }
                when (result) {
                    is PictureResult.Drawn -> try {
                        then(result.bitmap, l to s)
                    } finally {
                        result.bitmap.recycle()
                    }
                    PictureResult.TooLarge -> problem = PictureProblem.TooLarge
                    PictureResult.OutOfMemory -> problem = PictureProblem.NoMemory
                    is PictureResult.Failed -> problem = PictureProblem.Failed(result.why)
                }
            } finally {
                job = null
            }
        }
    }

    // The picture as an address another app can read: the one saved, while nothing changed, or a new one.
    fun withPicture(use: (Uri) -> Unit) {
        val l = look ?: return
        val s = size ?: return
        saved?.takeIf { it.key == (l to s) }?.let { return use(it.uri) }
        draw { bitmap, _ ->
            val file = PictureMath.fileName(name, LocalDateTime.now())
            withContext(Dispatchers.IO) { runCatching { PictureFiles.cache(app, bitmap, file) } }
                .onSuccess(use)
                .onFailure { say("Couldn't write the picture") }
        }
    }

    val onSave: () -> Unit = {
        draw { bitmap, key ->
            val file = PictureMath.fileName(name, LocalDateTime.now())
            withContext(Dispatchers.IO) { runCatching { PictureFiles.save(app, bitmap, file) } }
                .onSuccess { uri ->
                    saved = Saved(uri, key)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        if (snackbar.showSnackbar(SavedVisuals(uri)) == SnackbarResult.ActionPerformed) {
                            val gone = withContext(Dispatchers.IO) { PictureFiles.delete(app, uri) }
                            if (saved?.uri == uri) saved = null
                            say(if (gone) "Picture deleted" else "The picture was already gone")
                        }
                    }
                }
                .onFailure { say("Couldn't save the picture") }
        }
    }
    val onShare: () -> Unit = {
        withPicture { uri ->
            if (runCatching { context.startActivity(PictureFiles.share(uri)) }.isFailure) say("No app on this phone can share it")
        }
    }
    val onSet: () -> Unit = {
        withPicture { uri ->
            // Android's own screen first, where the phone has one: it asks home screen, lock screen or both.
            val crop = PictureFiles.cropAndSet(app, uri)
            if (crop == null || runCatching { context.startActivity(crop) }.isFailure) scope.launch {
                val live = withContext(Dispatchers.IO) { WallpaperChoice.isWallpaper(app) }
                asking = Asking(uri, live)
            }
        }
    }

    BackHandler {
        val running = job
        if (running != null) {
            running.cancel()
            job = null
            say("Cancelled")
        } else onBack()
    }

    GuiListPage(
        title = { Text("Make a picture") },
        subtitle = { Text(meta?.title ?: name) },
        onBack = {
            job?.cancel()
            onBack()
        },
        actions = {
            LabeledIconButton(R.drawable.ic_share, "Share", onShare, enabled = ready)
            LabeledIconButton(R.drawable.ic_image, "Full screen", { full = true }, enabled = look != null && preview != null)
        },
        pinned = {
            val high = (LocalConfiguration.current.screenHeightDp * 0.36f).dp
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                if (preview != null && look != null) PicturePreview(
                    preview, look,
                    Modifier
                        .height(high)
                        .aspectRatio(screen.width().toFloat() / screen.height().coerceAtLeast(1))
                        .clip(MaterialTheme.shapes.large)
                        .clickable { full = true }
                        .semantics {
                            contentDescription = "Picture of ${meta?.title ?: name}"
                            role = Role.Button
                            onClick(label = "Full screen") { full = true; true }
                        }
                        .testTag("picture-preview"),
                ) else Box(
                    Modifier
                        .height(high)
                        .aspectRatio(screen.width().toFloat() / screen.height().coerceAtLeast(1))
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SnackbarHost(snackbar) { data -> PictureSnackbar(data.visuals, { data.dismiss() }, { data.performAction() }, context) }
                if (busy) {
                    LinearWavyProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Drawing the picture" },
                    )
                    Text(
                        "Drawing the picture. Back cancels it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val height = ButtonDefaults.MediumContainerHeight
                    FilledTonalButton(
                        onClick = onSet,
                        shapes = ButtonDefaults.shapesFor(height),
                        enabled = ready,
                        contentPadding = ButtonDefaults.contentPaddingFor(height),
                        modifier = Modifier.weight(1f).heightIn(height).testTag("picture-set"),
                    ) { Text("Set as wallpaper", style = ButtonDefaults.textStyleFor(height), maxLines = 1) }
                    Button(
                        onClick = onSave,
                        shapes = ButtonDefaults.shapesFor(height),
                        enabled = ready,
                        contentPadding = ButtonDefaults.contentPaddingFor(height),
                        modifier = Modifier.weight(1f).heightIn(height).testTag("picture-save"),
                    ) { Text("Save to gallery", style = ButtonDefaults.textStyleFor(height), maxLines = 1) }
                }
            }
        },
    ) {
        shown?.let { p -> problemItem(p, onEdit.takeIf { p is PictureProblem.Compile }) }
        val m = meta ?: return@GuiListPage
        if (m.params.isNotEmpty()) {
            item(key = "h:params") { GroupHeader("Parameters") }
            item(key = "params") { ShaderParamRows(name, m.params, params) }
        }
        if (PictureMath.hasFrame(m)) {
            item(key = "h:frame") { GroupHeader("Frame") }
            item(key = "frame") {
                ValueSlider(
                    "Frame", "%.1f s".format(time.coerceAtLeast(0f)), time.coerceAtLeast(0f), 0f..PictureMath.MAX_TIME,
                    steps = 0, tag = "picture-frame",
                ) { time = it }
            }
        }
        if (PictureMath.hasHour(m, m.params.map { ShaderParams.follow(follows[name].orEmpty(), it) })) {
            item(key = "h:hour") { GroupHeader("Time of day") }
            item(key = "hour") {
                // Five minutes a step.
                ValueSlider(
                    "Time of day", PictureMath.clock(minute), minute.toFloat(), 0f..(PictureMath.MINUTES - 5).toFloat(),
                    steps = PictureMath.MINUTES / 5 - 2, tag = "picture-hour",
                ) { minute = (Math.round(it / 5f) * 5).coerceIn(0, PictureMath.MINUTES - 5) }
            }
        }
        if (PictureMath.hasSeed(m)) {
            item(key = "h:seed") { GroupHeader("Arrangement") }
            item(key = "seed") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (seed == 0) "The scene's own" else "Arrangement $seed",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = { seed = Random.nextInt(1, PictureMath.MAX_SEED + 1) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.testTag("picture-shuffle"),
                    ) {
                        Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Shuffle")
                    }
                }
            }
        }
        item(key = "h:look") { GroupHeader("Look") }
        item(key = "look") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                ConnectedChoices(listOf(DARK, LIGHT), if (dark) DARK else LIGHT, tagPrefix = "picture-look ", label = { it.replaceFirstChar(Char::uppercase) }) {
                    dark = it == DARK
                }
            }
        }
        item(key = "h:size") { GroupHeader("Size") }
        item(key = "size") {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                sizes.forEachIndexed { i, s ->
                    SegmentedListItem(
                        selected = s.id == size?.id,
                        onClick = { sizeId = s.id },
                        shapes = segShapes(i, sizes.size),
                        enabled = s.why == null,
                        colors = segColors(),
                        leadingContent = { RadioButton(selected = s.id == size?.id, onClick = null, enabled = s.why == null) },
                        supportingContent = { Text(s.why ?: s.pixels) },
                        modifier = Modifier.testTag("picture-size ${s.id}"),
                    ) { Text(s.label) }
                }
            }
        }
    }

    if (full && preview != null && look != null) Dialog(
        onDismissRequest = { full = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogBars()
        PicturePreview(
            preview, look,
            Modifier
                .fillMaxSize()
                .clickable { full = false }
                .semantics { contentDescription = "Picture of ${meta?.title ?: name}, full screen. Tap to close" }
                .testTag("picture-full"),
        )
    }

    asking?.let { a ->
        var where by remember(a) { mutableStateOf(PictureFiles.Where.BOTH) }
        AlertDialog(
            onDismissRequest = { asking = null },
            icon = { Icon(painterResource(R.drawable.ic_wallpaper), null) },
            title = { DialogBars(); Text(PictureMath.QUESTION) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(PictureMath.replaces(a.live))
                    ConnectedChoices(PictureFiles.Where.entries.map { it.name }, where.name, tagPrefix = "picture-where ",
                        label = { PictureFiles.Where.valueOf(it).label }) { where = PictureFiles.Where.valueOf(it) }
                }
            },
            confirmButton = {
                ErrorTextButton(onClick = {
                    asking = null
                    scope.launch {
                        val done = withContext(Dispatchers.IO) { PictureFiles.setWallpaper(app, a.uri, where) }
                        say(if (done) "Wallpaper set" else "Couldn't change the wallpaper on this phone")
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { asking = null }) { Text("Cancel") } },
        )
    }
}

private const val DARK = "dark"
private const val LIGHT = "light"

/** The text cell's size on this screen, as the live wallpaper's writer works it out ([WallpaperState.cell]). */
private fun cellOf(context: Context, config: Config): Pair<Float, Float> {
    val metrics = context.resources.displayMetrics
    fun px(size: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), metrics)
    return WallpaperState.cell(px(config[Keys.FONT_SIZE]), px(config[Keys.LOG_LINE_HEIGHT]))
}

/** The scene drawn as [look] says, at this box's own size: a smaller copy of the picture. Nothing where Android refuses it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PicturePreview(shader: PictureShader, look: PictureLook, modifier: Modifier) {
    val failed = remember(shader) { booleanArrayOf(false) }
    Box(
        modifier.drawBehind {
            if (failed[0] || size.width < 1f || size.height < 1f) return@drawBehind
            drawIntoCanvas { canvas ->
                try {
                    shader.draw(canvas.nativeCanvas, size.width.toInt(), size.height.toInt(), look)
                } catch (_: RuntimeException) {
                    failed[0] = true
                }
            }
        },
    )
}

/**
 * A control over a range ([range]): its [title] and [value] on a line, and a slider under it that a screen reader
 * adjusts, hearing the value.
 */
@Composable
private fun ValueSlider(
    title: String,
    value: String,
    at: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = at.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics {
                    contentDescription = title
                    stateDescription = value
                },
        )
    }
}

/** The error under the picture, and Edit for a shader that doesn't compile ([onEdit]). */
private fun LazyListScope.problemItem(problem: PictureProblem, onEdit: (() -> Unit)?) {
    item(key = "problem") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("picture-problem")) {
            Text(problem.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            if (onEdit != null) TextButton(shapes = ButtonDefaults.shapes(), onClick = onEdit) { Text("Edit") }
        }
    }
}

/** A message in the composer: after a save, with Open and Undo ([SavedVisuals]); else the message alone. */
@Composable
private fun PictureSnackbar(visuals: SnackbarVisuals, dismiss: () -> Unit, undo: () -> Unit, context: Context) {
    if (visuals !is SavedVisuals) return Snackbar { Text(visuals.message) }
    val colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)
    Snackbar(
        action = {
            Row {
                TextButton(shapes = ButtonDefaults.shapes(), colors = colors, onClick = {
                    dismiss()
                    runCatching { context.startActivity(PictureFiles.open(visuals.uri)) }
                }) { Text("Open") }
                TextButton(shapes = ButtonDefaults.shapes(), colors = colors, onClick = undo) { Text("Undo") }
            }
        },
    ) { Text(visuals.message) }
}
