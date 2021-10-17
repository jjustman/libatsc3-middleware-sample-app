package com.nextgenbroadcast.mobile.middleware.atsc3.entities

import com.nextgenbroadcast.mobile.middleware.atsc3.entities.service.Atsc3Service

data class Atsc3ServiceLocationTable(
        val bsid: Int,
        val services: List<Atsc3Service>,
        val urls: Map<Int, String>
)