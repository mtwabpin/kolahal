/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.RuntimeShader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A one-off shader ([ShaderPlay]) over the app in front, when a notification's word plays it
 * ([ShaderWords.onNotification]) while Kolahal is in the background (`screen.shader-over-apps`). Both builds
 * have it; `BuildConfig.SHADER_OVER_APPS` takes it out of one (app/build.gradle.kts says when). A phone that
 * can't give the access ([phoneGives]) isn't offered it.
 * - [plays] decides. Where it says no, the shader waits for home, as without this ([ShaderPlay.play]).
 * - The shader is read, rated and compiled here, off the main thread ([load]). The screen's code draws it in a
 *   window of its own ([Stage]), which it sets as home is made in this process: before that, a shader waits for home.
 * - While it is compiled and played it has a mark of its own ([ShaderSafety.overBegin]): one that was playing
 *   when Kolahal crashed or hung is turned off, and kept from other apps ([recover]).
 * - One plays at a time, and one that played here doesn't play again on home.
 * - Nothing of the app in front is read, and nothing of the notification is kept.
 *
 * A script's `shader.play` and the `shader play` command play over home only.
 */
object ShaderOverApps {
    /** What [plays] looks at: each as the phone, the build and the shader are when a word comes. */
    data class Now(
        /** `screen.shader-over-apps` is on. */
        val setting: Boolean,
        /** This build can draw over other apps (`BuildConfig.SHADER_OVER_APPS`). */
        val build: Boolean,
        /** Android's Display over other apps access is given. */
        val access: Boolean,
        /** A low-memory phone, such as one with Android Go ([phoneGives]). */
        val lowRam: Boolean,
        /** The phone's Android version: shaders need 13 ([Shaders.MIN_SDK]). */
        val sdk: Int,
        /** How the shader's header rates it for other apps ([fit]); null when it doesn't. */
        val fit: ShaderFit?,
        /** One of Kolahal's own screens is in front: home, or its editor. */
        val kolahalInFront: Boolean,
        val screenOn: Boolean,
        val locked: Boolean,
        /** Do Not Disturb holds the notification back. */
        val heldBack: Boolean,
        /** The status bar shows, so no full-screen app is in front. The window knows once it is attached: true until then. */
        val statusBar: Boolean,
        /** A screen reader is on: home plays no one-off then. */
        val screenReader: Boolean,
        /** The phone's state, which home asks before it plays a one-off ([ShaderClock.moves]). */
        val phone: ShaderClock.Phone,
    )

    /**
     * Whether a one-off shader plays over the app in front, [now]: only when everything says yes. Otherwise it
     * waits for home, where what stops a one-off stops it as before (a screen reader, battery saver, Remove
     * animations, a hot phone or a nearly empty battery).
     */
    fun plays(now: Now): Boolean = now.setting && now.build && now.access && phoneGives(now.lowRam) && now.sdk >= Shaders.MIN_SDK &&
        (now.fit == ShaderFit.GOOD || now.fit == ShaderFit.FAIR) &&
        !now.kolahalInFront && now.screenOn && !now.locked && !now.heldBack && now.statusBar &&
        !now.screenReader && ShaderClock.moves(now.phone, 0L)

    /** How the header of [meta] rates it for other apps (`@suits apps good`); null when it isn't rated, or is no one-off. */
    fun fit(meta: ShaderMeta): ShaderFit? = meta.suits[ShaderSurface.APPS].takeIf { meta.once != null }

    /**
     * The window's flags. It takes no touch and no focus, so the app under it works as before. It is laid out
     * in the whole screen, and drawn by the GPU, as a shader must be.
     */
    const val WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    /** Below this alpha there is too little of a shader to see. */
    const val MIN_ALPHA = 0.3f

    /**
     * The window's alpha, where Android passes touches on through a window that is no more opaque than [most]
     * (`InputManager.getMaximumObscuringOpacityForTouch`, Android 12 and newer): [most] itself and never more,
     * so every touch reaches the app under it. 0 where that is under [MIN_ALPHA]: then nothing plays over apps.
     */
    fun alpha(most: Float): Float = if (most.isNaN() || most < MIN_ALPHA) 0f else most.coerceAtMost(1f)

    /**
     * The window is gone after this long, whatever happens: a little over the longest one-off
     * ([ShaderPlay.MAX_SECONDS]), so no window ever stays over an app.
     */
    const val LIMIT_MS = 12_000L

    /**
     * Whether the phone can give Display over other apps at all. Android Go and other low-memory phones
     * ([lowRam], as [ShaderPhone.lowRam] reads it) never do: Android refuses it there, as drawing over apps takes
     * too much memory. On such a phone nothing offers it: no switch on the Shaders tab, no line in `allow` and the
     * setup checklist, and `allow overlay` says the phone can't give it.
     */
    fun phoneGives(lowRam: Boolean): Boolean = !lowRam

    /** Whether this build, this Android and this phone can play one over other apps at all: the Shaders tab shows its switch then. */
    fun offered(context: Context): Boolean = BuildConfig.SHADER_OVER_APPS && Shaders.supported && phoneGives(ShaderPhone.lowRam(context))

    /** Whether Android's Display over other apps access is given. */
    fun granted(context: Context): Boolean = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    /** Android's page where the access is given, as `allow overlay` opens it. */
    fun accessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** A one-off shader ready to play over an app: compiled and marked ([ShaderSafety.overBegin]). It plays for [seconds]. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    class Ready(val id: String, val name: String, val meta: ShaderMeta, val runtime: RuntimeShader, val seconds: Float)

