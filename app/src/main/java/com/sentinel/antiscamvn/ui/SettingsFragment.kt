package com.sentinel.antiscamvn.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.sentinel.antiscamvn.PermissionActivity
import com.sentinel.antiscamvn.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Log Viewer
        findPreference<Preference>("view_logs")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), LogViewerActivity::class.java))
            true
        }

        // Permissions
        findPreference<Preference>("manage_permissions")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), PermissionActivity::class.java))
            true
        }
        
        // Guide
        findPreference<Preference>("app_guide")?.setOnPreferenceClickListener {
            try {
                findNavController().navigate(R.id.nav_guide)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            true
        }

        // Theme Handling
        findPreference<ListPreference>("theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
            applyTheme(newValue as String)
            true
        }
    }

    private fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
