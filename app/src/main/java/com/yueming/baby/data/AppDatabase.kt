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

@Deprecated(
    message = "Room database has been replaced by WebDAV cloud backend (DataManager).",
    replaceWith = ReplaceWith("DataManager", "com.yueming.baby.data.DataManager")
)
@Database(
    entities = [TimelineEntity::class, PhotoEntity::class, BabyEntity::class, SettingsEntity::class],
    version = 3,
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

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes for this version; just preserving user data
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yueming.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }

    // Export database to JSON (excluding binary image data)
    suspend fun exportToJson(babyId: String): String {
        val gson = Gson()
        val babies = babyDao().getAllBabies()
        val timeline = timelineDao().getAllByBaby(babyId)
        val photos = photoDao().getAllByBaby(babyId)
        val settings = settingsDao().getAll()
        val activeBaby = babyDao().getActiveBaby()

        val export = mapOf(
            "baby" to babies.map {
                mapOf(
                    "id" to it.id, "name" to it.name, "nickname" to it.nickname,
                    "birthDate" to it.birthDate, "gender" to it.gender,
                    "avatarUri" to it.avatarUri, "isActive" to it.isActive
                )
            },
            "activeBabyId" to activeBaby?.id,
            "timeline" to timeline.map {
                mapOf(
                    "id" to it.id, "babyId" to it.babyId, "date" to it.date,
                    "title" to it.title, "description" to it.description,
                    "category" to it.category,
                    "tags" to try { Gson().fromJson<List<String>>(it.tags, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList<String>() },
                    "photos" to try { Gson().fromJson<List<String>>(it.photos, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList<String>() },
                    "videos" to try { Gson().fromJson<List<String>>(it.videos, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList<String>() }
                )
            },
            "photos" to photos.map {
                mapOf(
                    "id" to it.id, "babyId" to it.babyId, "url" to it.url,
                    "caption" to it.caption, "date" to it.date,
                    "timelineRecordId" to it.timelineRecordId,
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

            // Import babies
            val babiesList = map["baby"] as? List<*>
            val activeBabyId = map["activeBabyId"] as? String
            if (babiesList != null) {
                babyDao().deleteAll()
                babiesList.forEach { item ->
                    val m = item as? Map<*, *> ?: return@forEach
                    val isActive = m["isActive"] as? Boolean ?: false
                    val bid = (m["id"] as? String) ?: java.util.UUID.randomUUID().toString()
                    babyDao().upsert(BabyEntity(
                        id = bid,
                        name = m["name"] as? String ?: "",
                        nickname = m["nickname"] as? String ?: "",
                        birthDate = m["birthDate"] as? String ?: "",
                        gender = m["gender"] as? String ?: "girl",
                        avatarUri = m["avatarUri"] as? String,
                        isActive = bid == activeBabyId || isActive
                    ))
                }
            }

            // Import timeline
            val timelineList = map["timeline"] as? List<*>
            if (timelineList != null) {
                timelineList.mapNotNull { item ->
                    val m = item as? Map<*, *> ?: return@mapNotNull null
                    TimelineEntity(
                        id = m["id"] as? String ?: "",
                        babyId = m["babyId"] as? String ?: "",
                        date = m["date"] as? String ?: "",
                        title = m["title"] as? String ?: "",
                        description = m["description"] as? String ?: "",
                        category = m["category"] as? String ?: "other",
                        tags = gson.toJson(m["tags"] ?: emptyList<String>()),
                        photos = gson.toJson(m["photos"] ?: emptyList<String>()),
                        videos = gson.toJson(m["videos"] ?: emptyList<String>())
                    )
                }.forEach { timelineDao().insert(it) }
            }

            // Import photos
            val photosList = map["photos"] as? List<*>
            if (photosList != null) {
                photosList.mapNotNull { item ->
                    val m = item as? Map<*, *> ?: return@mapNotNull null
                    PhotoEntity(
                        id = m["id"] as? String ?: "",
                        babyId = m["babyId"] as? String ?: "",
                        url = m["url"] as? String ?: "",
                        caption = m["caption"] as? String ?: "",
                        date = m["date"] as? String ?: "",
                        timelineRecordId = m["timelineRecordId"] as? String,
                        tags = gson.toJson(m["tags"] ?: emptyList<String>())
                    )
                }.forEach { photoDao().insert(it) }
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
