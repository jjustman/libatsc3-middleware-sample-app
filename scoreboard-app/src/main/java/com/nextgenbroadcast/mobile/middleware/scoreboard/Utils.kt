package com.nextgenbroadcast.mobile.middleware.scoreboard

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan

internal fun formatDistance(distance: Float?, context: Context): String {
    distance ?: return context.getString(R.string.distance_unknown)
    return if (distance < 1000) {
        context.getString(R.string.distance_format_m, distance)
    } else {
        context.getString(R.string.distance_format_km, distance / 1000.0)
    }
}

internal fun formatDistanceSpannableString(distance: Float?, context: Context): SpannableString {
    val distanceText = formatDistance(distance, context)
    return SpannableString(
        context.getString(R.string.device_distance, distanceText)
    ).setBoldSpanFromTheEnd { length -> length - distanceText.length }
}

internal fun formatDistanceAndIdSpannableString(id: String, distance: Float?, context: Context): SpannableString {
    val distanceText = formatDistance(distance, context)
    return SpannableString(
        context.getString(R.string.device_id_and_distance, id, distanceText)
    )
        .setBoldSpan(0, id.length)
        .setBoldSpanFromTheEnd { length -> length - distanceText.length }
}

internal fun SpannableString.setBoldSpan(start: Int, end: Int): SpannableString = apply {
    setSpan(
        StyleSpan(Typeface.BOLD),
        start,
        end,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
}

internal fun SpannableString.setBoldSpanFromTheEnd(startIndex: (Int) -> Int): SpannableString =
    apply {
        setBoldSpan(start = startIndex(length), length)
    }
