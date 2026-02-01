package com.sentinel.antiscamvn.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(sms: SmsEntity): Long

    // Get latest message for each thread/address to show in conversation list
    // Also count unread messages for that address
    @Query("SELECT *, (SELECT COUNT(*) FROM sms_messages s2 WHERE s2.address = sms_messages.address AND s2.read = 0) as unreadCount FROM sms_messages WHERE id IN (SELECT MAX(id) FROM sms_messages GROUP BY address) ORDER BY date DESC")
    suspend fun getConversations(): List<ConversationResult>

    // Get all messages for a specific address
    // Canonicalize address search usually happens in Repository, here we assume exact or normalized match
    @Query("SELECT * FROM sms_messages WHERE address = :address OR address = :normalizedAddress ORDER BY date ASC")
    suspend fun getMessages(address: String, normalizedAddress: String): List<SmsEntity>

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId ORDER BY date ASC")
    suspend fun getMessagesByThread(threadId: Long): List<SmsEntity>
    
    // Helper to find existing threadId for an address
    @Query("SELECT threadId FROM sms_messages WHERE address = :address LIMIT 1")
    suspend fun getThreadIdForAddress(address: String): Long?

    @Query("UPDATE sms_messages SET read = 1 WHERE threadId = :threadId")
    suspend fun markThreadRead(threadId: Long)

    @Query("UPDATE sms_messages SET read = 1 WHERE address = :address")
    suspend fun markAddressRead(address: String)
}
