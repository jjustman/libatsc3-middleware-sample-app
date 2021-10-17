package com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class LanguagesRpcResponse(
        var preferredAudioLang: String? = null,
        var preferredUiLang: String? = null,
        var preferredCaptionSubtitleLang: String? = null
) : RpcResponse()