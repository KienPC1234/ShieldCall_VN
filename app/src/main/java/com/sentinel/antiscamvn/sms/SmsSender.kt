package com.sentinel.antiscamvn.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.sentinel.antiscamvn.utils.LogManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Module responsible for Sending SMS with status tracking.
 */
class SmsSender(private val context: Context) {

    private val SENT_ACTION = "com.sentinel.antiscamvn.SMS_SENT"

    /**
     * Sends a text message and writes it to the system database with proper status.
     * Returns true if the carrier accepted the request.
     */
    suspend fun sendSms(address: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (address.isBlank() || body.isBlank()) {
            LogManager.log("SmsSender", "Address or Body is empty. Aborting.")
            return@withContext false
        }
        
        LogManager.log("SmsSender", "Attempting to send SMS to: $address")

        val sentDeferred = CompletableDeferred<Boolean>()

        // Receiver to handle the immediate "Sent" result from the modem
        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val resultCode = resultCode
                val errorCode = intent.getIntExtra("errorCode", -1) // Some devices pass extra error
                
                LogManager.log("SmsSender", "Broadcast received. ResultCode: $resultCode, ErrorCode extra: $errorCode")
                
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        LogManager.log("SmsSender", "SMS sent successfully to $address")
                        
                        writeToSentBox(address, body, Telephony.Sms.MESSAGE_TYPE_SENT)
                        sentDeferred.complete(true)
                    }
                    else -> {
                        // Detailed error logging
                        val errorReason = when (resultCode) {
                            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic Failure (1)"
                            SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio Off (2)"
                            SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU (3)"
                            SmsManager.RESULT_ERROR_NO_SERVICE -> "No Service (4)"
                            32 -> "RIL/Modem Error (32) - Check SIM/Credit"
                            else -> "Unknown Code ($resultCode)"
                        }
                        
                        LogManager.log("SmsSender", "SMS Send Failed. Reason: $errorReason")
                        writeToSentBox(address, body, Telephony.Sms.MESSAGE_TYPE_FAILED)
                        sentDeferred.complete(false)
                    }
                }
                try {
                    context.unregisterReceiver(this)
                } catch (e: Exception) {}
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(sentReceiver, IntentFilter(SENT_ACTION), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(sentReceiver, IntentFilter(SENT_ACTION))
            }

            val smsManager = getBestSmsManager()
            LogManager.log("SmsSender", "Using SmsManager for subscriptionId: ${smsManager.subscriptionId}")
            
            val parts = smsManager.divideMessage(body)

            // Create PendingIntent
            val sentPendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(SENT_ACTION), 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val sentIntents = ArrayList<PendingIntent>()
            for (i in parts.indices) sentIntents.add(sentPendingIntent)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(address, null, body, sentPendingIntent, null)
            }
            
            // Wait with timeout
            try {
                return@withContext kotlinx.coroutines.withTimeout(10000L) {
                     sentDeferred.await()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                LogManager.log("SmsSender", "SMS send timed out waiting for broadcast")
                try { context.unregisterReceiver(sentReceiver) } catch (e2: Exception) {}
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e("SmsSender", "Exception sending SMS", e)
            LogManager.log("SmsSender", "Exception sending SMS: ${e.message}")
            try { context.unregisterReceiver(sentReceiver) } catch (e2: Exception) {}
            return@withContext false
        }
    }

    private fun getBestSmsManager(): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val sm = context.getSystemService(SmsManager::class.java)
            
            // If we can, try to find a valid subscription ID if default is invalid
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subId = SmsManager.getDefaultSmsSubscriptionId()
                if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    // Try to find any active subscription
                    try {
                         val subManager = context.getSystemService(SubscriptionManager::class.java)
                         if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                             val activeSubs = subManager.activeSubscriptionInfoList
                             if (!activeSubs.isNullOrEmpty()) {
                                 val firstSubId = activeSubs[0].subscriptionId
                                 LogManager.log("SmsSender", "Default sub invalid, using first active sub: $firstSubId")
                                 return context.getSystemService(SmsManager::class.java).createForSubscriptionId(firstSubId)
                             }
                         }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    // If default is valid, prefer createForSubscriptionId to be explicit
                    return context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                }
            }
            return sm
        } else {
            @Suppress("DEPRECATION")
            return SmsManager.getDefault()
        }
    }

    private fun writeToSentBox(address: String, body: String, type: Int) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, type)
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("SmsSender", "Failed to write to Sent box", e)
        }
    }
}