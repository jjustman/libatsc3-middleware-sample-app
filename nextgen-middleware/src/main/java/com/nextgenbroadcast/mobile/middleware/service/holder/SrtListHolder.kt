package com.nextgenbroadcast.mobile.middleware.service.holder

import android.content.Context
import androidx.annotation.MainThread
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.MiddlewareConfig
import com.nextgenbroadcast.mobile.core.model.RouteUrl
import com.nextgenbroadcast.mobile.middleware.dev.config.DevConfig

class SrtListHolder(
    private val context: Context,
) {
    private val externalSrtServices = mutableListOf<RouteUrl>()

    @MainThread
    fun read() {
        if (MiddlewareConfig.DEV_TOOLS) {
            try {
                DevConfig.get(context).srtList?.let {
                    externalSrtServices.addAll(it)
                }
            } catch (e: Exception) {
                LOG.w(TAG, "Failed to open external SRT config", e)
            }
        }
    }

    fun getDefaultRoutes(): String? = externalSrtServices.filter { source ->
        source.isDefault
    }.joinPathsOrNull()

    fun getPoolRoutes(): String? = externalSrtServices.filter { source ->
        source.isPool
    }.joinPathsOrNull()

    private fun List<RouteUrl>.joinPathsOrNull(): String? {
        return if (isNotEmpty()) {
            joinToString(SRT_PATH_SEPARATOR) { source ->
                source.path
            }
        } else null
    }

    fun getFullSrtList(): List<RouteUrl> {
        return sourceList + externalSrtServices
    }

    companion object {
        val TAG: String = SrtListHolder::class.java.simpleName

        const val SRT_PATH_SEPARATOR = "\n"

        val sourceList = listOf(
            RouteUrl(
                "A166AC45-DB7C-4B68-B957-09B8452C76A4",
                "srt://las.srt.atsc3.com:31350?passphrase=A166AC45-DB7C-4B68-B957-09B8452C76A4",
                "las"
            ),
            RouteUrl(
                "88731837-0EB5-4951-83AA-F515B3BEBC20",
                "srt://bna.srt.atsc3.com:31347?passphrase=88731837-0EB5-4951-83AA-F515B3BEBC20",
                "bna"
            ),
            RouteUrl(
                "B9E4F7B8-3CDD-4BA2-ACA6-13088AB855C0",
                "srt://slc.srt.atsc3.com:31341?passphrase=B9E4F7B8-3CDD-4BA2-ACA6-13088AB855C0",
                "slc"
            ),
            RouteUrl(
                "03760631-667B-4ADB-9E04-E4491B0A7CF1",
                "srt://lab.srt.atsc3.com:31340?passphrase=03760631-667B-4ADB-9E04-E4491B0A7CF1",
                "lab"
            ),
            RouteUrl(
                "f51e5a22-9b73-4ec8-be84-e4c173f1d913",
                "srt://lab.srt.atsc3.com:31347?passphrase=f51e5a22-9b73-4ec8-be84-e4c173f1d913",
                "qa"
            ),
            RouteUrl(
                "055E0771-97B2-4447-8B5C-3B2497D0DE32",
                "srt://lab.srt.atsc3.com:31346?passphrase=055E0771-97B2-4447-8B5C-3B2497D0DE32",
                "labJJ"
            ),
            RouteUrl(
                "3D5E5ED2-700D-443B-968F-598DB9A2750D",
                "srt://lab.srt.atsc3.com:31348?passphrase=3D5E5ED2-700D-443B-968F-598DB9A2750D&packetfilter=fec",
                "labJJPixel5"
            ),
            RouteUrl(
                "055E0771-97B2-4447-8B5C-3B2497D0DE32",
                "srt://sea.srt.atsc3.com:31346?passphrase=055E0771-97B2-4447-8B5C-3B2497D0DE32",
                "seaJJAndroid"
            )
        )

    }
}