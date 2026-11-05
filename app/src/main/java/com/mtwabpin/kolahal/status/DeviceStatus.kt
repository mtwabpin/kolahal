package com.mtwabpin.kolahal.status

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.mtwabpin.kolahal.data.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Status(
    val batteryPct: Int? = null,
    val charging: Boolean = false,
    val network: String = "offline",
    /** Epoch millis of the next alarm clock, if any. */
    val nextAlarm: Long? = null,
)

/** Battery, connectivity and next-alarm state for the header. */
class DeviceStatus private constructor(private val context: Context) {
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true

        // The level now, without listening: home listens while it is on screen (watchBattery). Started
        // in the background (a feed fetch, a reminder), the process isn't woken by every battery tick.
        ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)?.let(::onBattery)

        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refreshAlarm()
        }, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshAlarm()

        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                    else -> "net"
                }
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                _status.update { it.copy(network = if (validated) type else "$type?") }
            }

            override fun onLost(network: Network) {
                _status.update { it.copy(network = "offline") }
            }
        })
    }

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = onBattery(intent)
    }
    private var batteryOn = false

    /**
     * The battery broadcast comes on every change of level, voltage or temperature (often, while
     * charging): only listened to while home is on screen. The sticky value catches up on return.
     */
    fun watchBattery(on: Boolean) {
        if (on == batteryOn) return
        batteryOn = on
        if (on) {
            ContextCompat.registerReceiver(
                context, battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )?.let(::onBattery)
        } else {
            runCatching { context.unregisterReceiver(battery) }
        }
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        _status.update { it.copy(batteryPct = pct, charging = plugged) }
    }

    fun refreshAlarm() {
        val am = context.getSystemService(AlarmManager::class.java)
        _status.update { it.copy(nextAlarm = am.nextAlarmClock?.triggerTime) }
    }

    companion object : Singleton<DeviceStatus>(::DeviceStatus)
}
