package com.example.controller.bluetooth

/**
 * ONE report descriptor, registered once when the app starts.
 *
 * Why one descriptor instead of "a PS5 one" and "a wheel one":
 * BluetoothHidDevice.registerApp() is called a single time per app session.
 * If you swap the descriptor to match whatever preset is active, the PC sees
 * that as the USB/BT device disappearing and a new one appearing (drivers
 * re-enumerate, games lose the binding, DS4Windows/x360ce has to be
 * reconfigured, etc). Instead we register one generic descriptor that is a
 * superset of "gamepad" and "wheel" needs, and every preset just decides
 * which fields of that single report it writes into.
 *
 * Report layout (Report ID 1), 9 bytes after the report ID:
 *   byte 0: X    (-127..127)   -> left stick X / steering
 *   byte 1: Y    (-127..127)   -> left stick Y / throttle
 *   byte 2: Z    (-127..127)   -> right stick X / brake
 *   byte 3: Rx   (-127..127)   -> right stick Y / clutch
 *   byte 4: Ry   (-127..127)   -> free analog slot
 *   byte 5: Rz   (-127..127)   -> free analog slot
 *   byte 6: Buttons 1-8   (bit0 = button1 ... bit7 = button8)
 *   byte 7: Buttons 9-16  (bit0 = button9 ... bit7 = button16)
 *   byte 8: Hat switch in low nibble (0-7 = direction, 8 = neutral)
 *
 * AxisSlot below is what presets/elements reference instead of raw byte
 * indices, so the rest of the app never has to know this layout by heart.
 */
enum class AxisSlot(val byteIndex: Int) {
    X(0), Y(1), Z(2), RX(3), RY(4), RZ(5)
}

const val REPORT_ID: Byte = 1
const val REPORT_LENGTH = 9 // 6 axes + 2 button bytes + 1 hat byte

object HidDescriptors {

    val DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x05,             // Usage (Game Pad)
        0xA1, 0x01,             // Collection (Application)
        0x85, 0x01,             //   Report ID (1)

        // 6 analog axes, signed byte each
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x30,             //   Usage (X)
        0x09, 0x31,             //   Usage (Y)
        0x09, 0x32,             //   Usage (Z)
        0x09, 0x33,             //   Usage (Rx)
        0x09, 0x34,             //   Usage (Ry)
        0x09, 0x35,             //   Usage (Rz)
        0x15, 0x81,             //   Logical Minimum (-127)
        0x25, 0x7F,             //   Logical Maximum (127)
        0x75, 0x08,             //   Report Size (8)
        0x95, 0x06,             //   Report Count (6)
        0x81, 0x02,             //   Input (Data,Var,Abs)

        // 16 buttons
        0x05, 0x09,             //   Usage Page (Button)
        0x19, 0x01,             //   Usage Minimum (Button 1)
        0x29, 0x10,             //   Usage Maximum (Button 16)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95, 0x10,             //   Report Count (16)
        0x81, 0x02,             //   Input (Data,Var,Abs)

        // Hat switch (D-pad), 4 bits + 4 bits padding
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x39,             //   Usage (Hat switch)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x07,             //   Logical Maximum (7)
        0x35, 0x00,             //   Physical Minimum (0)
        0x46, 0x3B, 0x01,       //   Physical Maximum (315)
        0x65, 0x14,             //   Unit (Eng Rot:Angular Pos)
        0x75, 0x04,             //   Report Size (4)
        0x95, 0x01,             //   Report Count (1)
        0x81, 0x42,             //   Input (Data,Var,Abs,Null)
        0x75, 0x04,             //   Report Size (4) padding
        0x95, 0x01,
        0x81, 0x03,             //   Input (Const,Var,Abs)

        0xC0                    // End Collection
    ).map { it.toByte() }.toByteArray()
}
