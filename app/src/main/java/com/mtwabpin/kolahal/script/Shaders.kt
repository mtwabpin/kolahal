/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

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
 * - Names load at start ([load]), so `screen.shader` accepts and completes them at once.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The problems already put in the log, so a redraw doesn't repeat them. */
    private val reported = ConcurrentHashMap.newKeySet<String>()

    private fun dir(context: Context) = File(context.filesDir, DIR)
    private fun file(context: Context, name: String) = File(dir(context), "$name.${ShaderCatalog.EXT}")

    /** Reads the user's shader names. Called at start, before home is drawn. */
    fun load(context: Context) {
        if (!paramsRead) {
            paramsRead = true
            ShaderParams.load(context)
            ShaderValues.load(context)
        }
        mine = dir(context).list().orEmpty().mapNotNull(ShaderCatalog::nameOf).distinct().sorted()
        _version.update { it + 1 }
    }

    /** The shaders there are, the bundled ones first. */
    fun entries(): List<ShaderCatalog.Entry> = ShaderCatalog.list(mine)

    /** Whether there is a shader [name]. */
    fun exists(name: String): Boolean = name in ShaderCatalog.BUNDLED || name in mine

    /** The code of the user's own shader [name]; null when there is none. Reads a file: not on the main thread. */
    fun own(context: Context, name: String): String? =
        runCatching { file(context, name).takeIf { name in mine && it.isFile }?.readText() }.getOrNull()

    /** The code of shader [name], the user's file first; null when there is none. Reads a file: not on the main thread. */
    fun source(context: Context, name: String): String? = runCatching {
        val own = file(context, name)
        when {
            name in mine && own.isFile -> own.readText()
            name in ShaderCatalog.BUNDLED -> bundled(context, name)
            else -> null
        }
    }.getOrNull()

    /** The code of the bundled shader [name]. */
    fun bundled(context: Context, name: String): String =
        context.assets.open("$DIR/$name.${ShaderCatalog.EXT}").use { it.readBytes().toString(Charsets.UTF_8) }

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
    fun problem(context: Context, name: String): String? {
        val source = source(context, name) ?: return "can't be read"
        ShaderMeta.problems(source).firstOrNull()?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            compileError(source)?.let { return it.toString() }
        }
        return null
    }

    /** The parameters of shader [name]; none when it can't be read. Reads a file: not on the main thread. */
    fun params(context: Context, name: String): List<ShaderParam> =
        source(context, name)?.let { ShaderMeta.parse(it).params }.orEmpty()

    /** Whether [name] is a one-off shader ([ShaderPlay]). Reads a file: not on the main thread. */
    fun once(context: Context, name: String): Boolean =
        source(context, name)?.let { ShaderMeta.parse(it).once } != null

    /** What checking a shader found: its [problem] (null: none), the uniforms Kolahal [sets], those left [unset], and its [params]. */
    class Check(
        val problem: String?,
        val sets: List<String>,
        val unset: List<String>,
        val animated: Boolean,
        val params: List<ShaderParam> = emptyList(),
    )

    /** Compiles shader [name] and reads its uniforms. Null when it can't be read. Not on the main thread. */
    fun check(context: Context, name: String): Check? {
        val meta = ShaderMeta.parse(source(context, name) ?: return null)
        return Check(
            problem(context, name),
            meta.uniforms.map { it.name }.filter { it in ShaderMeta.KNOWN },
            meta.unset(),
            meta.animated,
            meta.params,
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
        if (reported.add("$id: $problem")) log(context, LogKind.ERROR, "Shader $name: $problem", "shader check $name")
    }

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
        log(app, LogKind.ERROR, "Shader ${ShaderSafety.nameOf(id)} turned off: $why · $again", "shader")
    }

    /** Two fingers held on home: no shader, whatever it did to the screen. One line says how to get it back. */
    fun offByHand(context: Context) {
        val app = context.applicationContext
        val was = off(app) ?: return
        log(app, LogKind.INFO, "Shader off: two fingers were held on the screen · 'shader on' turns $was on again", "shader")
    }

    private fun log(context: Context, kind: LogKind, text: String, command: String) {
        val app = context.applicationContext
        // Off the main thread: it writes to the database. A line that can't be written must not stop home.
        scope.launch {
            runCatching {
                LogRepository.get(app).add(kind, text, ctx = LogContext.of(LogContext.RUN to command))
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
