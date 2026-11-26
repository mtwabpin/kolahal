/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.extras

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.PatternMatcher
import androidx.core.content.ContextCompat
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.Links
import com.mtwabpin.kolahal.commands.BuildFeature
import com.mtwabpin.kolahal.data.Singleton
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** Where a feature that this build leaves out stands with Kolahal Extras ([ExtrasLink]). */
enum class ExtrasStatus {
    /** Extras isn't installed. */
    MISSING,

    /** Extras is installed, signed with another key, or another app holds its address: Kolahal doesn't ask it. */
    UNTRUSTED,

    /** Extras is installed but didn't answer. */
    SILENT,

    /** Extras speaks an older interface, or doesn't have the feature. */
    TOO_OLD,

    /** Extras has the feature, but Android doesn't give it to Extras now. */
    NOT_ALLOWED,
    READY;

    /** Whether the way on is Extras' own screen; otherwise its page in the guide. */
    val opensExtras: Boolean get() = this == SILENT || this == NOT_ALLOWED || this == READY
}

/** What Kolahal knows of Kolahal Extras: read at [ExtrasLink.refresh]. */
data class ExtrasState(
    val installed: Boolean = false,
    val trusted: Boolean = false,
    /** The interface version Extras speaks ([ExtrasContract.VERSION]); 0 when it didn't answer. */
    val version: Int = 0,
    val has: Set<String> = emptySet(),
    val allowed: Set<String> = emptySet(),
    val session: String? = null,
) {
    fun status(feature: String): ExtrasStatus = when {
        !installed -> ExtrasStatus.MISSING
        !trusted -> ExtrasStatus.UNTRUSTED
        version == 0 -> ExtrasStatus.SILENT
        version < ExtrasLink.MIN_VERSION || feature !in has -> ExtrasStatus.TOO_OLD
        feature !in allowed -> ExtrasStatus.NOT_ALLOWED
        else -> ExtrasStatus.READY
    }
}

/**
 * Kolahal's side of Kolahal Extras, the optional second app that holds what the Play build leaves out (the screen
 * lock, the call log, the contacts) and hands it to Kolahal alone ([ExtrasContract]).
 * - A build that has a feature itself (the full build) never asks Extras for it ([Builds.has]).
 * - What Extras can do is read again when home comes to the front and when Extras is installed, updated or removed.
 *   Reading it is a call to another app: always off the main thread, and the callers read the last [state].
 * - Every call can fail quietly (Extras missing, stopped, too old, not allowed): it then answers null or false.
 * - Kolahal trusts Extras only when it is signed with Kolahal's own key and holds its own address ([trusted]).
 */
class ExtrasLink private constructor(private val context: Context) {
    private val pkg = BuildConfig.EXTRAS_PACKAGE
    private val authority = ExtrasContract.authority(pkg)
    private val io = Executors.newSingleThreadExecutor()

    /** Where Extras' contacts are read, and the address that changes when they do. */
    val contactsUri: Uri = ExtrasContract.uri(pkg, ExtrasContract.PATH_CONTACTS)

    /** Where Extras' calls are read, and the address that changes when they do. */
    val callsUri: Uri = ExtrasContract.uri(pkg, ExtrasContract.PATH_CALLS)

    /** The last state read. Until the first read, Extras counts as missing. */
    @Volatile
    var state = ExtrasState()
        private set

    private val listeners = CopyOnWriteArrayList<(old: ExtrasState, new: ExtrasState) -> Unit>()
    private var started = false

    /** Runs [listener] off the main thread each time the state changes ([refresh]). */
    fun listen(listener: (old: ExtrasState, new: ExtrasState) -> Unit) {
        listeners += listener
    }

