package com.nextgenbroadcast.mobile.middleware.atsc3

enum class Atsc3ModuleState {
    IDLE,       // source is not configured (tuned) and no service is available
    SCANNING,   // enumerating over the source configurations (frequencies) and collecting SLT data
    SNIFFING,   // source configured (tuned) and awaiting for SLT data
    TUNED       // source successfully configured (tuned) and SLT data available
}