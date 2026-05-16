package com.yueming.baby.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
