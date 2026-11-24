/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The shader files on the phone: the bundled ones in assets, and the user's own in
 * `files/shaders/<name>.agsl`, which arrive as scripts do (from the script folder, `script import`,
 * or a picked file). The user's file of a bundled name is the one used.
 * - Names load at start ([load]), so `screen.shader` accepts and completes them at once. What people set
 *   for shaders loads then too, off the main thread.
 * - What goes wrong with a shader is a line in the log. Shaders need Android 13 ([supported]).
 */
object Shaders {
    /** Android 13, the first with AGSL. */
    const val MIN_SDK = Build.VERSION_CODES.TIRAMISU

    /** What a phone with an older Android is told. */
    const val NEEDS = "Shaders need Android 13 or newer"

    val supported: Boolean get() = Build.VERSION.SDK_INT >= MIN_SDK

    /** The folder for shaders, in Kolahal's files and in the script folder. */
    const val DIR = "shaders"

    private val _version = MutableStateFlow(0)

    /** Grows when a shader file is added, changed or removed. */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Volatile
    private var mine: List<String> = emptyList()

    /** The names of the user's own shaders. Empty until [load] runs. */
    val userNames: List<String> get() = mine

    /** Every shader's name, for completion. */
    val names: List<String> get() = ShaderCatalog.list(mine).map { it.name }

    /** What people set is read with the first names; later, it is what is in memory. */
    @Volatile
    private var paramsRead = false

    /**
     * The first read of what people set and scripts gave ([load]), on a thread of [scope]: it opens the
     * database, and home's first frame has no shader in it. Null in a process that never called [load].
     */
    @Volatile
    private var settings: Lazy<Unit>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The problems already put in the log, so a redraw doesn't repeat them. */
    private val reported = ConcurrentHashMap.newKeySet<String>()

    private fun dir(context: Context) = File(context.filesDir, DIR)
    private fun file(context: Context, name: String) = File(dir(context), "$name.${ShaderCatalog.EXT}")

    /**
     * Reads the user's shader names. Called at start, before home is drawn. The first time, it also
     * reads what people set and scripts gave, off the main thread ([settings]), except the live
     * wallpaper's shader where there are shaders: the wallpaper's writer starts with home.
     */
    fun load(context: Context) {
        if (!paramsRead) {
            paramsRead = true
            val app = context.applicationContext
            if (supported) WallpaperChoice.load(app)
            readSoon({
                // A read that fails leaves that part empty, as before anything was set; home goes on.
                runCatching { ShaderWords.load(app) }
                runCatching { ShaderParams.load(app) }
                if (!supported) runCatching { WallpaperChoice.load(app) }
                runCatching { ShaderValues.load(app) }
            }) { read -> scope.launch { read() } }
        }
        mine = dir(context).list().orEmpty().mapNotNull(ShaderCatalog::nameOf).distinct().sorted()
        _version.update { it + 1 }
    }

    /**
     * Waits until the first read of what people set and scripts gave is done ([load]). If it hasn't
     * started yet, it reads them on this thread. Before a shader is read to be drawn, so a parameter
     * starts at the value it follows instead of gliding to it; and before a notification is looked at.
     */
    internal fun settingsRead() {
        settings?.value
    }

    /** Makes [read] the first read that [settingsRead] waits for, and has [start] run it on another thread. It runs once. */
    internal fun readSoon(read: () -> Unit, start: (run: () -> Unit) -> Unit) {
        val once = lazy(read)
        settings = once
        start { once.value }
    }

    /** The shaders there are, the bundled ones first. */
    fun entries(): List<ShaderCatalog.Entry> = ShaderCatalog.list(mine)

    /** Whether there is a shader [name]. */
    fun exists(name: String): Boolean = name in ShaderCatalog.BUNDLED || name in mine

    /** The code of the user's own shader [name]; null when there is none. Reads a file: not on the main thread. */
    fun own(context: Context, name: String): String? =
        runCatching { file(context, name).takeIf { name in mine && it.isFile }?.readText() }.getOrNull()

    /**
     * The code of shader [name], the user's file first; null when there is none. Reads a file: not on
     * the main thread. Everything that draws a shader reads its code here, so this waits for [settingsRead].
     */
    fun source(context: Context, name: String): String? = runCatching {
        settingsRead()
        val own = file(context, name)
        when {
            name in mine && own.isFile -> own.readText()
            name in ShaderCatalog.BUNDLED -> bundled(context, name)
            else -> null
        }
    }.getOrNull()

