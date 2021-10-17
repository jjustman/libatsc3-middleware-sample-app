package com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class DatagramSocketWrapper(
    context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val socket: DatagramSocket = DatagramSocket()

    private val address: InetAddress? by lazy {
        getBroadcastAddress()
    }

    fun sendUdpMessage(message: String) {
        val buf = message.toByteArray()
        val packet = DatagramPacket(buf, buf.size, address, SOCKET_PORT)
        scope.launch {
            socket.send(packet)
        }
    }

    @Throws(IOException::class)
    private fun getBroadcastAddress(): InetAddress? {
        val dhcp = wifiManager.dhcpInfo
        // handle null somehow
        val broadcast = dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
        val quads = ByteArray(4)
        for (k in 0..3) quads[k] = (broadcast shr k * 8 and 0xFF).toByte()
        return InetAddress.getByAddress(quads)
    }

    companion object {
        const val SOCKET_PORT = 6970
    }
}