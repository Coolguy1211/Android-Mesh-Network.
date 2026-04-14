package com.zaiah.meshapp.network

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.io.File

class NearbyConnectionManager(private val context: Context, private val listener: ConnectionListener) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = mutableMapOf<String, String>() // id to name
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.zaiah.meshapp.SERVICE_ID"

    interface ConnectionListener {
        fun onConnectionInitiated(endpointId: String, info: ConnectionInfo)
        fun onConnectionResult(endpointId: String, result: ConnectionResolution)
        fun onDisconnected(endpointId: String)
        fun onPayloadReceived(endpointId: String, payload: Payload)
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            listener.onConnectionInitiated(endpointId, info)
            // Automatically accept the connection for simplicity in a mesh
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints[endpointId] = endpointId // Update with actual name if available
            }
            listener.onConnectionResult(endpointId, result)
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            listener.onDisconnected(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            listener.onPayloadReceived(endpointId, payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Handle file transfer progress here if needed
        }
    }

    fun startMesh(nickname: String) {
        startAdvertising(nickname)
        startDiscovery()
    }

    private fun startAdvertising(nickname: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(nickname, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnFailureListener { e -> Log.e("Nearby", "Advertising failed", e) }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                connectionsClient.requestConnection(SERVICE_ID, endpointId, connectionLifecycleCallback)
            }

            override fun onEndpointLost(endpointId: String) {}
        }, options)
            .addOnFailureListener { e -> Log.e("Nearby", "Discovery failed", e) }
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
    }

    fun sendBytes(data: ByteArray) {
        if (connectedEndpoints.isNotEmpty()) {
            val payload = Payload.fromBytes(data)
            connectionsClient.sendPayload(connectedEndpoints.keys.toList(), payload)
        }
    }

    fun sendFile(file: File) {
        if (connectedEndpoints.isNotEmpty()) {
            val payload = Payload.fromFile(file)
            connectionsClient.sendPayload(connectedEndpoints.keys.toList(), payload)
        }
    }
}
