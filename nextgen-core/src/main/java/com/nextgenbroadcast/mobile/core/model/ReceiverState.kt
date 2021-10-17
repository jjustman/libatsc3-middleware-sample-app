package com.nextgenbroadcast.mobile.core.model

data class ReceiverState(
        val state: State,
        val configIndex: Int,
        val configCount: Int
) {

    enum class State(
        val code: Int
    ) {
        IDLE(0),        // source is not opened
        SCANNING(1),    // iterating over source configurations and collect SLT data
        TUNING(2),      // source configured but receiver is awaiting for SLT data
        READY(3),       // SLT data received but service is not selected
        BUFFERING(4)    // buffering selected service content
    }

    companion object {
        fun idle() = ReceiverState(State.IDLE, -1, -1)
        fun scanning(configIndex: Int, configCount: Int) = ReceiverState(State.SCANNING, configIndex, configCount)
        fun tuning(configIndex: Int, configCount: Int) = ReceiverState(State.TUNING, configIndex, configCount)
        fun ready(configIndex: Int, configCount: Int) = ReceiverState(State.READY, configIndex, configCount)
        fun buffering(configIndex: Int, configCount: Int) = ReceiverState(State.BUFFERING, configIndex, configCount)
    }
}