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
 * global is the payload. The same script + event fires at most once per [DEBOUNCE_MS] (for
 * notifications: once per app). So a flapping charger or a chatty app can't start a storm of runs.
 */
class TriggerDispatcher private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val main = Handler(Looper.getMainLooper())

    /** (script id, events) for enabled scripts with `@on`. Reloaded when [ScriptRepository.version] changes. */
    private var subscriptions: List<Pair<Long, List<String>>> = emptyList()
    private var loadedVersion = -1L
    private val lastFired = HashMap<String, Long>()

    private var started = false
    /** The last default-network kind ("wifi", "other", "offline"), so events fire only on changes. */
    @Volatile private var netState: String? = null

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
                val targets = subscribers(event)
                if (targets.isEmpty()) return@runCatching
                val data = mapOf("name" to event) + payload.filterValues { it != null }.mapValues { (_, v) -> clip(v) }
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

    private fun clip(v: Any?): Any? = if (v is String && v.length > MAX_TEXT) v.takeWhole(MAX_TEXT) + "…" else v

    private fun subscribers(event: String): List<Long> {
        refresh()
        return subscriptions.filter { event in it.second }.map { it.first }
    }

    /** True when a switched-on script declares `@uses notifications`. Otherwise nothing keeps a copy of notifications for scripts. */
    fun wantsNotifications(): Boolean {
        refresh()
        return notificationReaders
    }

    @Volatile private var notificationReaders = false

    private fun refresh() {
        val v = ScriptRepository.version.get()
        if (v == loadedVersion) return
        val enabled = ScriptRepository.get(context).all().filter { it.enabled }
        subscriptions = enabled
            // Events the user switched off don't run the script. A notification's text only goes to scripts
            // that declare `@uses notifications` and are allowed it.
            .map { s ->
                val grants = s.grants
                s.id to ScriptMeta.events(s.code).filter { grants.allows(it) && (it != "notification" || grants.allows("notifications")) }
            }
            .filter { it.second.isNotEmpty() }
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
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return emptyMap()
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            0 -> null
            else -> "other"
        }
        return mapOf("level" to if (level >= 0 && scale > 0) level * 100 / scale else null, "plug" to plug)
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
        val prefs = context.getSharedPreferences("triggers", Context.MODE_PRIVATE)
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

    companion object : Singleton<TriggerDispatcher>(::TriggerDispatcher) {
        const val DEBOUNCE_MS = 30_000L
        private const val OFFLINE_CONFIRM_MS = 3_000L
        private const val MAX_TEXT = 2_000
    }
}
