package com.zaiah.meshapp.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Receiver to handle Device Admin events.
 * This allows the app to request high-level system persistence.
 */
class MeshDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Mesh Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Mesh Admin Disabled", Toast.LENGTH_SHORT).show()
    }
}