    /** The window that draws it, which the screen's code has ([stage]). Main thread. */
    interface Stage {
        /** Whether one of Kolahal's own screens is in front. */
        val inFront: Boolean

        /**
         * Draws [ready] in a window over the app in front, while [plays] goes on saying yes to [now] with the
         * status bar as the window finds it. False when no window can be made; it never throws. After true,
         * [ended] comes once, when the window is gone: with true when a frame of the shader was drawn.
         */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun show(context: Context, ready: Ready, now: Now, ended: (played: Boolean) -> Unit): Boolean
    }

    /** The screen's window; null until home is made in this process, and always in a build without it. */
    @Volatile
    var stage: Stage? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** One is being compiled or plays over an app. */
    private val busy = AtomicBoolean(false)

    /** The screen's code has a window for it, [window]: from now on a word can play its shader over an app. */
    fun install(context: Context, window: Stage) {
        stage = window
        val app = context.applicationContext
        scope.launch { runCatching { recover(app) } }
    }

    /**
     * A notification's word plays the one-off shader [name]: over the app in front when [plays] says so, else
     * as without this, on home ([ShaderPlay.play]). [heldBack]: Do Not Disturb holds the notification back.
     * Main thread.
     */
    fun onWord(context: Context, name: String, heldBack: Boolean) {
        val app = context.applicationContext
        val window = stage
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ShaderPlay.play(name, null)
            return
        }
        over(app, window, name, heldBack)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun over(app: Context, window: Stage, name: String, heldBack: Boolean) {
        // The rating is in the shader's file, which is read off this thread: it counts as rated until then.
        val first = runCatching { plays(now(app, window, heldBack, ShaderFit.GOOD)) }.getOrDefault(false)
        if (!first) {
            ShaderPlay.play(name, null)
            return
        }
        // One plays at a time: another that comes meanwhile is left out.
        if (!busy.compareAndSet(false, true)) return
        val at = System.currentTimeMillis()
        scope.launch {
            val ready = runCatching { load(app, name) }.getOrNull()
            main.post {
                // The phone may have changed while it compiled: home in front, the screen off.
                val shown = ready != null && runCatching {
                    val state = now(app, window, heldBack, fit(ready.meta))
                    plays(state) && window.show(app, ready, state) { played -> ended(app, ready.id, name, at, played) }
                }.getOrDefault(false)
                if (!shown) ended(app, ready?.id, name, at, played = false)
            }
        }
    }

    /** The window is gone, or there was none. A shader that drew no frame waits for home, as without this. */
    private fun ended(context: Context, id: String?, name: String, at: Long, played: Boolean) {
        if (id != null) runCatching { ShaderSafety.get(context).overEnd(id) }
        busy.set(false)
        if (!played) ShaderPlay.play(name, null, at)
    }

    /** What [plays] looks at, as the phone is now, for a shader rated [fit]. */
    private fun now(context: Context, window: Stage, heldBack: Boolean, fit: ShaderFit?): Now = Now(
        setting = Config.get(context)[Keys.SHADER_OVER_APPS],
        build = BuildConfig.SHADER_OVER_APPS,
        access = granted(context),
        lowRam = ShaderPhone.lowRam(context),
        sdk = Build.VERSION.SDK_INT,
        fit = fit,
        kolahalInFront = window.inFront,
        screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true,
        locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false,
        heldBack = heldBack,
        statusBar = true,
        screenReader = context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true,
        phone = ShaderPhone.of(context),
    )

    /**
     * The one-off shader [name], ready to play over an app: read, rated for it ([fit]), marked and compiled.
     * Null when it is no one-off, isn't rated good or fair, can't be drawn, or was turned off or kept from
     * other apps after a crash ([ShaderSafety.isTripped], [ShaderSafety.overBarred]). Not on the main thread.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun load(context: Context, name: String): Ready? {
        recover(context)
        Shaders.recover(context)
        val source = Shaders.source(context, name) ?: return null
        val meta = ShaderMeta.parse(source)
        val seconds = meta.once ?: return null
        val rated = fit(meta)
        if (rated != ShaderFit.GOOD && rated != ShaderFit.FAIR) return null
        if (ShaderMeta.problems(source).isNotEmpty()) return null
        val id = ShaderSafety.id(name, source)
        val safety = ShaderSafety.get(context)
        if (safety.isTripped(id) || safety.overBarred(id)) return null
        // Marked before it compiles: a crash in the compiler is found at the next start, too.
        safety.overBegin(id)
        val runtime = try {
            RuntimeShader(source)
        } catch (_: RuntimeException) {
            safety.overEnd(id)
            return null
        }
        return Ready(id, name, meta, runtime, seconds)
    }

    @Volatile
    private var recovered = false

    /**
     * Once per process, before a shader is first played over an app: one that an earlier process was playing
     * there when it crashed or hung is turned off, as home's is ([Shaders.turnOff]), and kept from other apps
     * until its file changes or the user turns a shader on ([ShaderSafety.overRecover]).
     */
    fun recover(context: Context) {
        if (recovered) return
        synchronized(this) {
            if (recovered) return
            recovered = true
            val id = ShaderSafety.get(context).overRecover(ShaderSafety.lastExit(context)) ?: return
            Shaders.turnOff(context, id, "Kolahal stopped while it played over an app")
        }
    }
}
