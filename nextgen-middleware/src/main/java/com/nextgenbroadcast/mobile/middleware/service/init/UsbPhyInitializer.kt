package com.nextgenbroadcast.mobile.middleware.service.init

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Log
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.XmlUtils
import com.nextgenbroadcast.mobile.middleware.phy.IUsbConnector
import org.xmlpull.v1.XmlPullParser

internal class UsbPhyInitializer : IServiceInitializer {
    @Volatile
    private var isCanceled = false

    override suspend fun initialize(context: Context, components: Map<Class<*>, Pair<Int, String>>): Boolean {
        components.filter { (clazz, _) ->
            IUsbConnector::class.java.isAssignableFrom(clazz)
        }.map { (clazz, data) ->
            val (resource) = data
            @Suppress("UNCHECKED_CAST")
            Pair(clazz as Class<IUsbConnector>, resource)
        }.forEach { (component, resource) ->
            if (isCanceled) return@forEach

            try {
                val phys = readPhyAttributes(context.resources.getXml(resource))

                val instance: Any = component.getDeclaredConstructor().newInstance()
                val connector = instance as IUsbConnector

                if (connector.connect(context, phys)) {
                    return true
                }
            } catch (e: Resources.NotFoundException) {
                Log.w(TAG, "Usb Phy resource reading error: ", e)
            }
        }

        return false
    }

    override fun cancel() {
        isCanceled = true
    }

    private fun readPhyAttributes(parser: XmlResourceParser): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()

        parser.use {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }

                if (parser.name == "resources") {
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType != XmlPullParser.START_TAG) {
                            continue
                        }

                        if (parser.name == "usb-device") {
                            var vendor = -1
                            var product = -1

                            for (i in 0 until parser.attributeCount) {
                                when (parser.getAttributeName(i)) {
                                    "vendor-id" -> vendor = parser.getAttributeIntValue(i, -1)
                                    "product-id" -> product = parser.getAttributeIntValue(i, -1)
                                }
                            }

                            result.add(Pair(vendor, product))
                        } else {
                            XmlUtils.skip(parser)
                        }
                    }
                } else {
                    XmlUtils.skip(parser)
                }
            }
        }

        return result
    }

    companion object {
        val TAG: String = UsbPhyInitializer::class.java.simpleName
    }
}