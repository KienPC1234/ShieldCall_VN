package com.sentinel.antiscamvn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {

    private lateinit var permissionCallLayout: LinearLayout
    private lateinit var btnGrantCallPermission: Button
    private lateinit var permissionOverlayLayout: LinearLayout
    private lateinit var btnGrantOverlayPermission: Button

    private val requestCallPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        permissionCallLayout = findViewById(R.id.permission_call_layout)
        btnGrantCallPermission = findViewById(R.id.btn_grant_call_permission)
        permissionOverlayLayout = findViewById(R.id.permission_overlay_layout)
        btnGrantOverlayPermission = findViewById(R.id.btn_grant_overlay_permission)

        btnGrantCallPermission.setOnClickListener {
            val permissions = mutableListOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestCallPermissionsLauncher.launch(permissions.toTypedArray())
        }

        btnGrantOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                android.widget.Toast.makeText(this, "Vui lòng tìm ShieldCallVN và bật 'Cho phép vẽ trên ứng dụng khác'", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        val hasPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasCallPermission = hasPhoneState && hasCallLog && hasRecordAudio && hasNotification && hasContacts

        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        permissionCallLayout.visibility = if (hasCallPermission) View.GONE else View.VISIBLE
        permissionOverlayLayout.visibility = if (hasOverlayPermission) View.GONE else View.VISIBLE

        if (hasCallPermission && hasOverlayPermission) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
