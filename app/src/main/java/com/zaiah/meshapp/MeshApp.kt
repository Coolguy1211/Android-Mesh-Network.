package com.zaiah.meshapp

import android.app.Application
import com.zaiah.meshapp.network.NearbyConnectionManager
import com.zaiah.meshapp.network.MeshVpnService
import com.zaiah.meshapp.network.MeshWebServer
import com.zaiah.meshapp.network.models.MeshPacket
import com.zaiah.meshapp.network.models.RouteEntry
import com.zaiah.meshapp.network.UserSpaceNAT

class MeshApp : Application(), NearbyConnectionManager.ConnectionListener {

    lateinit var meshManager: NearbyConnectionManager
    var nat: UserSpaceNAT? = null
    var vpnService: MeshVpnService? = null
    var webServer: MeshWebServer? = null
    
    // Topology data for UI
    var neighbors = setOf<String>()
    var routes = mapOf<String, RouteEntry>()
    
    // Chat data
    var chatMessages = mutableListOf<String>()
    var chatListener: ((String) -> Unit)? = null

    fun setGatewayMode(enabled: Boolean) {
        meshManager.localRole = if (enabled) com.zaiah.meshapp.network.models.NodeRole.GATEWAY else com.zaiah.meshapp.network.models.NodeRole.CLIENT
        if (enabled) {
            meshManager.broadcastRole()
        }
    }

    val isGateway: Boolean
        get() = meshManager.localRole == com.zaiah.meshapp.network.models.NodeRole.GATEWAY

    override fun onCreate() {
        super.onCreate()
        instance = this
        meshManager = NearbyConnectionManager(this, this)
        nat = UserSpaceNAT { originId, data ->
            // Send response back over mesh
            meshManager.sendToNode(originId, data, MeshPacket.PacketType.VPN_IP_PACKET)
        }
        
        try {
            webServer = com.zaiah.meshapp.network.MeshWebServer(8080)
            webServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
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
        if (packet.type == MeshPacket.PacketType.VPN_IP_PACKET) {
            if (isGateway) {
                nat?.handlePacket(packet.originId, packet.data)
            } else {
                vpnService?.injectPacket(packet.data)
            }
        } else if (packet.type == MeshPacket.PacketType.TEXT) {
            val msg = String(packet.data, java.nio.charset.Charset.forName("UTF-8"))
            chatMessages.add(msg)
            chatListener?.invoke(msg)
        }
    }
    override fun onTopologyUpdated(neighbors: Set<String>, routes: Map<String, RouteEntry>) {
        this.neighbors = neighbors
        this.routes = routes
    }
}
