/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.Quietly
import com.mtwabpin.kolahal.unlock.UnlockCounter
import com.mtwabpin.kolahal.usage.AppUsage
import com.mtwabpin.kolahal.usage.UsageAccess
import com.mtwabpin.kolahal.usage.UsageHistory
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Works out Kolahal's values from the phone's use ([UsageValues]) and hands them to [ShaderValues], and so to the
 * live wallpaper's process with the rest of its state. Main process; started from home's activity.
 * - Only while a shader in use follows one ([followed]): home's (`screen.shader`), or the live wallpaper's while
 *   Kolahal's is the phone's. While the Shaders screen is open, also those that the shaders it lists follow
 *   ([list]), so their details and previews show them. Only the values followed are handed over, and only when
 *   one changed.
 * - When: after an unlock ([UnlockCounter.unlocked], where `@on unlock` scripts fire); when home comes to the
 *   front, if the last read is [EVERY_MS] old; every [EVERY_MS] while home stays in front; and at once when a
 *   shader starts to follow one that wasn't read, or the Shaders screen opens with one that wasn't.
 * - What it costs: the unlocks come from [UnlockCounter] (a finished day is counted once and kept; today reads the
 *   unlock events since its last count). Screen time and `away` are one read of Android's usage events: from
 *   today's midnight, from yesterday's with `away`, and from the oldest day not read yet the first time in a day,
 *   as a finished day's screen time is kept. [UsageHistory] asks Android which days it has, at most once an hour.
 * - Screen time and `away` need usage access ([UsageAccess]): without it they have no value, and a parameter that
 *   follows one takes its slider. The unlocks are counted without it, as far as [UnlockCounter] sees them.
 */
object UsageWatch {
    /** How often it reads while home stays in front, at most. */
    const val EVERY_MS = 5 * 60_000L

    /** How long after an unlock it reads: Android writes the unlock's event a moment after it says so. */
    private const val AFTER_UNLOCK_MS = 2_000L

    /** Changes of settings that come together are looked at once. */
    private const val TOGETHER_MS = 300L

    private val lock = Mutex()

    /** What the shaders in use follow, of these values ([followed]). */
    @Volatile
    private var following: Set<String> = emptySet()

    /** What the last read was for. */
    @Volatile
    private var covered: Set<String> = emptySet()

    /** When the last read was, by [SystemClock.elapsedRealtime]; 0 before the first. */
    @Volatile
    private var readAt = 0L

    /** The parameters of each shader that the Shaders screen lists, by name, while it is open ([list]). */
    private val listed = MutableStateFlow<Map<String, List<ShaderParam>>>(emptyMap())

    /**
     * The Shaders screen lists [shaders], each with its parameters; none once it closes. While it is open, the values
     * that they follow are read as those of the shaders in use are.
     */
    fun list(shaders: Map<String, List<ShaderParam>>) {
        listed.value = shaders
    }

    /** Each finished day's screen time, in ms: such a day doesn't change, so it is read once. */
    private val pastScreen = ConcurrentHashMap<LocalDate, Long>()

    /** Runs while home's activity does: [lifecycle] is its own, which says when home is in front. */
    @OptIn(FlowPreview::class)
    suspend fun run(context: Context, lifecycle: Lifecycle): Unit = coroutineScope {
        launch {
            combine(Config.get(context).version, WallpaperChoice.name, ShaderParams.follows, Shaders.version, listed) { _, _, _, _, _ -> }
                .debounce(TOGETHER_MS)
                .collect {
                    following = withContext(Dispatchers.IO) { followed(context) }
                    // One that wasn't read is read at once; when none is followed any more, the values go.
                    if (!covered.containsAll(following) || (following.isEmpty() && covered.isNotEmpty())) read(context)
                }
        }
        launch {
            UnlockCounter.get(context).unlocked.collect {
                if (following.isEmpty()) return@collect
                delay(AFTER_UNLOCK_MS)
                read(context)
            }
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Kolahal's live wallpaper may have been set or replaced in Android's settings meanwhile.
            following = withContext(Dispatchers.IO) { followed(context) }
            if (!covered.containsAll(following)) read(context)
            while (true) {
                val since = SystemClock.elapsedRealtime() - readAt
                if (readAt != 0L && since < EVERY_MS) delay(EVERY_MS - since) else read(context)
            }
        }
    }

