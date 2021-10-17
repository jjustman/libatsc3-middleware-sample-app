package com.nextgenbroadcast.mobile.middleware.rpc.filterCodes

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse
import com.nextgenbroadcast.mobile.middleware.rpc.filterCodes.model.FilterCodes

@JsonRpcType
interface IFilterCodes {
    @JsonRpcMethod("org.atsc.getFilterCodes")
    fun getFilterCodes(): FilterCodes

    @JsonRpcMethod("org.atsc.setFilterCodes")
    fun setFilterCodes(@JsonRpcParam("filters") filters: List<FilterCodes.Filter>): RpcResponse
}