/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** The kind of network, named the same in the header and for scripts (`device.network()`). */
object NetworkKind {
    /** The kind of a network: "wifi", "cell", "eth", "vpn" or "net" (any other); "offline" without one. */
    fun of(caps: NetworkCapabilities?): String = when {
        caps == null -> "offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        else -> "net"
    }

    /** Whether Android found the network reaches the internet. The header marks one that doesn't with "?". */
    fun validated(caps: NetworkCapabilities?): Boolean = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    /** The active network's capabilities, or null when offline. */
    fun active(context: Context): NetworkCapabilities? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)
    }
}
