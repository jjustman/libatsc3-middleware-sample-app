package com.nextgenbroadcast.mobile.middleware.atsc3.source

import com.nextgenbroadcast.mobile.core.LOG
import org.ngbp.libatsc3.middleware.android.phy.Atsc3NdkPHYClientBase
import org.ngbp.libatsc3.middleware.android.phy.SaankhyaPHYAndroid
import java.lang.IllegalStateException

abstract class Atsc3Source : IAtsc3Source {
    private var atsc3NdkPHYClientInstance: Atsc3NdkPHYClientBase? = null

    @Synchronized
    override fun open(): Int {
        if (atsc3NdkPHYClientInstance != null) throw IllegalStateException("Source already opened")

        atsc3NdkPHYClientInstance = openPhyClient()

        return if (atsc3NdkPHYClientInstance != null) IAtsc3Source.RESULT_OK else IAtsc3Source.RESULT_ERROR
    }

    @Synchronized
    override fun close() {
        atsc3NdkPHYClientInstance?.let { client ->
            closePhyClient(client)
            atsc3NdkPHYClientInstance = null
        }
    }

    @Synchronized
    override fun stop() {
        atsc3NdkPHYClientInstance?.stop()
    }

    @Synchronized
    protected open fun tune(freqKhz: Int) {
        atsc3NdkPHYClientInstance?.tune(freqKhz, 0)
    }

    protected abstract fun openPhyClient(): Atsc3NdkPHYClientBase?

    protected open fun closePhyClient(client: Atsc3NdkPHYClientBase) {
        LOG.d(TAG, "close source -- calling client.deinit")

        client.deinit()
    }

    override fun getSdkVersion(): String? {
        return atsc3NdkPHYClientInstance?._sdk_version
    }

    override fun getFirmwareVersion(): String? {
        return atsc3NdkPHYClientInstance?._firmware_version
    }

    companion object {
        private val TAG: String = Atsc3Source::class.java.simpleName

        const val DEVICE_TYPE_UNKNOWN = -1
        const val DEVICE_TYPE_PREBOOT = -2

        const val DEVICE_TYPE_AUTO = 0 // use for SaankhyaPHYAndroid.DEVICE_TYPE_MARKONE
        const val DEVICE_TYPE_KAILASH = SaankhyaPHYAndroid.DEVICE_TYPE_FX3_KAILASH
        const val DEVICE_TYPE_YOGA = SaankhyaPHYAndroid.DEVICE_TYPE_FX3_YOGA

        fun getSaankhyaFX3DeviceType(vendorId: Int, productId: Int, manufacturer: String?): Int {
            if (vendorId == SaankhyaPHYAndroid.CYPRESS_VENDOR_ID) {
                if (productId == SaankhyaPHYAndroid.FX3_PREBOOT_PRODUCT_ID) {
                    return DEVICE_TYPE_PREBOOT
                } else if (productId == SaankhyaPHYAndroid.KAILASH_OR_YOGA_PRODUCT_ID) {
                    return if(manufacturer == SaankhyaPHYAndroid.KAILASH_FIRMWARE_MFG_NAME_JJ) {
                        DEVICE_TYPE_KAILASH
                    } else {
                        //jjustman-2021-04-29 - fall thru for BB dongle here
                        //TODO: update BB FX3 firmware with mProductName identifier for YOGA
                        DEVICE_TYPE_YOGA
                    }
                }
            }

            return DEVICE_TYPE_UNKNOWN
        }
    }
}