package com.zaiah.meshapp.network

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.zaiah.meshapp.network.models.MeshPacket
import com.zaiah.meshapp.network.models.RouteEntry
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

class NearbyConnectionManager(private val context: Context, private val listener: ConnectionListener) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val directNeighbors = mutableSetOf<String>() // Direct Bluetooth/WiFi links
    private val routingTable = mutableMapOf<String, RouteEntry>()
    
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.zaiah.meshapp.SERVICE_ID"
    private var localNodeId: String = "" // Will be set on start

    interface ConnectionListener {
        fun onConnectionInitiated(endpointId: String, info: ConnectionInfo)
        fun onConnectionResult(endpointId: String, result: ConnectionResolution)
        fun onDisconnected(endpointId: String)
        fun onMeshPacketReceived(packet: MeshPacket)
        fun onTopologyUpdated(neighbors: Set<String>, routes: Map<String, RouteEntry>)
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            listener.onConnectionInitiated(endpointId, info)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                directNeighbors.add(endpointId)
                // Add direct route
                routingTable[endpointId] = RouteEntry(endpointId, endpointId, 0)
                broadcastTopology()
            }
            listener.onConnectionResult(endpointId, result)
        }

        override fun onDisconnected(endpointId: String) {
            directNeighbors.remove(endpointId)
            routingTable.remove(endpointId)
            // Remove all routes using this neighbor as next hop
            val routesToRemove = routingTable.filter { it.value.nextHopId == endpointId }.keys
            routesToRemove.forEach { routingTable.remove(it) }
            
            broadcastTopology()
            listener.onDisconnected(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val packet = deserializePacket(payload.asBytes()!!) ?: return
                handleIncomingPacket(packet, endpointId)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun handleIncomingPacket(packet: MeshPacket, senderId: String) {
        // 1. Update routing table (Learning step)
        updateRoute(packet.originId, senderId, packet.hopCount)

        // 2. Is it for me?
        if (packet.destinationId == localNodeId || packet.destinationId == "BROADCAST") {
            if (packet.type == MeshPacket.PacketType.TOPOLOGY_UPDATE) {
                handleTopologyUpdate(packet)
            } else {
                listener.onMeshPacketReceived(packet)
            }
        }

        // 3. Should I forward it?
        if (packet.destinationId != localNodeId && packet.hopCount < 10) {
            forwardPacket(packet)
        }
    }

    private fun updateRoute(destId: String, nextHop: String, currentHopCount: Int) {
        val existingRoute = routingTable[destId]
        if (existingRoute == null || existingRoute.hopCount > (currentHopCount + 1)) {
            routingTable[destId] = RouteEntry(destId, nextHop, currentHopCount + 1)
            broadcastTopology()
        }
    }

    private fun forwardPacket(packet: MeshPacket) {
        val nextHop = routingTable[packet.destinationId]?.nextHopId
        if (nextHop != null && directNeighbors.contains(nextHop)) {
            val forwardedPacket = packet.copy(hopCount = packet.hopCount + 1)
            sendDirect(nextHop, forwardedPacket)
        } else if (packet.destinationId == "BROADCAST") {
            // Re-broadcast to all neighbors except origin
            val forwardedPacket = packet.copy(hopCount = packet.hopCount + 1)
            directNeighbors.forEach { neighbor ->
                if (neighbor != packet.originId) {
                    sendDirect(neighbor, forwardedPacket)
                }
            }
        }
    }

    fun startMesh(nickname: String) {
        localNodeId = nickname // Using nickname as ID for now
        startAdvertising(nickname)
        startDiscovery()
    }

    private fun startAdvertising(nickname: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(nickname, SERVICE_ID, connectionLifecycleCallback, options)
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                connectionsClient.requestConnection(localNodeId, endpointId, connectionLifecycleCallback)
            }
            override fun onEndpointLost(endpointId: String) {}
        }, options)
    }

    fun sendToNode(destId: String, data: ByteArray, type: MeshPacket.PacketType) {
        val packet = MeshPacket(localNodeId, destId, type = type, data = data)
        if (destId == "BROADCAST") {
            directNeighbors.forEach { sendDirect(it, packet) }
        } else {
            forwardPacket(packet)
        }
    }

    private fun sendDirect(endpointId: String, packet: MeshPacket) {
        val bytes = serializePacket(packet)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    private fun broadcastTopology() {
        // Periodically share routes with neighbors
        listener.onTopologyUpdated(directNeighbors, routingTable)
    }

    private fun handleTopologyUpdate(packet: MeshPacket) {
        // Advanced: Merge routing tables from neighbors
    }

    private fun serializePacket(packet: MeshPacket): ByteArray {
        val json = JSONObject()
        json.put("origin", packet.originId)
        json.put("dest", packet.destinationId)
        json.put("type", packet.type.name)
        json.put("hops", packet.hopCount)
        json.put("data", android.util.Base64.encodeToString(packet.data, android.util.Base64.DEFAULT))
        return json.toString().toByteArray(Charset.forName("UTF-8"))
    }

    private fun deserializePacket(bytes: ByteArray): MeshPacket? {
        return try {
            val json = JSONObject(String(bytes, Charset.forName("UTF-8")))
            MeshPacket(
                originId = json.getString("origin"),
                destinationId = json.getString("dest"),
                type = MeshPacket.PacketType.valueOf(json.getString("type")),
                hopCount = json.getInt("hops"),
                data = android.util.Base64.decode(json.getString("data"), android.util.Base64.DEFAULT)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        directNeighbors.clear()
        routingTable.clear()
    }
}
