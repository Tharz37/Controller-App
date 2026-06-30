package com.example.controller

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private lateinit var statusText: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var targetDevice: BluetoothDevice? = null

    private var currentSteering: Byte = 0
    private var currentThrottle: Byte = -127
    private var currentBrake: Byte = -127

    // Safely map all hex values to Bytes
    private val hidDescriptor = intArrayOf(
        0x05, 0x01, 0x09, 0x04, 0xa1, 0x01, 0x05, 0x01,
        0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x15, 0x81,
        0x25, 0x7f, 0x75, 0x08, 0x95, 0x03, 0x81, 0x02,
        0xc0
    ).map { it.toByte() }.toByteArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }

        val leftSlider = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            max = 254
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    currentBrake = (p - 127).toByte()
                    sendHidReport()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { s?.progress = 0; currentBrake = -127; sendHidReport() }
            })
        }

        statusText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            text = "Initializing Bluetooth..."
            gravity = Gravity.CENTER
        }

        val rightSlider = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            max = 254
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    currentThrottle = (p - 127).toByte()
                    sendHidReport()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { s?.progress = 0; currentThrottle = -127; sendHidReport() }
            })
        }

        layout.addView(leftSlider)
        layout.addView(statusText)
        layout.addView(rightSlider)
        setContentView(layout)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        setupBluetooth()
    }

    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE), 1)
            return
        }

        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerGamepad()
                }
            }
            override fun onServiceDisconnected(profile: Int) { hidDevice = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    private fun registerGamepad() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings("F1 Wheel", "Mobile Gamepad", "Tharun", BluetoothHidDevice.SUBCLASS1_COMBO, hidDescriptor)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            hidDevice?.registerApp(sdpSettings, null, null, Executors.newSingleThreadExecutor(), object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
                    runOnUiThread { statusText.text = if (registered) "Ready to Pair!\nConnect from PC" else "Registration Failed" }
                }
                override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                    targetDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
                    runOnUiThread { statusText.text = if (state == BluetoothProfile.STATE_CONNECTED) "Connected!\nDrive Safe, Boss." else "Waiting for PC..." }
                }
            })
        }
    }

    private fun sendHidReport() {
        targetDevice?.let { device ->
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                hidDevice?.sendReport(device, 0, byteArrayOf(currentSteering, currentThrottle, currentBrake))
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val tiltX = event.values[0]
            currentSteering = ((tiltX / 9.8f * -127f).coerceIn(-127f, 127f)).toInt().toByte()
            sendHidReport()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
