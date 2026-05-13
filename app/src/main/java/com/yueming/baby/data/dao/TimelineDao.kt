package com.yueming.baby.data.dao

import androidx.room.*
import com.yueming.baby.data.entity.TimelineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline ORDER BY date DESC")
    fun getAll(): Flow<List<TimelineEntity>>

    @Query("SELECT * FROM timeline ORDER BY date DESC")
    suspend fun getAllOnce(): List<TimelineEntity>

    @Query("SELECT * FROM timeline WHERE babyId = :babyId ORDER BY date DESC")
    suspend fun getAllByBaby(babyId: String): List<TimelineEntity>

    @Query("SELECT * FROM timeline WHERE id = :id")
    suspend fun getById(id: String): TimelineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TimelineEntity)

    @Update
    suspend fun update(record: TimelineEntity)

    @Delete
    suspend fun delete(record: TimelineEntity)

    @Query("DELETE FROM timeline WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM timeline WHERE babyId = :babyId")
    suspend fun deleteByBabyId(babyId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TimelineEntity>)

    @Query("SELECT COUNT(*) FROM timeline")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM timeline WHERE babyId = :babyId")
    suspend fun countByBaby(babyId: String): Int
}
