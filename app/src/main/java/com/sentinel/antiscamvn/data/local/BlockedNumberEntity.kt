package com.sentinel.antiscamvn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey val phoneNumber: String,
    val dateBlocked: Long = System.currentTimeMillis()
)