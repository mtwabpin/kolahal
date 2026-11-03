package com.mtwabpin.kolahal.contacts

import android.Manifest
import android.annotation.SuppressLint
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
) {
    /** What [ContactsRepository.search] compares, worked out at its first search rather than on every key. */
    internal val lowerName: String by lazy(LazyThreadSafetyMode.PUBLICATION) { name.lowercase() }
    internal val nameWords: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { lowerName.split(' ') }
    internal val digits: String by lazy(LazyThreadSafetyMode.PUBLICATION) { number.filter { it.isDigit() } }
}

/**
 * Contacts with phone numbers, cached in memory for instant `call` / `sms` suggestions.
 * Empty until READ_CONTACTS is granted; refreshes when the contacts database changes.
 *
 * The Play build ([picker]) has no READ_CONTACTS: its contacts are the ones picked with Android's
 * contact picker ([ContactPicker]), remembered on the phone so a name works again without picking.
 */
class ContactsRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    private var observing = false

    /**
     * Home is started: a change in the contacts reloads them as it comes. Not before home first
     * starts: a process started for a job (a feed, a reminder) has no home to show them.
     */
    @Volatile private var active = false

    /** A change came while home wasn't started: the list is reloaded on return, or when a command needs it. */
    @Volatile private var stale = false

    /**
     * Home started or stopped. Away, a sync's bursts of changes don't each reload every number. Back,
     * the reload waits as long as one for a change does, past the return's own work.
     */
    fun setActive(on: Boolean) {
        active = on
        if (on && stale) reload(RELOAD_DEBOUNCE_MS)
    }

    /** The contacts as they are now: read again first if they changed while home wasn't started. */
    fun current(): List<Contact> {
        if (stale && Looper.myLooper() != Looper.getMainLooper()) runCatching { reloadNow() }
        return _contacts.value
    }

    /** Contacts come from Android's contact picker, not READ_CONTACTS: the Play build. */
    var picker = !BuildConfig.CONTACTS
        internal set(value) { field = value; _contacts.value = emptyList(); refresh() }

    private val prefs by lazy { context.getSharedPreferences(PICKED, Context.MODE_PRIVATE) }

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

    fun refresh() {
        if (picker) {
            // In step with remember and forget, so a load that started first can't land after them.
            scope.launch { synchronized(this@ContactsRepository) { _contacts.value = loadPicked() } }
            return
        }
        if (!hasPermission()) return
        if (!observing) {
            observing = true
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    // Sync adapters fire bursts of changes; coalesce them into one reload.
                    override fun onChange(selfChange: Boolean) {
                        if (active) reload(RELOAD_DEBOUNCE_MS) else stale = true
                    }
                },
            )
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

    /** One load at a time, under a lock of its own: [reload], on the main thread, never waits for one. */
    private val loading = Any()

    /** Reads every number again, on the caller's thread. */
    private fun reloadNow() {
        synchronized(loading) {
            stale = false
            if (!hasPermission()) return
            _contacts.value = load()
        }
    }

    private fun load(): List<Contact> {
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY}",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val number = c.getString(2)?.trim() ?: continue
                // Same number stored twice (e.g. synced from two accounts): keep one.
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
                )
            }
        }
        return out
    }

    /** Picker: remembers the numbers of a contact just picked, replacing what was kept for it. */
    @Synchronized
    fun remember(picked: List<Contact>) {
        if (picked.isEmpty()) return
        val names = picked.map { it.name }.toSet()
        savePicked(picked + loadPicked().filter { it.name !in names })
    }

    /** Picker: forgets every number kept under [name]; false when none was. */
    @Synchronized
    fun forget(name: String): Boolean {
        val all = loadPicked()
        val kept = all.filterNot { it.name.equals(name, ignoreCase = true) }
        if (kept.size == all.size) return false
        savePicked(kept)
        return true
    }

    /** Newest pick first, like the list it was saved from. */
    private fun loadPicked(): List<Contact> = runCatching {
        val a = JSONArray(prefs.getString(KEY_PICKED, "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Contact(o.optLong("id"), o.getString("name"), o.getString("number"), o.optString("label"), o.optBoolean("starred"), Uri.parse(o.optString("lookup")))
        }
    }.getOrDefault(emptyList())

    private fun savePicked(list: List<Contact>) {
        val kept = list.take(MAX_PICKED)
        _contacts.value = kept
        val a = JSONArray()
        kept.forEach { c ->
            a.put(JSONObject().put("id", c.id).put("name", c.name).put("number", c.number).put("label", c.label).put("starred", c.starred).put("lookup", c.lookupUri.toString()))
        }
        prefs.edit { putString(KEY_PICKED, a.toString()) }
    }

    /** Name or number matches, the best match first and favourites first among equals. */
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

    companion object {
        private const val RELOAD_DEBOUNCE_MS = 1_000L
        /** Picked contacts' prefs: left out of cloud backups (data_extraction_rules.xml). */
        private const val PICKED = "picked_contacts"
        private const val KEY_PICKED = "list"
        private const val MAX_PICKED = 200

        @SuppressLint("StaticFieldLeak") // application context only

        @Volatile
        private var instance: ContactsRepository? = null

        fun get(context: Context): ContactsRepository = instance ?: synchronized(this) {
            instance ?: ContactsRepository(context.applicationContext).also { instance = it }
        }

        fun looksLikeNumber(s: String) = s.isNotBlank() && s.all { it.isDigit() || it in "+-() *#" } && s.count { it.isDigit() } >= 3
    }
}
