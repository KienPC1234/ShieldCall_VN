package com.sentinel.antiscamvn.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockNumber(blockedNumber: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun unblockNumber(phoneNumber: String)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE phoneNumber = :phoneNumber)")
    suspend fun isBlocked(phoneNumber: String): Boolean

    @Query("SELECT * FROM blocked_numbers")
    suspend fun getAllBlockedNumbers(): List<BlockedNumberEntity>
}