package com.yueming.baby.data.dao

import androidx.room.*
import com.yueming.baby.data.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY date DESC")
    fun getAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY date DESC")
    suspend fun getAllOnce(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE babyId = :babyId ORDER BY date DESC")
    suspend fun getAllByBaby(babyId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Delete
    suspend fun delete(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM photos WHERE timelineRecordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)

    @Query("DELETE FROM photos WHERE babyId = :babyId")
    suspend fun deleteByBabyId(babyId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM photos WHERE babyId = :babyId")
    suspend fun countByBaby(babyId: String): Int
}
