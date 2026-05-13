package com.yueming.baby.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timeline")
data class TimelineEntity(
    @PrimaryKey val id: String,
    val babyId: String = "",
    val date: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: String,
    val photos: String,
    val videos: String
)
