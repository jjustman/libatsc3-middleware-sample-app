package com.nextgenbroadcast.mobile.middleware.atsc3.source

import com.nextgenbroadcast.mobile.core.LOG
import org.ngbp.libatsc3.middleware.android.phy.Atsc3NdkPHYClientBase
import org.ngbp.libatsc3.middleware.android.phy.virtual.PcapDemuxedVirtualPHYAndroid
import org.ngbp.libatsc3.middleware.android.phy.virtual.PcapSTLTPVirtualPHYAndroid

abstract class PcapAtsc3Source(
    private val type: PcapType
) : Atsc3Source() {

    enum class PcapType {
        DEMUXED, STLTP
    }

    protected fun createPhyClient(): Atsc3NdkPHYClientBase? {
        return try {
            when (type) {
                PcapType.DEMUXED -> PcapDemuxedVirtualPHYAndroid()
                PcapType.STLTP -> PcapSTLTPVirtualPHYAndroid()
            }.apply {
                init()
            }
        } catch (e: Error) {
            LOG.e(TAG, "Can't create Phy of type: $type", e)
            null
        }
    }

    abstract fun getFileName(): String

    override fun getConfigCount() = 1

    override fun getConfigByIndex(configIndex: Int): String {
        return if (configIndex == 0) {
            getFileName()
        } else {
            throw IndexOutOfBoundsException("Incorrect configuration index: $configIndex")
        }
    }

    override fun getAllConfigs(): List<String> = listOf(getFileName())

    override fun toString(): String {
        return "PCAP Source: filename = ${getFileName()}"
    }

    companion object {
        val TAG: String = PcapAtsc3Source::class.java.simpleName
    }
}