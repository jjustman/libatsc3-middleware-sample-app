package com.nextgenbroadcast.mobile.middleware.rpc.filterCodes

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse
import com.nextgenbroadcast.mobile.middleware.rpc.filterCodes.model.FilterCodes

class FilterCodesImpl : IFilterCodes {
    override fun getFilterCodes(): FilterCodes {
        val filters = FilterCodes.Filter(10, " 10")
        return FilterCodes(listOf(filters))
    }

    override fun setFilterCodes(filters: List<FilterCodes.Filter>): RpcResponse {
        return RpcResponse()
    }
}