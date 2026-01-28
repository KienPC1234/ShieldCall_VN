package com.sentinel.antiscamvn

import android.os.Bundle
import android.widget.Button
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.core.content.edit

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val switchDebug = findViewById<MaterialSwitch>(R.id.switch_debug)
        val switchProtection = findViewById<MaterialSwitch>(R.id.switch_protection)
        val switchSensitivity = findViewById<MaterialSwitch>(R.id.switch_sensitivity)
        val switchParentMode = findViewById<MaterialSwitch>(R.id.switch_parent_mode)
        val btnBack = findViewById<Button>(R.id.btn_back)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Load saved state
        switchDebug.isChecked = prefs.getBoolean("debug_mode", false)
        switchProtection.isChecked = prefs.getBoolean("protection_enabled", true)
        switchSensitivity.isChecked = prefs.getBoolean("high_sensitivity", false)
        switchParentMode.isChecked = prefs.getBoolean("parent_mode_enabled", false)

        // Save state on change
        switchDebug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("debug_mode", isChecked) }
        }
        
        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("protection_enabled", isChecked) }
        }

        switchSensitivity.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("high_sensitivity", isChecked) }
        }

        switchParentMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("parent_mode_enabled", isChecked) }
        }

        btnBack.setOnClickListener { finish() }
    }
}