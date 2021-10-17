package com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.ReceiverTelemetry
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import org.ngbp.libatsc3.middleware.android.phy.models.RfPhyFecModCodTypes
import org.ngbp.libatsc3.middleware.android.phy.models.RfPhyStatistics

class RfPhyTelemetryReader(
        private val flow: SharedFlow<RfPhyStatistics>
) : ITelemetryReader {

    override val name = NAME
    override var delayMils: Long = -1

    //jjustman-2021-06-07 - TODO: add additional SL phy
    override suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>) {
        flow.collect { rfStatistic ->
            eventFlow.emit(TelemetryEvent(
                    TelemetryEvent.EVENT_TOPIC_PHY,
                    RfPhyData(
                            stat = rfStatistic,
                            plp_fec_type_0 = RfPhyFecModCodTypes.L1d_PlpFecType.getOrDefault(rfStatistic.plp_fec_type_0, RfPhyFecModCodTypes.L1d_PlpFecType[255]),
                            plp_mod_0 = RfPhyFecModCodTypes.L1d_PlpMod.getOrDefault(rfStatistic.plp_mod_0, RfPhyFecModCodTypes.L1d_PlpMod[255]),
                            plp_cod_0 = RfPhyFecModCodTypes.L1d_PlpCod.getOrDefault(rfStatistic.plp_cod_0, RfPhyFecModCodTypes.L1d_PlpCod[255]),
                    )
            ))
        }
    }

    companion object {
        const val NAME = ReceiverTelemetry.TELEMETRY_PHY
    }
}

data class RfPhyData(
    val stat: RfPhyStatistics,
    val plp_fec_type_0: String,
    val plp_mod_0: String,
    val plp_cod_0: String
) : TelemetryPayload()