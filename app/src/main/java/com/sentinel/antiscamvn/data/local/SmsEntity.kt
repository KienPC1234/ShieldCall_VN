package com.sentinel.antiscamvn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_messages")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean = false,
    val isSent: Boolean = false, // true = sent by us, false = inbox
    val threadId: Long = 0 // We can try to match system threadId or generate our own
)
