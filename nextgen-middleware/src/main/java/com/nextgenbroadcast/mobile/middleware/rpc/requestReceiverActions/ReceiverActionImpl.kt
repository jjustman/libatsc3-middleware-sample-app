package com.nextgenbroadcast.mobile.middleware.rpc.requestReceiverActions

import com.nextgenbroadcast.mobile.middleware.gateway.rpc.IRPCGateway
import com.nextgenbroadcast.mobile.middleware.rpc.RpcErrorCode
import com.nextgenbroadcast.mobile.middleware.rpc.RpcException
import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse
import com.nextgenbroadcast.mobile.middleware.rpc.requestReceiverActions.model.AudioVolume

class ReceiverActionImpl(
        private val gateway: IRPCGateway
) : IReceiverAction {

    override fun acquireService(svcToAcquire: String): RpcResponse {
        val result = gateway.requestServiceChange(svcToAcquire)
        if (!result) throw RpcException(RpcErrorCode.SERVICE_NOT_FOUND)
        //throw RpcException(RpcErrorCode.SERVICE_NOT_AUTHORIZED)
        return RpcResponse()
    }

    override fun videoScalingAndPositioning(scaleFactor: java.lang.Double, xPos: java.lang.Double, yPos: java.lang.Double): RpcResponse {
        gateway.updateRMPPosition(scaleFactor.toDouble(), xPos.toDouble(), yPos.toDouble())
        return RpcResponse()
    }

    override fun setRMPURL(operation: String, rmpUrl: String?, rmpSyncTime: Double?): RpcResponse {
        //TODO: currently we do not support delays
        if (rmpSyncTime != null) {
            throw RpcException(RpcErrorCode.SYNCHRONIZATION_CANNOT_BE_ACHIEVED)
        }

        when (operation) {
            "startRmp" -> {
                gateway.requestMediaPlay(rmpUrl, convertSecToMilliSec(rmpSyncTime))
            }
            "stopRmp" -> {
                gateway.requestMediaStop(delay = convertSecToMilliSec(rmpSyncTime))
            }
            "resumeService" -> {
                gateway.requestMediaPlay(delay = convertSecToMilliSec(rmpSyncTime))
            }
        }

        return RpcResponse()
    }

    private fun convertSecToMilliSec(rmpSyncTime: Double?): Long {
        return rmpSyncTime?.let {
            if (rmpSyncTime == -1.0) {
                Long.MAX_VALUE
            } else {
                (rmpSyncTime * 1000).toLong()
            }
        } ?: 0
    }

    override fun audioVolume(): AudioVolume {
        return AudioVolume()
    }

}

