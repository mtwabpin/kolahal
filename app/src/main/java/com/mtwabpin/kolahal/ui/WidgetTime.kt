/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

// ---- the time, for media, the clock and the countdown ----------------------------------------

/**
 * The time now, in milliseconds.
 * - It moves by the minute, with the header's clock ([rememberNow]). That tick also comes when
 *   home is back on screen and when the clock is set.
 * - With [seconds] it moves every second ([EverySecond]).
 * - It is a state: read it where the time is drawn, so a tick draws that again, not the card.
 */
@Composable
internal fun rememberMillis(seconds: Boolean): State<Long> {
    val (minute, _) = rememberNow()
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(minute) { now.longValue = System.currentTimeMillis() }
    EverySecond(seconds) { now.longValue = it }
    return now
}

/**
 * Calls [onSecond] with the time in milliseconds when each second starts.
 * - Only while [on], the card can be seen ([cardSeen]) and home is on screen. A card nobody sees
 *   ticks for nothing.
 * - The clock, media and countdown cards use it.
 */
@Composable
internal fun EverySecond(on: Boolean, onSecond: (Long) -> Unit) {
    if (!on || !cardSeen()) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tick by rememberUpdatedState(onSecond)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val ms = System.currentTimeMillis()
                tick(ms)
                // Wake when the next second starts.
                delay(1000 - ms % 1000)
            }
        }
    }
}
