package com.nextgenbroadcast.mobile.middleware.rpc.queryDeviceInf.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class DeviceInfo(
        var deviceMake: String? = null,
        var deviceModel: String? = null,
        var deviceInput: DeviceInput? = null,
        var deviceInfo: Info? = null,
        var deviceId: String? = null,
        var advertisingId :String? = null,
        var deviceCapabilities :String? = null
) : RpcResponse() {
        data class DeviceInput(
                var ArrowUp: Int? = null,
                var ArrowDown: Int? = null,
                var ArrowRight: Int? = null,
                var ArrowLeft: Int? = null,
                var Select: Int? = null,
                var Back: Int? = null
        )

        data class Info(
                var numberOfTuners: Int? = null,
                var yearOfMfr: Int? = null
        )
}