package com.nextgenbroadcast.mobile.middleware.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface ILocationRequester {
    fun checkPermission(): Boolean
    fun observeLocation(minUpdateTime: Long, minDistance: Float): Flow<Location?>

    suspend fun getLastLocation(): Location?
}