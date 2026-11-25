/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** The phone's state for [ShaderClock]. */
object ShaderPhone {
    fun of(context: Context): ShaderClock.Phone {
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.getSystemService(BatteryManager::class.java)
        return ShaderClock.Phone(
            batterySaver = power?.isPowerSaveMode == true,
            animationsOff = !ValueAnimator.areAnimatorsEnabled(),
            hot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE,
            charging = battery?.isCharging == true,
            batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 } ?: 100,
        )
    }

    /** Whether this is a low-memory phone, such as one with Android Go: it can't give Display over other apps ([ShaderOverApps.phoneGives]). */
    fun lowRam(context: Context): Boolean =
        runCatching { context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true }.getOrDefault(false)
}
