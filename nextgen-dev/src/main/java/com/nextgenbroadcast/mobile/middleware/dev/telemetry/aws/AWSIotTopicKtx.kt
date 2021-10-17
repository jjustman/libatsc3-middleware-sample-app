package com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws

import com.amazonaws.services.iot.client.AWSIotException
import com.amazonaws.services.iot.client.AWSIotMessage
import com.amazonaws.services.iot.client.AWSIotQos
import com.amazonaws.services.iot.client.AWSIotTopic
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class AWSIotTopicKtx(
        private val cont: CancellableContinuation<AWSIotMessage?>,
        topic: String,
        qos: AWSIotQos = AWSIotQos.QOS1
) : AWSIotTopic(topic, qos) {

    override fun onMessage(message: AWSIotMessage) {
        cont.resume(message)
    }

    override fun onFailure() {
        cont.resumeWithException(AWSIotException(errorCode, errorMessage))
    }

    override fun onTimeout() {
        cont.resume(null)
    }

    override fun onSuccess() {
        super.onSuccess()
    }
}