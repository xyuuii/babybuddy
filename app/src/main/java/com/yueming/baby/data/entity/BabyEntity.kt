package com.yueming.baby.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "baby")
data class BabyEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val nickname: String,
    val birthDate: String,
    val gender: String,
    val avatar: String?
)
