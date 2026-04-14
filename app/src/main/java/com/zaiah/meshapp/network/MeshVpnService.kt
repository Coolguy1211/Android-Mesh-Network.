package com.zaiah.meshapp.network

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
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
            .addAddress("10.0.0.2", 32) // Virtual IP for the client
            .addRoute("0.0.0.0", 0)    // Route all traffic through the VPN
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
        val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)

        while (isRunning) {
            val length = inputStream.read(buffer.array())
            if (length > 0) {
                // Here we have a raw IP packet in buffer.array() with 'length'
                // This packet should be sent over the mesh to the Gateway.
                Log.d("VPN", "Captured packet: $length bytes")
                
                // TODO: Send packet to Gateway via NearbyConnectionManager
                // This would likely involve broadcasting the packet or sending it
                // to a specific Gateway node ID.
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