    /** Reads what [following] needs, and hands it over. Nothing is read while it is empty. */
    private suspend fun read(context: Context) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val ids = following
                readAt = SystemClock.elapsedRealtime()
                covered = ids
                Quietly.run(context, "Reading the phone's use for shaders") {
                    val use = if (ids.isEmpty()) emptyMap() else values(context, ids).filterKeys { it in ids }
                    if (ShaderValues.setUse(use)) ShaderValues.keepUse(context, use)
                }
            }
        }
    }

    /**
     * The values of [UsageValues] that the shaders in use follow: home's, and the live wallpaper's while Kolahal's
     * is the phone's; and those of the shaders that the Shaders screen lists while it is open ([list]). Reads their
     * files and asks Android: not on the main thread.
     */
    private fun followed(context: Context): Set<String> {
        val home = Config.get(context)[Keys.SCREEN_SHADER].takeIf { it != ShaderCatalog.OFF && Shaders.exists(it) }
        val wallpaper = if (WallpaperChoice.isWallpaper(context)) WallpaperChoice.name.value?.takeIf(Shaders::exists) ?: WallpaperChoice.DEFAULT else null
        val shown = listed.value
        return (listOfNotNull(home, wallpaper).distinct()
            .flatMap { name -> Shaders.params(context, name).mapNotNull { ShaderParams.follow(name, it) } } +
                shown.flatMap { (name, params) -> params.mapNotNull { ShaderParams.follow(name, it) } })
            .filterTo(HashSet()) { UsageValues.has(it) }
    }

    /**
     * The values of the groups that [ids] are in, as they are now (more than [ids]: a day's values come together).
     * Without usage access, none of screen time or `away`. Reads Android's data: not on the main thread.
     */
    fun values(context: Context, ids: Collection<String>): Map<String, String> {
        val out = HashMap<String, String>()
        if (UsageValues.needsUnlocks(ids)) {
            out += UsageValues.unlocks(UnlockCounter.get(context).history(UsageValues.DAYS).map { it.second })
        }
        val screen = UsageValues.needsScreen(ids)
        val away = UsageValues.AWAY in ids
        if ((screen || away) && UsageAccess.granted(context)) out += fromEvents(context, screen, away)
        return out
    }

    /** Screen time's values and `away`, from one read of the usage events. */
    private fun fromEvents(context: Context, screen: Boolean, away: Boolean): Map<String, String> {
        val usage = AppUsage(context)
        val today = LocalDate.now(ZoneId.systemDefault())
        // The days Android has data for, today the last: a day before them has none, which isn't 0.
        val days = if (screen) UsageHistory.days(context, UsageValues.DAYS, today) else listOf(today)
        pastScreen.keys.removeAll { it.isBefore(today.minusDays(UsageValues.DAYS.toLong())) }
        val missing = days.filter { it.isBefore(today) && !pastScreen.containsKey(it) }
        // `away` may start before midnight: the lock before the first unlock of the day.
        val from = (missing + today + listOfNotNull(today.minusDays(1).takeIf { away })).minBy { it.toEpochDay() }
        val now = System.currentTimeMillis()
        val read = usage.read(usage.startOf(from), now, locks = away)
        for (day in missing) pastScreen[day] = usage.total(read.between(usage.startOf(day), usage.startOf(day.plusDays(1))))
        val out = HashMap<String, String>()
        if (screen) {
            val start = usage.startOf(today)
            val sessions = read.between(start, now)
            val perDay = days.map { if (it == today) usage.total(sessions) else pastScreen[it] ?: 0L }
            out += UsageValues.screen(perDay, UsageValues.blocks(sessions.map { it.start to it.end }, start, usage.startOf(today.plusDays(1))))
        }
        if (away) UsageValues.away(read.locks)?.let { out[UsageValues.AWAY] = UsageValues.number(it.toDouble()) }
        return out
    }
}
