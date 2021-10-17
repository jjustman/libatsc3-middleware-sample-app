package com.nextgenbroadcast.mobile.middleware.rpc.filterCodes.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class FilterCodes(
    var filters: List<Filter>
) : RpcResponse() {
    data class Filter(
        var filterCode: Int,
        var expires: String? = null
    )
}