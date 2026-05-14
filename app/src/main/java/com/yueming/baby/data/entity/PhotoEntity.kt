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
    val tags: String,
    val mediaType: String = "photo",
    val remoteUrl: String? = null,
    val remotePath: String? = null,
    val localOriginalPath: String? = null,
    val localThumbPath: String? = null,
    val localPreviewPath: String? = null,
    val uploadStatus: String = "SYNCED",
    val sha256: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
