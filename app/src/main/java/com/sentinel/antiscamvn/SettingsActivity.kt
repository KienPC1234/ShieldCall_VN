package com.sentinel.antiscamvn

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val switchDebug = findViewById<Switch>(R.id.switch_debug)
        val switchProtection = findViewById<Switch>(R.id.switch_protection)
        val switchSensitivity = findViewById<Switch>(R.id.switch_sensitivity)
        val switchParentMode = findViewById<Switch>(R.id.switch_parent_mode)
        val btnBack = findViewById<Button>(R.id.btn_back)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Load saved state
        switchDebug.isChecked = prefs.getBoolean("debug_mode", false)
        switchProtection.isChecked = prefs.getBoolean("protection_enabled", true)
        switchSensitivity.isChecked = prefs.getBoolean("high_sensitivity", false)
        switchParentMode.isChecked = prefs.getBoolean("parent_mode_enabled", false)

        // Save state on change
        switchDebug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("debug_mode", isChecked).apply()
        }
        
        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("protection_enabled", isChecked).apply()
        }

        switchSensitivity.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("high_sensitivity", isChecked).apply()
        }

        switchParentMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("parent_mode_enabled", isChecked).apply()
        }

        btnBack.setOnClickListener { finish() }
    }
}