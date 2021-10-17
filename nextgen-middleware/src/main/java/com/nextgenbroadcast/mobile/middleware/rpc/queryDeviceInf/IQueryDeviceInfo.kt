package com.nextgenbroadcast.mobile.middleware.rpc.queryDeviceInf

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.queryDeviceInf.model.DeviceInfo

@JsonRpcType
interface IQueryDeviceInfo {
    @JsonRpcMethod("org.atsc.query.deviceInfo")
    fun queryDeviceInfo(@JsonRpcParam(value = "deviceInfoProperties", nullable = true) deviceInfoParams: List<String>?): DeviceInfo
}