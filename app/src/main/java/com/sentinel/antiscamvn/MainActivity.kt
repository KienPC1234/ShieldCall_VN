package com.sentinel.antiscamvn

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply Theme
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val themeMode = prefs.getString("theme_mode", "system")
        val nightMode = when (themeMode) {
            "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
        
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        
        if (navHostFragment != null) {
            navController = navHostFragment.navController
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            
            // Sử dụng cách thủ công để tránh lỗi thư viện NavigationUI nếu có lằng nhằng
            bottomNav.setOnItemSelectedListener { item ->
                NavigationUI.onNavDestinationSelected(item, navController)
                true
            }
            
            // Đảm bảo BottomNav cập nhật khi NavController chuyển hướng (ví dụ qua code)
            navController.addOnDestinationChangedListener { _, destination, _ ->
                bottomNav.menu.findItem(destination.id)?.isChecked = true
            }
        }

        checkDefaultSmsApp()
        startOverlayService()
        
        handleSmsIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleSmsIntent(intent)
    }
    
    private fun handleSmsIntent(intent: Intent?) {
        val sender = intent?.getStringExtra("sender_address")
        val draftBody = intent?.getStringExtra("draft_body")
        
        if (!sender.isNullOrEmpty()) {
             // Navigate to SmsDetailFragment via global action or manually
             try {
                 // Ensure proper back stack: Home -> SmsFragment -> SmsDetailFragment
                 navController.popBackStack(R.id.nav_home, false)
                 navController.navigate(R.id.nav_sms)
                 
                 val bundle = Bundle().apply { 
                     putString("address", sender)
                     if (!draftBody.isNullOrEmpty()) putString("draft_body", draftBody)
                 }
                 navController.navigate(R.id.nav_sms_detail, bundle)
             } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun checkDefaultSmsApp() {
        if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                        @Suppress("DEPRECATION")
                        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS), 101)
                    }
                }
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivity(intent)
            }
        }
    }

    private fun startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }

        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_HEAD
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}