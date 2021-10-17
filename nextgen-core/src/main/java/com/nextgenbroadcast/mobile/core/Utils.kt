package com.nextgenbroadcast.mobile.core

import android.location.Location
import com.nextgenbroadcast.mobile.core.atsc3.SLTConstants
import java.math.BigInteger
import java.security.MessageDigest

fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(32, '0')
}

fun Double.toDegrees(): String {
    return Location.convert(this, Location.FORMAT_DEGREES).replace(",", ".")
}

fun <T> List<T>.isEquals(second: List<T>): Boolean {
    if (this.size != second.size) return false

    this.forEachIndexed { index, value ->
        if (second[index] != value) {
            return false
        }
    }

    return true
}

fun isClass(className: String) = try {
    Class.forName(className)
    true
} catch (e: Exception) {
    false
}

private const val apkServiceGlobalIdPrefix = "apk://"

fun getApkBaseServicePackage(serviceCategory: Int, globalServiceId: String): String? {
    return if(serviceCategory == SLTConstants.SERVICE_CATEGORY_ABS && globalServiceId.startsWith(apkServiceGlobalIdPrefix)) {
        globalServiceId.substring(apkServiceGlobalIdPrefix.length)
    } else null
}
