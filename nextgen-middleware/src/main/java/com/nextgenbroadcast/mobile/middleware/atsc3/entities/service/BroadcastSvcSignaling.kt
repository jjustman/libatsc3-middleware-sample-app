package com.nextgenbroadcast.mobile.middleware.atsc3.entities.service

data class BroadcastSvcSignaling (
    var slsProtocol: Int = 0,
    var slsMajorProtocolVersion: Int = 0,
    var slsMinorProtocolVersion: Int = 0,
    var slsDestinationIpAddress: String? = null,
    var slsDestinationUdpPort: String? = null,
    var slsSourceIpAddress: String? = null
)