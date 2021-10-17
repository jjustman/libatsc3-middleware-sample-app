package com.nextgenbroadcast.mobile.middleware.atsc3.source

abstract class PhyAtsc3Source : ConfigurableAtsc3Source<Int>(emptyList()), ITunableSource {

    override fun tune(freqKhz: Int) {
        super.tune(freqKhz)
    }

    override fun applyConfig(configIndex: Int): Int {
        tune(getConfigByIndex(configIndex))
        return configIndex
    }
}