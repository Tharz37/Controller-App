package com.example.controller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.example.controller.storage.PresetRepository
import java.util.concurrent.Executors

/**
 * Wraps BluetoothHidDevice: registers the app once, remembers the last
 * connected device (via PresetRepository/SharedPreferences), and tries to
 * reconnect to it automatically whenever the manager starts up and that
 * device is available/bonded.
 */
class HidControllerManager(
    private val context: Context,
    private val repo: PresetRepository,
    private val onStateChanged: (ConnectionState) -> Unit
) {
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Registering : ConnectionState()
        object ReadyWaitingForConnection : ConnectionState()
        data class Connected(val deviceName: String, val address: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var targetDevice: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val hasBtConnectPermission: Boolean
        get() {
            return if (android.os.Build.VERSION.SDK_INT >= 31) {
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        }

    @SuppressLint("MissingPermission")
    fun start() {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter
        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled == false) {
            onStateChanged(ConnectionState.Error("Bluetooth is off"))
            return
        }
        if (!hasBtConnectPermission) {
            onStateChanged(ConnectionState.Error("Missing BLUETOOTH_CONNECT permission"))
            return
        }

        onStateChanged(ConnectionState.Registering)
        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerApp()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                hidDevice = null
                onStateChanged(ConnectionState.Disconnected)
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Phone Controller",
            "Configurable gamepad / wheel",
            "Controller",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptors.DESCRIPTOR
        )

        hidDevice?.registerApp(
            sdp, null, null, executor,
            object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    if (registered) {
                        onStateChanged(ConnectionState.ReadyWaitingForConnection)
                        tryReconnectToRememberedDevice()
                    } else {
                        onStateChanged(ConnectionState.Error("HID registration failed"))
                    }
                }

                override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            targetDevice = device
                            device?.address?.let { repo.lastDeviceAddress = it }
                            onStateChanged(
                                ConnectionState.Connected(
                                    device?.name ?: "Unknown device",
                                    device?.address ?: ""
                                )
                            )
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (targetDevice?.address == device?.address) targetDevice = null
                            onStateChanged(ConnectionState.ReadyWaitingForConnection)
                        }
                    }
                }
            }
        )
    }

    /**
     * The PC is the one that normally initiates the HID connection (it sees
     * this phone in its Bluetooth device list and connects, same as a real
     * gamepad). What we *can* do on the phone side is nudge a previously
     * bonded device to reconnect via connect(), which works for devices
     * that support being woken by the peripheral.
     */
    @SuppressLint("MissingPermission")
    private fun tryReconnectToRememberedDevice() {
        val savedAddress = repo.lastDeviceAddress ?: return
        val bonded = bluetoothAdapter?.bondedDevices ?: return
        val match = bonded.firstOrNull { it.address == savedAddress } ?: return
        hidDevice?.connect(match)
    }

    @SuppressLint("MissingPermission")
    fun connectTo(device: BluetoothDevice) {
        hidDevice?.connect(device)
    }

    fun bondedDevices(): Set<BluetoothDevice> =
        if (hasBtConnectPermission) bluetoothAdapter?.bondedDevices ?: emptySet() else emptySet()

    /**
     * axes must be exactly 6 signed bytes in AxisSlot order (X,Y,Z,Rx,Ry,Rz).
     * buttons is a 16-bit mask, bit0 = button 1. hat is 0-7 or 8 for neutral.
     */
    @SuppressLint("MissingPermission")
    fun sendReport(axes: ByteArray, buttons: Int, hat: Int) {
        val device = targetDevice ?: return
        if (!hasBtConnectPermission) return
        require(axes.size == 6) { "Expected 6 axis bytes" }

        val payload = ByteArray(REPORT_LENGTH)
        System.arraycopy(axes, 0, payload, 0, 6)
        payload[6] = (buttons and 0xFF).toByte()
        payload[7] = ((buttons shr 8) and 0xFF).toByte()
        payload[8] = (hat and 0x0F).toByte()

        runCatching {
            hidDevice?.sendReport(device, REPORT_ID.toInt(), payload)
        }.onFailure { Log.w("HidControllerManager", "sendReport failed", it) }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        hidDevice?.let { proxy ->
            targetDevice?.let { runCatching { proxy.disconnect(it) } }
            runCatching { proxy.unregisterApp() }
        }
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
        targetDevice = null
    }

    val isConnected: Boolean get() = targetDevice != null
}
