package com.nextgenbroadcast.mobile.middleware.atsc3.source

import java.util.*
import kotlin.collections.ArrayList

abstract class ConfigurableAtsc3Source<T>(
        configs: List<T>
): Atsc3Source() where T: Any {
    private val configList = ArrayList(configs)
    private var currentConfigIndex: Int = configs.size - 1

    fun configure(configIndex: Int): Int {
        if (configList.isEmpty() || configIndex < 0 || configIndex >= configList.size) return IAtsc3Source.RESULT_ERROR

        currentConfigIndex = configIndex

        var result = applyConfig(configIndex)
        if (result != IAtsc3Source.RESULT_ERROR && configIndex >= 0) {
            result = configIndex
        }

        return result
    }

    override fun getConfigCount(): Int {
        return configList.size
    }

    override fun getConfigByIndex(configIndex: Int): T {
        return configList[configIndex]
    }

    override fun getAllConfigs(): List<T> {
        return Collections.unmodifiableList(configList)
    }

    fun setConfigs(configs: List<T>) {
        configList.clear()
        configList.addAll(configs)
        currentConfigIndex = configs.size - 1
    }

    fun getCurrentConfigIndex() = currentConfigIndex

    fun setInitialConfiguration(configIndex: Int) {
        this.currentConfigIndex = configIndex
    }

    protected abstract fun applyConfig(configIndex: Int): Int
}