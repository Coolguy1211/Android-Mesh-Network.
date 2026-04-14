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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        updatePermissionUi(allGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initial check
        updatePermissionUi(hasAllPermissions())

        binding.btnStartMesh.setOnClickListener {
            if (hasAllPermissions()) {
                setupMesh()
            } else {
                requestPermissions()
            }
        }

        binding.btnShareInternet.setOnClickListener {
            val newMode = !meshApp.isGateway
            meshApp.setGatewayMode(newMode)
            updateGatewayUi()
            if (!meshApp.isGateway) prepareVpn()
        }

        requestPersistence()
        startDashboardUpdates()
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            list.add(Manifest.permission.BLUETOOTH)
            list.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return list
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(getRequiredPermissions().toTypedArray())
    }

    private fun updatePermissionUi(granted: Boolean) {
        if (granted) {
            binding.textViewStatus.text = "Ready to Start"
            binding.textViewStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.btnStartMesh.isEnabled = true
            binding.btnStartMesh.text = "Start Mesh"
        } else {
            binding.textViewStatus.text = "Permissions Required"
            binding.textViewStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.btnStartMesh.isEnabled = true
            binding.btnStartMesh.text = "Grant Permissions"
        }
    }

    private fun updateGatewayUi() {
        binding.btnShareInternet.text = if (meshApp.isGateway) "Gateway Mode: ON" else "Become Gateway"
    }

    private fun setupMesh() {
        meshApp.meshManager.startMesh(Build.MODEL)
        binding.textViewStatus.text = "Mesh Active"
        binding.btnStartMesh.isEnabled = false
        Toast.makeText(this, "Mesh Discovery Started", Toast.LENGTH_SHORT).show()
    }

    private fun startDashboardUpdates() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val stats = StringBuilder()
                stats.append("LOCAL NODE: ${Build.MODEL}\n")
                stats.append("ROLE: ${if (meshApp.isGateway) "GATEWAY" else "CLIENT"}\n")
                stats.append("----------------------------\n")
                stats.append("NEIGHBORS (${meshApp.neighbors.size}):\n")
                meshApp.neighbors.forEach { stats.append(" • $it\n") }
                stats.append("\nROUTES (${meshApp.routes.size}):\n")
                meshApp.routes.values.forEach {
                    stats.append(" -> ${it.destinationId} via ${it.nextHopId} (${it.hopCount} hops) [${it.role}]\n")
                }
                binding.textViewDashboard.text = stats.toString()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun requestPersistence() {
        // Battery
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
        // WakeLock
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeshApp::Lock").apply { acquire() }
        // Admin
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, MeshDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Ensures mesh stability.")
            }
            startActivity(intent)
        }
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 0)
        else startService(Intent(this, com.zaiah.meshapp.network.MeshVpnService::class.java))
    }

    override fun onDestroy() {
        wakeLock?.release()
        super.onDestroy()
    }
}
