package com.nextgenbroadcast.mobile.middleware.settings

import android.content.SharedPreferences
import android.location.Location
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.nextgenbroadcast.mobile.core.DateUtils
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.analytics.Atsc3Analytics
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3Profile
import com.nextgenbroadcast.mobile.middleware.location.FrequencyLocation
import com.nextgenbroadcast.mobile.middleware.server.ServerConstants
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.*

internal class MiddlewareSettingsImpl (
    private val preferences: SharedPreferences
) : IMiddlewareSettings {
    private val gson = Gson()

    override val deviceId: String
        get() = requireString(DEVICE_ID) {
            randomUUID()
        }

    override val advertisingId: String
        get() = requireString(ADVERTISING_ID) {
            randomUUID()
        }

    override var lastReportDate: LocalDateTime
        get() = requireString(LAST_REPORT_DATE) {
            DateUtils.format(expiredReportTime(), "")
        }.let {
            DateUtils.parse(it) ?: expiredReportTime()
        }
        set(value) {
            saveString(LAST_REPORT_DATE, DateUtils.format(value, ""))
        }

    override var locale: Locale = Locale.getDefault()

    override var frequencyLocation: FrequencyLocation?
        get() = loadString(FREQUENCY_LOCATION)?.let {
            stringToFrequencyLocation(it)
        }
        set(value) {
            frequencyLocationToString(value)?.let {
                saveString(FREQUENCY_LOCATION, it)
            }
        }

    override var lastFrequency: List<Int>
        get() = loadStringSet(FREQUENCY_USER).mapNotNull { it.toIntOrNull() }
        set(value) {
            saveStringSet(FREQUENCY_USER, value.map { it.toString() })
        }

    override var receiverProfile: Atsc3Profile?
        get() = loadString(RECEIVER_PROFILE)?.let { json ->
            if (json.isNotBlank()) {
                try {
                    gson.fromJson<Atsc3Profile>(json, object : TypeToken<Atsc3Profile>() {}.type)
                } catch (e: JsonParseException) {
                    LOG.d(TAG, "Failed parsing receiver profile: $json", e)
                    null
                }
            } else null
        }
        set(value) {
            val json = value?.let {
                gson.toJson(value)
            } ?: ""
            saveString(RECEIVER_PROFILE, json)
        }

    override val hostName = ServerConstants.HOST_NAME
    override var httpPort = ServerConstants.PORT_AUTOFIT
    override var httpsPort = ServerConstants.PORT_AUTOFIT
    override var wsPort = ServerConstants.PORT_AUTOFIT
    override var wssPort = ServerConstants.PORT_AUTOFIT

    fun setDeviceId(id: String) {
        saveString(DEVICE_ID, id)
    }

    private fun saveString(key: String, value: String): String {
        preferences.edit { putString(key, value) }
        return value
    }

    private fun loadString(key: String): String? {
        return preferences.ignoreClassCastException { getString(key, null) }
    }

    private fun requireString(key: String, action: () -> String): String {
        return loadString(key) ?: saveString(key, action.invoke())
    }

    private fun saveStringSet(key: String, value: List<String>): List<String> {
        preferences.edit { putStringSet(key, value.toSet()) }
        return value
    }

    private fun loadStringSet(key: String): List<String> {
        return preferences.ignoreClassCastException { getStringSet(key, null) }?.toList() ?: emptyList()
    }

    private fun saveInt(key: String, value: Int): Int {
        preferences.edit { putInt(key, value) }
        return value
    }

    private fun loadInt(key: String): Int {
        return preferences.ignoreClassCastException { getInt(key, 0) } ?: 0
    }

    private fun stringToFrequencyLocation(flJsonStr: String): FrequencyLocation? {
        val frequencyLocationJson = JSONObject(flJsonStr)

        val frequencyJSONArray = frequencyLocationJson.optJSONArray(FREQUENCY_LIST)
        val frequencyList = mutableListOf<Int>().apply {
            frequencyJSONArray?.let {
                for (index in 0 until frequencyJSONArray.length()) {
                    add(index, frequencyJSONArray.get(index) as Int)
                }
            }
        }

        val location = if (frequencyLocationJson.has(LOCATION_PROVIDER)) {
            Location(frequencyLocationJson.getString(LOCATION_PROVIDER)).apply {
                latitude = frequencyLocationJson.optDouble(LOCATION_LATITUDE, 0.0)
                longitude = frequencyLocationJson.optDouble(LOCATION_LONGITUDE, 0.0)
            }
        } else {
            return null
        }

        return FrequencyLocation(location, frequencyList)
    }

    private fun frequencyLocationToString(frequencyLocation: FrequencyLocation?): String? {
        return frequencyLocation?.let {
            JSONObject().apply {
                put(LOCATION_PROVIDER, frequencyLocation.location.provider)
                put(LOCATION_LATITUDE, frequencyLocation.location.latitude)
                put(LOCATION_LONGITUDE, frequencyLocation.location.longitude)
                put(FREQUENCY_LIST, JSONArray(frequencyLocation.frequencyList))
            }.toString()
        }
    }

    private fun randomUUID() = UUID.randomUUID().toString()

    private fun expiredReportTime() =
            LocalDateTime.now().minusHours(Atsc3Analytics.MAX_HOURS_BEFORE_SEND_REPORT).minusSeconds(1)

    private inline fun <T, R> T.ignoreClassCastException(block: T.() -> R?): R? {
        return try {
            block(this)
        } catch (e: ClassCastException) {
            null
        }
    }

    companion object {
        val TAG: String = MiddlewareSettingsImpl::class.java.simpleName

        private const val DEVICE_ID = "device_id"
        private const val ADVERTISING_ID = "advertising_id"
        private const val LAST_REPORT_DATE = "last_report_date"
        private const val FREQUENCY_LOCATION = "frequency_location"
        private const val LOCATION_PROVIDER = "location_provider"
        private const val LOCATION_LATITUDE = "location_latitude"
        private const val LOCATION_LONGITUDE = "location_longitude"
        private const val FREQUENCY_LIST = "frequency_list"
        private const val FREQUENCY_USER = "frequency_user"
        private const val RECEIVER_PROFILE = "receiver_profile"
    }
}