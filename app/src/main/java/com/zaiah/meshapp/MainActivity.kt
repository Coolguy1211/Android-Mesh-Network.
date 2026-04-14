package com.zaiah.meshapp

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zaiah.meshapp.databinding.ActivityMainBinding
import com.zaiah.meshapp.receiver.MeshDeviceAdminReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val meshApp get() = MeshApp.instance
    private var wakeLock: PowerManager.WakeLock? = null

    private val vpnRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    private val adminRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        checkAdminStatus()
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

        // Keep Screen On
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnStartMesh.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.btnShareInternet.setOnClickListener {
            val newMode = !meshApp.isGateway
            meshApp.setGatewayMode(newMode)
            binding.btnShareInternet.text = if (meshApp.isGateway) "Gateway Mode: ON" else "Become Gateway"
            if (!meshApp.isGateway) {
                prepareVpn()
            }
        }

        // Request Device Admin and Battery Optimization exclusion
        requestBatteryOptimizations()
        requestDeviceAdmin()
        acquireWakeLock()
        
        startDashboardUpdates()
    }

    private fun checkAdminStatus() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminName = ComponentName(this, MeshDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(adminName)) {
            Toast.makeText(this, "Admin Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminName = ComponentName(this, MeshDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_description))
            }
            adminRequestLauncher.launch(intent)
        }
    }

    private fun requestBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_INT.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeshApp::WakeLock")
        wakeLock?.acquire()
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

    override fun onDestroy() {
        wakeLock?.release()
        super.onDestroy()
    }
}
