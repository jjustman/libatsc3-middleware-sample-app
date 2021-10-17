package com.nextgenbroadcast.mobile.middleware.phy

import android.hardware.usb.UsbDevice
import android.util.Log

object DeviceUtils {

    fun getKeyFromUsbDevName(devName: String): Long {
        // devName has form of '/dev/bus/usb/001/002'

        if (devName.length != 20) return -1
        if (devName.substring(0, 13) != "/dev/bus/usb/") return -1

        val pairs = devName.substring(13).split("/").toTypedArray()
        if (pairs.size != 2) return -1

        val bus = pairs[0].toInt()
        val addr = pairs[1].toInt()
        return (bus and 0xff shl 8) + (addr and 0xff).toLong()
    }


    fun dumpDevice(tag: String, device: UsbDevice, action: String) {
        // getConfiguration requires api level >= 21
        val conf = device.getConfiguration(0)
        val intf = conf.getInterface(0)
        val numEp = intf.endpointCount

        Log.d(tag, "******* device (vid: ${device.vendorId}, pid: ${device.productId}, ep: $numEp, ${device.deviceName}) $action")
    }
}