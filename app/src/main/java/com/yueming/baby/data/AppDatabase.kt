package com.yueming.baby.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yueming.baby.data.dao.*
import com.yueming.baby.data.entity.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [TimelineEntity::class, PhotoEntity::class, BabyEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timelineDao(): TimelineDao
    abstract fun photoDao(): PhotoDao
    abstract fun babyDao(): BabyDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yueming.db"
                )
                    .addCallback(PrepopulateCallback())
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private class PrepopulateCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        // Insert sample baby
                        val baby = BabyEntity(
                            name = sampleBaby.name,
                            nickname = sampleBaby.nickname,
                            birthDate = sampleBaby.birthDate,
                            gender = sampleBaby.gender,
                            avatar = sampleBaby.avatar
                        )
                        database.babyDao().upsert(baby)

                        // Insert sample timeline
                        val timelineEntities = sampleTimeline.map { record ->
                            TimelineEntity(
                                id = record.id,
                                date = record.date,
                                title = record.title,
                                description = record.description,
                                category = record.category,
                                tags = Gson().toJson(record.tags),
                                photos = Gson().toJson(record.photos),
                                videos = Gson().toJson(record.videos)
                            )
                        }
                        database.timelineDao().insertAll(timelineEntities)

                        // Insert sample photos
                        val photoEntities = samplePhotos.map { photo ->
                            PhotoEntity(
                                id = photo.id,
                                url = photo.url,
                                caption = photo.caption,
                                date = photo.date,
                                timelineRecordId = photo.timelineRecordId,
                                tags = Gson().toJson(photo.tags)
                            )
                        }
                        database.photoDao().insertAll(photoEntities)
                    }
                }
            }
        }
    }

    // Export database to JSON (excluding binary image data)
    suspend fun exportToJson(): String {
        val gson = Gson()
        val timeline = timelineDao().getAllOnce()
        val photos = photoDao().getAllOnce()
        val baby = babyDao().getBaby()
        val settings = settingsDao().getAll()

        val export = mapOf(
            "baby" to (baby?.let {
                mapOf("name" to it.name, "nickname" to it.nickname,
                    "birthDate" to it.birthDate, "gender" to it.gender,
                    "avatar" to it.avatar)
            } ?: emptyMap<String, Any?>()),
            "timeline" to timeline.map {
                mapOf(
                    "id" to it.id, "date" to it.date, "title" to it.title,
                    "description" to it.description, "category" to it.category,
                    "tags" to try { Gson().fromJson<List<String>>(it.tags, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList<String>() },
                    "photos" to try { Gson().fromJson<List<String>>(it.photos, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList<String>() },
                    "videos" to try { Gson().fromJson<List<String>>(it.videos, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList<String>() }
                )
            },
            "photos" to photos.map {
                mapOf(
                    "id" to it.id, "url" to it.url, "caption" to it.caption,
                    "date" to it.date, "timelineRecordId" to it.timelineRecordId,
                    "tags" to try { Gson().fromJson<List<String>>(it.tags, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList<String>() }
                )
            },
            "settings" to settings.associate { it.key to it.value }
        )
        return Gson().toJson(export)
    }

    // Import from JSON
    suspend fun importFromJson(json: String): Boolean {
        return try {
            val gson = Gson()
            val map = gson.fromJson(json, Map::class.java) ?: return false

            // Import baby
            val babyMap = map["baby"] as? Map<*, *>
            if (babyMap != null) {
                babyDao().deleteAll()
                babyDao().upsert(BabyEntity(
                    name = babyMap["name"] as? String ?: "",
                    nickname = babyMap["nickname"] as? String ?: "",
                    birthDate = babyMap["birthDate"] as? String ?: "",
                    gender = babyMap["gender"] as? String ?: "girl",
                    avatar = babyMap["avatar"] as? String
                ))
            }

            // Import timeline
            val timelineList = map["timeline"] as? List<*>
            if (timelineList != null) {
                timelineDao().let { dao ->
                    val entities = timelineList.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        TimelineEntity(
                            id = m["id"] as? String ?: "",
                            date = m["date"] as? String ?: "",
                            title = m["title"] as? String ?: "",
                            description = m["description"] as? String ?: "",
                            category = m["category"] as? String ?: "other",
                            tags = gson.toJson(m["tags"] ?: emptyList<String>()),
                            photos = gson.toJson(m["photos"] ?: emptyList<String>()),
                            videos = gson.toJson(m["videos"] ?: emptyList<String>())
                        )
                    }
                    entities.forEach { dao.insert(it) }
                }
            }

            // Import photos
            val photosList = map["photos"] as? List<*>
            if (photosList != null) {
                photoDao().let { dao ->
                    val entities = photosList.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        PhotoEntity(
                            id = m["id"] as? String ?: "",
                            url = m["url"] as? String ?: "",
                            caption = m["caption"] as? String ?: "",
                            date = m["date"] as? String ?: "",
                            timelineRecordId = m["timelineRecordId"] as? String,
                            tags = gson.toJson(m["tags"] ?: emptyList<String>())
                        )
                    }
                    entities.forEach { dao.insert(it) }
                }
            }

            // Import settings
            val settingsMap = map["settings"] as? Map<*, *>
            if (settingsMap != null) {
                settingsMap.forEach { (k, v) ->
                    if (k is String && v is String) {
                        settingsDao().upsert(SettingsEntity(key = k, value = v))
                    }
                }
            }

            true
        } catch (e: Exception) {
            false
        }
    }
}
