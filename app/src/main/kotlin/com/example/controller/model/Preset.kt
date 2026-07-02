package com.example.controller.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PresetCategory { GAMEPAD, WHEEL, CUSTOM }

data class Preset(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var category: PresetCategory,
    val elements: MutableList<ControlElement> = mutableListOf(),
    // Gyro zero-point offset (raw azimuth in degrees) captured last time the
    // user pressed "Set Zero" for this preset. Stored per-preset because the
    // phone might be mounted at a different angle for different setups.
    var gyroZeroOffsetDeg: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("category", category.name)
        put("gyroZeroOffsetDeg", gyroZeroOffsetDeg)
        put("elements", JSONArray(elements.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject): Preset {
            val elementsArr = o.getJSONArray("elements")
            val elements = mutableListOf<ControlElement>()
            for (i in 0 until elementsArr.length()) {
                elements.add(ControlElement.fromJson(elementsArr.getJSONObject(i)))
            }
            return Preset(
                id = o.getString("id"),
                name = o.getString("name"),
                category = PresetCategory.valueOf(o.getString("category")),
                elements = elements,
                gyroZeroOffsetDeg = o.optDouble("gyroZeroOffsetDeg", 0.0).toFloat()
            )
        }

        /** Sensible starting layouts so the user isn't building from a blank canvas. */
        fun defaultGamepad(name: String = "PS5 Style"): Preset {
            val p = Preset(name = name, category = PresetCategory.GAMEPAD)
            p.elements.addAll(
                listOf(
                    ControlElement(type = ElementType.JOYSTICK, label = "L Stick", x = 0.06f, y = 0.55f, w = 0.28f, h = 0.28f, axisX = com.example.controller.bluetooth.AxisSlot.X, axisY = com.example.controller.bluetooth.AxisSlot.Y),
                    ControlElement(type = ElementType.JOYSTICK, label = "R Stick", x = 0.66f, y = 0.55f, w = 0.28f, h = 0.28f, axisX = com.example.controller.bluetooth.AxisSlot.Z, axisY = com.example.controller.bluetooth.AxisSlot.RX),
                    ControlElement(type = ElementType.DPAD, label = "D-Pad", x = 0.06f, y = 0.15f, w = 0.22f, h = 0.22f),
                    ControlElement(type = ElementType.BUTTON, label = "△", x = 0.80f, y = 0.10f, w = 0.10f, h = 0.10f, buttonIndex = 3),
                    ControlElement(type = ElementType.BUTTON, label = "○", x = 0.87f, y = 0.18f, w = 0.10f, h = 0.10f, buttonIndex = 1),
                    ControlElement(type = ElementType.BUTTON, label = "✕", x = 0.80f, y = 0.26f, w = 0.10f, h = 0.10f, buttonIndex = 0),
                    ControlElement(type = ElementType.BUTTON, label = "□", x = 0.73f, y = 0.18f, w = 0.10f, h = 0.10f, buttonIndex = 2),
                    ControlElement(type = ElementType.BUTTON, label = "L1", x = 0.06f, y = 0.02f, w = 0.16f, h = 0.08f, buttonIndex = 4),
                    ControlElement(type = ElementType.BUTTON, label = "R1", x = 0.78f, y = 0.02f, w = 0.16f, h = 0.08f, buttonIndex = 5)
                )
            )
            return p
        }

        fun defaultWheel(name: String = "F1 Wheel"): Preset {
            val p = Preset(name = name, category = PresetCategory.WHEEL)
            p.elements.addAll(
                listOf(
                    ControlElement(type = ElementType.GYRO_STEER, label = "Steering (tilt)", x = 0.3f, y = 0.35f, w = 0.4f, h = 0.2f, axisX = com.example.controller.bluetooth.AxisSlot.X, gyroLockToLockDeg = 180f),
                    ControlElement(type = ElementType.SLIDER, label = "Throttle", x = 0.85f, y = 0.15f, w = 0.12f, h = 0.55f, axisX = com.example.controller.bluetooth.AxisSlot.Y, sliderVertical = true),
                    ControlElement(type = ElementType.SLIDER, label = "Brake", x = 0.03f, y = 0.15f, w = 0.12f, h = 0.55f, axisX = com.example.controller.bluetooth.AxisSlot.Z, sliderVertical = true),
                    ControlElement(type = ElementType.BUTTON, label = "Shift-", x = 0.03f, y = 0.75f, w = 0.16f, h = 0.1f, buttonIndex = 0),
                    ControlElement(type = ElementType.BUTTON, label = "Shift+", x = 0.81f, y = 0.75f, w = 0.16f, h = 0.1f, buttonIndex = 1)
                )
            )
            return p
        }
    }
}
