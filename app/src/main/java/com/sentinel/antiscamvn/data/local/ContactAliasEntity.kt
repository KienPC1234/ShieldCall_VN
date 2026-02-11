package com.sentinel.antiscamvn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_aliases")
data class ContactAliasEntity(
    @PrimaryKey val phoneNumber: String,
    val displayName: String? = null,
    val avatarPath: String? = null,
    val isArchived: Boolean = false,
    val aiTag: String? = null,
    val riskLevel: String? = null
)