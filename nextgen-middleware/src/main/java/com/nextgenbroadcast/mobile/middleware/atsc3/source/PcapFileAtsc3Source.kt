package com.nextgenbroadcast.mobile.middleware.atsc3.source

import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3Module
import org.ngbp.libatsc3.middleware.android.phy.Atsc3NdkPHYClientBase

class PcapFileAtsc3Source(
        private val filename: String,
        private val type: PcapType
) : PcapAtsc3Source(type) {

    override fun openPhyClient(): Atsc3NdkPHYClientBase? {
        try {
            val client = createPhyClient() ?: return null
            val res = client.open_from_capture(filename)

            if (res == Atsc3Module.RES_OK) {
                client.run()
                return client
            }
        } catch (e: Error) {
            LOG.e(TAG, "Can't open Pcap file: $filename, type: $type", e)
        }

        return null
    }

    override fun getFileName() = filename

    override fun toString(): String {
        return "PCAP Source: filename = $filename, type = $type"
    }

    companion object {
        val TAG: String = PcapFileAtsc3Source::class.java.simpleName
    }
}