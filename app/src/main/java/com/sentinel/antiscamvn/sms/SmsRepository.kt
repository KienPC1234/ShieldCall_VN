package com.sentinel.antiscamvn.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.sentinel.antiscamvn.data.local.AppDatabase
import com.sentinel.antiscamvn.data.local.ContactAliasEntity
import com.sentinel.antiscamvn.ui.SmsConversation
import com.sentinel.antiscamvn.ui.SmsMessage
import com.sentinel.antiscamvn.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val smsDao = db.smsDao()
    private val contactAliasDao = db.contactAliasDao()

    /**
     * ✅ Get conversations from SYSTEM DB + LOCAL DB (Hybrid)
     */
    suspend fun getConversations(): List<SmsConversation> = withContext(Dispatchers.IO) {
        // Map to hold merged conversations by Address (Primary Key for merging)
        // We use address because ThreadID might differ between System and Local if not synced perfectly.
        val conversationMap = mutableMapOf<String, SmsConversation>()
        
        // Load custom aliases
        val aliases = try {
            contactAliasDao.getAllAliases().associateBy { it.phoneNumber }
        } catch (e: Exception) {
            emptyMap<String, ContactAliasEntity>()
        }

        // 1. Fetch System Conversations
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ
            )

            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                Telephony.Sms.DEFAULT_SORT_ORDER // DATE DESC
            )

            cursor?.use {
                val threadIdIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)

                val seenThreads = HashSet<Long>()

                while (it.moveToNext()) {
                    val threadId = it.getLong(threadIdIdx)
                    
                    if (!seenThreads.contains(threadId)) {
                        seenThreads.add(threadId)
                        
                        val address = it.getString(addressIdx) ?: ""
                        val body = it.getString(bodyIdx) ?: ""
                        val date = it.getLong(dateIdx)
                        val read = it.getInt(readIdx)
                        
                        val alias = aliases[address]
                        val contactName = alias?.displayName ?: getContactName(address)
                        val avatarPath = alias?.avatarPath
                        val isArchived = alias?.isArchived ?: false

                        // Add to map using Address as key
                        if (address.isNotEmpty()) {
                            conversationMap[address] = SmsConversation(
                                address = address,
                                contactName = contactName,
                                body = body,
                                date = date,
                                read = read,
                                threadId = threadId,
                                unreadCount = getUnreadCount(threadId),
                                avatarPath = avatarPath,
                                isArchived = isArchived
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "Error loading system conversations: ${e.message}")
        }
        
        // 2. Fetch Local Conversations and Merge
        // CRITICAL FIX: Merge logic must overwrite System entry if Local entry is newer
        try {
            val localConvos = smsDao.getConversations()
            localConvos.forEach { local ->
                val entity = local.sms
                val address = entity.address
                
                // If this address exists in map, check if local message is newer
                val existing = conversationMap[address]
                
                if (existing == null || entity.date > existing.date) {
                    val alias = aliases[address]
                    
                    // If we are replacing or adding, we use Local Data
                    // Note: threadId might be the local one. If replacing, we might want to keep System ThreadID if available?
                    // Usually safer to keep existing threadId if we have one, to ensure clicks open the right system thread.
                    val targetThreadId = existing?.threadId ?: entity.threadId
                    
                    conversationMap[address] = SmsConversation(
                        address = address,
                        contactName = alias?.displayName ?: getContactName(address),
                        body = entity.body,
                        date = entity.date,
                        read = if (entity.read) 1 else 0,
                        threadId = targetThreadId,
                        unreadCount = local.unreadCount, // Use local unread count or merge? Local count is reliable for local msgs
                        avatarPath = alias?.avatarPath,
                        isArchived = alias?.isArchived ?: false
                    )
                }
            }
        } catch (e: Exception) {
            LogManager.log(TAG, "Error merging local conversations: ${e.message}")
        }
        
        // Return sorted list
        return@withContext conversationMap.values.sortedByDescending { it.date }
    }

    /**
     * ✅ Get messages for a thread (or address) from SYSTEM DB + LOCAL DB
     * Prioritize fetching by Address to avoid Thread ID mismatches.
     */
    suspend fun getMessagesForThread(threadId: Long, address: String?): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()
        val seenIds = HashSet<String>() // To avoid duplicates
        
        val systemSelection: String
        val systemArgs: Array<String>
        
        if (!address.isNullOrEmpty()) {
            systemSelection = "${Telephony.Sms.ADDRESS} = ?"
            // Normalize address if needed, but strict match is safer for now
            systemArgs = arrayOf(address)
        } else {
            systemSelection = "${Telephony.Sms.THREAD_ID} = ?"
            systemArgs = arrayOf(threadId.toString())
        }

        // 1. Fetch from System
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            )
            
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                systemSelection,
                systemArgs,
                null
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    val id = it.getString(idIdx)
                    val body = it.getString(bodyIdx)
                    val date = it.getLong(dateIdx)
                    
                    // Simple dedupe key
                    val key = "$body$date"
                    seenIds.add(key)
                    
                    val type = it.getInt(typeIdx)
                    val isSent = type != Telephony.Sms.MESSAGE_TYPE_INBOX
                    
                    messages.add(SmsMessage(
                        id = id,
                        body = body,
                        date = date,
                        isSent = isSent,
                        read = it.getInt(readIdx) == 1,
                        type = type
                    ))
                }
            }
        } catch (e: Exception) {
             LogManager.log(TAG, "Error loading system messages: ${e.message}")
        }
        
        // 2. Fetch from Local DB
        try {
            val localMessages = smsDao.getMessagesByThread(threadId)
            localMessages.forEach { entity ->
                val key = "${entity.body}${entity.date}"
                if (!seenIds.contains(key)) {
                    messages.add(SmsMessage(
                        id = "LOC_${entity.id}",
                        body = entity.body,
                        date = entity.date,
                        isSent = entity.isSent,
                        read = entity.read,
                        type = if (entity.isSent) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX
                    ))
                }
            }
        } catch (e: Exception) {
             LogManager.log(TAG, "Error loading local messages: ${e.message}")
        }
        
        return@withContext messages.sortedBy { it.date }
    }

    /**
     * Save message to Local DB
     */
    suspend fun saveMessage(address: String, body: String, isSent: Boolean, date: Long = System.currentTimeMillis()): Long {
        return try {
            // Try to find existing threadId
            var threadId = getThreadIdForAddress(address)
            if (threadId == null || threadId == 0L) {
                 threadId = address.hashCode().toLong()
            }
            
            val sms = com.sentinel.antiscamvn.data.local.SmsEntity(
                address = address,
                body = body,
                date = date,
                read = isSent, // Sent are read
                isSent = isSent,
                threadId = threadId!!
            )
            smsDao.insertMessage(sms)
        } catch (e: Exception) {
            LogManager.log(TAG, "Error saving local message: ${e.message}")
            -1L
        }
    }

    private fun getUnreadCount(threadId: Long): Int {
        var count = 0
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    suspend fun markAsRead(threadId: Long) = withContext(Dispatchers.IO) {
        // Update System DB
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Update Local DB
        try {
            smsDao.markThreadRead(threadId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun markAddressAsRead(address: String) = withContext(Dispatchers.IO) {
        // Update System DB
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(address)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Update Local DB
        try {
            smsDao.markAddressRead(address)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun saveContactAlias(phoneNumber: String, name: String?, avatarPath: String?) = withContext(Dispatchers.IO) {
        val existing = contactAliasDao.getAlias(phoneNumber)
        val newEntity = if (existing != null) {
            existing.copy(displayName = name, avatarPath = avatarPath)
        } else {
            ContactAliasEntity(phoneNumber, name, avatarPath)
        }
        contactAliasDao.insertAlias(newEntity)
    }
    
    suspend fun archiveConversation(address: String, isArchived: Boolean) = withContext(Dispatchers.IO) {
        val existing = contactAliasDao.getAlias(address)
        if (existing != null) {
            contactAliasDao.updateArchivedStatus(address, isArchived)
        } else {
            contactAliasDao.insertAlias(ContactAliasEntity(address, null, null, isArchived))
        }
    }
    
    suspend fun getThreadIdForAddress(address: String): Long? = withContext(Dispatchers.IO) {
        var threadId: Long? = null
        try {
            val cursor = context.contentResolver.query(
                Uri.withAppendedPath(Telephony.Sms.Conversations.CONTENT_URI, "thread_id"), 
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(address),
                null
            ) ?: context.contentResolver.query(
                 Telephony.Sms.CONTENT_URI,
                 arrayOf(Telephony.Sms.THREAD_ID),
                 "${Telephony.Sms.ADDRESS} = ?",
                 arrayOf(address),
                 null
            )
            
            if (cursor != null && cursor.moveToFirst()) {
                 threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                 cursor.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext threadId
    }

    suspend fun deleteConversation(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
            return@withContext rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    // -------------------------
    // Helpers
    // -------------------------

    private fun getContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        var name: String? = null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                }
                cursor.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    companion object {
        private const val TAG = "SmsRepository"
    }
}