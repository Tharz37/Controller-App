package com.example.controller.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.view.Gravity
import android.widget.*
import com.example.controller.bluetooth.HidControllerManager
import com.example.controller.model.Preset
import com.example.controller.model.PresetCategory
import com.example.controller.storage.PresetRepository

class MainActivity : Activity() {

    private lateinit var repo: PresetRepository
    private lateinit var hid: HidControllerManager
    private lateinit var presetListLayout: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PresetRepository(this)
        ensureBtPermissions()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Phone Controller"
            textSize = 22f
        }

        statusText = TextView(this).apply {
            text = "Bluetooth: initializing"
            setPadding(0, 8, 0, 16)
        }

        val newRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        newRow.addView(Button(this).apply {
            text = "+ Gamepad"
            setOnClickListener { createAndEdit(Preset.defaultGamepad()) }
        })
        newRow.addView(Button(this).apply {
            text = "+ Wheel"
            setOnClickListener { createAndEdit(Preset.defaultWheel()) }
        })
        newRow.addView(Button(this).apply {
            text = "+ Blank/Custom"
            setOnClickListener { createAndEdit(Preset(name = "Custom", category = PresetCategory.CUSTOM)) }
        })

        val connectButton = Button(this).apply {
            text = "Choose Bluetooth device"
            setOnClickListener { showDevicePicker() }
        }

        val presetsHeader = TextView(this).apply {
            text = "Presets"
            textSize = 18f
            setPadding(0, 24, 0, 8)
        }

        presetListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(title)
        root.addView(statusText)
        root.addView(newRow)
        root.addView(connectButton)
        root.addView(presetsHeader)
        root.addView(ScrollView(this).apply { addView(presetListLayout) })

        setContentView(root)

        hid = HidControllerManager(this, repo) { state ->
            runOnUiThread {
                statusText.text = "Bluetooth: " + when (state) {
                    is HidControllerManager.ConnectionState.Connected -> "connected to ${state.deviceName}"
                    HidControllerManager.ConnectionState.ReadyWaitingForConnection -> "ready, waiting for PC / last device"
                    HidControllerManager.ConnectionState.Registering -> "registering..."
                    HidControllerManager.ConnectionState.Disconnected -> "disconnected"
                    is HidControllerManager.ConnectionState.Error -> "error - ${state.message}"
                }
            }
        }
        hid.start()
    }

    override fun onResume() {
        super.onResume()
        refreshPresetList()
    }

    override fun onDestroy() {
        super.onDestroy()
        hid.stop()
    }

    private fun ensureBtPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val needed = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN)
            val missing = needed.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        }
    }

    private fun createAndEdit(preset: Preset) {
        repo.save(preset)
        val intent = Intent(this, PresetEditorActivity::class.java)
        intent.putExtra(PresetEditorActivity.EXTRA_PRESET_ID, preset.id)
        startActivity(intent)
    }

    private fun refreshPresetList() {
        presetListLayout.removeAllViews()
        val presets = repo.listPresets()
        if (presets.isEmpty()) {
            presetListLayout.addView(TextView(this).apply { text = "No presets yet — create one above." })
            return
        }
        presets.forEach { preset ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            row.addView(TextView(this).apply {
                text = "${preset.name}  [${preset.category}]"
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup_WRAP, 1f)
                gravity = Gravity.CENTER_VERTICAL
            })
            row.addView(Button(this).apply {
                text = "Run"
                setOnClickListener {
                    val intent = Intent(this@MainActivity, ControllerRunActivity::class.java)
                    intent.putExtra(PresetEditorActivity.EXTRA_PRESET_ID, preset.id)
                    startActivity(intent)
                }
            })
            row.addView(Button(this).apply {
                text = "Edit"
                setOnClickListener {
                    val intent = Intent(this@MainActivity, PresetEditorActivity::class.java)
                    intent.putExtra(PresetEditorActivity.EXTRA_PRESET_ID, preset.id)
                    startActivity(intent)
                }
            })
            row.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete ${preset.name}?")
                        .setPositiveButton("Delete") { _, _ -> repo.delete(preset.id); refreshPresetList() }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })
            presetListLayout.addView(row)
        }
    }

    private fun showDevicePicker() {
        val devices = hid.bondedDevices().toList()
        if (devices.isEmpty()) {
            Toast.makeText(this, "No bonded devices. Pair with the PC in Android Bluetooth settings first.", Toast.LENGTH_LONG).show()
            return
        }
        val names = devices.map { "${it.name} (${it.address})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose device")
            .setItems(names) { _, which ->
                hid.connectTo(devices[which])
                repo.lastDeviceAddress = devices[which].address
            }
            .show()
    }

    companion object {
        // ViewGroup.LayoutParams.WRAP_CONTENT, aliased for a shorter reference above.
        private const val ViewGroup_WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
