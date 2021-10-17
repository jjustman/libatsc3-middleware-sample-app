package com.nextgenbroadcast.mobile.middleware.atsc3.entities.alerts

import android.util.Log
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*
import kotlin.jvm.Throws

class LLSParserAEAT {

    fun parseAeaTable(xmlPayload: String): ArrayList<AeaTable> {
        val aeaList = ArrayList<AeaTable>()
        try {
            val parser = XmlUtils.newParser(xmlPayload)
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "AEAT")

            parser.iterateDocument { tagName ->
                when (tagName) {
                    "AEA" -> aeaList.add(readAEA(parser))
                    else -> parser.skipTag()
                }
            }
        } catch (e: XmlPullParserException) {
            Log.e("LLSParserAEAT", "exception in parsing: $e")
        } catch (e: IOException) {
            Log.e("LLSParserAEAT", "exception in parsing: $e")
        }

        //TODO: This is temporary solutions, it should be reworked
        val startIndxs = getAEATagsIdx(xmlPayload, "<AEA ")
        val endIndxs = getAEATagsIdx(xmlPayload, "</AEA>")

        aeaList.forEachIndexed { index, aeaStore ->
            aeaStore.xml = xmlPayload.substring(startIndxs[index], endIndxs[index] + "</AEA>".length)
        }

        return aeaList
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readAEA(parser: XmlPullParser): AeaTable {
        val aea = AeaTable()
        val alternateUrlList = mutableListOf<String>()

        parser.iterateAttrs { attrName, attrValue ->
            when (attrName) {
                "aeaId" -> aea.id = attrValue
                "aeaType" -> aea.type = attrValue
                "refAEAid" -> aea.refId = attrValue
                else -> {
                    // skip attribute
                }
            }
        }

        parser.iterateSubTags { tagName ->
            when (tagName) {
                "Header" -> {
                    val header = readHeader(parser)
                    aea.effective = header.effective
                    aea.expires = XmlUtils.strToDate(header.expires)
                }
                "AEAText" -> readAEAText(parser).also {
                    aea.messages?.put(it.lang, it.message)
                }
                "Media" -> readMedia(parser).also { aeaMedia ->
                    aeaMedia.alternateUrl?.let { url ->
                        alternateUrlList.add(url)
                    }
                }
                else -> parser.skipTag()
            }
        }

        if (alternateUrlList.isNotEmpty()) {
           aea.alternateUrlList = alternateUrlList.toList()
        }

        return aea
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readHeader(parser: XmlPullParser): Header {
        val header = Header()

        parser.iterateAttrs { attrName, attrValue ->
            when (attrName) {
                "effective" -> header.effective = attrValue
                "expires" -> header.expires = attrValue
                else -> {
                    // skip attribute
                }
            }
        }

        parser.iterateSubTags { parser.skipTag() }

        return header
    }

    private fun getAEATagsIdx(str: String, substr: String): ArrayList<Int> {
        val listIdx = arrayListOf<Int>()
        var lastIndex = -1
        while (str.indexOf(substr, lastIndex + 1).also { lastIndex = it } != -1) {
            listIdx.add(lastIndex)
        }
        return listIdx
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readAEAText(parser: XmlPullParser): AeaText {
        val aeaText = AeaText()

        parser.iterateAttrs { attrName, attrValue ->
            when (attrName) {
                "xml:lang" -> aeaText.lang = attrValue
                else -> {
                    // skip attribute
                }
            }
        }

        parser.readTextTag()?.let { aeaText.message = it }

        return aeaText
    }

    private fun readMedia(parser: XmlPullParser): AeaMedia {
        var alternativeUrl: String? = null

        parser.iterateAttrs { attrName, attrValue ->
            when (attrName) {
                "alternateUrl" -> alternativeUrl = attrValue
                else -> {
                    // skip attribute
                }
            }
        }

        parser.iterateSubTags { parser.skipTag() }

        return AeaMedia(alternativeUrl)
    }

}