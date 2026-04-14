package com.zaiah.meshapp

import android.app.Application
import com.zaiah.meshapp.network.NearbyConnectionManager
import com.zaiah.meshapp.network.models.MeshPacket
import com.zaiah.meshapp.network.models.RouteEntry
import com.zaiah.meshapp.network.UserSpaceNAT

class MeshApp : Application(), NearbyConnectionManager.ConnectionListener {

    lateinit var meshManager: NearbyConnectionManager
    var nat: UserSpaceNAT? = null
    var isGateway = false
    
    // Topology data for UI
    var neighbors = setOf<String>()
    var routes = mapOf<String, RouteEntry>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        meshManager = NearbyConnectionManager(this, this)
        nat = UserSpaceNAT { originId, data ->
            // Send response back over mesh
            meshManager.sendToNode(originId, data, MeshPacket.PacketType.VPN_IP_PACKET)
        }
    }

    companion object {
        lateinit var instance: MeshApp
            private set
    }

    // NearbyConnectionManager.ConnectionListener implementation
    override fun onConnectionInitiated(endpointId: String, info: com.google.android.gms.nearby.connection.ConnectionInfo) {}
    override fun onConnectionResult(endpointId: String, result: com.google.android.gms.nearby.connection.ConnectionResolution) {}
    override fun onDisconnected(endpointId: String) {}
    override fun onMeshPacketReceived(packet: MeshPacket) {
        if (isGateway && packet.type == MeshPacket.PacketType.VPN_IP_PACKET) {
            nat?.handlePacket(packet.originId, packet.data)
        }
    }
    override fun onTopologyUpdated(neighbors: Set<String>, routes: Map<String, RouteEntry>) {
        this.neighbors = neighbors
        this.routes = routes
    }
}
