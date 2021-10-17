package com.nextgenbroadcast.mobile.middleware.analytics.serializer

import android.location.Location
import com.google.gson.*
import com.nextgenbroadcast.mobile.core.toDegrees
import java.lang.reflect.Type

class LocationSerializer : JsonSerializer<Location> {
    override fun serialize(src: Location, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        return JsonObject().apply {
                addProperty("latitude", degrees(src.latitude))
                addProperty("longitude", degrees(src.longitude))
            }
    }

    private fun degrees(value: Double): String {
        return if (value != 0.0) {
            value.toDegrees()
        } else {
            EMPTY
        }
    }

    companion object {
        const val EMPTY = "NOTREPORTED"
    }
}