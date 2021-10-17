package com.nextgenbroadcast.mobile.middleware.atsc3.source

import com.nextgenbroadcast.mobile.core.LOG
import org.ngbp.libatsc3.middleware.android.phy.Atsc3NdkPHYClientBase

class NdkPhyAtsc3Source(
        private val phy: Atsc3NdkPHYClientBase,
        private val fd: Int,
        private val devicePath: String?,
        private val freqKhz: Int
) : PhyAtsc3Source() {

    override fun openPhyClient(): Atsc3NdkPHYClientBase? {
        try {
            if (phy.init() == 0) {
                if (phy.open(fd, DEVICE_TYPE_AUTO, devicePath) == 0) {
                    if (freqKhz > 0) {
                        phy.tune(freqKhz, 0)
                    }

                    return phy
                }
            }
        } catch (e: Error) {
            LOG.e(TAG, "Can't open Phy: fd: $fd, devicePath: $devicePath, freqKhz: $freqKhz", e)
        }

        return null
    }

    override fun toString(): String {
        return "Phy Source: fd = $fd, devicePath = $devicePath, freqKhz = $freqKhz"
    }

    companion object {
        val TAG: String = NdkPhyAtsc3Source::class.java.simpleName
    }
}