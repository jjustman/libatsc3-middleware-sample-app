package com.nextgenbroadcast.mobile.middleware.rpc.processor

import android.util.Log
import com.fasterxml.jackson.core.JsonProcessingException
import com.github.nmuzhichin.jsonrpc.api.Processor
import com.github.nmuzhichin.jsonrpc.api.RpcConsumer
import com.github.nmuzhichin.jsonrpc.context.ConsumerBuilder
import com.github.nmuzhichin.jsonrpc.model.request.Request
import com.github.nmuzhichin.jsonrpc.model.response.ResponseUtils
import com.github.nmuzhichin.jsonrpc.model.response.errors.Error
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3Module
import com.nextgenbroadcast.mobile.middleware.gateway.rpc.IRPCGateway
import com.nextgenbroadcast.mobile.middleware.rpc.RpcError
import com.nextgenbroadcast.mobile.middleware.rpc.RpcErrorCode
import com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest.CacheRequestImpl
import com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest.ICacheRequest
import com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery.ContentRecoveryImpl
import com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery.IContentRecovery
import com.nextgenbroadcast.mobile.middleware.rpc.drm.DRMImpl
import com.nextgenbroadcast.mobile.middleware.rpc.drm.IDRM
import com.nextgenbroadcast.mobile.middleware.rpc.eventStream.EventStreamImpl
import com.nextgenbroadcast.mobile.middleware.rpc.eventStream.IEventStream
import com.nextgenbroadcast.mobile.middleware.rpc.filterCodes.FilterCodesImpl
import com.nextgenbroadcast.mobile.middleware.rpc.filterCodes.IFilterCodes
import com.nextgenbroadcast.mobile.middleware.rpc.keys.IKeys
import com.nextgenbroadcast.mobile.middleware.rpc.keys.KeysImpl
import com.nextgenbroadcast.mobile.middleware.rpc.markUnused.IMarkUnused
import com.nextgenbroadcast.mobile.middleware.rpc.markUnused.MarkUnusedImpl
import com.nextgenbroadcast.mobile.middleware.rpc.mediaTrackSelection.IMediaTrackSelection
import com.nextgenbroadcast.mobile.middleware.rpc.mediaTrackSelection.MediaTrackSelectionImpl
import com.nextgenbroadcast.mobile.middleware.rpc.queryDeviceInf.IQueryDeviceInfo
import com.nextgenbroadcast.mobile.middleware.rpc.queryDeviceInf.QueryDeviceInfoImpl
import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.IReceiverQueryApi
import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.ReceiverQueryApiImpl
import com.nextgenbroadcast.mobile.middleware.rpc.requestReceiverActions.IReceiverAction
import com.nextgenbroadcast.mobile.middleware.rpc.requestReceiverActions.ReceiverActionImpl
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.IRMPContentSynchronization
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.RMPContentSynchronizationImpl
import com.nextgenbroadcast.mobile.middleware.rpc.subscribeUnsubscribe.ISubscribeUnsubscribe
import com.nextgenbroadcast.mobile.middleware.rpc.subscribeUnsubscribe.SubscribeUnsubscribeImpl
import com.nextgenbroadcast.mobile.middleware.rpc.xLink.IXLink
import com.nextgenbroadcast.mobile.middleware.rpc.xLink.XLinkImpl

internal class RPCProcessor(
        private val gateway: IRPCGateway
) : IRPCProcessor {

    private val consumer: RpcConsumer
    private val rpcObjectMapper = RPCObjectMapper()

    init {
        consumer = ConsumerBuilder().build().also {
            filRequests(it.processor)
        }
    }

    private fun filRequests(processor: Processor) {
        processor.process(FilterCodesImpl(), IFilterCodes::class.java)
        processor.process(CacheRequestImpl(gateway), ICacheRequest::class.java)
        processor.process(ContentRecoveryImpl(), IContentRecovery::class.java)
        processor.process(DRMImpl(), IDRM::class.java)
        processor.process(EventStreamImpl(), IEventStream::class.java)
        processor.process(KeysImpl(), IKeys::class.java)
        processor.process(MarkUnusedImpl(), IMarkUnused::class.java)
        processor.process(MediaTrackSelectionImpl(), IMediaTrackSelection::class.java)
        processor.process(QueryDeviceInfoImpl(gateway), IQueryDeviceInfo::class.java)
        processor.process(ReceiverQueryApiImpl(gateway), IReceiverQueryApi::class.java)
        processor.process(ReceiverActionImpl(gateway), IReceiverAction::class.java)
        processor.process(RMPContentSynchronizationImpl(gateway), IRMPContentSynchronization::class.java)
        processor.process(SubscribeUnsubscribeImpl(gateway), ISubscribeUnsubscribe::class.java)
        processor.process(XLinkImpl(), IXLink::class.java)
    }

    override fun processRequest(request: String): String {
        var requestId: Long = -1L
        return try {
            val req = rpcObjectMapper.jsonToObject(request, Request::class.java).also {
                requestId = it.id
            }

            val response = consumer.execution(req)

            if(response.isError) {
                Log.w(TAG, "processRequest: response.isError: requestId: $requestId, request: $request, $response")
            }

            rpcObjectMapper.objectToJson(response)
        } catch (e: JsonProcessingException) {
            Log.e(TAG, "processRequest: exception: requestId: $requestId, request: $request", e)
            errorResponse(requestId, e)
        }
    }

    override fun processRequest(requests: List<String>): List<String> {
        return try {
            val requestList = requests.map { rpcObjectMapper.jsonToObject(it, Request::class.java) }

            val responseList = consumer.execution(requestList)

            responseList.map { rpcObjectMapper.objectToJson(it) }
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
            ArrayList<String>(requests.size).apply {
                //TODO: we should return error's with request ID's
                fill("")
            }
        }
    }

    private fun errorResponse(requestId: Long, e: Exception): String {
        return try {
            rpcObjectMapper.objectToJson(ResponseUtils.createResponse(requestId, InternalRpcError(RpcErrorCode.PARSING_ERROR_CODE.code, e.localizedMessage)))
        } catch (ex: JsonProcessingException) {
            // This catch should never being executed, but it's need because objectMapper throw exception
            ""
        }
    }

    internal class InternalRpcError(code: Int, message: String?) : RpcError(code, message), Error {
        override fun getData(): Any? {
            return null
        }
    }

    companion object {
        val TAG: String = RPCProcessor::class.java.simpleName
    }
}