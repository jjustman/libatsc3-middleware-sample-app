package com.nextgenbroadcast.mobile.middleware.rpc.queryDeviceInf

import android.annotation.SuppressLint
import android.os.Build
import com.nextgenbroadcast.mobile.middleware.gateway.rpc.IRPCGateway
import com.nextgenbroadcast.mobile.middleware.rpc.KeyCode
import com.nextgenbroadcast.mobile.middleware.rpc.queryDeviceInf.model.DeviceInfo
import java.text.SimpleDateFormat

class QueryDeviceInfoImpl(val gateway: IRPCGateway) : IQueryDeviceInfo {
    @SuppressLint("SimpleDateFormat")
    override fun queryDeviceInfo(deviceInfoParams: List<String>?): DeviceInfo {
        return DeviceInfo()
            .apply {
                deviceMake = Build.MANUFACTURER
                deviceModel = Build.MODEL
                deviceInput = DeviceInfo.DeviceInput(
                    ArrowUp = KeyCode.ARROW_UP,
                    ArrowDown = KeyCode.ARROW_DOWN,
                    ArrowRight = KeyCode.ARROW_RIGHT,
                    ArrowLeft = KeyCode.ARROW_LEFT,
                    Select = KeyCode.SELECT,
                    Back = KeyCode.BACK
                )
                deviceInfo = DeviceInfo.Info(
                    numberOfTuners = 1,
                    yearOfMfr = SimpleDateFormat("yyyy").format(Build.TIME).toInt()
                )
                deviceId = gateway.deviceId
                advertisingId = gateway.advertisingId
            }
    }
}
