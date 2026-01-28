package com.sentinel.antiscamvn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.preference.PreferenceManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isProtectionEnabled = prefs.getBoolean("protection_enabled", true)

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d("CallReceiver", "Phone state: $state, Protection enabled: $isProtectionEnabled")

        if (!isProtectionEnabled) {
            return // Don't do anything if protection is off
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                Log.d("CallReceiver", "Incoming call from: $incomingNumber")

                if (!incomingNumber.isNullOrEmpty()) {
                    val serviceIntent = Intent(context, OverlayService::class.java).apply {
                        action = "SHOW_POPUP"
                        putExtra("PHONE", incomingNumber)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
            else -> {
                // Do nothing, let the service continue running
            }
        }
    }
}
