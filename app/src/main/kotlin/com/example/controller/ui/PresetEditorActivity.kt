package com.example.controller.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.example.controller.bluetooth.AxisSlot
import com.example.controller.model.ControlElement
import com.example.controller.model.ElementType
import com.example.controller.model.Preset
import com.example.controller.storage.PresetRepository

class PresetEditorActivity : Activity() {

    private lateinit var repo: PresetRepository
    private lateinit var preset: Preset
    private lateinit var canvas: FrameLayout
    private val elementViews = mutableMapOf<String, ElementView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PresetRepository(this)

        val presetId = intent.getStringExtra(EXTRA_PRESET_ID)
        preset = presetId?.let { repo.load(it) } ?: Preset.defaultGamepad()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
        }
        toolbar.addView(smallButton("Button") { addElement(ElementType.BUTTON) })
        toolbar.addView(smallButton("Joystick") { addElement(ElementType.JOYSTICK) })
        toolbar.addView(smallButton("Slider") { addElement(ElementType.SLIDER) })
        toolbar.addView(smallButton("D-Pad") { addElement(ElementType.DPAD) })
        toolbar.addView(smallButton("Gyro") { addElement(ElementType.GYRO_STEER) })
        toolbar.addView(smallButton("Save") { savePreset() })

        canvas = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(0xFF14161C.toInt())
        }

        root.addView(toolbar)
        root.addView(canvas)
        setContentView(root)

        // Elements are (re)laid out once the canvas has an actual measured size.
        canvas.post { preset.elements.forEach { addElementView(it) } }
    }

    private fun smallButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 11f
            setOnClickListener { onClick() }
        }

    private fun addElement(type: ElementType) {
        val el = when (type) {
            ElementType.BUTTON -> ControlElement(type = type, label = "BTN", x = 0.4f, y = 0.4f, w = 0.14f, h = 0.08f, buttonIndex = nextFreeButtonIndex())
            ElementType.JOYSTICK -> ControlElement(type = type, label = "Stick", x = 0.35f, y = 0.35f, w = 0.28f, h = 0.28f, axisX = AxisSlot.X, axisY = AxisSlot.Y)
            ElementType.SLIDER -> ControlElement(type = type, label = "Slider", x = 0.45f, y = 0.2f, w = 0.12f, h = 0.5f, axisX = AxisSlot.RY)
            ElementType.DPAD -> ControlElement(type = type, label = "D-Pad", x = 0.1f, y = 0.35f, w = 0.22f, h = 0.22f)
            ElementType.GYRO_STEER -> ControlElement(type = type, label = "Steer", x = 0.3f, y = 0.4f, w = 0.4f, h = 0.18f, axisX = AxisSlot.X)
        }
        preset.elements.add(el)
        addElementView(el)
    }

    private fun nextFreeButtonIndex(): Int {
        val used = preset.elements.filter { it.type == ElementType.BUTTON }.map { it.buttonIndex }.toSet()
        return (0..15).firstOrNull { it !in used } ?: 0
    }

    private fun addElementView(el: ControlElement) {
        val view = ElementView(this, el, editable = true)
        val lp = FrameLayout.LayoutParams(
            (el.w * canvas.width).toInt().coerceAtLeast(60),
            (el.h * canvas.height).toInt().coerceAtLeast(60)
        )
        canvas.addView(view, lp)
        view.x = el.x * canvas.width
        view.y = el.y * canvas.height
        view.onMoved = { nx, ny -> el.x = nx; el.y = ny }
        view.onTap = { showConfigDialog(el, view) }
        elementViews[el.id] = view
    }

    private fun showConfigDialog(el: ControlElement, view: ElementView) {
        val options = mutableListOf("Rename", "Delete")
        if (el.type == ElementType.BUTTON) options.add(0, "Set button #")
        if (el.type == ElementType.SLIDER || el.type == ElementType.GYRO_STEER) options.add(0, "Set axis")
        if (el.type == ElementType.GYRO_STEER) options.add(0, "Set lock-to-lock degrees")

        AlertDialog.Builder(this)
            .setTitle(el.label)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Rename" -> promptText("New label", el.label) { el.label = it; view.invalidate() }
                    "Delete" -> { canvas.removeView(view); preset.elements.remove(el); elementViews.remove(el.id) }
                    "Set button #" -> promptText("Button index (0-15)", el.buttonIndex.toString()) {
                        it.toIntOrNull()?.let { i -> el.buttonIndex = i.coerceIn(0, 15) }
                    }
                    "Set axis" -> {
                        val names = AxisSlot.values().map { it.name }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Axis")
                            .setItems(names) { _, i -> el.axisX = AxisSlot.values()[i] }
                            .show()
                    }
                    "Set lock-to-lock degrees" -> promptText("Degrees (e.g. 180)", el.gyroLockToLockDeg.toString()) {
                        it.toFloatOrNull()?.let { d -> el.gyroLockToLockDeg = d }
                    }
                    else -> Unit
                }
            }
            .show()
    }

    private fun promptText(title: String, current: String, onSet: (String) -> Unit) {
        val input = EditText(this).apply { setText(current) }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onSet(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePreset() {
        val nameInput = EditText(this).apply { setText(preset.name) }
        AlertDialog.Builder(this)
            .setTitle("Preset name")
            .setView(nameInput)
            .setPositiveButton("Save") { _, _ ->
                preset.name = nameInput.text.toString().ifBlank { preset.name }
                repo.save(preset)
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val EXTRA_PRESET_ID = "preset_id"
        const val EXTRA_PRESET_CATEGORY = "preset_category"
    }
}
