package com.nextgenbroadcast.mobile.middleware.service

import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.DeviceTypeSelectionDialog
import com.nextgenbroadcast.mobile.middleware.atsc3.source.*

fun startAtsc3ServiceForDevice(context: Context, device: UsbDevice, forceOpen: Boolean) {
    val type = UsbPhyAtsc3Source.getSaankhyaFX3DeviceType(device)
    when {
        type == Atsc3Source.DEVICE_TYPE_PREBOOT -> {
            context.startActivity(DeviceTypeSelectionDialog.newIntent(context, device))
        }
        type != Atsc3Source.DEVICE_TYPE_UNKNOWN -> {
            Atsc3ForegroundService.startForDevice(context, device, type, forceOpen)
        }
        else -> {
            Atsc3ForegroundService.startForDevice(context, device, Atsc3Source.DEVICE_TYPE_AUTO, forceOpen)
        }
    }
}

fun routePathToSource(context: Context, path: String): IAtsc3Source? {
    return if (path.startsWith("srt://")) {
        if (path.contains('\n')) {
            val sources = path.split('\n')
            SrtListAtsc3Source(sources)
        } else {
            SrtAtsc3Source(path)
        }
    } else {
        //TODO: temporary solution
        val type = if (path.contains(".demux.") || path.contains(".demuxed.")) {
            PcapAtsc3Source.PcapType.DEMUXED
        } else {
            PcapAtsc3Source.PcapType.STLTP
        }
        try {
            context.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use { descriptor ->
                PcapDescriptorAtsc3Source(descriptor.detachFd(), descriptor.statSize, type)
            }
        } catch (e: Exception) {
            LOG.w("routePathToSource", "Can't create source for path: $path", e)
            null
        }
        //PcapFileAtsc3Source(path, type)
    }
}