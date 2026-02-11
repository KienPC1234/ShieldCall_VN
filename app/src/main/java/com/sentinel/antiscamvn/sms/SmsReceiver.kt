package com.sentinel.antiscamvn.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import com.sentinel.antiscamvn.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION || 
            intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                LogManager.log("SmsReceiver", "Received SMS intent but messages list is empty")
                return
            }

            val sb = StringBuilder()
            var sender = ""
            var timestamp = System.currentTimeMillis()
            
            if (messages.isNotEmpty()) {
                sender = messages[0].displayOriginatingAddress ?: "Unknown"
                timestamp = messages[0].timestampMillis
                
                for (sms in messages) {
                    sb.append(sms.messageBody)
                }
            }
            
            val combinedBody = sb.toString()
            LogManager.log("SmsReceiver", "Processing SMS from $sender: ${combinedBody.take(20)}...")
            
            // Save to local DB (Fire & Forget) using goAsync to keep receiver alive
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = com.sentinel.antiscamvn.sms.SmsRepository(context)
                    // We need to ensure saveMessage exists in Repository (I will add it back in next step)
                    repository.saveMessage(sender, combinedBody, isSent = false, date = timestamp)
                    LogManager.log("SmsReceiver", "Saved to local DB")
                } catch (e: Exception) {
                    LogManager.log("SmsReceiver", "Failed to save to local DB: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }

            LogManager.log("SmsReceiver", "Full message reconstructed. Sender: $sender, Length: ${combinedBody.length}")

            // If we are the default SMS app, we must write to the provider
            if (isDefaultSmsApp(context)) {
                LogManager.log("SmsReceiver", "App is default SMS handler. Writing to Inbox.")
                writeToInbox(context, sender, combinedBody, timestamp)
            } else {
                 LogManager.log("SmsReceiver", "App is NOT default SMS handler. Skipping write to Inbox.")
            }

            // Gửi nội dung này cho AI phân tích ngầm
            showNotification(context, sender, combinedBody)
            // Toast removed per user request
        }
    }
    
    private fun showNotification(context: Context, sender: String?, body: String) {
        val channelId = "SmsChannel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Tin nhắn mới", android.app.NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(context, com.sentinel.antiscamvn.MainActivity::class.java)
        intent.putExtra("sender_address", sender)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(sender ?: "Tin nhắn mới")
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun isDefaultSmsApp(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        } else {
            true
        }
    }
    
    private fun writeToInbox(context: Context, address: String?, body: String, date: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.READ, 0) // Unread
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            LogManager.log("SmsReceiver", "Successfully wrote message to Inbox: $address")
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error writing to inbox: ${e.message}")
            LogManager.log("SmsReceiver", "Error writing to inbox: ${e.message}")
        }
    }
}