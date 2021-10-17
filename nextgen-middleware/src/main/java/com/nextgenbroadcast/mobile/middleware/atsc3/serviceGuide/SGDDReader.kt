package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide

import android.util.Log
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.descriptor.SGDeliveryUnit
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.descriptor.SGDescriptorEntry
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.descriptor.SGFragment
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException

internal class SGDDReader {
    fun readFromFile(file: File): List<SGDescriptorEntry> {
        try {
            val xml = file.inputStream().bufferedReader().use { reader ->
                StringBuilder().apply {
                    var line: String? = reader.readLine()
                    while (line != null) {
                        append(line)
                        append("\n")
                        line = reader.readLine()
                    }
                }.toString()
            }

            return parseXML(xml)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return emptyList()
    }

    fun parseXML(xmlPayload: String): List<SGDescriptorEntry> {
        val units = arrayListOf<SGDescriptorEntry>()
        try {
            val parser = XmlUtils.newParser(xmlPayload)

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "ServiceGuideDeliveryDescriptor")

            parser.iterateDocument { tagName ->
                if (tagName == "DescriptorEntry") {
                    units.add(readDescriptorEntry(parser))
                } else {
                    parser.skipTag()
                }
            }
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "exception in parsing: $e")
        } catch (e: IOException) {
            Log.e(TAG, "exception in parsing: $e")
        }

        return units
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readDescriptorEntry(parser: XmlPullParser): SGDescriptorEntry {
        var startTime = -1L
        var endTime = -1L
        var sessionID = -1L
        val fragments = HashMap<String, SGFragment>()

        parser.iterateSubTags { tagName ->
            when (tagName) {
                "GroupingCriteria" -> parser.iterateSubTags { name ->
                    if (name == "TimeGroupingCriteria") {
                        parser.iterateAttrs { attrName, attrValue ->
                            when (attrName) {
                                "startTime" -> startTime = XmlUtils.hexToLong(attrValue)
                                "endTime" -> endTime = XmlUtils.hexToLong(attrValue)
                            }
                        }
                    } else {
                        parser.skipTag()
                    }
                }.skipSubTags()

                "Transport" -> parser.iterateAttrs { attrName, attrValue ->
                    when (attrName) {
                        "transmissionSessionID" -> sessionID = XmlUtils.hexToLong(attrValue)
                    }
                }.skipSubTags()

                "ServiceGuideDeliveryUnit" -> readDeliveryUnit(parser, fragments)

                else -> parser.skipTag()
            }
        }

        return SGDescriptorEntry(
                startTime,
                endTime,
                sessionID,
                fragments
        )
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readDeliveryUnit(parser: XmlPullParser, fragments: MutableMap<String, SGFragment>) {
        val deliveryUnit = SGDeliveryUnit().apply {
            parser.iterateAttrs { attrName, attrValue ->
                when (attrName) {
                    "transportObjectID" -> transportObjectID = XmlUtils.hexToLong(attrValue)
                    "contentLocation" -> contentLocation = attrValue
                }
            }
        }

        parser.iterateSubTags { tagName ->
            if (tagName == "Fragment") {
                SGFragment().apply {
                    parser.iterateAttrs { name, value ->
                        when (name) {
                            "transportID" -> transportID = XmlUtils.hexToLong(value)
                            "version" -> version = XmlUtils.hexToLong(value)
                            "fragmentType" -> type = XmlUtils.strToInt(value)
                            "fragmentEncoding" -> encoding = XmlUtils.strToInt(value)
                            "id" -> id = value
                        }
                    }.skipSubTags()
                }.also { fragment ->
                    fragment.id?.let { fragmentId ->
                        fragments[fragmentId] = fragment.apply {
                            unit = deliveryUnit
                        }
                    }
                }
            } else {
                parser.skipTag()
            }
        }
    }

    companion object {
        val TAG: String = SGDDReader::class.java.simpleName
    }
}