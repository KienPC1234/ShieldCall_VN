package com.sentinel.antiscamvn.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContactAliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: ContactAliasEntity)

    @Query("SELECT * FROM contact_aliases WHERE phoneNumber = :phoneNumber")
    suspend fun getAlias(phoneNumber: String): ContactAliasEntity?

    @Query("SELECT * FROM contact_aliases")
    suspend fun getAllAliases(): List<ContactAliasEntity>
    
    @Query("UPDATE contact_aliases SET isArchived = :isArchived WHERE phoneNumber = :phoneNumber")
    suspend fun updateArchivedStatus(phoneNumber: String, isArchived: Boolean)

    @Query("UPDATE contact_aliases SET aiTag = :tag, riskLevel = :risk WHERE phoneNumber = :phoneNumber")
    suspend fun updateAiInfo(phoneNumber: String, tag: String, risk: String)
}