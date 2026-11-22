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
 * - Names load at start ([load]), so `tty.shader` accepts and completes them at once.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The problems already put in the log, so a redraw doesn't repeat them. */
    private val reported = ConcurrentHashMap.newKeySet<String>()

    private fun dir(context: Context) = File(context.filesDir, DIR)
    private fun file(context: Context, name: String) = File(dir(context), "$name.${ShaderCatalog.EXT}")

    /** Reads the user's shader names. Called at start, before home is drawn. */
    fun load(context: Context) {
        mine = dir(context).list().orEmpty().mapNotNull(ShaderCatalog::nameOf).distinct().sorted()
        _version.update { it + 1 }
    }

    /** The shaders there are, the bundled ones first. */
    fun entries(): List<ShaderCatalog.Entry> = ShaderCatalog.list(mine)

    /** Whether there is a shader [name]. */
    fun exists(name: String): Boolean = name in ShaderCatalog.BUNDLED || name in mine

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

    /** Deletes the user's shader [name]. False when there is none. */
    fun remove(context: Context, name: String): Boolean {
        val gone = file(context, name).delete()
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
     * ([ShaderSafety.trip]); one line says so. After several in a row, `tty.shader` goes off.
     */
    fun turnOff(context: Context, id: String, why: String) {
        val app = context.applicationContext
        val count = ShaderSafety.get(app).trip(id)
        if (count == 0) return
        val off = count >= ShaderSafety.MAX_TRIPS
        if (off) Config.get(app).set(Keys.TTY_SHADER, ShaderCatalog.OFF)
        val again =
            if (off) "tty.shader is off after $count times" else "'shader on' turns it on again"
        log(app, LogKind.ERROR, "Shader ${ShaderSafety.nameOf(id)} turned off: $why · $again", "shader")
    }

    /** Two fingers held on home: no shader, whatever it did to the screen. One line says how to get it back. */
    fun offByHand(context: Context) {
        val app = context.applicationContext
        val conf = Config.get(app)
        val was = conf[Keys.TTY_SHADER]
        if (was == ShaderCatalog.OFF) return
        ShaderSafety.get(app).last = was
        conf.set(Keys.TTY_SHADER, ShaderCatalog.OFF)
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
