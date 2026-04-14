package com.zaiah.meshapp.network.models

import java.io.Serializable

enum class NodeRole {
    CLIENT,
    RELAY,
    GATEWAY
}

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
    val sequenceNum: Int = 0,
    val originRole: NodeRole = NodeRole.CLIENT,
    val data: ByteArray
) : Serializable {
    enum class PacketType {
        TEXT,
        VPN_IP_PACKET,
        ROUTING_CONTROL, // Used for route discovery
        TOPOLOGY_UPDATE, // Used for the dashboard
        ROLE_ADVERTISEMENT, // Used for gateways to announce themselves
        PING,
        PONG,
        RETICULUM_PACKET
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
 * A simple routing table entry with AODV-style sequence numbers and route aging.
 */
data class RouteEntry(
    val destinationId: String,
    val nextHopId: String,
    val hopCount: Int,
    val sequenceNum: Int,
    val role: NodeRole = NodeRole.CLIENT,
    var timestamp: Long = System.currentTimeMillis()
) {
    val isStale: Boolean
        get() = (System.currentTimeMillis() - timestamp) > 60000 // 60 seconds
}

data class ChatMessage(
    val senderId: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSentByMe: Boolean
) : Serializable
