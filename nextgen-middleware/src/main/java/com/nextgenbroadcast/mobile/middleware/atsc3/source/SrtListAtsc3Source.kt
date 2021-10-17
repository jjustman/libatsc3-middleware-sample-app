package com.nextgenbroadcast.mobile.middleware.atsc3.source

import com.nextgenbroadcast.mobile.core.LOG
import org.ngbp.libatsc3.middleware.android.phy.Atsc3NdkPHYClientBase
import org.ngbp.libatsc3.middleware.android.phy.virtual.srt.SRTRxSTLTPVirtualPHYAndroid

class SrtListAtsc3Source(
        private val srtSourceList: List<String>
) : ConfigurableAtsc3Source<String>(srtSourceList) {

    override fun open(): Int {
        return configure(getCurrentConfigIndex())
    }

    override fun openPhyClient(): Atsc3NdkPHYClientBase? {
        try {
            return SRTRxSTLTPVirtualPHYAndroid().apply {
                init()
                setSrtSourceConnectionString(getConfigByIndex(getCurrentConfigIndex()))
            }.also { client ->
                client.run()
            }
        } catch (e: Error) {
            LOG.e(TAG, "Can't open SRT list: $srtSourceList", e)
        }

        return null
    }

    override fun applyConfig(configIndex: Int): Int {
        close()
        return super.open()
    }

    override fun toString(): String {
        return "SRT list Source: ${getAllConfigs()}"
    }

    companion object {
        val TAG: String = SrtListAtsc3Source::class.java.simpleName
    }
}