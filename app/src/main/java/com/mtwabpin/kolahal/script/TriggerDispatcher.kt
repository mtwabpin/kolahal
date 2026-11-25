/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.takeWhole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs scripts subscribed with `@on <event>` when the event happens. It lives in the UI process,
 * because on Android 8+ only runtime receivers can receive charger and headset broadcasts. The
 * network callback, unlock receiver and notification listener also live here.
 *
 * Each matching, enabled script gets a one-time, non-interactive [ScriptWorker] run. Its `event`
 * global is the payload. A script whose `@on notification` names apps gets runs only for their
 * notifications: the others queue no run at all ([targets]). The same script + event fires at most once per [DEBOUNCE_MS] (for
 * notifications: once per app). So a flapping charger or a chatty app can't start a storm of runs.
 */
class TriggerDispatcher private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val main = Handler(Looper.getMainLooper())

    /** Enabled scripts with `@on`. Reloaded when [ScriptRepository.version] changes. */
    private var subscriptions: List<Subscription> = emptyList()
    private var loadedVersion = -1L
    private val lastFired = HashMap<String, Long>()

    private var started = false

    /** The last default-network kind ("wifi", "other", "offline"), so events fire only on changes. */
    @Volatile
    private var netState: String? = null

    fun start() {
        if (started) return
        started = true
        registerPower()
        registerHeadset()
        registerNetwork()
        // This reads its prefs from disk, so it must not run on the main thread at start-up.
        Thread { runCatching { checkBoot() } }.start()
    }

    /** Fires [event] for every subscribed script. Safe to call from any thread. */
    fun fire(event: String, payload: Map<String, Any?> = emptyMap()) {
        val now = SystemClock.elapsedRealtime()
        scope.launch {
            runCatching {
                refresh()
                // A script whose `@on notification` names other apps is not started at all.
                val targets = targets(subscriptions, event, payload["pkg"] as? String)
                if (targets.isEmpty()) return@runCatching
                val data = mapOf("name" to event) + payload.filterValues { it != null }
                    .mapValues { (_, v) -> clip(v) }
                val extra = if (event == "notification") ":" + payload["pkg"] else ""
                for (id in targets) {
                    val key = "$id:$event$extra"
                    val last = lastFired[key]
                    if (last != null && now - last < DEBOUNCE_MS) continue
                    lastFired[key] = now
                    // If one script's run can't be queued, the other scripts must still get the event.
                    runCatching { ScriptScheduler.runForEvent(context, id, data) }
                }
                if (lastFired.size > 500) lastFired.entries.removeAll { now - it.value > DEBOUNCE_MS }
            }
        }
    }

    private fun clip(v: Any?): Any? =
        if (v is String && v.length > MAX_TEXT) v.takeWhole(MAX_TEXT) + "…" else v

    /** True when a switched-on script declares `@uses notifications`. Otherwise nothing keeps a copy of notifications for scripts. */
    fun wantsNotifications(): Boolean {
        refresh()
        return notificationReaders
    }

    @Volatile
    private var notificationReaders = false

    private fun refresh() {
        val v = ScriptRepository.version.get()
        if (v == loadedVersion) return
        val enabled = ScriptRepository.get(context).all().filter { it.enabled }
        subscriptions = enabled.map { Subscription.of(it.id, it.code, it.grants) }
            .filter { it.events.isNotEmpty() }
        notificationReaders = enabled.any { it.grants.allows("notifications") }
        loadedVersion = v
    }

    private fun registerPower() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val battery = battery()
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> fire("charger", battery)
                    Intent.ACTION_POWER_DISCONNECTED -> fire("unplug", battery)
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun battery(): Map<String, Any?> {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return emptyMap()
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            0 -> null
            else -> "other"
        }
        return mapOf(
            "level" to if (level >= 0 && scale > 0) level * 100 / scale else null,
            "plug" to plug
        )
    }

    private fun registerHeadset() {
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                // A sticky broadcast: registering delivers the current state, which is not a new connection.
                if (isInitialStickyBroadcast) return
                if (intent.getIntExtra("state", 0) != 1) return
                fire(
                    "headset",
                    mapOf(
                        "type" to "wired",
                        "device" to intent.getStringExtra("name"),
                        "mic" to (intent.getIntExtra("microphone", 0) == 1),
                    ),
                )
            }
        }, IntentFilter(AudioManager.ACTION_HEADSET_PLUG), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun registerNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        netState = kindOf(cm.getNetworkCapabilities(cm.activeNetwork))
        val recheckOffline = Runnable {
            if (cm.activeNetwork == null) transition("offline")
        }
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    main.removeCallbacks(recheckOffline)
                    transition(kindOf(caps))
                }

                override fun onLost(network: Network) {
                    // A switch between networks can briefly report "lost", so confirm before firing.
                    main.removeCallbacks(recheckOffline)
                    main.postDelayed(recheckOffline, OFFLINE_CONFIRM_MS)
                }
            })
        }
    }

    private fun kindOf(caps: NetworkCapabilities?): String = when {
        caps == null -> "offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        else -> "other"
    }

    @Synchronized
    private fun transition(kind: String) {
        val previous = netState
        netState = kind
        if (kind == previous) return
        when (kind) {
            "wifi" -> fire("wifi", mapOf("network" to "wifi"))
            "offline" -> fire("offline")
        }
    }

    /** `boot` fires on the first launcher start after each device boot. The launcher starts at boot. */
    private fun checkBoot() {
        val prefs = Stores.TRIGGERS.open(context)
        val bootId = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toLong()
        }.getOrNull()
        // Fallback: the boot time on the wall clock, rounded to minutes to absorb clock jitter.
        val id = bootId?.let { "count:$it" }
            ?: "time:${(System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000}"
        if (prefs.getString("boot", null) == id) return
        prefs.edit { putString("boot", id) }
        fire("boot", mapOf("uptime" to SystemClock.elapsedRealtime() / 1000))
    }

    /**
     * What runs a script: its [id], the [events] it runs on, and for a notification, the [apps] whose
     * notifications do (null: every app's, [ScriptMeta.notificationApps]).
     */
    internal class Subscription(val id: Long, val events: List<String>, val apps: Set<String>?) {
        companion object {
            /**
             * The script's subscription from its [code] and its [grants]. Events the user switched off
             * don't run the script. A notification's text only goes to scripts that declare
             * `@uses notifications` and are allowed it.
             */
            fun of(id: Long, code: String, grants: ScriptAccess.Grants): Subscription {
                val parsed = ScriptMeta.parse(code)
                return Subscription(
                    id,
                    parsed.events.filter { grants.allows(it) && (it != "notification" || grants.allows("notifications")) },
                    parsed.notificationApps?.toSet(),
                )
            }
        }
    }

    companion object : Singleton<TriggerDispatcher>(::TriggerDispatcher) {
        const val DEBOUNCE_MS = 30_000L
        private const val OFFLINE_CONFIRM_MS = 3_000L
        private const val MAX_TEXT = 2_000

        /** The ids of the scripts that [event] runs. A notification from [pkg] runs only those for its app ([ScriptMeta.runsFor]). */
        internal fun targets(subscriptions: List<Subscription>, event: String, pkg: String?): List<Long> =
            subscriptions.filter {
                event in it.events && (event != "notification" || ScriptMeta.runsFor(it.apps, pkg))
            }.map { it.id }
    }
}
