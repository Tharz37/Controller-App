package com.example.controller.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Wheel steering from phone rotation.
 *
 * Uses the rotation vector sensor (fused gyro + accel + mag, drift-free
 * enough for this purpose) rather than raw gyro integration, so the value
 * doesn't slowly drift the way integrating angular velocity would.
 *
 * "Zero point" problem: the raw azimuth from the sensor is relative to
 * magnetic north, not to however the user is holding the phone. setZero()
 * captures whatever the current raw reading is as `zeroOffsetDeg`; every
 * subsequent reading is reported relative to that, so however the phone
 * happened to be tilted when the user tapped "Set Zero" becomes centre.
 */
class MotionManager(context: Context, private var zeroOffsetDeg: Float = 0f) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Raw azimuth-equivalent angle in degrees, -180..180, updated on every sensor event.
    @Volatile var rawAngleDeg: Float = 0f
        private set

    fun start() {
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        // orientationAngles[2] = roll, which is what changes when you tilt the phone
        // left/right like a steering wheel while holding it landscape.
        rawAngleDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Call when the user presses "Set Zero" — whatever tilt they're holding now becomes centre. */
    fun setZero() {
        zeroOffsetDeg = rawAngleDeg
    }

    fun currentZeroOffset(): Float = zeroOffsetDeg

    fun restoreZero(offsetDeg: Float) {
        zeroOffsetDeg = offsetDeg
    }

    /**
     * Steering value scaled to a signed byte range (-127..127).
     * lockToLockDeg is the total rotation (both directions combined) that
     * corresponds to full lock, e.g. 180 means 90 degrees either side of
     * zero maps to full lock.
     */
    fun steeringByte(lockToLockDeg: Float): Byte {
        var delta = rawAngleDeg - zeroOffsetDeg
        // normalize to -180..180 in case of wraparound near +/-180
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        val halfRange = lockToLockDeg / 2f
        val normalized = (delta / halfRange).coerceIn(-1f, 1f)
        return (normalized * 127f).toInt().toByte()
    }
}
