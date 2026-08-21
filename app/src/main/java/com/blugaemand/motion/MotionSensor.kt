package com.blugaemand.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager

/**
 * The gyroscope, turned into a stream of [MotionAim]s through [aimOf].
 *
 * Registered only while motion is switched on: a gyroscope left running costs battery for a
 * reading nothing would use, and this is off by default. The listener is called on a sensor thread,
 * so [onAim] must be safe to call from one — the activity's is, it writes a volatile field.
 *
 * Held by the activity rather than by the service. The service's job is what goes on the wire, and
 * what the phone is doing in space is an input to the state the activity assembles, exactly like a
 * thumb on the glass.
 */
class MotionSensor(private val context: Context, private val onAim: (MotionAim) -> Unit) :
    SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val gyroscope = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    /** Whether this phone has a gyroscope at all. Plenty of cheap ones do not. */
    val available: Boolean get() = gyroscope != null

    private var settings = MotionSettings()
    private var started = false
    private var listening = false

    /**
     * Takes the settings the menu is on, starting or stopping the sensor to match.
     *
     * Reports a centred aim on the way down, so switching motion off releases the stick rather than
     * leaving it wherever the last reading put it.
     */
    fun configure(settings: MotionSettings) {
        this.settings = settings
        sync()
    }

    fun start() {
        started = true
        sync()
    }

    fun stop() {
        started = false
        sync()
    }

    private fun sync() {
        val wanted = started && settings.enabled && gyroscope != null
        if (wanted == listening) return
        listening = wanted
        if (wanted) {
            manager?.registerListener(this, gyroscope, SAMPLE_PERIOD_US)
        } else {
            manager?.unregisterListener(this)
            onAim(MotionAim.NONE)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        onAim(
            settings.aimOf(
                event.values[0],
                event.values[1],
                event.values[2],
                screenRotationDegrees(),
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * How far the screen is turned from the phone's natural orientation.
     *
     * Read on every event rather than cached: the pad is `sensorLandscape`, so it flips between the
     * two landscapes without the activity being recreated, and a cached rotation would leave aiming
     * inverted for as long as the phone stayed the other way up.
     */
    private fun screenRotationDegrees(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private companion object {
        /**
         * 100 Hz, matching the ceiling the report pump puts on the wire. Faster would produce
         * readings that are averaged away before anything sees them, and a gyroscope is not free.
         */
        const val SAMPLE_PERIOD_US = 10_000
    }
}
