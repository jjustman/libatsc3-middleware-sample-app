package com.nextgenbroadcast.mobile.middleware.dev.telemetry.control

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryControl
import kotlinx.coroutines.flow.MutableSharedFlow

interface ITelemetryControl {
    suspend fun subscribe(commandFlow: MutableSharedFlow<TelemetryControl>)

    companion object {
        const val CONTROL_ACTION_TUNE = "tune"
        const val CONTROL_ACTION_ACQUIRE_SERVICE = "acquireService"
        const val CONTROL_ACTION_SET_TEST_CASE = "setTestCase"
        const val CONTROL_ACTION_RESTART_APP = "restartApp"
        const val CONTROL_ACTION_REBOOT_DEVICE = "rebootDevice"
        const val CONTROL_ACTION_TELEMETRY_ENABLE = "enableTelemetry"
        const val CONTROL_ACTION_PING = "ping"
        const val CONTROL_ACTION_SHOW_DEBUG_INFO = "showDebugInfo"
        const val CONTROL_ACTION_VOLUME = "volume"
        const val CONTROL_ACTION_WIFI_INFO = "networkInfo"
        const val CONTROL_ACTION_FILE_WRITER = "fileWriter"
        const val CONTROL_ACTION_RESET_RECEIVER_DEMODE = "reset"
        const val CONTROL_BA_ENTRYPOINT = "defaultBA"

        const val CONTROL_ARGUMENT_DELIMITER = ","

        const val CONTROL_ARGUMENT_FREQUENCY = "frequency"
        const val CONTROL_ARGUMENT_SERVICE_ID = "serviceId"
        const val CONTROL_ARGUMENT_SERVICE_BSID = "serviceBsid"
        const val CONTROL_ARGUMENT_SERVICE_NAME = "serviceName"
        const val CONTROL_ARGUMENT_CASE = "case"
        const val CONTROL_ARGUMENT_START_DELAY = "startDelay"
        const val CONTROL_ARGUMENT_NAME = "name"
        const val CONTROL_ARGUMENT_ENABLE = "enable"
        const val CONTROL_ARGUMENT_VALUE = "value"
        const val CONTROL_ARGUMENT_DURATION = "duration"
        const val CONTROL_ARGUMENT_ENTRYPOINT = "entryPoint"
        const val CONTROL_ARGUMENT_CERT_HASH = "serverCertHash"
    }
}