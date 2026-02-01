package com.sentinel.antiscamvn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_aliases")
data class ContactAliasEntity(
    @PrimaryKey val phoneNumber: String, // Normalized phone number
    val displayName: String?,
    val avatarPath: String?, // URI string or file path
    val isArchived: Boolean = false
)