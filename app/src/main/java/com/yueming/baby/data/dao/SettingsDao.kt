package com.yueming.baby.data.dao

import androidx.room.*
import com.yueming.baby.data.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingsEntity?

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
