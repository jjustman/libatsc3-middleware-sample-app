package com.nextgenbroadcast.mobile.middleware

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.nextgenbroadcast.mobile.core.FileUtils
import com.nextgenbroadcast.mobile.core.MiddlewareConfig
import com.nextgenbroadcast.mobile.middleware.atsc3.source.Atsc3Source
import com.nextgenbroadcast.mobile.middleware.dev.config.DevConfig
import com.nextgenbroadcast.mobile.middleware.phy.Atsc3DeviceReceiver
import com.nextgenbroadcast.mobile.middleware.service.Atsc3ForegroundService

class DeviceTypeSelectionDialog : AppCompatActivity() {

    private lateinit var device: UsbDevice

    private var deviceReceiver: Atsc3DeviceReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: let {
            finish()
            return
        }

        val typeOverload: Int? = if (MiddlewareConfig.DEV_TOOLS) {
            DevConfig.get(applicationContext).phyType?.let { type ->
                when {
                    "YOGA".equals(type, true) -> Atsc3Source.DEVICE_TYPE_YOGA
                    "KAILASH".equals(type, true) -> Atsc3Source.DEVICE_TYPE_KAILASH
                    else -> null
                }
            }
        } else null

        if (typeOverload != null) {
            Atsc3ForegroundService.startForDevice(this, device, typeOverload)
            finish()
            return
        }

        setContentView(R.layout.activity_device_type_selection)

        setFinishOnTouchOutside(true)

        title = getString(R.string.device_type_selection_title)

        findViewById<View>(R.id.kailash_type_btn).setOnClickListener {
            Atsc3ForegroundService.startForDevice(this, device, Atsc3Source.DEVICE_TYPE_KAILASH)
            finish()
        }

        findViewById<View>(R.id.yoga_type_btn).setOnClickListener {
            Atsc3ForegroundService.startForDevice(this, device, Atsc3Source.DEVICE_TYPE_YOGA)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()

        deviceReceiver = Atsc3DeviceReceiver(device.deviceName) {
            finish()
        }.also { receiver ->
            registerReceiver(receiver, receiver.intentFilter)
        }
    }

    override fun onStop() {
        super.onStop()

        deviceReceiver?.let { receiver ->
            unregisterReceiver(receiver)
            deviceReceiver = null
        }
    }

    companion object {
        fun newIntent(context: Context, device: UsbDevice): Intent {
            return Intent(context, DeviceTypeSelectionDialog::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(UsbManager.EXTRA_DEVICE, device)
            }
        }
    }
}