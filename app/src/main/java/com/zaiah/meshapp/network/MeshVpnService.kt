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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
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
            thread {
                runVpnTunnel()
            }
        }
    }

    private fun runVpnTunnel() {
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)

        while (isRunning) {
            val length = inputStream.read(buffer.array())
            if (length > 0) {
                // Find a Gateway in our routing table
                val gatewayId = MeshApp.instance.routes.values.find { 
                    // Assume the Gateway node's ID starts with "Gateway" or similar
                    // In a real app, nodes would broadcast their capabilities
                    true // For now, we'll try to send to any available route
                }?.destinationId ?: "BROADCAST"

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
