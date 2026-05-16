package com.yueming.baby.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "babies")
data class BabyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val nickname: String,
    val birthDate: String,
    val gender: String,
    val avatarUri: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
