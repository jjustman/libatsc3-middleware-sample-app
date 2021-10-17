package com.nextgenbroadcast.mobile.middleware.atsc3.source

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.nextgenbroadcast.mobile.core.LOG
import org.ngbp.libatsc3.middleware.android.phy.Atsc3NdkPHYClientBase
import org.ngbp.libatsc3.middleware.android.phy.Atsc3UsbDevice

class UsbPhyAtsc3Source(
        private val usbManager: UsbManager,
        private val device: UsbDevice,
        val type: Int
) : PhyAtsc3Source() {

    override fun openPhyClient(): Atsc3NdkPHYClientBase? {
        val candidatePHYList = getPHYImplementations(device)
        if (candidatePHYList.isEmpty()) return null

        try {
            val conn = usbManager.openDevice(device) ?: return null

            val atsc3UsbDevice = Atsc3UsbDevice(device, conn)

            candidatePHYList.forEach { candidatePHY ->
                val atsc3NdkPHYClientBaseCandidate = Atsc3NdkPHYClientBase.CreateInstanceFromUSBVendorIDProductIDSupportedPHY(candidatePHY)

                if (candidatePHY.getIsBootloader(device)) {
                    val r = atsc3NdkPHYClientBaseCandidate.download_bootloader_firmware(atsc3UsbDevice.fd, type, atsc3UsbDevice.deviceName)
                    if (r < 0) {
                        Log.d(TAG, "prepareDevices: download_bootloader_firmware with $atsc3NdkPHYClientBaseCandidate failed for path: ${atsc3UsbDevice.deviceName}, fd: ${atsc3UsbDevice.fd}")
                    } else {
                        Log.d(TAG, "prepareDevices: download_bootloader_firmware with $atsc3NdkPHYClientBaseCandidate for path: ${atsc3UsbDevice.deviceName}, fd: ${atsc3UsbDevice.fd}, success")
                        //pre-boot devices should re-enumerate, so don't track this connection just yet...
                    }
                } else {
                    val r = atsc3NdkPHYClientBaseCandidate.open(atsc3UsbDevice.fd, type, atsc3UsbDevice.deviceName)
                    if (r < 0) {
                        Log.d(TAG, "prepareDevices: open with $atsc3NdkPHYClientBaseCandidate failed for path: ${atsc3UsbDevice.deviceName}, fd: ${atsc3UsbDevice.fd}, res: $r")
                    } else {
                        Log.d(TAG, "prepareDevices: open with $atsc3NdkPHYClientBaseCandidate for path: ${atsc3UsbDevice.deviceName}, fd: ${atsc3UsbDevice.fd}, success")

                        atsc3NdkPHYClientBaseCandidate.setAtsc3UsbDevice(atsc3UsbDevice)
                        atsc3UsbDevice.setAtsc3NdkPHYClientBase(atsc3NdkPHYClientBaseCandidate)

                        return atsc3NdkPHYClientBaseCandidate
                    }
                }
            }

            atsc3UsbDevice.destroy()
        } catch (e: Error) {
            LOG.e(TAG, "Can't open Usb device: $device", e)
        }

        return null
    }

    private fun getPHYImplementations(device: UsbDevice): List<Atsc3NdkPHYClientBase.USBVendorIDProductIDSupportedPHY> {
        Atsc3UsbDevice.FindFromUsbDevice(device)?.let {
            Log.d(TAG, "usbPHYLayerDeviceTryToInstantiateFromRegisteredPHYNDKs: Atsc3UsbDevice already instantiated: $device, instance: $it")
            return emptyList()
        }
                ?: Log.d(TAG, "usbPHYLayerDeviceTryToInstantiateFromRegisteredPHYNDKs: Atsc3UsbDevice map returned : $device, but null instance?")

        return Atsc3NdkPHYClientBase.GetCandidatePHYImplementations(device) ?: emptyList()
    }

    override fun closePhyClient(client: Atsc3NdkPHYClientBase) {
        super.closePhyClient(client)

        client.atsc3UsbDevice?.let { device ->
            Log.d(TAG, "closeUsbDevice -- before FindFromUsbDevice")
            Atsc3UsbDevice.DumpAllAtsc3UsbDevices()

            device.destroy()

            Atsc3UsbDevice.DumpAllAtsc3UsbDevices()
        }
    }

    override fun toString(): String {
        return "USB device: ${device.deviceName}"
    }

    companion object {
        private val TAG: String = UsbPhyAtsc3Source::class.java.simpleName

        fun getSaankhyaFX3DeviceType(device: UsbDevice) = getSaankhyaFX3DeviceType(device.vendorId, device.productId, device.manufacturerName)
    }
}