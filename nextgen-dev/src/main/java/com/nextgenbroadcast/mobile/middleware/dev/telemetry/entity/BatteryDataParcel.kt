package com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Parcelize
@TypeParceler<Float, BatteryDataParceler>()
class BatteryDataParcel(val payload: Float): Parcelable

object BatteryDataParceler : Parceler<Float> {
    override fun create(parcel: Parcel) = parcel.readFloat()

    override fun Float.write(parcel: Parcel, flags: Int) {
        parcel.writeFloat(this)
    }
}
