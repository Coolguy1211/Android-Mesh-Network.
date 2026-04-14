package com.zaiah.meshapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.Payload
import com.zaiah.meshapp.databinding.ActivityMainBinding
import com.zaiah.meshapp.network.NearbyConnectionManager
import com.zaiah.meshapp.network.MeshVpnService

class MainActivity : AppCompatActivity(), NearbyConnectionManager.ConnectionListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var meshManager: NearbyConnectionManager
    private var isGateway = false

    private val vpnRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startService(Intent(this, MeshVpnService::class.java))
            Toast.makeText(this, "VPN Tunnel active", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            setupMesh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        meshManager = NearbyConnectionManager(this, this)

        binding.btnStartMesh.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.btnShareInternet.setOnClickListener {
            isGateway = !isGateway
            binding.btnShareInternet.text = if (isGateway) "Gateway Mode: ON" else "Become Gateway"
            if (!isGateway) {
                // If not gateway, we act as a client needing VPN
                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent != null) {
                    vpnRequestLauncher.launch(vpnIntent)
                } else {
                    startService(Intent(this, MeshVpnService::class.java))
                }
            }
        }
    }

    private fun setupMesh() {
        meshManager.startMesh(Build.MODEL)
        binding.textViewStatus.text = "Status: Mesh Active (Searching...)"
    }

    override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
        Log.d("Mesh", "Connecting to ${info.endpointName}")
    }

    override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
        if (result.status.isSuccess) {
            binding.textViewStatus.text = "Status: Connected to Mesh"
        }
    }

    override fun onDisconnected(endpointId: String) {
        binding.textViewStatus.text = "Status: Disconnected from Mesh"
    }

    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        if (payload.type == Payload.Type.BYTES) {
            val message = String(payload.asBytes()!!)
            Toast.makeText(this, "Message: $message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_INT.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            setupMesh()
        }
    }

    override fun onDestroy() {
        meshManager.stopAll()
        super.onDestroy()
    }
}
