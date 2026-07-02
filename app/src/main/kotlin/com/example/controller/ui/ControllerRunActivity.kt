package com.example.controller.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.example.controller.bluetooth.AxisSlot
import com.example.controller.bluetooth.HidControllerManager
import com.example.controller.model.ControlElement
import com.example.controller.model.ElementType
import com.example.controller.model.Preset
import com.example.controller.sensors.MotionManager
import com.example.controller.storage.PresetRepository

class ControllerRunActivity : Activity() {

    private lateinit var repo: PresetRepository
    private lateinit var preset: Preset
    private lateinit var hid: HidControllerManager
    private lateinit var motion: MotionManager
    private lateinit var canvas: FrameLayout
    private lateinit var statusText: TextView

    // Live state, rebuilt into a 6-byte report every tick.
    private val axisValues = FloatArray(6) // -1f..1f per AxisSlot
    private var buttonMask = 0
    private var hat = 8 // neutral

    private val handler = Handler(Looper.getMainLooper())
    private val tickIntervalMs = 20L // 50Hz
    private var gyroElement: ControlElement? = null
    private lateinit var gyroView: ElementView

    private val tick = object : Runnable {
        override fun run() {
            gyroElement?.let { el ->
                val v = motion.steeringByte(el.gyroLockToLockDeg)
                axisValues[el.axisX?.byteIndex ?: AxisSlot.X.byteIndex] = v / 127f
                if (::gyroView.isInitialized) gyroView.externalGyroValue = v / 127f
            }
            sendCurrentReport()
            handler.postDelayed(this, tickIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PresetRepository(this)
        val presetId = intent.getStringExtra(PresetEditorActivity.EXTRA_PRESET_ID)
        preset = presetId?.let { repo.load(it) } ?: run {
            Toast.makeText(this, "Preset not found", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        repo.lastUsedPresetId = preset.id

        motion = MotionManager(this, preset.gyroZeroOffsetDeg)

        val root = FrameLayout(this)
        canvas = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF14161C.toInt())
        }
        root.addView(canvas)

        statusText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            text = "Connecting..."
            setPadding(16, 16, 16, 16)
        }
        root.addView(statusText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.START })

        val zeroButton = Button(this).apply {
            text = "Set Zero"
            setOnClickListener {
                motion.setZero()
                preset.gyroZeroOffsetDeg = motion.currentZeroOffset()
                repo.save(preset)
                Toast.makeText(this@ControllerRunActivity, "Zero point set", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(zeroButton, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = 16; rightMargin = 16 })

        setContentView(root)

        hid = HidControllerManager(this, repo) { state ->
            runOnUiThread {
                statusText.text = when (state) {
                    is HidControllerManager.ConnectionState.Connected -> "Connected: ${state.deviceName}"
                    HidControllerManager.ConnectionState.ReadyWaitingForConnection -> "Ready — connect from PC"
                    HidControllerManager.ConnectionState.Registering -> "Registering..."
                    HidControllerManager.ConnectionState.Disconnected -> "Disconnected"
                    is HidControllerManager.ConnectionState.Error -> "Error: ${state.message}"
                }
            }
        }
        hid.start()

        canvas.post { preset.elements.forEach { addElementView(it) } }
    }

    private fun addElementView(el: ControlElement) {
        val view = ElementView(this, el, editable = false)
        val lp = FrameLayout.LayoutParams(
            (el.w * canvas.width).toInt().coerceAtLeast(60),
            (el.h * canvas.height).toInt().coerceAtLeast(60)
        )
        canvas.addView(view, lp)
        view.x = el.x * canvas.width
        view.y = el.y * canvas.height

        when (el.type) {
            ElementType.BUTTON -> view.onButtonState = { pressed ->
                buttonMask = if (pressed) buttonMask or (1 shl el.buttonIndex) else buttonMask and (1 shl el.buttonIndex).inv()
            }
            ElementType.JOYSTICK -> view.onLiveValue = { vx, vy ->
                el.axisX?.let { axisValues[it.byteIndex] = vx }
                el.axisY?.let { axisValues[it.byteIndex] = vy }
            }
            ElementType.SLIDER -> view.onLiveValue = { v, _ ->
                el.axisX?.let { axisValues[it.byteIndex] = v * 2f - 1f } // 0..1 -> -1..1
            }
            ElementType.DPAD -> view.onDpadDirection = { dir -> hat = if (dir < 0) 8 else dir }
            ElementType.GYRO_STEER -> {
                gyroElement = el
                gyroView = view
            }
        }
    }

    private fun sendCurrentReport() {
        val axesBytes = ByteArray(6) { i -> (axisValues[i].coerceIn(-1f, 1f) * 127f).toInt().toByte() }
        hid.sendReport(axesBytes, buttonMask, hat)
    }

    override fun onResume() {
        super.onResume()
        motion.start()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        motion.stop()
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        super.onDestroy()
        hid.stop()
    }
}
