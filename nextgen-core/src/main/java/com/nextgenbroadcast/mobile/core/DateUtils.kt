package com.nextgenbroadcast.mobile.core

import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

object DateUtils {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)

    fun format(date: Date, default: String) = format(date) ?: default

    fun format(date: Date): String? {
        return try {
            dateFormat.format(date)
        } catch (e: ParseException) {
            null
        }
    }

    fun parse(value: String, default: Date? = null): Date? {
        return try {
            dateFormat.parse(value)
        } catch (e: ParseException) {
            default
        }
    }

    fun format(date: LocalDateTime, default: String) = format(date) ?: default

    fun format(date: LocalDateTime): String? {
        return try {
            date.format(DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: DateTimeException) {
            null
        }
    }

    fun parse(value: String, default: LocalDateTime) = parse(value) ?: default

    fun parse(value: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun hoursTillNow(date: LocalDateTime): Long {
        return Duration.between(date, LocalDateTime.now()).toHours()
    }
}