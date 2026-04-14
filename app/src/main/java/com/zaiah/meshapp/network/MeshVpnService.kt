package com.zaiah.meshapp.network

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.zaiah.meshapp.MeshApp
import com.zaiah.meshapp.network.models.MeshPacket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MeshVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    private var outputStream: FileOutputStream? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        MeshApp.instance.vpnService = this
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true

        val builder = Builder()
            .setSession("MeshVPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)

        vpnInterface = builder.establish()

        if (vpnInterface != null) {
            outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
            thread {
                runVpnTunnel()
            }
        }
    }

    fun injectPacket(packet: ByteArray) {
        try {
            outputStream?.write(packet)
        } catch (e: Exception) {
            Log.e("VPN", "Error injecting packet", e)
        }
    }

    private fun runVpnTunnel() {
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)

        while (isRunning) {
            val length = inputStream.read(buffer.array())
            if (length > 0) {
                // Find the closest Gateway in our routing table
                val gatewayId = MeshApp.instance.routes.values.filter { 
                    it.role == com.zaiah.meshapp.network.models.NodeRole.GATEWAY
                }.minByOrNull { it.hopCount }?.destinationId ?: "BROADCAST"

                MeshApp.instance.meshManager.sendToNode(
                    gatewayId, 
                    buffer.array().copyOf(length), 
                    MeshPacket.PacketType.VPN_IP_PACKET
                )
            }
            buffer.clear()
        }
    }

    private fun stopVpn() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
