package com.nextgenbroadcast.mobile.middleware.atsc3.source

import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3Module
import org.ngbp.libatsc3.middleware.android.phy.Atsc3NdkPHYClientBase

class PcapDescriptorAtsc3Source(
        private val fd: Int,
        private val length: Long,
        private val type: PcapType
) : PcapAtsc3Source(type) {

    override fun openPhyClient(): Atsc3NdkPHYClientBase? {
        try {
            val client = createPhyClient() ?: return null
            val res = client.open_from_capture_fd(getFileName(), fd, length)

            if (res == Atsc3Module.RES_OK) {
                client.run()
                return client
            }
        } catch (e: Error) {
            LOG.e(TAG, "Can't open Pcap fd: $fd, type: $type", e)
        }

        return null
    }

    override fun getFileName() = "/proc/self/fd/$fd"

    override fun toString(): String {
        return "PCAP Source: fd = $fd, type = $type"
    }

    companion object {
        val TAG: String = PcapDescriptorAtsc3Source::class.java.simpleName
    }
}