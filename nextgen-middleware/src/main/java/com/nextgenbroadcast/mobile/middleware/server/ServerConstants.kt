package com.nextgenbroadcast.mobile.middleware.server

import com.nextgenbroadcast.mobile.core.atsc3.Atsc3Config

internal object ServerConstants {
    const val HOST_NAME = "localhost"
    const val PORT_AUTOFIT = 0
    const val REVISION = Atsc3Config.A300_YEAR + Atsc3Config.A300_MONTH + Atsc3Config.A300_DAY
    const val ATSC_CMD_PATH = "/atscCmd"
}