package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide

import android.util.Log
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGContent
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.*
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.*
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.TimeUtils
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException

internal class SGDUReader(
        private val services: MutableMap<Int, SGService>,
        private val contents: MutableMap<String, SGContent>
) {

    fun readFromFile(file: File, isActive: () -> Boolean, bsid: Int) {
        val fileName = file.name

        SGDUFile.open(file).use { reader ->
            var index = 0;
            while (isActive() && reader.hasNext()) {
                reader.next()?.let { (fragmentType, xml) ->
                    when (fragmentType) {
                        SGFragmentType.SERVICE -> parseServiceXML(xml)?.let { service ->
                            service.bsid = bsid
                            service.duFileName = fileName
                            service.duIndex = index
                        }

                        SGFragmentType.SCHEDULE -> parseScheduleXML(xml)?.let { schedule ->
                            schedule.duFileName = fileName
                            schedule.duIndex = index
                        }

                        SGFragmentType.CONTENT -> {
                            val content = parseContentXML(xml).apply {
                                duFileName = fileName
                                duIndex = index
                            }
                            content.id?.let { contentId ->
                                contents[contentId] = content
                            }
                        }

                        else -> {
                            // ignore
                        }
                    }
                }

                index++
            }
        }
    }

    private fun getOrCreateService(serviceId: Int): SGService {
        return services[serviceId] ?: SGService(serviceId).also {
            services[serviceId] = it
        }
    }

    private fun parseServiceXML(xmlPayload: String): SGService? {
        try {
            val parser = XmlUtils.newParser(xmlPayload)

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "Service")

            var serviceId: Int = -1
            var globalServiceId: String? = null
            var version: Long = 0

            parser.iterateAttrs { name, value ->
                when (name) {
                    "id" -> serviceId = XmlUtils.strToInt(value)
                    "globalServiceID" -> globalServiceId = value
                    "version" -> version = XmlUtils.strToLong(value)
                }
            }

            val service = getOrCreateService(serviceId)

            if (service.version > version) {
                return service
            } else {
                service.version = version
            }

            if (service.globalServiceId != globalServiceId) {
                service.globalServiceId = globalServiceId
            }

            parser.iterateDocument { tagName ->
                when (tagName) {
                    "Name" -> readTextAttr(parser) {
                        service.shortServiceName = it
                    }

                    "PrivateExt" -> parser.iterateSubTags { name ->
                        if (name == "sa:ATSC3ServiceExtension") {
                            parser.iterateSubTags { textName ->
                                when (textName) {
                                    "sa:MajorChannelNum" -> parser.readTextTag()?.let { text ->
                                        service.majorChannelNo = XmlUtils.strToInt(text)
                                    }
                                    "sa:MinorChannelNum" -> parser.readTextTag()?.let { text ->
                                        service.minorChannelNo = XmlUtils.strToInt(text)
                                    }
                                    else -> parser.skipTag()
                                }
                            }
                        } else {
                            parser.skipTag()
                        }
                    }

                    else -> parser.skipTag()
                }
            }

            return service
        } catch (e: XmlPullParserException) {
            Log.e(SGDDReader.TAG, "exception in parsing: $e")
        } catch (e: IOException) {
            Log.e(SGDDReader.TAG, "exception in parsing: $e")
        }
        return null
    }

    private fun parseScheduleXML(xmlPayload: String): SGSchedule? {
        try {
            val parser = XmlUtils.newParser(xmlPayload)

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "Schedule")

            var scheduleId: String? = null
            var scheduleVersion: Long = 0

            parser.iterateAttrs { name, value ->
                when (name) {
                    "id" -> scheduleId = value
                    "version" -> scheduleVersion = XmlUtils.strToLong(value)
                }
            }

            if (scheduleId == null) return null

            var schedule: SGSchedule? = null

            parser.iterateDocument { tagName ->
                when (tagName) {
                    "ServiceReference" -> readIdRefTag(parser) { serviceId ->
                        schedule = getOrCreateService(serviceId).let { service ->
                            service.scheduleMap?.get(scheduleId)
                                    ?: SGSchedule(scheduleId, serviceId, scheduleVersion).also {
                                        service.addSchedule(it)
                                    }
                        }
                    }

                    "ContentReference" -> SGScheduleContent().apply {
                        parser.iterateAttrs { name, value ->
                            if (name == "idRef") {
                                contentId = value
                            }
                        }.iterateSubTags { name ->
                            if (name == "PresentationWindow") {
                                var startTime = 0L
                                var endTime = 0L
                                var duration = 0
                                parser.iterateAttrs { attrName, value ->
                                    when (attrName) {
                                        "startTime" -> startTime = XmlUtils.strToLong(value)
                                        "endTime" -> endTime = XmlUtils.strToLong(value)
                                        "duration" -> duration = XmlUtils.strToInt(value)
                                    }
                                }.skipSubTags()

                                if (startTime > 0) startTime = TimeUtils.ntpSecondsToUtc(startTime)
                                if (endTime > 0) endTime = TimeUtils.ntpSecondsToUtc(endTime)

                                addPresentation(startTime, endTime, duration)
                            } else {
                                parser.skipTag()
                            }
                        }
                    }.also {
                        schedule?.addContent(it)
                    }
                }
            }

            return schedule
        } catch (e: XmlPullParserException) {
            Log.e(SGDDReader.TAG, "exception in parsing: $e")
        } catch (e: IOException) {
            Log.e(SGDDReader.TAG, "exception in parsing: $e")
        }

        return null
    }

    private fun parseContentXML(xmlPayload: String): SGContent {
        return SGContent().apply {
            try {
                val parser = XmlUtils.newParser(xmlPayload)

                parser.nextTag()
                parser.require(XmlPullParser.START_TAG, null, "Content")

                parser.iterateAttrs { name, value ->
                    when (name) {
                        "id" -> id = value
                        "version" -> version = XmlUtils.strToLong(value)
                    }
                }

                parser.iterateDocument { tagName ->
                    when (tagName) {
                        "ServiceReference" -> readIdRefTag(parser) {
                            addServiceId(it)
                        }

                        "Name" -> readLangStringAttr(parser) { text, lang ->
                            addName(text, lang)
                        }

                        "Description" -> readLangStringAttr(parser) { text, lang ->
                            addDescription(text, lang)
                        }

                        "PrivateExt" -> parser.iterateSubTags { name ->
                            when (name) {
                                "sa:ContentIcon" -> parser.readTextTag()?.let { text -> icon = text }
                                else -> parser.skipTag()
                            }
                        }

                        else -> parser.skipTag()
                    }
                }

            } catch (e: XmlPullParserException) {
                Log.e(SGDDReader.TAG, "exception in parsing: $e")
            } catch (e: IOException) {
                Log.e(SGDDReader.TAG, "exception in parsing: $e")
            }
        }
    }

    private fun readTextAttr(parser: XmlPullParser, action: (String) -> Unit) {
        parser.iterateAttrs { name, value ->
            if (name == "text") {
                action(value)
            }
        }.skipSubTags()
    }

    private fun readLangStringAttr(parser: XmlPullParser, action: (text: String, lang: String) -> Unit) {
        var text: String? = null
        var lang: String? = null
        parser.iterateAttrs { name, value ->
            when (name) {
                "text" -> text = value
                "xml:lang" -> lang = value
            }
        }.skipSubTags()

        text?.let { txt ->
            lang?.let { lng ->
                action(txt, lng)
            }
        }
    }

    private fun readIdRefTag(parser: XmlPullParser, action: (Int) -> Unit) {
        parser.iterateAttrs { name, value ->
            if (name == "idRef") {
                action(XmlUtils.strToInt(value))
            }
        }.skipSubTags()
    }
}