    /** The code of the bundled shader or template [name] ([ShaderCatalog.TEMPLATES]). */
    fun bundled(context: Context, name: String): String =
        context.assets.open("$DIR/$name.${ShaderCatalog.EXT}").use { it.readBytes().toString(Charsets.UTF_8) }

    /** Whether [name] is a template ([ShaderCatalog.TEMPLATES]) and no shader of the user's has its name. */
    fun isTemplate(name: String): Boolean = name in ShaderCatalog.TEMPLATES && name !in mine

    /**
     * Saves a copy of the template [template] as a shader of the user's, under a free name
     * ([ShaderCatalog.yourName]): none of the user's shaders has it, and [taken] doesn't say it is taken.
     * Returns its name and its code. Reads a file: not on the main thread.
     */
    fun fromTemplate(context: Context, template: String, taken: (String) -> Boolean = { false }): Pair<String, String> {
        val code = bundled(context, template)
        val name = ShaderCatalog.yourName(template) { it in mine || taken(it) }
        save(context, name, code)
        return name to code
    }

    /** Saves the user's shader [name] with code [code]. */
    fun save(context: Context, name: String, code: String) {
        require(ShaderCatalog.valid(name)) { "'$name' can't be a shader's name" }
        require(code.toByteArray().size <= ShaderMeta.MAX_BYTES) { "larger than ${ShaderMeta.MAX_BYTES / 1024} KB" }
        dir(context).mkdirs()
        file(context, name).writeText(code)
        load(context)
    }

    /**
     * Deletes the user's shader [name], and where it came from ([ShaderStore]). False when there is none.
     * One that was on is off then, unless Kolahal has one of its name: a file of the same name that
     * comes later doesn't turn itself on.
     */
    fun remove(context: Context, name: String): Boolean {
        val conf = Config.get(context)
        val on = name !in ShaderCatalog.BUNDLED && conf[Keys.SCREEN_SHADER] == name
        val gone = file(context, name).delete()
        if (gone) {
            if (on) conf.reset(Keys.SCREEN_SHADER)
            ShaderStore(context).forget(name)
        }
        load(context)
        return gone
    }

    /** What the compiler says is wrong with [code], or null when it compiles. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun compileError(code: String): ShaderMeta.CompileError? = try {
        RuntimeShader(code)
        null
    } catch (e: RuntimeException) {
        ShaderMeta.compileError(e.message.orEmpty())
    }

    // ---- using one ------------------------------------------------------------------------

    /**
     * What keeps shader [name] from being used: its first problem ([ShaderMeta.problems]), or the
     * compiler's error where Android has the compiler. Null when nothing does. Not on the main thread.
     */
    fun problem(context: Context, name: String): String? = fault(context, name)?.toString()

