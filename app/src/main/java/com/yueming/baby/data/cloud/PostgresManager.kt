package com.yueming.baby.data.cloud

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

object PostgresManager {
    private var connection: Connection? = null
    private var config: PostgresConfig = PostgresConfig()
    private val gson = Gson()

    private fun getJdbcUrl(): String {
        return "jdbc:postgresql://${config.host}:${config.port}/${config.database}?currentSchema=${config.schema}"
    }

    suspend fun initialize(cfg: PostgresConfig = PostgresConfig()): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            config = cfg
            // Don't call Class.forName - it can crash on Android.
            // DriverManager auto-discovers via service loader or we try direct connection.
            val props = Properties().apply {
                setProperty("user", config.username)
                setProperty("password", config.password)
                setProperty("connectTimeout", "5")
                setProperty("socketTimeout", "10")
                setProperty("loginTimeout", "5")
            }
            val conn = DriverManager.getConnection(getJdbcUrl(), props)
            connection = conn
            createTablesIfNeeded(conn)
            Result.success(true)
        } catch (e: Exception) {
            android.util.Log.e("PostgresManager", "Init failed", e)
            Result.failure(e)
        }
    }

    private fun createTablesIfNeeded(conn: Connection) {
        val stmt = conn.createStatement()
        stmt.execute("CREATE SCHEMA IF NOT EXISTS ${config.schema}")
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ${config.schema}.babies (
                id TEXT PRIMARY KEY, name TEXT, nickname TEXT,
                birth_date TEXT, gender TEXT, avatar_url TEXT,
                is_active BOOLEAN DEFAULT true, created_at BIGINT
            )
        """)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ${config.schema}.timeline (
                id TEXT PRIMARY KEY, baby_id TEXT,
                date TEXT, title TEXT, description TEXT, category TEXT,
                tags JSONB DEFAULT '[]', photos JSONB DEFAULT '[]', videos JSONB DEFAULT '[]'
            )
        """)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ${config.schema}.photos (
                id TEXT PRIMARY KEY, baby_id TEXT, url TEXT, caption TEXT,
                date TEXT, timeline_record_id TEXT,
                tags JSONB DEFAULT '[]', media_type TEXT DEFAULT 'image'
            )
        """)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ${config.schema}.settings (key TEXT PRIMARY KEY, value TEXT)
        """)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ${config.schema}.ai_profiles (
                id TEXT PRIMARY KEY, name TEXT, api_base_url TEXT, api_key TEXT,
                model TEXT, system_prompt TEXT, temperature REAL DEFAULT 0.7,
                max_tokens INT DEFAULT 2048, is_active BOOLEAN DEFAULT false
            )
        """)
        stmt.close()
    }

    fun getConnection(): Connection? = connection

    // --- Babies ---
    suspend fun getAllBabies(): Result<List<Map<String, Any?>>> = queryList(
        "SELECT id, name, nickname, birth_date, gender, avatar_url, is_active, created_at FROM ${config.schema}.babies ORDER BY created_at ASC"
    )

    suspend fun getActiveBaby(): Result<Map<String, Any?>?> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "SELECT id, name, nickname, birth_date, gender, avatar_url, is_active, created_at FROM ${config.schema}.babies WHERE is_active = true LIMIT 1"
            )
            val rs = ps.executeQuery()
            val result = if (rs.next()) {
                mapOf(
                    "id" to rs.getString("id"),
                    "name" to rs.getString("name"),
                    "nickname" to rs.getString("nickname"),
                    "birth_date" to rs.getString("birth_date"),
                    "gender" to rs.getString("gender"),
                    "avatar_url" to rs.getString("avatar_url"),
                    "is_active" to rs.getBoolean("is_active"),
                    "created_at" to rs.getLong("created_at")
                )
            } else null
            rs.close()
            ps.close()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertBaby(
        id: String, name: String, nickname: String, birthDate: String,
        gender: String, avatarUrl: String?, isActive: Boolean, createdAt: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "INSERT INTO ${config.schema}.babies (id, name, nickname, birth_date, gender, avatar_url, is_active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, nickname=EXCLUDED.nickname, birth_date=EXCLUDED.birth_date, gender=EXCLUDED.gender, avatar_url=EXCLUDED.avatar_url, is_active=EXCLUDED.is_active, created_at=EXCLUDED.created_at"
            )
            ps.setString(1, id)
            ps.setString(2, name)
            ps.setString(3, nickname)
            ps.setString(4, birthDate)
            ps.setString(5, gender)
            ps.setString(6, avatarUrl)
            ps.setBoolean(7, isActive)
            ps.setLong(8, createdAt)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deactivateAllBabies(): Result<Boolean> = execute(
        "UPDATE ${config.schema}.babies SET is_active = false"
    )

    suspend fun setActiveBaby(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("UPDATE ${config.schema}.babies SET is_active = true WHERE id = ?")
            ps.setString(1, id)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBaby(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("DELETE FROM ${config.schema}.babies WHERE id = ?")
            ps.setString(1, id)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Timeline ---
    suspend fun getAllTimelineByBaby(babyId: String): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "SELECT id, baby_id, date, title, description, category, tags::text, photos::text, videos::text FROM ${config.schema}.timeline WHERE baby_id = ? ORDER BY date DESC"
            )
            ps.setString(1, babyId)
            val rs = ps.executeQuery()
            val results = mutableListOf<Map<String, Any?>>()
            while (rs.next()) {
                results.add(mapOf(
                    "id" to rs.getString("id"),
                    "baby_id" to rs.getString("baby_id"),
                    "date" to rs.getString("date"),
                    "title" to rs.getString("title"),
                    "description" to rs.getString("description"),
                    "category" to rs.getString("category"),
                    "tags" to rs.getString("tags"),
                    "photos" to rs.getString("photos"),
                    "videos" to rs.getString("videos")
                ))
            }
            rs.close()
            ps.close()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertTimeline(
        id: String, babyId: String, date: String, title: String,
        description: String, category: String, tags: String, photos: String, videos: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "INSERT INTO ${config.schema}.timeline (id, baby_id, date, title, description, category, tags, photos, videos) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb) ON CONFLICT (id) DO UPDATE SET baby_id=EXCLUDED.baby_id, date=EXCLUDED.date, title=EXCLUDED.title, description=EXCLUDED.description, category=EXCLUDED.category, tags=EXCLUDED.tags, photos=EXCLUDED.photos, videos=EXCLUDED.videos"
            )
            ps.setString(1, id)
            ps.setString(2, babyId)
            ps.setString(3, date)
            ps.setString(4, title)
            ps.setString(5, description)
            ps.setString(6, category)
            ps.setString(7, tags)
            ps.setString(8, photos)
            ps.setString(9, videos)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTimelineById(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("DELETE FROM ${config.schema}.timeline WHERE id = ?")
            ps.setString(1, id)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTimelineByBabyId(babyId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("DELETE FROM ${config.schema}.timeline WHERE baby_id = ?")
            ps.setString(1, babyId)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Photos ---
    suspend fun getAllPhotosByBaby(babyId: String): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "SELECT id, baby_id, url, caption, date, timeline_record_id, tags::text, media_type FROM ${config.schema}.photos WHERE baby_id = ? ORDER BY date DESC"
            )
            ps.setString(1, babyId)
            val rs = ps.executeQuery()
            val results = mutableListOf<Map<String, Any?>>()
            while (rs.next()) {
                results.add(mapOf(
                    "id" to rs.getString("id"),
                    "baby_id" to rs.getString("baby_id"),
                    "url" to rs.getString("url"),
                    "caption" to rs.getString("caption"),
                    "date" to rs.getString("date"),
                    "timeline_record_id" to rs.getString("timeline_record_id"),
                    "tags" to rs.getString("tags"),
                    "media_type" to rs.getString("media_type")
                ))
            }
            rs.close()
            ps.close()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertPhoto(
        id: String, babyId: String, url: String, caption: String,
        date: String, timelineRecordId: String?, tags: String, mediaType: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "INSERT INTO ${config.schema}.photos (id, baby_id, url, caption, date, timeline_record_id, tags, media_type) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?) ON CONFLICT (id) DO UPDATE SET baby_id=EXCLUDED.baby_id, url=EXCLUDED.url, caption=EXCLUDED.caption, date=EXCLUDED.date, timeline_record_id=EXCLUDED.timeline_record_id, tags=EXCLUDED.tags, media_type=EXCLUDED.media_type"
            )
            ps.setString(1, id)
            ps.setString(2, babyId)
            ps.setString(3, url)
            ps.setString(4, caption)
            ps.setString(5, date)
            ps.setString(6, timelineRecordId)
            ps.setString(7, tags)
            ps.setString(8, mediaType)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePhotoById(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("DELETE FROM ${config.schema}.photos WHERE id = ?")
            ps.setString(1, id)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePhotosByRecordId(recordId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("DELETE FROM ${config.schema}.photos WHERE timeline_record_id = ?")
            ps.setString(1, recordId)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePhotosByBabyId(babyId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("DELETE FROM ${config.schema}.photos WHERE baby_id = ?")
            ps.setString(1, babyId)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Settings ---
    suspend fun getSetting(key: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("SELECT value FROM ${config.schema}.settings WHERE key = ?")
            ps.setString(1, key)
            val rs = ps.executeQuery()
            val result = if (rs.next()) rs.getString("value") else null
            rs.close()
            ps.close()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllSettings(): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("SELECT key, value FROM ${config.schema}.settings")
            val rs = ps.executeQuery()
            val results = mutableListOf<Pair<String, String>>()
            while (rs.next()) {
                results.add(Pair(rs.getString("key"), rs.getString("value")))
            }
            rs.close()
            ps.close()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertSetting(key: String, value: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "INSERT INTO ${config.schema}.settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value"
            )
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSetting(key: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("DELETE FROM ${config.schema}.settings WHERE key = ?")
            ps.setString(1, key)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAllSettings(): Result<Boolean> = execute("DELETE FROM ${config.schema}.settings")

    // --- AI Profiles ---
    suspend fun getAllAIProfiles(): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "SELECT id, name, api_base_url, api_key, model, system_prompt, temperature, max_tokens, is_active FROM ${config.schema}.ai_profiles"
            )
            val rs = ps.executeQuery()
            val results = mutableListOf<Map<String, Any?>>()
            while (rs.next()) {
                results.add(mapOf(
                    "id" to rs.getString("id"),
                    "name" to rs.getString("name"),
                    "api_base_url" to rs.getString("api_base_url"),
                    "api_key" to rs.getString("api_key"),
                    "model" to rs.getString("model"),
                    "system_prompt" to rs.getString("system_prompt"),
                    "temperature" to rs.getFloat("temperature"),
                    "max_tokens" to rs.getInt("max_tokens"),
                    "is_active" to rs.getBoolean("is_active")
                ))
            }
            rs.close()
            ps.close()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertAIProfile(
        id: String, name: String, apiBaseUrl: String, apiKey: String,
        model: String, systemPrompt: String, temperature: Float, maxTokens: Int, isActive: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement(
                "INSERT INTO ${config.schema}.ai_profiles (id, name, api_base_url, api_key, model, system_prompt, temperature, max_tokens, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, api_base_url=EXCLUDED.api_base_url, api_key=EXCLUDED.api_key, model=EXCLUDED.model, system_prompt=EXCLUDED.system_prompt, temperature=EXCLUDED.temperature, max_tokens=EXCLUDED.max_tokens, is_active=EXCLUDED.is_active"
            )
            ps.setString(1, id)
            ps.setString(2, name)
            ps.setString(3, apiBaseUrl)
            ps.setString(4, apiKey)
            ps.setString(5, model)
            ps.setString(6, systemPrompt)
            ps.setFloat(7, temperature)
            ps.setInt(8, maxTokens)
            ps.setBoolean(9, isActive)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAIProfile(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("DELETE FROM ${config.schema}.ai_profiles WHERE id = ?")
            ps.setString(1, id)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deactivateAllAIProfiles(): Result<Boolean> = execute(
        "UPDATE ${config.schema}.ai_profiles SET is_active = false"
    )

    suspend fun setActiveAIProfile(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val ps = conn.prepareStatement("UPDATE ${config.schema}.ai_profiles SET is_active = true WHERE id = ?")
            ps.setString(1, id)
            ps.executeUpdate()
            ps.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Helpers ---
    private suspend fun queryList(sql: String): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery(sql)
            val results = mutableListOf<Map<String, Any?>>()
            val meta = rs.metaData
            val colCount = meta.columnCount
            while (rs.next()) {
                val row = mutableMapOf<String, Any?>()
                for (i in 1..colCount) {
                    row[meta.getColumnName(i)] = rs.getObject(i)
                }
                results.add(row)
            }
            rs.close()
            stmt.close()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun execute(sql: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = connection ?: return@withContext Result.failure(Exception("Not connected"))
            val stmt = conn.createStatement()
            stmt.execute(sql)
            stmt.close()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun close() {
        try {
            connection?.close()
            connection = null
        } catch (_: Exception) {}
    }
}
