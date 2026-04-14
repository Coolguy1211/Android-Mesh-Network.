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
import com.zaiah.meshapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val meshApp get() = MeshApp.instance

    private val vpnRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
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

        binding.btnStartMesh.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.btnShareInternet.setOnClickListener {
            meshApp.isGateway = !meshApp.isGateway
            binding.btnShareInternet.text = if (meshApp.isGateway) "Gateway Mode: ON" else "Become Gateway"
            if (!meshApp.isGateway) {
                prepareVpn()
            }
        }
        
        // Start a simple UI update loop for the dashboard
        startDashboardUpdates()
    }

    private fun startVpnService() {
        startService(Intent(this, com.zaiah.meshapp.network.MeshVpnService::class.java))
        Toast.makeText(this, "VPN Tunnel active", Toast.LENGTH_SHORT).show()
    }

    private fun prepareVpn() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnRequestLauncher.launch(vpnIntent)
        } else {
            startVpnService()
        }
    }

    private fun setupMesh() {
        meshApp.meshManager.startMesh(Build.MODEL)
        binding.textViewStatus.text = "Status: Mesh Active (Searching...)"
    }

    private fun startDashboardUpdates() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                val stats = "Neighbors: ${meshApp.neighbors.size}\nRoutes: ${meshApp.routes.size}\n" +
                        meshApp.routes.values.joinToString("\n") { 
                            "-> ${it.destinationId} via ${it.nextHopId} (${it.hopCount} hops)" 
                        }
                // We'll log it for now, can be put in a TextView if added
                Log.d("Dashboard", stats)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 5000)
            }
        }, 5000)
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
}
