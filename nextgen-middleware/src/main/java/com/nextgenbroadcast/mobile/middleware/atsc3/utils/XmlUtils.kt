package com.nextgenbroadcast.mobile.middleware.atsc3.utils

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

internal object XmlUtils {

    @Throws(XmlPullParserException::class)
    fun newParser(cmlPayload: String): XmlPullParser {
        return Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(cmlPayload.toByteArray()), null)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun skip(parser: XmlPullParser) {
        check(parser.eventType == XmlPullParser.START_TAG)
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    fun strToBool(value: String?): Boolean {
        return java.lang.Boolean.parseBoolean(value)
    }

    fun strToInt(value: String, default: Int = 0): Int {
        try {
            return value.toInt()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return default
    }

    fun hexToLong(value: String): Long {
        try {
            return value.toLong(16)
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return -1
    }

    fun strToLong(value: String, default: Long = 0): Long {
        try {
            return value.toLong(10)
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return default
    }

    fun strToListOfInt(value: String): List<Int> {
        return value.split(" ").map { strToInt(it) }
    }

    fun strToDate(value: String): ZonedDateTime? {
        return try {
            ZonedDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

internal inline fun XmlPullParser.iterateDocument(action: (name: String) -> Unit) = iterateTags(action, XmlPullParser.END_DOCUMENT)

internal inline fun XmlPullParser.iterateSubTags(action: (name: String) -> Unit) = iterateTags(action, XmlPullParser.END_TAG)

internal inline fun XmlPullParser.iterateTags(action: (name: String) -> Unit, endTag: Int): XmlPullParser {
    while (next() != endTag) {
        if (eventType != XmlPullParser.START_TAG) {
            continue
        }

        action(name)
    }

    return this
}

internal inline fun XmlPullParser.iterateSubText(action: (name: String, text: String) -> Unit): XmlPullParser {
    while (next() != XmlPullParser.END_TAG) {
        if (eventType != XmlPullParser.TEXT) {
            XmlUtils.skip(this)
            continue
        }

        action(name, text)
    }

    return this
}

internal inline fun XmlPullParser.iterateAttrs(action: (name: String, value: String) -> Unit): XmlPullParser {
    for (i in 0 until attributeCount) {
        action(getAttributeName(i), getAttributeValue(i))
    }

    return this
}

internal fun XmlPullParser.readTextTag(): String? {
    var result: String? = null
    while (next() != XmlPullParser.END_TAG) {
        if (eventType == XmlPullParser.TEXT) {
            result = text
        } else {
            XmlUtils.skip(this)
        }
    }

    return result
}

internal fun XmlPullParser.skipSubTags() = iterateSubTags { XmlUtils.skip(this) }

internal fun XmlPullParser.skipTag() {
    XmlUtils.skip(this)
}
