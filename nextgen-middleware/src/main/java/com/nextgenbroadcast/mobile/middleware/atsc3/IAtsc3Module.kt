package com.nextgenbroadcast.mobile.middleware.atsc3

import com.nextgenbroadcast.mobile.core.atsc3.INtpClock
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.Atsc3ServiceLocationTable
import com.nextgenbroadcast.mobile.middleware.atsc3.source.IAtsc3Source
import kotlinx.coroutines.flow.SharedFlow
import org.ngbp.libatsc3.middleware.android.phy.models.L1D_timePhyInformation
import org.ngbp.libatsc3.middleware.android.phy.models.RfPhyStatistics

interface IAtsc3Module {
    val rfPhyMetricsFlow: SharedFlow<RfPhyStatistics>
    val l1dPhyInfoFlow: SharedFlow<L1D_timePhyInformation>

    fun setListener(listener: Atsc3ModuleListener?)

    suspend fun open(source: IAtsc3Source, defaultConfig: Map<Any, Atsc3ServiceLocationTable>? = null): Boolean
    suspend fun tune(frequencyList: List<Int>, force: Boolean): Boolean
    suspend fun cancelScanning()
    suspend fun selectAdditionalService(serviceId: Int): Boolean
    suspend fun selectService(bsid: Int, serviceId: Int): Boolean
    suspend fun close()
    suspend fun getCurrentConfiguration(): Pair<String, Map<Any, Atsc3ServiceLocationTable>>?

    fun isServiceSelected(bsid: Int, serviceId: Int): Boolean
    fun isIdle(): Boolean
    fun getSelectedBSID(): Int

    fun getVersionInfo(): Map<String, String?>
    fun getSerialNum(): String?

    fun getNtpClock(): INtpClock?
}