package com.example.controller.model

import com.example.controller.bluetooth.AxisSlot
import org.json.JSONObject
import java.util.UUID

enum class ElementType { BUTTON, JOYSTICK, SLIDER, DPAD, GYRO_STEER }

/**
 * A single control the user has placed on the canvas.
 * x/y/w/h are normalized 0..1 fractions of the screen so the exact same
 * preset renders correctly on any phone size (editor and runner both use
 * the same normalized coordinates).
 */
data class ControlElement(
    val id: String = UUID.randomUUID().toString(),
    var type: ElementType,
    var label: String,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,

    // BUTTON / part of DPAD
    var buttonIndex: Int = -1,          // 0..15

    // JOYSTICK uses two axis slots, SLIDER/GYRO_STEER use axisX only
    var axisX: AxisSlot? = null,
    var axisY: AxisSlot? = null,

    var sliderVertical: Boolean = true,

    // GYRO_STEER only: how many degrees of physical rotation = full lock
    var gyroLockToLockDeg: Float = 180f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("label", label)
        put("x", x); put("y", y); put("w", w); put("h", h)
        put("buttonIndex", buttonIndex)
        put("axisX", axisX?.name ?: "")
        put("axisY", axisY?.name ?: "")
        put("sliderVertical", sliderVertical)
        put("gyroLockToLockDeg", gyroLockToLockDeg)
    }

    companion object {
        fun fromJson(o: JSONObject): ControlElement = ControlElement(
            id = o.getString("id"),
            type = ElementType.valueOf(o.getString("type")),
            label = o.getString("label"),
            x = o.getDouble("x").toFloat(),
            y = o.getDouble("y").toFloat(),
            w = o.getDouble("w").toFloat(),
            h = o.getDouble("h").toFloat(),
            buttonIndex = o.optInt("buttonIndex", -1),
            axisX = o.optString("axisX", "").let { if (it.isEmpty()) null else AxisSlot.valueOf(it) },
            axisY = o.optString("axisY", "").let { if (it.isEmpty()) null else AxisSlot.valueOf(it) },
            sliderVertical = o.optBoolean("sliderVertical", true),
            gyroLockToLockDeg = o.optDouble("gyroLockToLockDeg", 180.0).toFloat()
        )
    }
}
