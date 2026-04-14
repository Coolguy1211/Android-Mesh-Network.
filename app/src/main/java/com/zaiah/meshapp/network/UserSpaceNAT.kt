package com.zaiah.meshapp.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * A basic user-space NAT forwarder for the Gateway node.
 * Translates IP packets received from the mesh into actual socket connections.
 */
class UserSpaceNAT(private val onResponse: (originNodeId: String, data: ByteArray) -> Unit) {

    private val udpSessions = ConcurrentHashMap<String, DatagramSocket>()
    private val tcpSessions = ConcurrentHashMap<String, TCPRelay>()
    private val selector = Selector.open()

    init {
        // TCP event loop
        thread(name = "TCP-NAT-Loop") {
            while (true) {
                if (selector.select(1000) == 0) continue
                val keys = selector.selectedKeys().iterator()
                while (keys.hasNext()) {
                    val key = keys.next()
                    keys.remove()
                    if (key.isReadable) {
                        val relay = key.attachment() as? TCPRelay
                        relay?.readFromInternet()
                    }
                }
            }
        }
    }

    fun handlePacket(originNodeId: String, packetData: ByteArray) {
        try {
            val buffer = ByteBuffer.wrap(packetData)
            val versionByte = buffer.get()
            val version = (versionByte.toInt() shr 4) and 0x0F
            if (version != 4) return

            val ihl = (versionByte.toInt() and 0x0F) * 4
            val protocol = buffer.get(9).toInt() and 0xFF
            
            val srcIp = ByteArray(4)
            val destIp = ByteArray(4)
            buffer.position(12)
            buffer.get(srcIp)
            buffer.get(destIp)
            
            val destAddress = InetAddress.getByAddress(destIp)

            when (protocol) {
                17 -> handleUDP(originNodeId, destAddress, buffer, ihl)
                6 -> handleTCP(originNodeId, destAddress, buffer, ihl, packetData)
                else -> Log.d("NAT", "Unsupported protocol: $protocol")
            }
        } catch (e: Exception) {
            Log.e("NAT", "Error parsing packet", e)
        }
    }

    private fun handleUDP(originId: String, destAddr: InetAddress, buffer: ByteBuffer, ihl: Int) {
        buffer.position(ihl)
        val srcPort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        val length = buffer.short.toInt() and 0xFFFF
        buffer.short // Checksum
        
        val payload = ByteArray(length - 8)
        buffer.get(payload)

        val sessionKey = "UDP:$originId:$srcPort->$destAddr:$destPort"
        
        val socket = udpSessions.getOrPut(sessionKey) {
            val s = DatagramSocket()
            thread {
                try {
                    val recvBuf = ByteArray(16384)
                    while (true) {
                        val p = DatagramPacket(recvBuf, recvBuf.size)
                        s.receive(p)
                        // In a real NAT we'd wrap this back in an IPv4 header
                        // For now we send raw payload; the client VpnService must re-wrap
                        onResponse(originId, p.data.copyOf(p.length))
                    }
                } catch (e: Exception) { s.close() }
            }
            s
        }

        val packet = DatagramPacket(payload, payload.size, destAddr, destPort)
        socket.send(packet)
    }

    private fun handleTCP(originId: String, destAddr: InetAddress, buffer: ByteBuffer, ihl: Int, rawPacket: ByteArray) {
        buffer.position(ihl)
        val srcPort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        val seqNum = buffer.int
        val ackNum = buffer.int
        val offsetAndFlags = buffer.short.toInt()
        val flags = offsetAndFlags and 0x3F
        
        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0
        val isFin = (flags and 0x01) != 0

        val sessionKey = "TCP:$originId:$srcPort->$destAddr:$destPort"
        
        if (isSyn && !isAck) {
            // New connection
            val relay = TCPRelay(originId, destAddr, destPort, onResponse)
            tcpSessions[sessionKey] = relay
            relay.connect(selector)
        } else {
            val relay = tcpSessions[sessionKey]
            if (relay != null) {
                // Extract TCP payload
                val dataOffset = ((offsetAndFlags shr 12) and 0x0F) * 4
                val payloadSize = rawPacket.size - ihl - dataOffset
                if (payloadSize > 0) {
                    val payload = ByteArray(payloadSize)
                    System.arraycopy(rawPacket, ihl + dataOffset, payload, 0, payloadSize)
                    relay.writeToInternet(payload)
                }
                if (isFin) {
                    relay.close()
                    tcpSessions.remove(sessionKey)
                }
            }
        }
    }

    private class TCPRelay(
        val originId: String,
        val destAddr: InetAddress,
        val destPort: Int,
        val onResponse: (String, ByteArray) -> Unit
    ) {
        private var channel: SocketChannel? = null

        fun connect(selector: Selector) {
            thread {
                try {
                    channel = SocketChannel.open()
                    channel?.configureBlocking(false)
                    channel?.connect(InetSocketAddress(destAddr, destPort))
                    while (!channel!!.finishConnect()) { Thread.sleep(10) }
                    channel?.register(selector, SelectionKey.OP_READ, this)
                } catch (e: Exception) { Log.e("NAT", "TCP Connect failed", e) }
            }
        }

        fun writeToInternet(data: ByteArray) {
            try {
                channel?.write(ByteBuffer.wrap(data))
            } catch (e: Exception) { Log.e("NAT", "TCP Write failed", e) }
        }

        fun readFromInternet() {
            try {
                val buf = ByteBuffer.allocate(16384)
                val read = channel?.read(buf)
                if (read != null && read > 0) {
                    onResponse(originId, buf.array().copyOf(read))
                } else if (read == -1) {
                    close()
                }
            } catch (e: Exception) { close() }
        }

        fun close() {
            try { channel?.close() } catch (e: Exception) {}
        }
    }
}
