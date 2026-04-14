package com.zaiah.meshapp.network.models

import java.io.Serializable

/**
 * Represents a packet sent over the mesh.
 * This allows for multi-hop routing by specifying origin and destination.
 */
data class MeshPacket(
    val originId: String,
    val destinationId: String,
    val packetId: Long = System.currentTimeMillis(),
    val type: PacketType,
    val hopCount: Int = 0,
    val data: ByteArray
) : Serializable {
    enum class PacketType {
        TEXT,
        VPN_IP_PACKET,
        ROUTING_CONTROL, // Used for route discovery
        TOPOLOGY_UPDATE  // Used for the dashboard
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MeshPacket
        return packetId == other.packetId && originId == other.originId
    }

    override fun hashCode(): Int {
        var result = originId.hashCode()
        result = 31 * result + packetId.hashCode()
        return result
    }
}

/**
 * A simple routing table entry.
 */
data class RouteEntry(
    val destinationId: String,
    val nextHopId: String,
    val hopCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
