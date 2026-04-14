package com.zaiah.meshapp.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * A basic user-space NAT forwarder for the Gateway node.
 * Translates IP packets received from the mesh into actual socket connections.
 */
class UserSpaceNAT(private val onResponse: (originNodeId: String, data: ByteArray) -> Unit) {

    private val udpSessions = ConcurrentHashMap<String, DatagramSocket>()
    private val tcpSessions = ConcurrentHashMap<String, Socket>()

    fun handlePacket(originNodeId: String, packetData: ByteArray) {
        try {
            val buffer = ByteBuffer.wrap(packetData)
            val version = (buffer.get().toInt() shr 4) and 0x0F
            if (version != 4) return // Only IPv4 for now

            // Skip to protocol (offset 9)
            val protocol = buffer.get(9).toInt() and 0xFF
            
            // Source and Dest IP
            val srcIp = ByteArray(4)
            val destIp = ByteArray(4)
            buffer.position(12)
            buffer.get(srcIp)
            buffer.get(destIp)
            
            val destAddress = InetAddress.getByAddress(destIp)

            when (protocol) {
                17 -> handleUDP(originNodeId, destAddress, buffer, packetData)
                6 -> handleTCP(originNodeId, destAddress, buffer, packetData)
                else -> Log.d("NAT", "Unsupported protocol: $protocol")
            }
        } catch (e: Exception) {
            Log.e("NAT", "Error parsing packet", e)
        }
    }

    private fun handleUDP(originId: String, destAddr: InetAddress, buffer: ByteBuffer, rawPacket: ByteArray) {
        // Simple UDP Proxy
        val headerLength = (rawPacket[0].toInt() and 0x0F) * 4
        buffer.position(headerLength)
        val srcPort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        val length = buffer.short.toInt() and 0xFFFF
        buffer.short // Checksum
        
        val payload = ByteArray(length - 8)
        buffer.get(payload)

        val sessionKey = "$originId:$srcPort->$destAddr:$destPort"
        
        val socket = udpSessions.getOrPut(sessionKey) {
            val s = DatagramSocket()
            thread {
                val recvBuf = ByteArray(16384)
                while (true) {
                    val p = DatagramPacket(recvBuf, recvBuf.size)
                    s.receive(p)
                    // Re-encapsulate as IP response? 
                    // This is hard without a full IP stack.
                    // For now, we'll just send the raw payload back (incomplete NAT)
                    onResponse(originId, p.data.copyOf(p.length))
                }
            }
            s
        }

        val packet = DatagramPacket(payload, payload.size, destAddr, destPort)
        socket.send(packet)
    }

    private fun handleTCP(originId: String, destAddr: InetAddress, buffer: ByteBuffer, rawPacket: ByteArray) {
        // TCP is much harder because it's stateful.
        // For a prototype, we would need a library like 'NetGuard' or 'tun2socks'
        // For now, we log the attempt.
        Log.d("NAT", "TCP Connection requested to $destAddr. Complex NAT needed.")
    }
}
