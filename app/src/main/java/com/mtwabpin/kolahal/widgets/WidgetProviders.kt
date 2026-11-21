/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle

/**
 * One app's widgets in one profile, for the widget picker: the app's name and its info (the screen
 * draws its icon from it), and its widgets with their names and descriptions, by name.
 */
class ProviderApp(
    val pkg: String,
    val serial: Long,
    val user: UserHandle,
    val label: String,
    val app: ApplicationInfo?,
    val providers: List<ProviderEntry>,
    val names: List<String>,
    val descriptions: List<String?>,
)

/**
 * Every installed widget, by app and profile ([ProviderApp]). It reads the other apps' names and
 * resources, so call it off the main thread.
 */
fun WidgetHost.providerApps(context: Context): List<ProviderApp> {
    val pm = context.packageManager
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return providers().groupBy { it.packageName to it.serial }.map { (k, list) ->
        val (pkg, serial) = k
        val user = list.first().user
        val ai = runCatching { launcherApps.getApplicationInfo(pkg, 0, user) }.getOrNull()
        val named = list.map { p ->
            val description = if (Build.VERSION.SDK_INT >= 31) runCatching {
                p.info.loadDescription(context)?.toString()
            }.getOrNull() else null
            Triple(p, p.info.loadLabel(pm).toString(), description)
        }.sortedBy { it.second.lowercase() }
        ProviderApp(
            pkg, serial, user,
            label = ai?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
            app = ai,
            providers = named.map { it.first },
            names = named.map { it.second },
            descriptions = named.map { it.third },
        )
    }
}