    /** The same as [problem], with the line the compiler names, when it names one. */
    private fun fault(context: Context, name: String): ShaderMeta.CompileError? {
        val source = source(context, name) ?: return ShaderMeta.CompileError(null, "its file can't be read")
        ShaderMeta.problems(source).firstOrNull()?.let { return ShaderMeta.CompileError(null, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            compileError(source)?.let { return it }
        }
        return null
    }

    /** The parameters of shader [name]; none when it can't be read. Reads a file: not on the main thread. */
    fun params(context: Context, name: String): List<ShaderParam> =
        source(context, name)?.let { ShaderMeta.parse(it).params }.orEmpty()

    /** Whether [name] is a one-off shader ([ShaderPlay]). Reads a file: not on the main thread. */
    fun once(context: Context, name: String): Boolean =
        source(context, name)?.let { ShaderMeta.parse(it).once } != null

    /**
     * What checking a shader found: its [problem] (null: none), the uniforms Kolahal [sets], those left [unset],
     * its [params], how often it is drawn while it moves ([pace]), and the header's lines that were left out ([warnings]).
     * [line]: the line of the problem, counted from 1, when the compiler names one.
     */
    class Check(
        val problem: String?,
        val sets: List<String>,
        val unset: List<String>,
        val animated: Boolean,
        val params: List<ShaderParam> = emptyList(),
        val pace: ShaderClock.Pace = ShaderClock.Pace.DEFAULT,
        val warnings: List<String> = emptyList(),
        val line: Int? = null,
    )

    /** Compiles shader [name] and reads its uniforms. Null when it can't be read. Not on the main thread. */
    fun check(context: Context, name: String): Check? {
        val source = source(context, name) ?: return null
        val meta = ShaderMeta.parse(source)
        val fault = fault(context, name)
        return Check(
            fault?.toString(),
            meta.uniforms.map { it.name }.filter { it in ShaderMeta.KNOWN },
            meta.unset(),
            meta.animated,
            meta.params,
            ShaderClock.Pace.of(meta),
            ShaderMeta.warnings(source),
            fault?.line,
        )
    }

    /**
     * Turns shader [name] on, also one that was turned off for being slow. Returns why it can't be
     * used, and then the one that is on stays; null when it is on. Not on the main thread: it compiles.
     */
    fun use(context: Context, name: String): String? {
        if (!supported) return NEEDS
        if (once(context, name)) return "it is a one-off: 'shader play $name' plays it"
        problem(context, name)?.let { return it }
        val safety = ShaderSafety.get(context)
        safety.turnedOn()
        safety.last = name
        Config.get(context).set(Keys.SCREEN_SHADER, name)
        return null
    }

    /** Turns shaders off. Returns the one that was on, which `shader on` turns on again; null when none was. */
    fun off(context: Context): String? {
        val conf = Config.get(context)
        val was = conf[Keys.SCREEN_SHADER]
        if (was == ShaderCatalog.OFF) return null
        ShaderSafety.get(context).last = was
        conf.set(Keys.SCREEN_SHADER, ShaderCatalog.OFF)
        return was
    }

    // ---- what it says in the log ----------------------------------------------------------

    /**
     * Logs [problem] with shader [name], once per [id] (and kind of trouble) while Kolahal runs: a
     * redraw doesn't repeat it. A tap on the line checks the shader.
     */
    fun report(context: Context, name: String, id: String, problem: String) {
        if (reported.add("$id: $problem")) log(context, LogKind.ERROR, problemLine(name, problem), "shader check $name")
    }

    /**
     * The log's words when shader [name] can't be used for [problem] (what [use] or [problem] returns, or a
     * compiler's error): that it can't be used, then the problem on the line under it.
     */
    fun problemLine(name: String, problem: String): String =
        "Shader $name can't be used\n${problem.replaceFirstChar { it.uppercase() }}"

    /**
     * Turns the shader of [id] off for [why] until its file changes or the user turns it on again
     * ([ShaderSafety.trip]); one line says so. After several in a row, `screen.shader` goes off.
     */
    fun turnOff(context: Context, id: String, why: String) {
        val app = context.applicationContext
        val count = ShaderSafety.get(app).trip(id, why)
        if (count == 0) return
        val off = count >= ShaderSafety.MAX_TRIPS
        if (off) Config.get(app).set(Keys.SCREEN_SHADER, ShaderCatalog.OFF)
        val again =
            if (off) "screen.shader is off after $count times" else "'shader on' turns it on again"
        log(
            app, LogKind.ERROR,
            "Shader ${ShaderSafety.nameOf(id)} turned off\n${why.replaceFirstChar { it.uppercase() }}\n$again",
            "shader", copy = if (off) null else "shader on",
        )
    }

    /** Two fingers held on home: no shader, whatever it did to the screen. One line says how to get it back. */
    fun offByHand(context: Context) {
        val app = context.applicationContext
        val was = off(app) ?: return
        log(
            app, LogKind.INFO,
            "Shader $was turned off\nTwo fingers were held on the screen\n'shader on' turns it on again",
            "shader", copy = "shader on",
        )
    }

    /** Logs [text]: a tap runs [command], and Copy gives [copy] when there is one. */
    private fun log(context: Context, kind: LogKind, text: String, command: String, copy: String? = null) {
        val app = context.applicationContext
        // Off the main thread: it writes to the database. A line that can't be written must not stop home.
        scope.launch {
            runCatching {
                LogRepository.get(app).add(kind, text, ctx = LogContext.of(LogContext.RUN to command, LogContext.COPY to copy))
            }
        }
    }

    @Volatile
    private var recovered = false

    /**
     * Once per process, before a shader is first drawn: a shader that was on screen when Kolahal last
     * crashed or hung is turned off ([ShaderSafety.recover]).
     */
    fun recover(context: Context) {
        if (recovered) return
        synchronized(this) {
            if (recovered) return
            recovered = true
            val id = ShaderSafety.get(context).recover(ShaderSafety.lastExit(context)) ?: return
            turnOff(context, id, "Kolahal stopped while it was on")
        }
    }
}
