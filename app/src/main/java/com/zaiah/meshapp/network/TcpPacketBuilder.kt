package com.zaiah.meshapp.network

import java.nio.ByteBuffer

/**
 * Utility to construct raw IPv4 + TCP packets to send back to the VpnService's TUN interface.
 */
object TcpPacketBuilder {

    fun buildPacket(
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
        buffer.put((0x45).toByte()) // Version 4, IHL 5
        buffer.put(0) // TOS
        buffer.putShort(totalLen.toShort()) // Total Length
        buffer.putShort(0) // Identification
        buffer.putShort(0x4000.toShort()) // Flags & Fragment Offset (Don't Fragment)
        buffer.put(64) // TTL
        buffer.put(6) // Protocol (TCP)
        buffer.putShort(0) // Checksum (calculate later)
        buffer.put(srcIp) // Source IP (Gateway's fake IP or original destination)
        buffer.put(destIp) // Destination IP (Client's IP, usually 10.0.0.2)

        // Calculate IP Checksum
        val ipChecksum = calculateChecksum(packet, 0, 20)
        buffer.putShort(10, ipChecksum)

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
        
        // Copy TCP segment into pseudo header buffer for calculation
        pseudoHeader.put(packet, tcpOffset, tcpLen)
        
        val tcpChecksum = calculateChecksum(pseudoHeader.array(), 0, pseudoHeader.capacity())
        buffer.putShort(tcpOffset + 16, tcpChecksum)

        return packet
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
