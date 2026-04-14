package com.zaiah.meshapp.network

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility to construct raw IPv4 + TCP/UDP packets to send back to the VpnService's TUN interface.
 */
object IpPacketBuilder {

    private val ipIdCounter = AtomicInteger(0)

    fun buildTcpPacket(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        payload: ByteArray? = null
    ): ByteArray {
        val payloadLen = payload?.size ?: 0
        val totalLen = 20 + 20 + payloadLen // IPv4 header (20) + TCP header (20)

        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)

        // --- IPv4 Header ---
        writeIpv4Header(buffer, totalLen, 6, srcIp, destIp)

        // --- TCP Header ---
        val tcpOffset = 20
        buffer.position(tcpOffset)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(destPort.toShort())
        buffer.putInt(seqNum.toInt())
        buffer.putInt(ackNum.toInt())
        buffer.putShort(((5 shl 12) or flags).toShort()) // Data offset (5 words) + flags
        buffer.putShort(8192) // Window size
        buffer.putShort(0) // Checksum (calculate later)
        buffer.putShort(0) // Urgent pointer

        // Payload
        if (payload != null && payload.isNotEmpty()) {
            buffer.put(payload)
        }

        // Calculate TCP Checksum (requires pseudo header)
        val tcpLen = 20 + payloadLen
        val pseudoHeader = ByteBuffer.allocate(12 + tcpLen)
        pseudoHeader.put(srcIp)
        pseudoHeader.put(destIp)
        pseudoHeader.put(0)
        pseudoHeader.put(6)
        pseudoHeader.putShort(tcpLen.toShort())
        pseudoHeader.put(packet, tcpOffset, tcpLen)
        
        val tcpChecksum = calculateChecksum(pseudoHeader.array(), 0, pseudoHeader.capacity())
        buffer.putShort(tcpOffset + 16, tcpChecksum)

        return packet
    }

    fun buildUdpPacket(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val payloadLen = payload.size
        val totalLen = 20 + 8 + payloadLen // IPv4 header (20) + UDP header (8)

        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)

        // --- IPv4 Header ---
        writeIpv4Header(buffer, totalLen, 17, srcIp, destIp)

        // --- UDP Header ---
        val udpOffset = 20
        buffer.position(udpOffset)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(destPort.toShort())
        val udpLength = 8 + payloadLen
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0) // Checksum (optional in IPv4, but let's calculate it)

        // Payload
        buffer.put(payload)

        // Calculate UDP Checksum (requires pseudo header)
        val pseudoHeader = ByteBuffer.allocate(12 + udpLength)
        pseudoHeader.put(srcIp)
        pseudoHeader.put(destIp)
        pseudoHeader.put(0)
        pseudoHeader.put(17)
        pseudoHeader.putShort(udpLength.toShort())
        pseudoHeader.put(packet, udpOffset, udpLength)

        val udpChecksum = calculateChecksum(pseudoHeader.array(), 0, pseudoHeader.capacity())
        buffer.putShort(udpOffset + 6, if (udpChecksum.toInt() == 0) 0xFFFF.toShort() else udpChecksum)

        return packet
    }

    private fun writeIpv4Header(buffer: ByteBuffer, totalLen: Int, protocol: Int, srcIp: ByteArray, destIp: ByteArray) {
        buffer.position(0)
        buffer.put((0x45).toByte()) // Version 4, IHL 5
        buffer.put(0) // TOS
        buffer.putShort(totalLen.toShort()) // Total Length
        buffer.putShort((ipIdCounter.getAndIncrement() and 0xFFFF).toShort()) // Identification
        buffer.putShort(0x4000.toShort()) // Flags & Fragment Offset (Don't Fragment)
        buffer.put(64) // TTL
        buffer.put(protocol.toByte()) // Protocol
        buffer.putShort(0) // Checksum (calculate later)
        buffer.put(srcIp) // Source IP
        buffer.put(destIp) // Destination IP

        // Calculate IP Checksum
        val ipChecksum = calculateChecksum(buffer.array(), 0, 20)
        buffer.putShort(10, ipChecksum)
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0L
        var i = offset
        var len = length

        while (len > 1) {
            val word = ((data[i].toLong() and 0xFF) shl 8) or (data[i + 1].toLong() and 0xFF)
            sum += word
            i += 2
            len -= 2
        }

        if (len > 0) {
            val word = (data[i].toLong() and 0xFF) shl 8
            sum += word
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv().toInt() and 0xFFFF).toShort()
    }
}
