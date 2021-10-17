package com.nextgenbroadcast.mobile.middleware.rpc.keys

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse
import com.nextgenbroadcast.mobile.middleware.rpc.keys.model.Keys

class KeysImpl() : IKeys {
    override fun requestKeys(listOfKeys: List<String>): Keys {
        return Keys().apply {
            this.accepted = listOfKeys
        }
    }

    override fun relinquishKeys(listOfKeys: List<String>): RpcResponse {
        return RpcResponse()
    }
}