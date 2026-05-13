package com.yueming.baby.data.dao

import androidx.room.*
import com.yueming.baby.data.entity.BabyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyDao {
    @Query("SELECT * FROM babies ORDER BY createdAt ASC")
    fun getAllBabiesFlow(): Flow<List<BabyEntity>>

    @Query("SELECT * FROM babies ORDER BY createdAt ASC")
    suspend fun getAllBabies(): List<BabyEntity>

    @Query("SELECT * FROM babies WHERE id = :id")
    suspend fun getBabyById(id: String): BabyEntity?

    @Query("SELECT * FROM babies WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveBaby(): BabyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(baby: BabyEntity)

    @Query("DELETE FROM babies WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM babies")
    suspend fun deleteAll()

    @Query("UPDATE babies SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE babies SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)
}
