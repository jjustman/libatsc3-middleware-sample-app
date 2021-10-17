package com.nextgenbroadcast.mobile.middleware.atsc3.entities.service

import android.util.Log
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.Atsc3ServiceLocationTable
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap

class LLSParserSLT {
    fun parseXML(xmlPayload: String): Atsc3ServiceLocationTable {
        val services = ArrayList<Atsc3Service>()
        val urls = HashMap<Int, String>()
        var bsid = 0
        try {
            val parser = XmlUtils.newParser(xmlPayload)
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "SLT")

            bsid = parser.getAttributeValue(null, "bsid")?.let {
                XmlUtils.strToInt(it)
            } ?: 0

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                when (parser.name) {
                    "Service" -> services.add(readService(bsid, parser))
                    "SLTInetUrl" -> readInetUrl(parser)?.let { (urlType, url) ->
                        urls.put(urlType, url)
                    }
                    else -> XmlUtils.skip(parser)
                }
            }
        } catch (e: XmlPullParserException) {
            Log.e("LLSParserSLT", "exception in parsing: $e")
        } catch (e: IOException) {
            Log.e("LLSParserSLT", "exception in parsing: $e")
        }

        return Atsc3ServiceLocationTable(bsid, services, urls)
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readInetUrl(parser: XmlPullParser): Pair<Int, String>? {
        val urlType: Int? = parser.getAttributeValue(null, "urlType")?.let {
            XmlUtils.strToInt(it, -1)
        }

        var url: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.TEXT) {
                url = parser.text
            } else {
                XmlUtils.skip(parser)
            }
        }

        return if (url != null && urlType != null && urlType >= 0) Pair(urlType, url) else null
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readService(bsid: Int, parser: XmlPullParser): Atsc3Service {
        val currentService = Atsc3Service(bsid)

        val attrCount = parser.attributeCount
        for (i in 0 until attrCount) {
            when (parser.getAttributeName(i)) {
                "serviceId" -> currentService.serviceId = XmlUtils.strToInt(parser.getAttributeValue(i))
                "globalServiceID" -> currentService.globalServiceId = parser.getAttributeValue(i)
                "sltSvcSeqNum" -> currentService.sltSvcSeqNum = XmlUtils.strToInt(parser.getAttributeValue(i))
                "majorChannelNo" -> currentService.majorChannelNo = XmlUtils.strToInt(parser.getAttributeValue(i))
                "minorChannelNo" -> currentService.minorChannelNo = XmlUtils.strToInt(parser.getAttributeValue(i))
                "serviceCategory" -> currentService.serviceCategory = XmlUtils.strToInt(parser.getAttributeValue(i))
                "shortServiceName" -> currentService.shortServiceName = parser.getAttributeValue(i)
                "hidden" -> currentService.hidden = XmlUtils.strToBool(parser.getAttributeValue(i))
                else -> {
                    // skip attribute
                }
            }
        }

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            val name = parser.name
            if (name == "BroadcastSvcSignaling") {
                currentService.broadcastSvcSignalingCollection.add(readBroadcastSvcSignaling(parser))
            } else {
                XmlUtils.skip(parser)
            }
        }

        return currentService
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readBroadcastSvcSignaling(parser: XmlPullParser): BroadcastSvcSignaling {
        val broadcastSvcSignaling = BroadcastSvcSignaling()

        val attrCount = parser.attributeCount
        for (i in 0 until attrCount) {
            val value = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "slsProtocol" -> broadcastSvcSignaling.slsProtocol = XmlUtils.strToInt(value)
                "slsMajorProtocolVersion" -> broadcastSvcSignaling.slsMajorProtocolVersion = XmlUtils.strToInt(value)
                "slsMinorProtocolVersion" -> broadcastSvcSignaling.slsMinorProtocolVersion = XmlUtils.strToInt(value)
                "slsDestinationIpAddress" -> broadcastSvcSignaling.slsDestinationIpAddress = value
                "slsDestinationUdpPort" -> broadcastSvcSignaling.slsDestinationUdpPort = value
                "slsSourceIpAddress" -> broadcastSvcSignaling.slsSourceIpAddress = value
                else -> {
                    // skip attribute
                }
            }
        }

        return broadcastSvcSignaling
    }
}