package com.nextgenbroadcast.mobile.middleware.service.init

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Log
import com.nextgenbroadcast.mobile.middleware.IAtsc3ReceiverCore
import com.nextgenbroadcast.mobile.middleware.atsc3.source.NdkPhyAtsc3Source
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.XmlUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import org.ngbp.libatsc3.middleware.android.phy.Atsc3NdkPHYClientBase
import org.xmlpull.v1.XmlPullParser
import java.lang.Exception
import kotlin.coroutines.resume

internal class OnboardPhyInitializer(
        private val receiver: IAtsc3ReceiverCore
) : IServiceInitializer {

    @Volatile
    private var isCanceled = false

    override suspend fun initialize(context: Context, components: Map<Class<*>, Pair<Int, String>>): Boolean {
        components.filter { (clazz, _) ->
            Atsc3NdkPHYClientBase::class.java.isAssignableFrom(clazz)
        }.map { (clazz, data) ->
            val (resource) = data
            @Suppress("UNCHECKED_CAST")
            Pair(clazz as Class<Atsc3NdkPHYClientBase>, resource)
        }.forEach { (component, resource) ->
            try {
                val parser = context.resources.getXml(resource)
                val params = readPhyAttributes(parser)

                val instance: Any = component.getDeclaredConstructor().newInstance()
                val phy = instance as Atsc3NdkPHYClientBase

                var connected = false
                params.forEach params@{ (fd, devicePath, freqKhz) ->
                    if (isCanceled) return@forEach

                    try {
                        connected = suspendCancellableCoroutine { cont ->
                            receiver.openRoute(NdkPhyAtsc3Source(phy, fd, devicePath, freqKhz)) { result ->
                                cont.resume(result)
                            }
                        }
                        if (connected) return@params
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }

                if (connected) {
                    return true
                } else {
                    try {
                        phy.deinit()
                    } catch (e: Exception) {
                        Log.d(TAG, "Crash when trying deinit an Onboard Phy", e)
                    }
                }
            } catch (e: Resources.NotFoundException) {
                Log.w(TAG, "Onboard Phy resource reading error: ", e)
            }

            if (isCanceled) return@forEach
        }

        return false
    }

    override fun cancel() {
        isCanceled = true
    }

    private fun readPhyAttributes(parser: XmlResourceParser): List<Triple<Int, String?, Int>> {
        val result = ArrayList<Triple<Int, String?, Int>>()

        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }

                if (parser.name == "resources") {
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType != XmlPullParser.START_TAG) {
                            continue
                        }

                        if (parser.name == "phy-device") {
                            var fd = -1
                            var path: String? = null
                            var freqKhz = -1

                            for (i in 0 until parser.attributeCount) {
                                when (parser.getAttributeName(i)) {
                                    "fd" -> fd = parser.getAttributeIntValue(i, -1)
                                    "path" -> path = parser.getAttributeValue(i)
                                    "freqKhz" -> freqKhz = parser.getAttributeIntValue(i, -1)
                                }
                            }

                            result.add(Triple(fd, path, freqKhz))
                        } else {
                            XmlUtils.skip(parser)
                        }
                    }
                } else {
                    XmlUtils.skip(parser)
                }
            }
        } finally {
            parser.close()
            return result
        }
    }

    companion object {
        val TAG: String = OnboardPhyInitializer::class.java.simpleName
    }
}