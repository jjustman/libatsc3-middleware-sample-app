package com.nextgenbroadcast.mobile.middleware.rpc.requestReceiverActions

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcError
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.RpcException
import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse
import com.nextgenbroadcast.mobile.middleware.rpc.requestReceiverActions.model.AudioVolume

@JsonRpcType
interface IReceiverAction {
    @JsonRpcMethod("org.atsc.acquire.service")
    fun acquireService(@JsonRpcParam("svcToAcquire") svcToAcquire: String): RpcResponse

    // This RPC send incorrect data: Integer instead of Double
    // Using of java.lang.Double allows us avoid cast exception
    // Migrate to kotlin types when RPC will be fixed
    @JsonRpcMethod("org.atsc.scale-position")
    fun videoScalingAndPositioning(@JsonRpcParam("scaleFactor") scaleFactor: java.lang.Double, @JsonRpcParam("xPos") xPos: java.lang.Double, @JsonRpcParam("yPos") yPos: java.lang.Double): RpcResponse

    @JsonRpcError(RpcException::class)
    @JsonRpcMethod("org.atsc.setRMPURL")
    fun setRMPURL(@JsonRpcParam("operation") operation: String, @JsonRpcParam(value = "rmpurl", nullable = true) rmpUrl: String?, @JsonRpcParam(value = "rmpSyncTime", nullable = true) rmpSyncTime: Double?): RpcResponse

    @JsonRpcMethod("org.atsc.audioVolume")
    fun audioVolume(): AudioVolume
}