package com.zaiah.meshapp.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.zaiah.meshapp.network.models.MeshPacket
import com.zaiah.meshapp.network.models.NodeRole
import com.zaiah.meshapp.network.models.RouteEntry
import org.json.JSONObject
import org.json.JSONArray
import java.nio.charset.Charset

class NearbyConnectionManager(private val context: Context, private val listener: ConnectionListener) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val directNeighbors = mutableSetOf<String>()
    private val routingTable = mutableMapOf<String, RouteEntry>()
    
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.zaiah.meshapp.SERVICE_ID"
    private var localNodeId: String = ""
    
    var localRole: NodeRole = NodeRole.CLIENT
        set(value) {
            field = value
            broadcastRole()
        }

    private var currentSequenceNum = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    interface ConnectionListener {
        fun onConnectionInitiated(endpointId: String, info: ConnectionInfo)
        fun onConnectionResult(endpointId: String, result: ConnectionResolution)
        fun onDisconnected(endpointId: String)
        fun onMeshPacketReceived(packet: MeshPacket)
        fun onTopologyUpdated(neighbors: Set<String>, routes: Map<String, RouteEntry>)
    }

    private val routeCleaner = object : Runnable {
        override fun run() {
            val staleKeys = routingTable.filter { it.value.isStale && it.key !in directNeighbors }.keys
            if (staleKeys.isNotEmpty()) {
                staleKeys.forEach { routingTable.remove(it) }
                broadcastTopology()
            }
            mainHandler.postDelayed(this, 30000) // 30 seconds
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            listener.onConnectionInitiated(endpointId, info)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                directNeighbors.add(endpointId)
                routingTable[endpointId] = RouteEntry(endpointId, endpointId, 0, 0, NodeRole.CLIENT)
                broadcastRole()
                broadcastTopology()
            }
            listener.onConnectionResult(endpointId, result)
        }

        override fun onDisconnected(endpointId: String) {
            directNeighbors.remove(endpointId)
            
            // AGGRESSIVE CLEANUP: Remove ALL routes that rely on this endpoint as the next hop
            val routesToRemove = routingTable.filter { it.value.nextHopId == endpointId }.keys
            routesToRemove.forEach { routingTable.remove(it) }
            
            // Also remove the direct route to the endpoint itself
            routingTable.remove(endpointId)
            
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
        // 1. Update routing table using AODV logic
        updateRoute(packet.originId, senderId, packet.hopCount, packet.sequenceNum, packet.originRole)

        // 2. Is it for me?
        if (packet.destinationId == localNodeId || packet.destinationId == "BROADCAST") {
            when (packet.type) {
                MeshPacket.PacketType.ROLE_ADVERTISEMENT -> broadcastTopology()
                MeshPacket.PacketType.TOPOLOGY_UPDATE -> handleTopologyUpdate(packet)
                MeshPacket.PacketType.PING -> {
                    // Immediate response with PONG
                    sendToNode(packet.originId, packet.data, MeshPacket.PacketType.PONG)
                }
                MeshPacket.PacketType.PONG -> {
                    listener.onMeshPacketReceived(packet)
                }
                else -> listener.onMeshPacketReceived(packet)
            }
        }

        // 3. Should I forward it?
        if (packet.destinationId != localNodeId && packet.hopCount < 15) { // Max TTL 15
            forwardPacket(packet)
        }
    }

    fun pingNode(destId: String) {
        val data = System.currentTimeMillis().toString().toByteArray(Charset.forName("UTF-8"))
        sendToNode(destId, data, MeshPacket.PacketType.PING)
    }

    private fun handleTopologyUpdate(packet: MeshPacket) {
        try {
            val jsonArray = JSONArray(String(packet.data, Charset.forName("UTF-8")))
            for (i in 0 until jsonArray.length()) {
                val routeJson = jsonArray.getJSONObject(i)
                val destId = routeJson.getString("dest")
                val hops = routeJson.getInt("hops")
                val seq = routeJson.getInt("seq")
                val role = NodeRole.valueOf(routeJson.getString("role"))
                
                if (destId == localNodeId) continue
                
                // The next hop to that destination is the node that sent us this update (packet.originId)
                updateRoute(destId, packet.originId, hops + packet.hopCount, seq, role)
            }
        } catch (e: Exception) {
            Log.e("Mesh", "Failed to parse topology update", e)
        }
    }

    private fun updateRoute(destId: String, nextHop: String, currentHopCount: Int, seqNum: Int, role: NodeRole) {
        val existingRoute = routingTable[destId]
        
        // AODV sequence number and hop count logic
        val isNewer = existingRoute == null || 
                      seqNum > existingRoute.sequenceNum || 
                      (seqNum == existingRoute.sequenceNum && currentHopCount + 1 < existingRoute.hopCount)

        if (isNewer || existingRoute?.nextHopId == nextHop) {
            routingTable[destId] = RouteEntry(destId, nextHop, currentHopCount + 1, seqNum, role)
            broadcastTopology()
        }
    }

    private fun forwardPacket(packet: MeshPacket) {
        val nextHop = routingTable[packet.destinationId]?.nextHopId
        if (nextHop != null && directNeighbors.contains(nextHop)) {
            val forwardedPacket = packet.copy(hopCount = packet.hopCount + 1)
            sendDirect(nextHop, forwardedPacket)
        } else if (packet.destinationId == "BROADCAST") {
            val forwardedPacket = packet.copy(hopCount = packet.hopCount + 1)
            directNeighbors.forEach { neighbor ->
                if (neighbor != packet.originId) {
                    sendDirect(neighbor, forwardedPacket)
                }
            }
        }
    }

    var isCompatibilityMode = false // If true, only discover, don't advertise

    fun startMesh(nickname: String) {
        localNodeId = nickname
        mainHandler.post(routeCleaner)
        if (!isCompatibilityMode) {
            startAdvertising(nickname)
        }
        startDiscovery()
    }

    private fun startAdvertising(nickname: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(nickname, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnFailureListener {
                Log.e("Mesh", "Advertising failed. Auto-switching to Compatibility Mode.")
                isCompatibilityMode = true
            }
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

    fun broadcastRole() {
        currentSequenceNum++
        val packet = MeshPacket(
            originId = localNodeId, 
            destinationId = "BROADCAST", 
            type = MeshPacket.PacketType.ROLE_ADVERTISEMENT, 
            sequenceNum = currentSequenceNum,
            originRole = localRole,
            data = ByteArray(0)
        )
        directNeighbors.forEach { sendDirect(it, packet) }
    }

    fun sendToNode(destId: String, data: ByteArray, type: MeshPacket.PacketType) {
        currentSequenceNum++
        val packet = MeshPacket(
            originId = localNodeId, 
            destinationId = destId, 
            type = type, 
            sequenceNum = currentSequenceNum,
            originRole = localRole,
            data = data
        )
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
        listener.onTopologyUpdated(directNeighbors, routingTable)
        
        val summaries = JSONArray()
        routingTable.values.forEach {
            val routeJson = JSONObject()
            routeJson.put("dest", it.destinationId)
            routeJson.put("hops", it.hopCount)
            routeJson.put("seq", it.sequenceNum)
            routeJson.put("role", it.role.name)
            summaries.put(routeJson)
        }
        val data = summaries.toString().toByteArray(Charset.forName("UTF-8"))
        directNeighbors.forEach { neighbor ->
            val packet = MeshPacket(
                originId = localNodeId,
                destinationId = neighbor,
                type = MeshPacket.PacketType.TOPOLOGY_UPDATE,
                sequenceNum = ++currentSequenceNum,
                originRole = localRole,
                data = data
            )
            sendDirect(neighbor, packet)
        }
    }

    private fun serializePacket(packet: MeshPacket): ByteArray {
        val json = JSONObject()
        json.put("origin", packet.originId)
        json.put("dest", packet.destinationId)
        json.put("type", packet.type.name)
        json.put("hops", packet.hopCount)
        json.put("seq", packet.sequenceNum)
        json.put("role", packet.originRole.name)
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
                sequenceNum = json.optInt("seq", 0),
                originRole = NodeRole.valueOf(json.optString("role", "CLIENT")),
                data = android.util.Base64.decode(json.getString("data"), android.util.Base64.DEFAULT)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun stopAll() {
        mainHandler.removeCallbacks(routeCleaner)
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        directNeighbors.clear()
        routingTable.clear()
    }
}
