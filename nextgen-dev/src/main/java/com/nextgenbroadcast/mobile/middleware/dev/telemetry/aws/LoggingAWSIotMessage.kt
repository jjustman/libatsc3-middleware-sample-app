package com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws

import com.amazonaws.services.iot.client.AWSIotMessage
import com.amazonaws.services.iot.client.AWSIotQos
import com.nextgenbroadcast.mobile.core.LOG

class LoggingAWSIotMessage(
        topic: String,
        payload: String,
        qos: AWSIotQos = AWSIotQos.QOS0
) : AWSIotMessage(topic, qos, payload) {

    override fun onFailure() {
        LOG.e(TAG, errorMessage)
    }

    override fun onTimeout() {
        LOG.e(TAG, errorMessage)
    }

    companion object {
        val TAG: String = LoggingAWSIotMessage::class.java.simpleName
    }
}