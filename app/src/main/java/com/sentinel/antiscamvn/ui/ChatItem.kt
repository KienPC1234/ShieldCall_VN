package com.sentinel.antiscamvn.ui

sealed class ChatItem {
    data class Message(val message: SmsMessage) : ChatItem()
    data class DateHeader(val date: Long) : ChatItem()
    object NewMessageIndicator : ChatItem()
}
