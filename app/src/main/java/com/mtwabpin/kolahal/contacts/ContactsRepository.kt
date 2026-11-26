/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.commands.BuildFeature
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.extras.ExtrasContract
import com.mtwabpin.kolahal.extras.ExtrasLink
import com.mtwabpin.kolahal.data.Stores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

data class Contact(
    val id: Long,
    val name: String,
    val number: String,
    val label: String,
    val starred: Boolean,
    val lookupUri: Uri,
    /** The contact's small photo (a content: address), or null without one. */
    val photoUri: String? = null,
) {
    /** Values that [ContactsRepository.search] compares. They are computed at the first search, not on every key press. */
    internal val lowerName: String by lazy(LazyThreadSafetyMode.PUBLICATION) { name.lowercase() }
    internal val nameWords: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        lowerName.split(
            ' '
        )
    }
    internal val digits: String by lazy(LazyThreadSafetyMode.PUBLICATION) { number.filter { it.isDigit() } }
}

/**
 * Contacts with phone numbers, cached in memory for instant `call` / `sms` suggestions.
 * The list is empty until READ_CONTACTS is granted. It refreshes when the contacts database changes.
 *
 * The Play build has no READ_CONTACTS.
 * - With Kolahal Extras that has contacts access ([ExtrasLink]), the contacts come from Extras: the whole list once,
 *   and again when Extras says they changed, or when Extras restarted and may have missed a change.
 * - Without it ([picker]), its contacts are the ones the user picked with Android's contact picker ([ContactPicker]).
 *   They are saved on the phone, so a name works again without picking it again.
 */
class ContactsRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    private var observing = false

    /**
     * True while home is started. Then a change in the contacts reloads them right away. It stays
     * false until home first starts, because a process started for a job (a feed, a reminder) has no
     * home to show them.
     */
    @Volatile
    private var active = false

    /** True when a change came while home wasn't started. The list is reloaded on return, or when a command needs it. */
    @Volatile
    private var stale = false

    /**
     * Call when home starts or stops.
     * - While home is away, each burst of changes from a sync doesn't reload every number.
     * - When home is back, the reload waits as long as a reload after a change does. So it runs
     *   after the work of returning home.
     */
    fun setActive(on: Boolean) {
        active = on
        if (on && stale) reload(RELOAD_DEBOUNCE_MS)
    }

    /** Returns the current contacts. If they changed while home wasn't started, it reads them again first. */
    fun current(): List<Contact> {
        if (stale && Looper.myLooper() != Looper.getMainLooper()) runCatching { reloadNow() }
        return _contacts.value
    }

    private val extras by lazy { ExtrasLink.get(context) }

    /** Set by tests: the picker on or off, whatever the build and Kolahal Extras say. */
    @Volatile
    private var pickerSet: Boolean? = null

    /**
     * True when contacts come from Android's contact picker, not READ_CONTACTS: the Play build, unless Kolahal Extras
     * can read the contacts now.
     */
    var picker: Boolean
        get() = pickerSet ?: (!BuildConfig.CONTACTS && !extras.ready(BuildFeature.CONTACTS))
        internal set(value) {
            synchronized(this) { pickerSet = value; _contacts.value = emptyList() }
            refresh()
        }

    private val prefs by lazy { Stores.PICKED_CONTACTS.open(context) }

    /** Whether the contacts can be read: READ_CONTACTS, or in the Play build Kolahal Extras' (as last read). */
    fun hasPermission() =
        if (BuildConfig.CONTACTS) ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        else extras.ready(BuildFeature.CONTACTS)

    init {
        // The Play build: Kolahal Extras installed, allowed, removed, or restarted (it may have missed a change).
        if (!BuildConfig.CONTACTS) ExtrasLink.get(context).listen { old, new ->
            val was = old.status(ExtrasContract.CONTACTS)
            val now = new.status(ExtrasContract.CONTACTS)
            if (was != now || old.session != new.session) {
                synchronized(this) { if (pickerSet == null) _contacts.value = emptyList() }
                refresh()
            }
        }
    }

    fun refresh() {
        if (picker) {
            // Use the same lock as remember and forget, so a load that started earlier can't finish after them.
            scope.launch {
                synchronized(this@ContactsRepository) {
                    if (picker) _contacts.value = loadPicked()
                }
            }
            return
        }
        if (!hasPermission()) return
        if (!observing) {
            // The phone's contacts, or in the Play build Kolahal Extras' signal that they changed.
            val uri = if (BuildConfig.CONTACTS) ContactsContract.Contacts.CONTENT_URI else extras.contactsUri
            observing = runCatching {
                context.contentResolver.registerContentObserver(
                    uri, true,
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        // Sync adapters send bursts of changes. Combine them into one reload.
                        override fun onChange(selfChange: Boolean) {
                            if (active) reload(RELOAD_DEBOUNCE_MS) else stale = true
                        }
                    },
                )
            }.isSuccess
        }
        reload(0)
    }

    private var reloadJob: Job? = null

    /** Cancels any pending reload and starts a new one after [delayMs]. */
    @Synchronized
    private fun reload(delayMs: Long) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            if (delayMs > 0) delay(delayMs.milliseconds)
            runCatching { reloadNow() }
        }
    }

    /** Lock that allows one load at a time. It is separate, so [reload] on the main thread never waits for a load. */
    private val loading = Any()

    /**
     * Reads every number again, on the caller's thread. Not kept when [picker] was switched on meanwhile (tests
     * switch it): a read that started before would replace the picked contacts.
     */
    private fun reloadNow() {
        synchronized(loading) {
            stale = false
            if (!hasPermission()) return
            val read = load()
            synchronized(this) { if (!picker) _contacts.value = read }
        }
    }

    private fun load(): List<Contact> {
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        // The same columns from the phone or, in the Play build, from Kolahal Extras (whose photos open through it).
        val rows = if (BuildConfig.CONTACTS) context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, ExtrasContract.CONTACT_COLUMNS, null, null,
            "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY}",
        ) else extras.query(extras.contactsUri)
        rows?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val number = c.getString(2)?.trim() ?: continue
                // The same number can be stored twice (for example, synced from two accounts). Keep one.
                if (!seen.add("$id:${number.filter { it.isDigit() || it == '+' }}")) continue
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, c.getInt(3), c.getString(4),
                ).toString().lowercase()
                out += Contact(
                    id = id,
                    name = name,
                    number = number,
                    label = label,
                    starred = c.getInt(5) != 0,
                    lookupUri = ContactsContract.Contacts.getLookupUri(id, c.getString(6)),
                    photoUri = c.getString(7)?.takeIf { it.isNotBlank() },
                )
            }
        }
        return out
    }

    /** Picker build: saves the numbers of a contact just picked. They replace the numbers saved for that name. */
    @Synchronized
    fun remember(picked: List<Contact>) {
        if (picked.isEmpty()) return
        val names = picked.map { it.name }.toSet()
        savePicked(picked + loadPicked().filter { it.name !in names })
    }

    /** Picker build: forgets every number saved under [name]. Returns false when there was none. */
    @Synchronized
    fun forget(name: String): Boolean {
        val all = loadPicked()
        val kept = all.filterNot { it.name.equals(name, ignoreCase = true) }
        if (kept.size == all.size) return false
        savePicked(kept)
        return true
    }

    /** Returns the saved picks, newest first, in the order of the saved list. */
    private fun loadPicked(): List<Contact> = runCatching {
        val a = JSONArray(prefs.getString(KEY_PICKED, "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Contact(
                o.optLong("id"),
                o.getString("name"),
                o.getString("number"),
                o.optString("label"),
                o.optBoolean("starred"),
                Uri.parse(o.optString("lookup"))
            )
        }
    }.getOrDefault(emptyList())

    private fun savePicked(list: List<Contact>) {
        val kept = list.take(MAX_PICKED)
        _contacts.value = kept
        val a = JSONArray()
        kept.forEach { c ->
            a.put(
                JSONObject().put("id", c.id).put("name", c.name).put("number", c.number)
                    .put("label", c.label).put("starred", c.starred)
                    .put("lookup", c.lookupUri.toString())
            )
        }
        prefs.edit { putString(KEY_PICKED, a.toString()) }
    }

    /** Returns contacts whose name or number matches. Best matches come first, and favorites first among equal matches. */
    fun search(query: String, limit: Int = 12): List<Contact> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val digits = q.filter { it.isDigit() }
        return current().asSequence()
            .mapNotNull { c ->
                val name = c.lowerName
                val score = when {
                    name == q -> 0
                    name.startsWith(q) -> 1
                    c.nameWords.any { it.startsWith(q) } -> 2
                    name.contains(q) -> 3
                    digits.length >= 3 && c.digits.contains(digits) -> 4
                    else -> null
                }
                score?.let { c to it }
            }
            .sortedWith(compareBy({ it.second }, { !it.first.starred }, { it.first.name }))
            .map { it.first }
            .take(limit)
            .toList()
    }

    companion object : Singleton<ContactsRepository>(::ContactsRepository) {
        private const val RELOAD_DEBOUNCE_MS = 1_000L
        private const val KEY_PICKED = "list"
        private const val MAX_PICKED = 200

        fun looksLikeNumber(s: String) =
            s.isNotBlank() && s.all { it.isDigit() || it in "+-() *#" } && s.count { it.isDigit() } >= 3
    }
}
