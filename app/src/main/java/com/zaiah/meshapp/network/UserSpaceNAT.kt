package com.zaiah.meshapp.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
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

    enum class TcpState { CLOSED, SYN_SENT, ESTABLISHED, FIN_WAIT, CLOSE_WAIT }

    private val udpSessions = ConcurrentHashMap<String, DatagramSocket>()
    private val tcpSessions = ConcurrentHashMap<String, TCPRelay>()
    private val selector = Selector.open()

    init {
        thread(name = "TCP-NAT-Loop") {
            while (true) {
                try {
                    if (selector.select(1000) == 0) continue
                    val keys = selector.selectedKeys().iterator()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        keys.remove()
                        if (key.isValid && key.isReadable) {
                            (key.attachment() as? TCPRelay)?.readFromInternet()
                        }
                    }
                } catch (e: Exception) { Log.e("NAT", "Selector error", e) }
            }
        }
    }

    fun handlePacket(originNodeId: String, packetData: ByteArray) {
        try {
            val buffer = ByteBuffer.wrap(packetData)
            val versionByte = buffer.get()
            if ((versionByte.toInt() shr 4) != 4) return

            val ihl = (versionByte.toInt() and 0x0F) * 4
            val protocol = buffer.get(9).toInt() and 0xFF
            
            val srcIp = ByteArray(4)
            val destIp = ByteArray(4)
            buffer.position(12)
            buffer.get(srcIp)
            buffer.get(destIp)
            
            val destAddress = InetAddress.getByAddress(destIp)

            when (protocol) {
                17 -> handleUDP(originNodeId, srcIp, destIp, destAddress, buffer, ihl)
                6 -> handleTCP(originNodeId, srcIp, destIp, destAddress, buffer, ihl, packetData)
                else -> Log.d("NAT", "Unsupported protocol: $protocol")
            }
        } catch (e: Exception) { Log.e("NAT", "Packet parse error", e) }
    }

    private fun handleUDP(originId: String, srcIp: ByteArray, destIp: ByteArray, destAddr: InetAddress, buffer: ByteBuffer, ihl: Int) {
        buffer.position(ihl)
        val srcPort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        val length = buffer.short.toInt() and 0xFFFF
        
        val payload = ByteArray(length - 8)
        buffer.position(ihl + 8)
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
                        val response = IpPacketBuilder.buildUdpPacket(destIp, srcIp, destPort, srcPort, p.data.copyOf(p.length))
                        onResponse(originId, response)
                    }
                } catch (e: Exception) { s.close(); udpSessions.remove(sessionKey) }
            }
            s
        }
        socket.send(DatagramPacket(payload, payload.size, destAddr, destPort))
    }

    private fun handleTCP(originId: String, srcIp: ByteArray, destIp: ByteArray, destAddr: InetAddress, buffer: ByteBuffer, ihl: Int, rawPacket: ByteArray) {
        buffer.position(ihl)
        val srcPort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        val seqNum = buffer.int.toLong() and 0xFFFFFFFFL
        val ackNum = buffer.int.toLong() and 0xFFFFFFFFL
        val offsetAndFlags = buffer.short.toInt()
        val flags = offsetAndFlags and 0x3F
        
        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0

        val sessionKey = "TCP:$originId:$srcPort->$destAddr:$destPort"
        
        if (isRst) {
            tcpSessions.remove(sessionKey)?.close()
            return
        }

        if (isSyn && !isAck) {
            val relay = TCPRelay(originId, srcIp, destIp, destAddr, srcPort, destPort, 1000L, seqNum, onResponse)
            tcpSessions[sessionKey] = relay
            relay.connect(selector)
        } else {
            val relay = tcpSessions[sessionKey] ?: return
            val dataOffset = ((offsetAndFlags shr 12) and 0x0F) * 4
            val payloadSize = rawPacket.size - ihl - dataOffset
            
            if (payloadSize > 0) {
                val payload = ByteArray(payloadSize)
                System.arraycopy(rawPacket, ihl + dataOffset, payload, 0, payloadSize)
                relay.writeToInternet(payload, seqNum)
            }
            
            if (isFin) {
                relay.handleFin(seqNum)
                if (relay.state == TcpState.CLOSED) tcpSessions.remove(sessionKey)
            }
        }
    }

    private class TCPRelay(
        val originId: String, val clientIp: ByteArray, val serverIp: ByteArray,
        val destAddr: InetAddress, val clientPort: Int, val serverPort: Int,
        var serverSeqNum: Long, var clientSeqNum: Long,
        val onResponse: (String, ByteArray) -> Unit
    ) {
        var state = TcpState.CLOSED
        private var channel: SocketChannel? = null

        fun connect(selector: Selector) {
            thread {
                try {
                    state = TcpState.SYN_SENT
                    channel = SocketChannel.open().apply {
                        configureBlocking(false)
                        connect(InetSocketAddress(destAddr, serverPort))
                    }
                    while (channel?.finishConnect() == false) Thread.sleep(10)
                    
                    clientSeqNum++ // ACK the client's SYN
                    onResponse(originId, IpPacketBuilder.buildTcpPacket(serverIp, clientIp, serverPort, clientPort, serverSeqNum, clientSeqNum, 0x12))
                    serverSeqNum++
                    state = TcpState.ESTABLISHED
                    channel?.register(selector, SelectionKey.OP_READ, this)
                } catch (e: Exception) { close() }
            }
        }

        fun writeToInternet(data: ByteArray, seq: Long) {
            if (seq < clientSeqNum) return // Duplicate/Old packet
            try {
                channel?.write(ByteBuffer.wrap(data))
                clientSeqNum = seq + data.size
                onResponse(originId, IpPacketBuilder.buildTcpPacket(serverIp, clientIp, serverPort, clientPort, serverSeqNum, clientSeqNum, 0x10))
            } catch (e: Exception) { close() }
        }

        fun readFromInternet() {
            try {
                val buf = ByteBuffer.allocate(16384)
                val read = channel?.read(buf)
                if (read != null && read > 0) {
                    val payload = buf.array().copyOf(read)
                    onResponse(originId, IpPacketBuilder.buildTcpPacket(serverIp, clientIp, serverPort, clientPort, serverSeqNum, clientSeqNum, 0x18, payload))
                    serverSeqNum += read
                } else if (read == -1) close()
            } catch (e: Exception) { close() }
        }

        fun handleFin(seq: Long) {
            clientSeqNum = seq + 1
            onResponse(originId, IpPacketBuilder.buildTcpPacket(serverIp, clientIp, serverPort, clientPort, serverSeqNum, clientSeqNum, 0x11))
            state = TcpState.CLOSED
            close()
        }

        fun close() {
            try { channel?.close() } catch (e: Exception) {}
            state = TcpState.CLOSED
        }
    }
}