    /** Starts watching Extras being installed, updated or removed, and reads its state. Nothing in the full build. */
    @Synchronized
    fun start() {
        if (!ANY || started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
            addDataSchemeSpecificPart(pkg, PatternMatcher.PATTERN_LITERAL)
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh()
        }, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    /** Reads Extras' state again, off the main thread. */
    fun refresh() {
        if (ANY) io.execute { runCatching { refreshNow() } }
    }

    /** Reads Extras' state again on the caller's thread, which mustn't be the main one, and returns it. */
    fun refreshNow(): ExtrasState {
        if (!ANY || Looper.myLooper() == Looper.getMainLooper()) return state
        val new = read()
        val old = synchronized(this) { state.also { state = new } }
        if (new != old) listeners.forEach { runCatching { it(old, new) } }
        return new
    }

    private fun read(): ExtrasState {
        val pm = context.packageManager
        val installed = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        if (!installed) return ExtrasState()
        if (!trusted()) return ExtrasState(installed = true)
        val hello = call(ExtrasContract.HELLO) ?: return ExtrasState(installed = true, trusted = true)
        return ExtrasState(
            installed = true,
            trusted = true,
            version = hello.getInt(ExtrasContract.KEY_VERSION),
            has = hello.getStringArray(ExtrasContract.KEY_HAS).orEmpty().toSet(),
            allowed = hello.getStringArray(ExtrasContract.KEY_ALLOWED).orEmpty().toSet(),
            session = hello.getString(ExtrasContract.KEY_SESSION),
        )
    }

    /**
     * Whether Extras is signed with Kolahal's own key, and its address belongs to it: an address goes to the first app
     * that claims it, so another app could hold it while Extras isn't installed.
     */
    private fun trusted(): Boolean = runCatching {
        val pm = context.packageManager
        pm.resolveContentProvider(authority, 0)?.packageName.let { it == null || it == pkg } &&
                pm.checkSignatures(context.packageName, pkg) == PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)

    private fun call(method: String): Bundle? = runCatching {
        context.contentResolver.call(ExtrasContract.uri(pkg), method, null, null)
    }.getOrNull()

    /** The status of the [feature] this build leaves out ([BuildFeature]), as last read. */
    fun status(feature: BuildFeature): ExtrasStatus = state.status(key(feature))

    /** Whether the build leaves [feature] out and Extras is installed: then Kolahal offers it, and says what's missing. */
    fun offers(feature: BuildFeature): Boolean = !Builds.has(feature) && state.installed

    /** Whether the build leaves [feature] out and Extras can do it now. */
    fun ready(feature: BuildFeature): Boolean = !Builds.has(feature) && status(feature) == ExtrasStatus.READY

    /**
     * Locks the screen through Extras, on the caller's thread (never the main one). True when it locked; false when
     * Extras is missing, not trusted, or its service is off.
     */
    fun lock(): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper() || !trusted()) return false
        val answer = call(ExtrasContract.METHOD_LOCK)
        // The service may have been turned on or off in the meantime: read the state again.
        if (answer == null || !answer.getBoolean(ExtrasContract.KEY_ENABLED)) refresh()
        return answer?.getBoolean(ExtrasContract.KEY_LOCKED) == true
    }

    /** Queries Extras at [uri] ([contactsUri], [callsUri]) off the main thread; null when it can't answer. */
    fun query(uri: Uri): Cursor? {
        if (Looper.myLooper() == Looper.getMainLooper() || !trusted()) return null
        return runCatching { context.contentResolver.query(uri, null, null, null, null) }.getOrNull()
    }

    /** Watches [uri] ([contactsUri], [callsUri]): Extras calls notifyChange on it while it runs. */
    fun observe(uri: Uri, observer: ContentObserver) {
        runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }
    }

    /**
     * What opens for a feature that needs Extras now: Extras' own screen when it is installed and trusted
     * ([ExtrasStatus.opensExtras]), and its page in the guide otherwise. Kolahal never installs Extras itself.
     */
    fun intent(status: ExtrasStatus): Intent =
        (if (status.opensExtras) context.packageManager.getLaunchIntentForPackage(pkg) else null)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE))

    companion object : Singleton<ExtrasLink>(::ExtrasLink) {
        /** The oldest interface ([ExtrasContract.VERSION]) this Kolahal works with. */
        const val MIN_VERSION = 1

        /** Extras' page in the guide: what it is, where to get it. */
        const val GUIDE = Links.GUIDE + "guides/extras/"

        /** Whether this build leaves out any feature that Extras has: only then does Kolahal ask it anything. */
        val ANY = !BuildConfig.LOCK_SERVICE || !BuildConfig.CALL_LOG || !BuildConfig.CONTACTS

        /** [ExtrasContract]'s name for [feature]. */
        fun key(feature: BuildFeature): String = when (feature) {
            BuildFeature.LOCK_SERVICE -> ExtrasContract.LOCK
            BuildFeature.CONTACTS -> ExtrasContract.CONTACTS
            BuildFeature.CALL_LOG -> ExtrasContract.CALLS
            BuildFeature.SHADER_OVER_APPS -> ""
        }
    }
}

/** Whether this build has a feature itself. Extras can't give [BuildFeature.SHADER_OVER_APPS], so it counts as had. */
internal object Builds {
    fun has(feature: BuildFeature): Boolean = when (feature) {
        BuildFeature.LOCK_SERVICE -> BuildConfig.LOCK_SERVICE
        BuildFeature.CONTACTS -> BuildConfig.CONTACTS
        BuildFeature.CALL_LOG -> BuildConfig.CALL_LOG
        BuildFeature.SHADER_OVER_APPS -> true
    }
}
