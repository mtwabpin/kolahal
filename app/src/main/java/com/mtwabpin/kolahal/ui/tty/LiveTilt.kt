/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.mtwabpin.kolahal.script.ShaderTilt

/** Takes one reading of gravity, as [ShaderTilt.gravity] does. Plain numbers: a reading allocates nothing. */
internal fun interface OnGravity {
    fun on(x: Float, y: Float, z: Float, shaky: Boolean, rotation: Int, atNs: Long)
}

/** Gives the phone's gravity on the main thread while it is started: [PhoneGravity], or a test's own. */
internal interface GravitySource {
    /** Starts giving readings [to]. False when there is nothing to read. */
    fun start(to: OnGravity): Boolean
    fun stop()
}

/**
 * The phone's lean, for the shaders that declare `tilt` ([ShaderTilt] works it out). The surfaces that
 * draw one now [hold] it. They share one listener, which is there only while one of them does. When the
 * last lets go, the lean eases back to 0, and then nothing runs.
 *
 * Main thread only.
 */
internal object LiveTilt {
    private val tilt = ShaderTilt()
    private val holders = HashSet<Any>()
    private val main = Handler(Looper.getMainLooper())
    private var source: GravitySource? = null

    /** Grows with each change, so a drawing that read it is drawn again. */
    private var changes by mutableIntStateOf(0)

    /** The uniform's numbers. Reading them alone doesn't draw again when they change ([follow]). */
    val x: Float get() = tilt.x
    val y: Float get() = tilt.y

    /** Called in a drawing, it draws that again at each change. */
    fun follow(): Int = changes

    private val reading = OnGravity { x, y, z, shaky, rotation, atNs ->
        if (holders.isNotEmpty() && tilt.gravity(x, y, z, shaky, rotation, atNs)) changes++
    }

    private val back = object : Runnable {
        override fun run() {
            val more = tilt.ease(STEP_MS / 1_000f)
            changes++
            if (more) main.postDelayed(this, STEP_MS)
        }
    }

    /** [who] draws the lean from now on. When nobody did before, it is read from what [make] makes. */
    fun hold(context: Context, who: Any, make: (Context) -> GravitySource) {
        if (!holders.add(who) || source != null) return
        tilt.restart()
        val made = make(context.applicationContext)
        // Without a sensor it stays at 0, or goes on back to it.
        if (!made.start(reading)) return
        main.removeCallbacks(back)
        source = made
    }

    /** [who] doesn't draw it any more. */
    fun release(who: Any) {
        if (!holders.remove(who) || holders.isNotEmpty()) return
        source?.stop()
        source = null
        main.removeCallbacks(back)
        if (!tilt.resting) main.postDelayed(back, STEP_MS)
    }

    /** Between two steps back to 0. */
    private const val STEP_MS = 1_000L / ShaderTilt.FPS
}

/**
 * Gravity from the phone's own sensors: the gravity sensor, or the accelerometer of a phone without
 * one. At this rate, about 50 readings a second, neither needs a permission.
 */
internal class PhoneGravity(context: Context) : GravitySource, SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val sensor = gravity ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val display =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    private var to: OnGravity? = null

    override fun start(to: OnGravity): Boolean {
        val from = sensor ?: return false
        this.to = to
        // Android refuses when an app has too many listeners: home goes on without it.
        val on = runCatching {
            sensors?.registerListener(
                this,
                from,
                SensorManager.SENSOR_DELAY_GAME,
                Handler(Looper.getMainLooper())
            )
        }.getOrNull() == true
        if (!on) this.to = null
        return on
    }

    override fun stop() {
        to = null
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size < 3) return
        to?.on(v[0], v[1], v[2], gravity == null, display?.rotation ?: 0, event.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
