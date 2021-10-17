package com.nextgenbroadcast.mobile.middleware.dev.config

import android.content.Context
import android.net.Uri
import com.nextgenbroadcast.mobile.core.FileUtils
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.model.RouteUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/*
{
  "srt": [
    {
      "name": "saankhya-bwi",
      "url": "srt://bwi.srt.atsc3.com:31349?passphrase=71aecfb4-b8ed-4958-8aeb-6ddadafff22b&packetfilter=fec",
      "default": true
    },
    {
      "name": "saankhya-sea",
      "url": "srt://sea.srt.atsc3.com:31350?passphrase=9ab4abe2-5bba-41ea-a9bb-4c19cca31e46&packetfilter=fec"
    }
  ],
  "service": {
    "name": "wbff"
  },
  "ba": {
    "entryPoint": "https://172.22.224.24:3000/index.html",
    "serverCertHash": "03ZkN06FzE++4gzCzRlbP8vN/rxYMYjzVVPhTT7Z3tg="
  },
  "phy": {
    "type": "YOGA"
  },
  "frequency": [
    569
  ]
}
*/

class DevConfig private constructor() {
    var srtList: List<RouteUrl>? = null
        private set
    var applicationEntryPoint: String? = null
        private set
    var service: AVService? = null
        private set
    var serverCertHash: String? = null
        private set
    var phyType: String? = null
        private set
    var frequencies: List<Int>? = null
        private set
    var rawConfig: String? = null
        private set
    val configDetected: Boolean
        get() = rawConfig != null

    fun write(context: Context, uri: Uri) {
        FileUtils.writeExternalFile(context, CONFIG_FILENAME, uri)
        read(context)
    }

    fun write(context: Context, source: String) {
        FileUtils.writeExternalFile(context, CONFIG_FILENAME, source)
        read(context)
    }

    fun remove(context: Context) {
        FileUtils.removeFile(context, CONFIG_FILENAME)
        rawConfig = null
    }

    fun read(context: Context) {
        FileUtils.readExternalFileAsString(context, CONFIG_FILENAME)?.let { str ->
            rawConfig = str
            try {
                with(JSONObject(str)) {
                    optJSONArray("srt")?.let {
                        srtList = parseSrtList(it)
                    }

                    optJSONObject("service")?.let {
                        service = AVService(
                            it.optInt("bsid", 0),
                            it.optInt("id", 0),
                            it.optString("name"),
                            it.optString("globalId"),
                            0, 0, 0
                        )
                    }

                    optJSONObject("ba")?.let {
                        applicationEntryPoint = it.optString("entryPoint")
                        serverCertHash = it.optString("serverCertHash")
                    }

                    optJSONObject("phy")?.let {
                        phyType = it.optString("type")
                    }

                    optJSONArray("frequency")?.let {
                        frequencies = mutableListOf<Int>().apply {
                            for (i: Int in 0 until it.length()) {
                                add(it.getInt(i) * 1000)
                            }
                        }
                    }
                }
            } catch (e: JSONException) {
                rawConfig = null
                LOG.e(TAG, "Failed to parse SRT config: ", e)
            }
        }?.run {
            rawConfig = null
        }
    }

    fun setBaEntrypoint(entryPoint: String?, certHash: String?) {
        applicationEntryPoint = entryPoint?.takeIf { it.isNotBlank() }
        serverCertHash = certHash?.takeIf { it.isNotBlank() }
    }

    private fun parseSrtList(json: JSONArray) = mutableListOf<RouteUrl>().apply {
        for (i in 0 until json.length()) {
            val jsonObject = json.getJSONObject(i)
            val name = jsonObject.optString("name", "srt $i")
            val url = jsonObject.optString("url")
            val isDefault = jsonObject.optBoolean("default", false)
            val isPool = jsonObject.optBoolean("pool", false)
            if (url.isNotBlank()) {
                add(RouteUrl(UUID.randomUUID().toString(), url, name, isDefault, isPool))
            }
        }
    }

    companion object {
        val TAG: String = DevConfig::class.java.simpleName

        const val CONFIG_FILENAME = "middleware.conf"

        @Volatile
        private var INSTANCE: DevConfig? = null

        @JvmStatic
        fun get(context: Context): DevConfig {
            val instance = INSTANCE
            return instance ?: synchronized(this) {
                val instance2 = INSTANCE
                instance2 ?: let {
                    newInstance(context.applicationContext).also {
                        INSTANCE = it
                    }
                }
            }
        }

        private fun newInstance(appContext: Context): DevConfig {
            return DevConfig().apply {
                read(appContext)
            }
        }
    }
}