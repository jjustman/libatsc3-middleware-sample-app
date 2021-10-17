package com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model.*

@JsonRpcType
interface IReceiverQueryApi {
    @JsonRpcMethod("org.atsc.query.ratingLevel")
    fun queryContentAdvisoryRating(): RatingLevelRpcResponse

    @JsonRpcMethod("org.atsc.query.cc")
    fun queryClosedCaptionsStatus(): CaptionsRpcResponse

    @JsonRpcMethod("org.atsc.query.service")
    fun queryServiceID(): ServiceRpcResponse

    @JsonRpcMethod("org.atsc.query.languages")
    fun queryLanguagePreferences(): LanguagesRpcResponse

    @JsonRpcMethod("org.atsc.query.captionDisplay")
    fun queryCaptionDisplayPreferences(): CaptionDisplayRpcResponse

    @JsonRpcMethod("org.atsc.query.audioAccessibilityPref")
    fun queryAudioAccessibilityPreferences(): AudioAccessibilityPrefRpcResponse

    @JsonRpcMethod("org.atsc.query.MPDUrl")
    fun queryMPDUrl(): MPDUrlRpcResponse

    @JsonRpcMethod("org.atsc.query.baseURI")
    fun queryReceiverWebServerURI(): BaseURIRpcResponse

    @JsonRpcMethod("org.atsc.query.alerting")
    fun queryAlertingSignaling(@JsonRpcParam("alertingTypes") alertingTypes: List<String>): AlertingSignalingRpcResponse

    @JsonRpcMethod("org.atsc.query.serviceGuideUrls")
    fun queryServiceGuideURLs(@JsonRpcParam("service", nullable = true) service: String?): ServiceGuideUrlsRpcResponse
}