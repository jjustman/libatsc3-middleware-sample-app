package com.nextgenbroadcast.mobile.middleware.analytics.serializer

import com.google.gson.*
import com.nextgenbroadcast.mobile.core.DateUtils
import java.lang.reflect.Type
import java.util.*

class DateSerializer : JsonSerializer<Date> {
    override fun serialize(src: Date, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(DateUtils.format(src))
    }
}