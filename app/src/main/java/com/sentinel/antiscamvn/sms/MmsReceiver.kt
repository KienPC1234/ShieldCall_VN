package com.sentinel.antiscamvn.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sentinel.antiscamvn.utils.LogManager

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Xử lý MMS (nếu cần)
        LogManager.log("MmsReceiver", "Received WAP/MMS push intent: ${intent.action}")
        
        try {
            val bundle = intent.extras
            if (bundle != null) {
                for (key in bundle.keySet()) {
                     LogManager.log("MmsReceiver", "Extra: $key = ${bundle.get(key)}")
                }
                
                // Try to parse transaction ID if available (basic check)
                val transactionId = bundle.getString("transactionId")
                if (transactionId != null) {
                    LogManager.log("MmsReceiver", "MMS Transaction ID: $transactionId")
                }
            }
        } catch (e: Exception) {
            LogManager.log("MmsReceiver", "Error parsing WAP push intent: ${e.message}")
        }
    }
}