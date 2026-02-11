package com.sentinel.antiscamvn.phone

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import com.sentinel.antiscamvn.MainActivity
import com.sentinel.antiscamvn.R
import com.sentinel.antiscamvn.utils.LogManager

class ShieldInCallService : InCallService() {

    companion object {
        var currentCall: Call? = null
        private val listeners = mutableListOf<CallStateListener>()
        const val ACTION_ACCEPT = "com.sentinel.antiscamvn.ACTION_ACCEPT"
        const val ACTION_REJECT = "com.sentinel.antiscamvn.ACTION_REJECT"

        fun addListener(listener: CallStateListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: CallStateListener) {
            listeners.remove(listener)
        }

        fun updateState(call: Call) {
            listeners.forEach { it.onStateChanged(call) }
        }
    }

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ACCEPT -> currentCall?.answer(0)
                ACTION_REJECT -> currentCall?.reject(false, null)
            }
            context?.getSystemService(android.app.NotificationManager::class.java)?.cancel(2001)
        }
    }

    interface CallStateListener {
        fun onStateChanged(call: Call)
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(ACTION_ACCEPT)
            addAction(ACTION_REJECT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(callReceiver)
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            updateState(call)
            if (state == Call.STATE_DISCONNECTED) {
                currentCall = null
                call.unregisterCallback(this)
                getSystemService(android.app.NotificationManager::class.java).cancel(2001)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callback)
        LogManager.log("ShieldInCallService", "New call added: $call")

        if (call.state == Call.STATE_RINGING) {
            showIncomingCallNotification(call)
        } else {
            launchCallActivity(false)
        }
        updateState(call)
    }

    private fun showIncomingCallNotification(call: Call) {
        val handle = call.details.handle?.schemeSpecificPart ?: "Unknown"
        
        val acceptIntent = Intent(ACTION_ACCEPT)
        val acceptPendingIntent = PendingIntent.getBroadcast(this, 0, acceptIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val rejectIntent = Intent(ACTION_REJECT)
        val rejectPendingIntent = PendingIntent.getBroadcast(this, 1, rejectIntent, PendingIntent.FLAG_IMMUTABLE)

        val fullScreenIntent = Intent(this, CallActivity::class.java).apply {
            putExtra("is_incoming", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 2, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "call_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Cuộc gọi đến")
            .setContentText(handle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_arrow_back, "Từ chối", rejectPendingIntent) // Use existing icons
            .addAction(R.drawable.ic_verified, "Trả lời", acceptPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        getSystemService(android.app.NotificationManager::class.java).notify(2001, notification)
    }

    private fun launchCallActivity(isIncoming: Boolean) {
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("is_incoming", isIncoming)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        currentCall = null
        updateState(call)
        LogManager.log("ShieldInCallService", "Call removed")
    }
}
