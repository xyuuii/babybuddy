package com.yueming.baby.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yueming.baby.data.cloud.CloudManager
import com.yueming.baby.data.cloud.CloudStorageConfig
import com.yueming.baby.data.cloud.PostgresManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DataManager {
    private var appContext: Context? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isCloudInitialized = false

    // --- Photo cache helper (copy content URI to temp for upload) ---
    fun copyPhotoToInternalStorage(uri: android.net.Uri): String? {
        val context = appContext ?: return null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val photoDir = File(context.filesDir, "photos").also { it.mkdirs() }
            val photoFile = File(photoDir, "photo-${java.util.UUID.randomUUID()}.jpg")
            inputStream.use { input ->
                photoFile.outputStream().use { output -> input.copyTo(output) }
            }
            photoFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("DataManager", "Failed to copy photo", e)
            null
        }
    }

    // --- Video cache helper ---
    fun copyVideoToInternalStorage(uri: android.net.Uri): String? {
        val context = appContext ?: return null
        return try {
            val videoDir = java.io.File(context.filesDir, "videos").also { it.mkdirs() }
            val ext = context.contentResolver.getType(uri)?.let { mime ->
                when {
                    mime.contains("mp4") -> ".mp4"
                    mime.contains("webm") -> ".webm"
                    mime.contains("3gpp") -> ".3gp"
                    else -> ".mp4"
                }
            } ?: ".mp4"
            val videoFile = java.io.File(videoDir, "video-${java.util.UUID.randomUUID()}$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                videoFile.outputStream().use { output -> input.copyTo(output) }
            }
            android.util.Log.d("DataManager", "Video copied to: ${videoFile.absolutePath}")
            videoFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("DataManager", "Failed to copy video", e)
            null
        }
    }

    // --- StateFlows ---
    private val _babies = MutableStateFlow<List<BabyInfo>>(emptyList())
    val babies: StateFlow<List<BabyInfo>> = _babies.asStateFlow()

    private val _activeBaby = MutableStateFlow(BabyInfo())
    val activeBaby: StateFlow<BabyInfo> = _activeBaby.asStateFlow()

    val activeBabyId: String get() = _activeBaby.value.id

    val babyInfo: StateFlow<BabyInfo> = _activeBaby.asStateFlow()

    private val _timeline = MutableStateFlow<List<TimelineRecord>>(emptyList())
    val timeline: StateFlow<List<TimelineRecord>> = _timeline.asStateFlow()

    private val _photos = MutableStateFlow<List<PhotoEntry>>(emptyList())
    val photos: StateFlow<List<PhotoEntry>> = _photos.asStateFlow()

    private val _customCategories = MutableStateFlow(emptyList<CategoryConfig>())
    val customCategories: StateFlow<List<CategoryConfig>> = _customCategories.asStateFlow()

    val allCategories: List<CategoryConfig>
        get() = getAllCategories(_customCategories.value)

    private val _aiConfig = MutableStateFlow(AIConfig())
    val aiConfig: StateFlow<AIConfig> = _aiConfig.asStateFlow()

    private val _aiProfiles = MutableStateFlow<List<AIProfile>>(emptyList())
    val aiProfiles: StateFlow<List<AIProfile>> = _aiProfiles.asStateFlow()

    val activeAIProfile: AIProfile?
        get() = _aiProfiles.value.firstOrNull { it.isActive }

    private val _chatMessages = MutableStateFlow(emptyList<ChatMessage>())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Cloud storage config
    private val _cloudStorageConfig = MutableStateFlow(CloudStorageConfig())
    val cloudStorageConfig: StateFlow<CloudStorageConfig> = _cloudStorageConfig.asStateFlow()

    // Legacy WebDAV state (forward-compat)
    private val _webDavConfig = MutableStateFlow<WebDavManager.WebDavConfig?>(null)
    val webDavConfig: StateFlow<WebDavManager.WebDavConfig?> = _webDavConfig.asStateFlow()

    private val _backupFiles = MutableStateFlow<List<String>>(emptyList())
    val backupFiles: StateFlow<List<String>> = _backupFiles.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    // --- Initialization ---
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        android.util.Log.d("DataManager", "Initializing with cloud backend...")
        appScope.launch {
            try {
                // Initialize PostgreSQL connection
                val pgResult = PostgresManager.initialize()
                if (pgResult.isSuccess) {
                    isCloudInitialized = true
                    android.util.Log.d("DataManager", "PostgreSQL connected")
                    loadFromPostgres()
                } else {
                    android.util.Log.w("DataManager", "PostgreSQL init failed, using local-only mode")
                    // Fall back to empty data for now (no Room available)
                    _babies.value = emptyList()
                    _activeBaby.value = BabyInfo()
                }
            } catch (e: Exception) {
                android.util.Log.e("DataManager", "Init failed", e)
            }
        }
    }

    private suspend fun loadFromPostgres() {
        val gson = Gson()

        // Load all babies
        val babiesResult = PostgresManager.getAllBabies()
        if (babiesResult.isSuccess) {
            val babyList = babiesResult.getOrThrow()
            if (babyList.isNotEmpty()) {
                _babies.value = babyList.map { row ->
                    BabyInfo(
                        id = row["id"] as? String ?: "",
                        name = row["name"] as? String ?: "",
                        nickname = row["nickname"] as? String ?: "",
                        birthDate = row["birth_date"] as? String ?: "",
                        gender = row["gender"] as? String ?: "",
                        avatar = row["avatar_url"] as? String
                    )
                }
                val activeRow = babyList.firstOrNull { (it["is_active"] as? Boolean) == true }
                    ?: babyList.first()
                _activeBaby.value = BabyInfo(
                    id = activeRow["id"] as? String ?: "",
                    name = activeRow["name"] as? String ?: "",
                    nickname = activeRow["nickname"] as? String ?: "",
                    birthDate = activeRow["birth_date"] as? String ?: "",
                    gender = activeRow["gender"] as? String ?: "",
                    avatar = activeRow["avatar_url"] as? String
                )
            } else {
                _babies.value = emptyList()
                _activeBaby.value = BabyInfo()
            }
        }

        // Load baby data
        loadBabyDataFromPostgres()
        loadGlobalSettingsFromPostgres()
    }

    private suspend fun loadBabyDataFromPostgres() {
        val babyId = _activeBaby.value.id
        val gson = Gson()

        if (babyId.isEmpty()) {
            _timeline.value = emptyList()
            _photos.value = emptyList()
            return
        }

        // Load timeline
        val timelineResult = PostgresManager.getAllTimelineByBaby(babyId)
        if (timelineResult.isSuccess) {
            _timeline.value = timelineResult.getOrThrow().map { row ->
                TimelineRecord(
                    id = row["id"] as? String ?: "",
                    babyId = row["baby_id"] as? String ?: "",
                    date = row["date"] as? String ?: "",
                    title = row["title"] as? String ?: "",
                    description = row["description"] as? String ?: "",
                    category = row["category"] as? String ?: "",
                    tags = parseJsonList(gson, row["tags"] as? String ?: "[]"),
                    photos = parseJsonList(gson, row["photos"] as? String ?: "[]"),
                    videos = parseJsonList(gson, row["videos"] as? String ?: "[]")
                )
            }
        }

        // Load photos
        val photosResult = PostgresManager.getAllPhotosByBaby(babyId)
        if (photosResult.isSuccess) {
            _photos.value = photosResult.getOrThrow().map { row ->
                PhotoEntry(
                    id = row["id"] as? String ?: "",
                    babyId = row["baby_id"] as? String ?: "",
                    url = row["url"] as? String ?: "",
                    caption = row["caption"] as? String ?: "",
                    date = row["date"] as? String ?: "",
                    timelineRecordId = row["timeline_record_id"] as? String,
                    tags = parseJsonList(gson, row["tags"] as? String ?: "[]")
                )
            }
        }
    }

    private suspend fun loadGlobalSettingsFromPostgres() {
        val gson = Gson()

        // Load custom categories
        val catResult = PostgresManager.getSetting("custom_categories")
        if (catResult.isSuccess && catResult.getOrNull() != null) {
            try {
                val type = object : TypeToken<List<CategoryConfig>>() {}.type
                val cats: List<CategoryConfig> = gson.fromJson(catResult.getOrNull(), type)
                _customCategories.value = cats.toMutableList()
            } catch (_: Exception) {}
        }

        // Load AI config (legacy)
        val aiResult = PostgresManager.getSetting("ai_config")
        if (aiResult.isSuccess && aiResult.getOrNull() != null) {
            try {
                _aiConfig.value = gson.fromJson(aiResult.getOrNull(), AIConfig::class.java)
            } catch (_: Exception) {}
        }

        // Load AI profiles
        val profilesResult = PostgresManager.getAllAIProfiles()
        if (profilesResult.isSuccess) {
            val profileList = profilesResult.getOrThrow()
            if (profileList.isNotEmpty()) {
                _aiProfiles.value = profileList.map { row ->
                    AIProfile(
                        id = row["id"] as? String ?: "",
                        name = row["name"] as? String ?: "",
                        apiBaseUrl = row["api_base_url"] as? String ?: "",
                        apiKey = row["api_key"] as? String ?: "",
                        model = row["model"] as? String ?: "",
                        systemPrompt = row["system_prompt"] as? String ?: "",
                        temperature = (row["temperature"] as? Number)?.toFloat() ?: 0.7f,
                        maxTokens = (row["max_tokens"] as? Number)?.toInt() ?: 2048,
                        isActive = (row["is_active"] as? Boolean) ?: false
                    )
                }
            }
        }

        // Migrate legacy AI config if no profiles
        if (_aiProfiles.value.isEmpty() && _aiConfig.value.apiKey.isNotEmpty()) {
            val legacyProfile = AIProfile(
                name = "默认配置",
                apiBaseUrl = _aiConfig.value.apiBaseUrl,
                apiKey = _aiConfig.value.apiKey,
                model = _aiConfig.value.model,
                isActive = true
            )
            _aiProfiles.value = listOf(legacyProfile)
            persistAIProfiles()
        }

        // Load theme
        val themeResult = PostgresManager.getSetting("theme_mode")
        if (themeResult.isSuccess && themeResult.getOrNull() != null) {
            try { _themeMode.value = ThemeMode.valueOf(themeResult.getOrNull()!!) } catch (_: Exception) {}
        }

        // Load WebDAV config
        val wdResult = PostgresManager.getSetting("webdav_config")
        if (wdResult.isSuccess && wdResult.getOrNull() != null) {
            try {
                _webDavConfig.value = gson.fromJson(wdResult.getOrNull(), WebDavManager.WebDavConfig::class.java)
            } catch (_: Exception) {}
        }

        // Load cloud storage config
        val csResult = PostgresManager.getSetting("cloud_storage_config")
        if (csResult.isSuccess && csResult.getOrNull() != null) {
            try {
                _cloudStorageConfig.value = gson.fromJson(csResult.getOrNull(), CloudStorageConfig::class.java)
            } catch (_: Exception) {}
        }
    }

    private fun parseJsonList(gson: Gson, json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }

    // --- Cloud Storage Config ---
    fun saveCloudStorageConfig(config: CloudStorageConfig) {
        _cloudStorageConfig.value = config
        appScope.launch {
            val json = Gson().toJson(config)
            PostgresManager.upsertSetting("cloud_storage_config", json)
        }
    }

    // --- Baby Management ---
    fun addBaby(info: BabyInfo) {
        val entityId = if (info.id.isEmpty()) java.util.UUID.randomUUID().toString() else info.id
        val baby = BabyInfo(
            id = entityId, name = info.name, nickname = info.nickname,
            birthDate = info.birthDate, gender = info.gender, avatar = info.avatar
        )
        _babies.value = (_babies.value + baby).toMutableList()

        if (isCloudInitialized) {
            appScope.launch {
                PostgresManager.upsertBaby(
                    id = entityId, name = info.name, nickname = info.nickname,
                    birthDate = info.birthDate, gender = info.gender,
                    avatarUrl = info.avatar, isActive = _babies.value.size == 1,
                    createdAt = System.currentTimeMillis()
                )
            }
        }
    }

    fun switchBaby(babyId: String) {
        val babiesList = _babies.value
        val target = babiesList.find { it.id == babyId } ?: return
        _activeBaby.value = target
        appScope.launch {
            if (isCloudInitialized) {
                PostgresManager.deactivateAllBabies()
                PostgresManager.setActiveBaby(babyId)
            }
            loadBabyDataFromPostgres()
        }
    }

    fun deleteBaby(babyId: String, onComplete: () -> Unit) {
        if (_babies.value.size <= 1) {
            onComplete()
            return
        }
        _babies.value = _babies.value.filter { it.id != babyId }.toMutableList()
        appScope.launch {
            if (isCloudInitialized) {
                PostgresManager.deleteBaby(babyId)
                PostgresManager.deleteTimelineByBabyId(babyId)
                PostgresManager.deletePhotosByBabyId(babyId)
                val remaining = PostgresManager.getAllBabies()
                if (remaining.isSuccess && remaining.getOrThrow().isNotEmpty()) {
                    val first = remaining.getOrThrow().first()
                    val firstId = first["id"] as? String ?: ""
                    PostgresManager.setActiveBaby(firstId)
                    withContext(Dispatchers.Main) {
                        _activeBaby.value = BabyInfo(
                            id = firstId,
                            name = first["name"] as? String ?: "",
                            nickname = first["nickname"] as? String ?: "",
                            birthDate = first["birth_date"] as? String ?: "",
                            gender = first["gender"] as? String ?: "",
                            avatar = first["avatar_url"] as? String
                        )
                    }
                    loadBabyDataFromPostgres()
                }
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun updateBabyInfo(info: BabyInfo) {
        _activeBaby.value = info
        _babies.value = _babies.value.map {
            if (it.id == info.id) info else it
        }.toMutableList()

        if (isCloudInitialized) {
            appScope.launch {
                PostgresManager.upsertBaby(
                    id = info.id, name = info.name, nickname = info.nickname,
                    birthDate = info.birthDate, gender = info.gender,
                    avatarUrl = info.avatar, isActive = true,
                    createdAt = System.currentTimeMillis()
                )
            }
        }
    }

    // --- Timeline CRUD ---
    fun addRecord(record: TimelineRecord) {
        val r = if (record.babyId.isEmpty()) record.copy(babyId = _activeBaby.value.id) else record
        _timeline.value = (_timeline.value + r).toMutableList()

        if (isCloudInitialized) {
            val gson = Gson()
            appScope.launch {
                PostgresManager.upsertTimeline(
                    id = r.id, babyId = r.babyId, date = r.date,
                    title = r.title, description = r.description, category = r.category,
                    tags = gson.toJson(r.tags), photos = gson.toJson(r.photos), videos = gson.toJson(r.videos)
                )
            }
        }
    }

    fun updateRecord(id: String, updates: (TimelineRecord) -> TimelineRecord) {
        _timeline.value = _timeline.value.map { if (it.id == id) updates(it) else it }.toMutableList()
        saveTimelineAsync()
    }

    fun deleteRecord(id: String) {
        _timeline.value = _timeline.value.filter { it.id != id }.toMutableList()
        _photos.value = _photos.value.filter { it.timelineRecordId != id }.toMutableList()
        appScope.launch {
            if (isCloudInitialized) {
                PostgresManager.deleteTimelineById(id)
                PostgresManager.deletePhotosByRecordId(id)
            }
        }
    }

    private fun saveTimelineAsync() {
        val records = _timeline.value
        val gson = Gson()
        appScope.launch {
            if (isCloudInitialized) {
                records.forEach { record ->
                    PostgresManager.upsertTimeline(
                        id = record.id, babyId = record.babyId, date = record.date,
                        title = record.title, description = record.description,
                        category = record.category,
                        tags = gson.toJson(record.tags),
                        photos = gson.toJson(record.photos),
                        videos = gson.toJson(record.videos)
                    )
                }
            }
        }
    }

    // --- Photos CRUD ---
    fun addPhoto(photo: PhotoEntry) {
        val p = if (photo.babyId.isEmpty()) photo.copy(babyId = _activeBaby.value.id) else photo
        _photos.value = (listOf(p) + _photos.value).toMutableList()

        if (isCloudInitialized) {
            val gson = Gson()
            appScope.launch {
                PostgresManager.upsertPhoto(
                    id = p.id, babyId = p.babyId, url = p.url,
                    caption = p.caption, date = p.date,
                    timelineRecordId = p.timelineRecordId,
                    tags = gson.toJson(p.tags),
                    mediaType = if (p.tags.contains("视频")) "video" else "image"
                )
                // Trigger cloud upload for local file paths
                val config = _cloudStorageConfig.value
                val localFile = File(p.url)
                if (localFile.exists()) {
                    val remoteName = "photos/${p.id}.jpg"
                    val result = CloudManager.uploadFile(p.url, remoteName, config)
                    if (result.success) {
                        val remoteUrl = CloudManager.getPublicUrl(result.remotePath, config)
                        // Update photo url in PostgreSQL
                        _photos.value = _photos.value.map {
                            if (it.id == p.id) it.copy(url = remoteUrl) else it
                        }.toMutableList()
                        PostgresManager.upsertPhoto(
                            id = p.id, babyId = p.babyId, url = remoteUrl,
                            caption = p.caption, date = p.date,
                            timelineRecordId = p.timelineRecordId,
                            tags = gson.toJson(p.tags),
                            mediaType = if (p.tags.contains("视频")) "video" else "image"
                        )
                    }
                }
            }
        }
    }

    fun deletePhoto(id: String) {
        _photos.value = _photos.value.filter { it.id != id }.toMutableList()
        appScope.launch {
            if (isCloudInitialized) {
                PostgresManager.deletePhotoById(id)
            }
        }
    }

    // --- Categories ---
    fun addCategory(cat: CategoryConfig) {
        _customCategories.value = (_customCategories.value + cat).toMutableList()
        persistCategories()
    }

    fun removeCategory(id: String) {
        _customCategories.value = _customCategories.value.filter { it.id != id }.toMutableList()
        persistCategories()
    }

    private fun persistCategories() {
        appScope.launch {
            val json = Gson().toJson(_customCategories.value)
            if (isCloudInitialized) {
                PostgresManager.upsertSetting("custom_categories", json)
            }
        }
    }

    // --- AI Config ---
    fun updateAIConfig(config: AIConfig) {
        _aiConfig.value = config
        appScope.launch {
            val json = Gson().toJson(config)
            if (isCloudInitialized) {
                PostgresManager.upsertSetting("ai_config", json)
            }
        }
    }

    val isAIConfigured: Boolean get() {
        val profile = _aiProfiles.value.firstOrNull { it.isActive }
        return profile != null && profile.apiKey.isNotEmpty()
    }

    // --- AI Profile Management ---
    private fun persistAIProfiles() {
        appScope.launch {
            if (isCloudInitialized) {
                _aiProfiles.value.forEach { profile ->
                    PostgresManager.upsertAIProfile(
                        id = profile.id, name = profile.name,
                        apiBaseUrl = profile.apiBaseUrl, apiKey = profile.apiKey,
                        model = profile.model, systemPrompt = profile.systemPrompt,
                        temperature = profile.temperature, maxTokens = profile.maxTokens,
                        isActive = profile.isActive
                    )
                }
            }
        }
    }

    fun addAIProfile(profile: AIProfile) {
        _aiProfiles.value = (_aiProfiles.value + profile).toMutableList()
        persistAIProfiles()
    }

    fun updateAIProfile(profile: AIProfile) {
        _aiProfiles.value = _aiProfiles.value.map {
            if (it.id == profile.id) profile else it
        }.toMutableList()
        persistAIProfiles()
    }

    fun deleteAIProfile(profileId: String) {
        _aiProfiles.value = _aiProfiles.value.filter { it.id != profileId }.toMutableList()
        if (_aiProfiles.value.none { it.isActive } && _aiProfiles.value.isNotEmpty()) {
            val first = _aiProfiles.value.first()
            _aiProfiles.value = _aiProfiles.value.map {
                if (it.id == first.id) it.copy(isActive = true) else it
            }.toMutableList()
        }
        appScope.launch {
            if (isCloudInitialized) {
                PostgresManager.deleteAIProfile(profileId)
            }
        }
    }

    fun setActiveAIProfile(profileId: String) {
        _aiProfiles.value = _aiProfiles.value.map {
            it.copy(isActive = it.id == profileId)
        }.toMutableList()
        persistAIProfiles()
    }

    fun testAIProfileConnection(profile: AIProfile, onResult: (Result<Boolean>) -> Unit) {
        appScope.launch {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(profile.apiBaseUrl)
                    .header("Authorization", "Bearer ${profile.apiKey}")
                    .build()
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful || response.code == 401 || response.code == 403) {
                        onResult(Result.success(true))
                    } else {
                        onResult(Result.failure(Exception("HTTP ${response.code}")))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(Result.failure(e)) }
            }
        }
    }

    // --- Chat ---
    fun addMessage(msg: ChatMessage) {
        _chatMessages.value = (_chatMessages.value + msg).toMutableList()
    }

    fun clearMessages() { _chatMessages.value = emptyList() }

    // --- Theme ---
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        appScope.launch {
            if (isCloudInitialized) {
                PostgresManager.upsertSetting("theme_mode", mode.name)
            }
        }
    }

    // --- WebDAV ---
    fun saveWebDavConfig(config: WebDavManager.WebDavConfig) {
        _webDavConfig.value = config
        appScope.launch {
            val json = Gson().toJson(config)
            if (isCloudInitialized) {
                PostgresManager.upsertSetting("webdav_config", json)
            }
        }
    }

    fun clearWebDavConfig() {
        _webDavConfig.value = null
        appScope.launch {
            if (isCloudInitialized) {
                PostgresManager.deleteSetting("webdav_config")
            }
        }
    }

    fun testWebDavConnection(config: WebDavManager.WebDavConfig, onResult: (Result<WebDavManager.ConnectionTestResult>) -> Unit) {
        appScope.launch {
            val result = WebDavManager.testConnection(config)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun uploadBackup(onProgress: (String) -> Unit, onComplete: (Result<Boolean>) -> Unit) {
        val config = _webDavConfig.value
        if (config == null) {
            onComplete(Result.failure(Exception("请先配置 WebDAV")))
            return
        }
        _isBackingUp.value = true
        appScope.launch {
            try {
                onProgress("正在导出数据...")
                val json = exportCurrentDataToJson()
                if (json.isEmpty()) throw Exception("No data to export")
                val jsonBytes = json.toByteArray(Charsets.UTF_8)

                onProgress("正在打包...")
                val zipBytes = createBackupZip(jsonBytes, activeBabyId)
                if (zipBytes.isEmpty()) throw Exception("备份数据为空")

                android.util.Log.d("DataManager", "Backup zip: ${zipBytes.size} bytes")

                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val filename = "yueming-backup-${timestamp}.zip"

                onProgress("正在上传到 WebDAV...")
                val result = WebDavManager.uploadBackup(config, zipBytes, filename)

                withContext(Dispatchers.Main) {
                    _isBackingUp.value = false
                    onComplete(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isBackingUp.value = false
                    onComplete(Result.failure(e))
                }
            }
        }
    }

    private fun exportCurrentDataToJson(): String {
        val gson = Gson()
        val export = mapOf(
            "baby" to _babies.value.map {
                mapOf(
                    "id" to it.id, "name" to it.name, "nickname" to it.nickname,
                    "birthDate" to it.birthDate, "gender" to it.gender,
                    "avatarUri" to it.avatar, "isActive" to (it.id == _activeBaby.value.id)
                )
            },
            "activeBabyId" to _activeBaby.value.id,
            "timeline" to _timeline.value.map {
                mapOf(
                    "id" to it.id, "babyId" to it.babyId, "date" to it.date,
                    "title" to it.title, "description" to it.description,
                    "category" to it.category,
                    "tags" to it.tags,
                    "photos" to it.photos,
                    "videos" to it.videos
                )
            },
            "photos" to _photos.value.map {
                mapOf(
                    "id" to it.id, "babyId" to it.babyId, "url" to it.url,
                    "caption" to it.caption, "date" to it.date,
                    "timelineRecordId" to it.timelineRecordId,
                    "tags" to it.tags
                )
            },
            "settings" to mapOf<String, String>()
        )
        return gson.toJson(export)
    }

    private suspend fun createBackupZip(jsonData: ByteArray, babyId: String): ByteArray = withContext(Dispatchers.IO) {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val jsonEntry = ZipEntry("database.json")
            zos.putNextEntry(jsonEntry)
            zos.write(jsonData)
            zos.closeEntry()

            val photosEntry = ZipEntry("photos/")
            zos.putNextEntry(photosEntry)
            zos.closeEntry()

            val photoUrls = _photos.value.map { it.url }.filter { it.startsWith("content://") || it.startsWith("file://") }
            if (photoUrls.isNotEmpty()) {
                val photoListEntry = ZipEntry("photos/manifest.json")
                zos.putNextEntry(photoListEntry)
                zos.write(Gson().toJson(photoUrls).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            val videosEntry = ZipEntry("videos/")
            zos.putNextEntry(videosEntry)
            zos.closeEntry()
        }
        baos.toByteArray()
    }

    fun downloadBackup(filename: String, onProgress: (String) -> Unit, onComplete: (Result<ByteArray>) -> Unit) {
        val config = _webDavConfig.value
        if (config == null) {
            onComplete(Result.failure(Exception("请先配置 WebDAV")))
            return
        }
        appScope.launch {
            onProgress("正在从 WebDAV 下载备份...")
            val result = WebDavManager.downloadBackup(config, filename)
            withContext(Dispatchers.Main) { onComplete(result) }
        }
    }

    fun refreshBackupList(onResult: (Result<List<String>>) -> Unit) {
        val config = _webDavConfig.value
        if (config == null) {
            onResult(Result.failure(Exception("请先配置 WebDAV")))
            return
        }
        appScope.launch {
            val result = WebDavManager.listBackups(config)
            result.onSuccess { _backupFiles.value = it }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun restoreFromBackup(bytes: ByteArray, onComplete: (Boolean) -> Unit) {
        appScope.launch {
            try {
                val jsonStr = extractJsonFromZip(bytes)
                if (jsonStr != null) {
                    val gson = Gson()
                    val map = gson.fromJson(jsonStr, Map::class.java) ?: run {
                        withContext(Dispatchers.Main) { onComplete(false) }
                        return@launch
                    }

                    // Import babies
                    val babiesList = map["baby"] as? List<*>
                    val activeBabyId = map["activeBabyId"] as? String
                    if (babiesList != null) {
                        val newBabies = mutableListOf<BabyInfo>()
                        babiesList.forEach { item ->
                            val m = item as? Map<*, *> ?: return@forEach
                            val bid = (m["id"] as? String) ?: java.util.UUID.randomUUID().toString()
                            val baby = BabyInfo(
                                id = bid,
                                name = m["name"] as? String ?: "",
                                nickname = m["nickname"] as? String ?: "",
                                birthDate = m["birthDate"] as? String ?: "",
                                gender = m["gender"] as? String ?: "girl",
                                avatar = m["avatarUri"] as? String
                            )
                            newBabies.add(baby)
                            if (isCloudInitialized) {
                                PostgresManager.upsertBaby(
                                    id = bid, name = baby.name, nickname = baby.nickname,
                                    birthDate = baby.birthDate, gender = baby.gender,
                                    avatarUrl = baby.avatar, isActive = (bid == activeBabyId),
                                    createdAt = System.currentTimeMillis()
                                )
                            }
                        }
                        _babies.value = newBabies
                        val active = newBabies.find { it.id == activeBabyId } ?: newBabies.firstOrNull()
                        if (active != null) _activeBaby.value = active
                    }

                    // Import timeline
                    val timelineList = map["timeline"] as? List<*>
                    if (timelineList != null) {
                        val newTimeline = mutableListOf<TimelineRecord>()
                        timelineList.mapNotNull { item ->
                            val m = item as? Map<*, *> ?: return@mapNotNull null
                            val record = TimelineRecord(
                                id = m["id"] as? String ?: "",
                                babyId = m["babyId"] as? String ?: "",
                                date = m["date"] as? String ?: "",
                                title = m["title"] as? String ?: "",
                                description = m["description"] as? String ?: "",
                                category = m["category"] as? String ?: "other",
                                tags = (m["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                photos = (m["photos"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                videos = (m["videos"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            )
                            newTimeline.add(record)
                            if (isCloudInitialized) {
                                PostgresManager.upsertTimeline(
                                    id = record.id, babyId = record.babyId, date = record.date,
                                    title = record.title, description = record.description,
                                    category = record.category,
                                    tags = gson.toJson(record.tags),
                                    photos = gson.toJson(record.photos),
                                    videos = gson.toJson(record.videos)
                                )
                            }
                        }
                        _timeline.value = newTimeline
                    }

                    // Import photos
                    val photosList = map["photos"] as? List<*>
                    if (photosList != null) {
                        val newPhotos = mutableListOf<PhotoEntry>()
                        photosList.mapNotNull { item ->
                            val m = item as? Map<*, *> ?: return@mapNotNull null
                            val photo = PhotoEntry(
                                id = m["id"] as? String ?: "",
                                babyId = m["babyId"] as? String ?: "",
                                url = m["url"] as? String ?: "",
                                caption = m["caption"] as? String ?: "",
                                date = m["date"] as? String ?: "",
                                timelineRecordId = m["timelineRecordId"] as? String,
                                tags = (m["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            )
                            newPhotos.add(photo)
                            if (isCloudInitialized) {
                                PostgresManager.upsertPhoto(
                                    id = photo.id, babyId = photo.babyId, url = photo.url,
                                    caption = photo.caption, date = photo.date,
                                    timelineRecordId = photo.timelineRecordId,
                                    tags = gson.toJson(photo.tags),
                                    mediaType = "image"
                                )
                            }
                        }
                        _photos.value = newPhotos
                    }

                    withContext(Dispatchers.Main) { onComplete(true) }
                } else {
                    withContext(Dispatchers.Main) { onComplete(false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    private fun extractJsonFromZip(zipBytes: ByteArray): String? {
        return try {
            val zis = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes))
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "database.json") {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
            null
        } catch (e: Exception) { null }
    }

    // --- Export / Import ---
    fun exportToJson(onResult: (String) -> Unit) {
        appScope.launch {
            val json = exportCurrentDataToJson()
            withContext(Dispatchers.Main) { onResult(json) }
        }
    }

    fun importFromJson(json: String, onResult: (Boolean) -> Unit) {
        appScope.launch {
            try {
                val gson = Gson()
                val map = gson.fromJson(json, Map::class.java) ?: run {
                    withContext(Dispatchers.Main) { onResult(false) }
                    return@launch
                }
                // Simple import - just load into StateFlows
                val babiesList = map["baby"] as? List<*>
                if (babiesList != null) {
                    val newBabies = babiesList.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        BabyInfo(
                            id = m["id"] as? String ?: "",
                            name = m["name"] as? String ?: "",
                            nickname = m["nickname"] as? String ?: "",
                            birthDate = m["birthDate"] as? String ?: "",
                            gender = m["gender"] as? String ?: "girl",
                            avatar = m["avatarUri"] as? String
                        )
                    }
                    _babies.value = newBabies
                    newBabies.firstOrNull()?.let { _activeBaby.value = it }
                }
                withContext(Dispatchers.Main) { onResult(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun resetAllData(onComplete: () -> Unit) {
        appScope.launch {
            if (isCloudInitialized) {
                PostgresManager.deleteAllSettings()
            }
            _timeline.value = emptyList()
            _photos.value = emptyList()
            _babies.value = emptyList()
            _activeBaby.value = BabyInfo()
            _customCategories.value = emptyList()
            _aiConfig.value = AIConfig()
            _aiProfiles.value = emptyList()
            _chatMessages.value = emptyList()
            _themeMode.value = ThemeMode.SYSTEM
            _webDavConfig.value = null
            _cloudStorageConfig.value = CloudStorageConfig()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    // --- Age calculations ---
    fun getAgeInMonths(birthDate: String): Int {
        if (birthDate.isBlank()) return 0
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val birth = try { fmt.parse(birthDate) } catch (e: Exception) { return 0 }
        birth ?: return 0
        val birthCal = java.util.Calendar.getInstance().apply { time = birth }
        val nowCal = java.util.Calendar.getInstance()
        var months = (nowCal.get(java.util.Calendar.YEAR) - birthCal.get(java.util.Calendar.YEAR)) * 12
        months += nowCal.get(java.util.Calendar.MONTH) - birthCal.get(java.util.Calendar.MONTH)
        if (nowCal.get(java.util.Calendar.DAY_OF_MONTH) < birthCal.get(java.util.Calendar.DAY_OF_MONTH)) months--
        return maxOf(0, months)
    }

    fun getAgeInDays(birthDate: String): Int {
        if (birthDate.isBlank()) return 0
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val birth = try { fmt.parse(birthDate) } catch (e: Exception) { return 0 }
        birth ?: return 0
        val now = java.util.Date()
        val diff = now.time - birth.time
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    fun getRecentRecords(count: Int = 5): List<TimelineRecord> {
        return _timeline.value.sortedByDescending { it.date }.take(count)
    }

    fun getMilestoneCount(): Int {
        return _timeline.value.count { it.category == "milestone" }
    }

    fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 6 -> "夜深了"
            hour < 9 -> "早上好"
            hour < 12 -> "上午好"
            hour < 14 -> "中午好"
            hour < 18 -> "下午好"
            else -> "晚上好"
        }
    }
}
