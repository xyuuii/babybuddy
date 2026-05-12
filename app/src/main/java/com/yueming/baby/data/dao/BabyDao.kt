package com.yueming.baby.data.dao

import androidx.room.*
import com.yueming.baby.data.entity.BabyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyDao {
    @Query("SELECT * FROM baby WHERE id = 1")
    fun getBabyFlow(): Flow<BabyEntity?>

    @Query("SELECT * FROM baby WHERE id = 1")
    suspend fun getBaby(): BabyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(baby: BabyEntity)

    @Query("DELETE FROM baby")
    suspend fun deleteAll()
}
