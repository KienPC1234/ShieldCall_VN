package com.sentinel.antiscamvn.data.local

import androidx.room.Embedded

data class ConversationResult(
    @Embedded val sms: SmsEntity,
    val unreadCount: Int
)
