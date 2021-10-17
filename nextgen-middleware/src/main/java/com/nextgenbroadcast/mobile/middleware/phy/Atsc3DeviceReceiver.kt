package com.nextgenbroadcast.mobile.middleware.phy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class Atsc3DeviceReceiver(
        private val deviceName: String,
        private val onAction: (device: UsbDevice) -> Unit
) : BroadcastReceiver() {

    val intentFilter: IntentFilter
        get() = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

    override fun onReceive(context: Context, intent: Intent?) {
        intent?.action?.let { action ->
            val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return

            DeviceUtils.dumpDevice(TAG, device, action)

            if (deviceName != device.deviceName) return

            if (action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                onAction(device)
            }
        }
    }

    companion object {
        val TAG: String = Atsc3DeviceReceiver::class.java.simpleName
    }
}