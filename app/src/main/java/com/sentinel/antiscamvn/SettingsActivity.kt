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
        val btnBack = findViewById<Button>(R.id.btn_back)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Load saved state
        switchDebug.isChecked = prefs.getBoolean("debug_mode", false)

        // Save state on change
        switchDebug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("debug_mode", isChecked).apply()
        }

        btnBack.setOnClickListener { finish() }
    }
}