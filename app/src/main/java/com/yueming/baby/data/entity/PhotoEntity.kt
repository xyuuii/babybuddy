package com.yueming.baby.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val babyId: String = "",
    val url: String,
    val caption: String,
    val date: String,
    val timelineRecordId: String?,
    val tags: String
)
