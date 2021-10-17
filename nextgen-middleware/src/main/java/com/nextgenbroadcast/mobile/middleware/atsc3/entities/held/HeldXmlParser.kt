package com.nextgenbroadcast.mobile.middleware.atsc3.entities.held

import android.util.Log
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class HeldXmlParser {
    fun parseXML(xmlPayload: String): Atsc3Held? {
        val packages = arrayListOf<Atsc3HeldPackage>()
        try {
            val parser = XmlUtils.newParser(xmlPayload)

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "HELD")

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }

                if (parser.name == ENTRY_HELD_PACKAGE) {
                    packages.add(readHeld(parser))
                } else {
                    XmlUtils.skip(parser)
                }
            }

            return Atsc3Held(packages)
        } catch (e: XmlPullParserException) {
            Log.e("HeldXmlParser", "exception in parsing: $e")
        } catch (e: IOException) {
            Log.e("HeldXmlParser", "exception in parsing: $e")
        }

        return null
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readHeld(parser: XmlPullParser): Atsc3HeldPackage {
        parser.require(XmlPullParser.START_TAG, null, ENTRY_HELD_PACKAGE)

        val pkg = Atsc3HeldPackage()

        val attrCount = parser.attributeCount
        for (i in 0 until attrCount) {
            val value = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "appContextId" -> pkg.appContextId = value
                "requiredCapabilities" -> pkg.requiredCapabilities = XmlUtils.hexToLong(value)
                "appRendering" -> pkg.appRendering = XmlUtils.strToBool(value)
                "clearAppContextCacheDate" -> pkg.clearAppContextCacheDate = XmlUtils.strToDate(value)
                "bcastEntryPackageUrl" -> pkg.bcastEntryPackageUrl = value
                "bcastEntryPageUrl" -> pkg.bcastEntryPageUrl = value
                "bbandEntryPageUrl" -> pkg.bbandEntryPageUrl = value
                "validFrom" -> pkg.validFrom = XmlUtils.strToDate(value)
                "validUntil" -> pkg.validUntil = XmlUtils.strToDate(value)
                "coupledServices" -> pkg.coupledServices = XmlUtils.strToListOfInt(value)
                else -> {
                    // skip
                }
            }
        }

        return pkg
    }

    companion object {
        private const val ENTRY_HELD_PACKAGE = "HTMLEntryPackage"
    }
}