package com.mtwabpin.kolahal.widgets

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

/**
 * Widgets for the device tests: the stock clock's providers, Kolahal allowed (or not) to bind them
 * without Android's "allow Kolahal to create widgets" dialog, and everything a test placed taken
 * away again.
 */
class TestWidgets {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    val host: WidgetHost = WidgetHost.get(context)
    private val hadKeys = host.widgets.value.map { it.key }.toSet()
    private val hadIds = runCatching { host.host.appWidgetIds.toSet() }.getOrDefault(emptySet())
    private var granted = false
    /** This user's number, as `appwidget` takes it ("current" isn't looked up for a grant). */
    private val userId = Process.myUid() / 100_000

    init {
        // Messages left from before aren't the test's.
        while (host.takeMessage() != null) Unit
    }

    /** The clock app's widgets in this profile, by name; [unconfigured] leaves out those with a setup screen. */
    fun clocks(unconfigured: Boolean = false): List<ProviderEntry> {
        val pm = context.packageManager
        val found = host.providers()
            .filter { it.user == Process.myUserHandle() && it.packageName.endsWith("deskclock") }
            .filter { !unconfigured || it.info.configure == null }
            .sortedBy { it.info.loadLabel(pm).lowercase() }
        assumeTrue("no clock widgets on this device", found.isNotEmpty())
        return found
    }

    fun label(p: ProviderEntry): String = p.info.loadLabel(context.packageManager)

    /** Lets Kolahal bind widgets as a launcher the user allowed; [close] takes it back when this gave it. */
    fun allowBinding(probe: ProviderEntry) {
        if (mayBind(probe)) return
        val said = shell("appwidget grantbind --package ${context.packageName} --user $userId")
        granted = true
        assertTrue("binding widgets wasn't allowed: $said", mayBind(probe))
    }

    private var revoked = false

    /** Takes away Kolahal's leave to bind widgets, so Android's dialog is asked; [close] gives it back when this took it. */
    fun forbidBinding(probe: ProviderEntry) {
        if (!mayBind(probe)) return
        shell("appwidget revokebind --package ${context.packageName} --user $userId")
        revoked = true
        assertTrue("binding widgets is still allowed", !mayBind(probe))
    }

    /** Whether Kolahal may bind [p] without Android's dialog: tried on an id given back at once. */
    private fun mayBind(p: ProviderEntry): Boolean {
        val id = host.host.allocateAppWidgetId()
        return try {
            runCatching { host.manager.bindAppWidgetIdIfAllowed(id, p.user, p.info.provider, null) }.getOrDefault(false)
        } finally {
            host.host.deleteAppWidgetId(id)
        }
    }

    /** [p] bound and placed on the widgets page, as the picker leaves it. */
    fun place(p: ProviderEntry): PlacedWidget {
        val id = host.allocate()
        val options = host.options(host.defaultHeightDp(p.info))
        assertTrue("couldn't bind ${p.flat}", host.manager.bindAppWidgetIdIfAllowed(id, p.user, p.info.provider, options))
        host.place(id, p.info)
        return host.widgets.value.single { it.id == id }
    }

    /**
     * An id bound to [p] and not placed: a widget whose setup screen was open when Android stopped
     * Kolahal. Allocated past [WidgetHost.allocate], as by the process before: nothing marks it in use.
     */
    fun bound(p: ProviderEntry): Int {
        val id = host.host.allocateAppWidgetId()
        assertTrue("couldn't bind ${p.flat}", host.manager.bindAppWidgetIdIfAllowed(id, p.user, p.info.provider, null))
        return id
    }

    /** A widget restored from a backup on another phone: waiting to be added again. */
    fun restore(provider: String, serial: Long, height: Int, order: Int = 90, span: Int = WidgetLayout.COLUMNS): PlacedWidget {
        host.importWidgets(listOf(mapOf("provider" to provider, "profile" to serial, "height" to height, "order" to order, "span" to span)))
        return host.widgets.value.single { it.pending && it.provider == provider && it.order == order }
    }

    fun ids(): Set<Int> = host.host.appWidgetIds.toSet()

    fun get(key: String): PlacedWidget? = host.widgets.value.firstOrNull { it.key == key }

    /** Takes away the widgets, ids, messages and binding the test added. */
    fun close() {
        while (host.takeMessage() != null) Unit
        host.widgets.value.filter { it.key !in hadKeys }.forEach { host.remove(it.key) }
        val used = host.widgets.value.mapNotNull { it.id }.toSet()
        runCatching { host.host.appWidgetIds }.getOrNull()?.filter { it !in hadIds && it !in used }?.forEach { host.discard(it) }
        if (granted) shell("appwidget revokebind --package ${context.packageName} --user $userId")
        if (revoked) shell("appwidget grantbind --package ${context.packageName} --user $userId")
    }

    /** What [command] printed, its errors too. */
    private fun shell(command: String) = com.mtwabpin.kolahal.shell(command, errors = true)
}
