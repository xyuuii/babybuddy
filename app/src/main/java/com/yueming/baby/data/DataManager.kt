package com.yueming.baby.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yueming.baby.data.cloud.CloudManager
import com.yueming.baby.data.cloud.CloudStorageConfig
import com.yueming.baby.data.cloud.toWebDavConfig
import com.yueming.baby.data.entity.PhotoEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType

object DataManager {
    data class MediaUploadEvent(
        val photoId: String,
        val success: Boolean,
        val message: String,
        val remoteUrl: String? = null
    )

    private var appContext: Context? = null
    private var database: AppDatabase? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    // Data paths
    private const val DATA_PATH = "/sata1-15529232180/yueming/data"
    private const val MEDIA_PATH = "/sata1-15529232180/yueming/media"
    private const val LOCAL_CONFIG_PREFS = "yueming_local_storage_config"
    private const val KEY_CLOUD_STORAGE_CONFIG = "cloud_storage_config"
    private const val KEY_WEBDAV_CONFIG = "webdav_config"

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
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
    private val _mediaUploadEvents = MutableSharedFlow<MediaUploadEvent>(extraBufferCapacity = 16)
    val mediaUploadEvents: SharedFlow<MediaUploadEvent> = _mediaUploadEvents.asSharedFlow()

    // Legacy WebDAV state (forward-compat)
    private val _webDavConfig = MutableStateFlow<WebDavManager.WebDavConfig?>(null)
    val webDavConfig: StateFlow<WebDavManager.WebDavConfig?> = _webDavConfig.asStateFlow()

    // --- WebDAV Config helper ---
    private fun getWebDavConfig(): WebDavManager.WebDavConfig {
        val wd = _webDavConfig.value
        if (wd != null) return wd
        val cs = _cloudStorageConfig.value
        return cs.toWebDavConfig()
    }

    private fun loadLocalStorageConfig() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(LOCAL_CONFIG_PREFS, Context.MODE_PRIVATE)
        val gson = Gson()
        prefs.getString(KEY_CLOUD_STORAGE_CONFIG, null)?.let { json ->
            runCatching {
                gson.fromJson(json, CloudStorageConfig::class.java)
            }.getOrNull()?.let { _cloudStorageConfig.value = it }
        }
        prefs.getString(KEY_WEBDAV_CONFIG, null)?.let { json ->
            runCatching {
                gson.fromJson(json, WebDavManager.WebDavConfig::class.java)
            }.getOrNull()?.let { _webDavConfig.value = it }
        }
    }

    private fun saveLocalStorageConfig() {
        val context = appContext ?: return
        val gson = Gson()
        context.getSharedPreferences(LOCAL_CONFIG_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLOUD_STORAGE_CONFIG, gson.toJson(_cloudStorageConfig.value))
            .apply {
                val webDavConfig = _webDavConfig.value
                if (webDavConfig == null) remove(KEY_WEBDAV_CONFIG)
                else putString(KEY_WEBDAV_CONFIG, gson.toJson(webDavConfig))
            }
            .apply()
    }

    // --- Initialization ---
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        database = AppDatabase.getInstance(context.applicationContext)
        loadLocalStorageConfig()
        android.util.Log.d("DataManager", "Initializing with local media index + WebDAV backend")
        appScope.launch {
            _isLoading.value = true
            try {
                initWebDavData()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun initWebDavData() {
        val config = getWebDavConfig()
        android.util.Log.d("DataManager", "WebDAV: ${config.url}, data path: $DATA_PATH")

        // Ensure directory structure exists
        WebDavManager.createDirectoryChain(config, DATA_PATH)
        WebDavManager.createDirectoryChain(config, "$MEDIA_PATH/photos")
        WebDavManager.createDirectoryChain(config, "$MEDIA_PATH/videos")
        uploadPendingMedia(config)

        // Load settings first (contains activeBabyId, theme, etc.)
        loadSettingsFromWebDav(config)

        // Load data files
        loadBabiesFromWebDav(config)
        loadTimelineFromWebDav(config)
        loadPhotosFromWebDav(config)
        loadAiProfilesFromWebDav(config)
    }

    private suspend fun loadBabiesFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "$DATA_PATH/babies.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<BabyInfo>>() {}.type
                    val list: List<BabyInfo> = Gson().fromJson(json, type)
                    if (list.isNotEmpty()) {
                        _babies.value = list
                        val activeId = _activeBaby.value.id
                        val active = if (activeId.isNotEmpty()) {
                            list.find { it.id == activeId } ?: list.first()
                        } else list.first()
                        _activeBaby.value = active
                        android.util.Log.d("DataManager", "Loaded ${list.size} babies from WebDAV")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse babies.json", e)
                    _babies.value = emptyList()
                    _activeBaby.value = BabyInfo()
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "babies.json not found, starting fresh")
                _babies.value = emptyList()
                _activeBaby.value = BabyInfo()
            }
        )
    }

    private suspend fun loadTimelineFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "$DATA_PATH/timeline.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<TimelineRecord>>() {}.type
                    val list: List<TimelineRecord> = Gson().fromJson(json, type)
                    _timeline.value = list
                    android.util.Log.d("DataManager", "Loaded ${list.size} timeline records from WebDAV")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse timeline.json", e)
                    _timeline.value = emptyList()
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "timeline.json not found, starting fresh")
                _timeline.value = emptyList()
            }
        )
    }

    private suspend fun loadPhotosFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "$DATA_PATH/photos.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<PhotoEntry>>() {}.type
                    val list: List<PhotoEntry> = Gson().fromJson(json, type)
                    _photos.value = list
                    upsertPhotoIndex(list.map { it.toPhotoEntity(uploadStatus = "SYNCED") })
                    android.util.Log.d("DataManager", "Loaded ${list.size} photos from WebDAV")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse photos.json", e)
                    if (_photos.value.isEmpty()) _photos.value = emptyList()
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "photos.json not found, starting fresh")
                _photos.value = emptyList()
            }
        )
    }

    private suspend fun upsertPhotoIndex(entities: List<PhotoEntity>) {
        if (entities.isEmpty()) return
        try {
            database?.photoDao()?.insertAll(entities)
        } catch (e: Exception) {
            android.util.Log.e("DataManager", "Failed to upsert local media index", e)
        }
    }

    private suspend fun loadSettingsFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "$DATA_PATH/settings.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val map: Map<String, String> = Gson().fromJson(json, type) ?: emptyMap()
                    applySettingsMap(map)
                    android.util.Log.d("DataManager", "Loaded settings from WebDAV")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse settings.json", e)
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "settings.json not found, using defaults")
            }
        )
    }

    private suspend fun loadAiProfilesFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "$DATA_PATH/ai_profiles.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<AIProfile>>() {}.type
                    val list: List<AIProfile> = Gson().fromJson(json, type)
                    _aiProfiles.value = list
                    android.util.Log.d("DataManager", "Loaded ${list.size} AI profiles from WebDAV")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse ai_profiles.json", e)
                    _aiProfiles.value = emptyList()
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "ai_profiles.json not found, starting fresh")
                _aiProfiles.value = emptyList()
            }
        )
    }

    private fun applySettingsMap(map: Map<String, String>) {
        val gson = Gson()
        map["theme_mode"]?.let {
            try { _themeMode.value = ThemeMode.valueOf(it) } catch (_: Exception) {}
        }
        map["active_baby_id"]?.let { id ->
            if (id.isNotEmpty()) {
                _activeBaby.value = _activeBaby.value.copy(id = id)
            }
        }
        map["custom_categories"]?.let {
            try {
                val type = object : TypeToken<List<CategoryConfig>>() {}.type
                _customCategories.value = gson.fromJson(it, type)
            } catch (_: Exception) {}
        }
        map["ai_config"]?.let {
            try {
                _aiConfig.value = gson.fromJson(it, AIConfig::class.java) ?: AIConfig()
            } catch (_: Exception) {}
        }
        map["webdav_config"]?.let {
            try {
                _webDavConfig.value = gson.fromJson(it, WebDavManager.WebDavConfig::class.java)
            } catch (_: Exception) {}
        }
        map["cloud_storage_config"]?.let {
            try {
                _cloudStorageConfig.value = gson.fromJson(it, CloudStorageConfig::class.java)
            } catch (_: Exception) {}
        }
    }

    private fun buildSettingsMap(): Map<String, String> {
        val gson = Gson()
        return mapOf(
            "theme_mode" to _themeMode.value.name,
            "active_baby_id" to _activeBaby.value.id,
            "custom_categories" to gson.toJson(_customCategories.value),
            "ai_config" to gson.toJson(_aiConfig.value),
            "webdav_config" to (_webDavConfig.value?.let { gson.toJson(it) } ?: ""),
            "cloud_storage_config" to gson.toJson(_cloudStorageConfig.value)
        )
    }

    // --- Save helpers (async, serialized via Mutex) ---

    private fun saveBabies() {
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfig()
                    val json = Gson().toJson(_babies.value)
                    val result = WebDavManager.writeJson(config, "$DATA_PATH/babies.json", json)
                    android.util.Log.d("DataManager", "Saved ${_babies.value.size} babies to WebDAV, result: $result")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to save babies", e)
                }
            }
        }
    }

    private fun saveTimeline() {
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfig()
                    val json = Gson().toJson(_timeline.value)
                    val result = WebDavManager.writeJson(config, "$DATA_PATH/timeline.json", json)
                    android.util.Log.d("DataManager", "Saved ${_timeline.value.size} timeline records to WebDAV, result: $result")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to save timeline", e)
                }
            }
        }
    }

    private fun savePhotos() {
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfig()
                    val json = Gson().toJson(_photos.value)
                    val result = WebDavManager.writeJson(config, "$DATA_PATH/photos.json", json)
                    android.util.Log.d("DataManager", "Saved ${_photos.value.size} photos to WebDAV, result: $result")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to save photos", e)
                }
            }
        }
    }

    private fun saveSettings() {
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfig()
                    val json = Gson().toJson(buildSettingsMap())
                    val result = WebDavManager.writeJson(config, "$DATA_PATH/settings.json", json)
                    android.util.Log.d("DataManager", "Saved settings to WebDAV, result: $result")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to save settings", e)
                }
            }
        }
    }

    private fun saveAiProfiles() {
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfig()
                    val json = Gson().toJson(_aiProfiles.value)
                    val result = WebDavManager.writeJson(config, "$DATA_PATH/ai_profiles.json", json)
                    android.util.Log.d("DataManager", "Saved ${_aiProfiles.value.size} AI profiles to WebDAV, result: $result")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to save AI profiles", e)
                }
            }
        }
    }

    // --- Cloud Storage Config ---
    fun saveCloudStorageConfig(config: CloudStorageConfig) {
        _cloudStorageConfig.value = config
        _webDavConfig.value = null
        saveLocalStorageConfig()
        saveSettings()
    }

    // --- Baby Management ---
    fun addBaby(info: BabyInfo) {
        val entityId = if (info.id.isEmpty()) java.util.UUID.randomUUID().toString() else info.id
        val baby = BabyInfo(
            id = entityId, name = info.name, nickname = info.nickname,
            birthDate = info.birthDate, gender = info.gender, avatar = info.avatar
        )
        _babies.value = (_babies.value + baby).toMutableList()
        if (_babies.value.size == 1) {
            _activeBaby.value = baby
        }
        saveBabies()
    }

    fun switchBaby(babyId: String) {
        val babiesList = _babies.value
        val target = babiesList.find { it.id == babyId } ?: return
        _activeBaby.value = target
        saveSettings()
    }

    fun deleteBaby(babyId: String, onComplete: () -> Unit) {
        if (_babies.value.size <= 1) {
            onComplete()
            return
        }
        _babies.value = _babies.value.filter { it.id != babyId }.toMutableList()
        saveBabies()
        appScope.launch {
            if (_activeBaby.value.id == babyId) {
                val first = _babies.value.firstOrNull()
                if (first != null) {
                    _activeBaby.value = first
                    saveSettings()
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
        saveBabies()
    }

    // --- Timeline CRUD ---
    fun addRecord(record: TimelineRecord) {
        val r = if (record.babyId.isEmpty()) record.copy(babyId = _activeBaby.value.id) else record
        _timeline.value = (_timeline.value + r).toMutableList()
        saveTimeline()
        // Sync attached photos/videos to photo wall
        syncRecordMediaToPhotos(r)
    }

    private fun syncRecordMediaToPhotos(record: TimelineRecord) {
        val newPhotos = mutableListOf<PhotoEntry>()
        record.photos.forEachIndexed { index, url ->
            newPhotos.add(PhotoEntry(
                id = "photo-${record.id}-$index",
                babyId = record.babyId,
                url = url,
                caption = "${record.title} - 照片${index + 1}",
                date = record.date,
                timelineRecordId = record.id,
                tags = listOf(record.category)
            ))
        }
        record.videos.forEachIndexed { index, url ->
            newPhotos.add(PhotoEntry(
                id = "video-${record.id}-$index",
                babyId = record.babyId,
                url = url,
                caption = "${record.title} - 视频${index + 1}",
                date = record.date,
                timelineRecordId = record.id,
                tags = listOf(record.category, "视频")
            ))
        }
        if (newPhotos.isNotEmpty()) {
            // Deduplicate: don't add if URL already exists in photos
            val existingUrls = _photos.value.map { it.url }.toSet()
            val deduped = newPhotos.filter { it.url !in existingUrls }
            if (deduped.isNotEmpty()) {
                _photos.value = (deduped + _photos.value).toMutableList()
                savePhotos()
            }
        }
    }

    fun updateRecord(id: String, updates: (TimelineRecord) -> TimelineRecord) {
        _timeline.value = _timeline.value.map { if (it.id == id) updates(it) else it }.toMutableList()
        saveTimeline()
    }

    fun deleteRecord(id: String) {
        _timeline.value = _timeline.value.filter { it.id != id }.toMutableList()
        _photos.value = _photos.value.filter { it.timelineRecordId != id }.toMutableList()
        saveTimeline()
        savePhotos()
    }

    // --- Photos CRUD ---
    fun addPhoto(photo: PhotoEntry) {
        val p = if (photo.babyId.isEmpty()) photo.copy(babyId = _activeBaby.value.id) else photo
        _photos.value = (listOf(p) + _photos.value).toMutableList()
        appScope.launch {
            val localFile = File(p.url)
            database?.photoDao()?.insert(
                p.toPhotoEntity(
                    localOriginalPath = p.url.takeIf { localFile.exists() },
                    uploadStatus = if (localFile.exists()) "PENDING_UPLOAD" else "SYNCED",
                    sha256 = localFile.takeIf { it.exists() }?.sha256()
                )
            )
        }
        // Don't save to WebDAV yet — wait for upload to complete
        // savePhotos() will be called after remote URL is set

        appScope.launch {
            val localFile = File(p.url)
            if (localFile.exists()) {
                uploadLocalMedia(p, localFile, getWebDavConfig())
            } else {
                // No local file, URL is already remote — save now
                savePhotos()
            }
        }
    }

    private suspend fun uploadPendingMedia(config: WebDavManager.WebDavConfig) {
        val pending = database?.photoDao()?.getAllOnce()
            ?.filter { it.uploadStatus != "SYNCED" && !it.localOriginalPath.isNullOrBlank() }
            ?: return
        pending.forEach { entity ->
            val localFile = File(entity.localOriginalPath ?: return@forEach)
            if (localFile.exists()) {
                uploadLocalMedia(entity.toPhotoEntry(), localFile, config)
            }
        }
    }

    private suspend fun uploadLocalMedia(
        photo: PhotoEntry,
        localFile: File,
        config: WebDavManager.WebDavConfig
    ) {
        try {
            val ext = localFile.extension.ifBlank { photo.url.substringAfterLast(".", "jpg") }
            val mediaFolder = if (photo.tags.any { it == "视频" || it.equals("video", ignoreCase = true) }) "videos" else "photos"
            val remoteName = "$mediaFolder/${photo.id}.$ext"
            val mimeType = when (ext.lowercase()) {
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "png" -> "image/png"
                else -> "image/jpeg"
            }
            val remotePath = "${mediaRootPath(config)}/$remoteName"
            val uploadSucceeded = WebDavManager.uploadFile(config, remotePath, localFile, mimeType).getOrThrow()
            if (!uploadSucceeded) {
                markMediaUploadFailed(photo, localFile, "NAS 上传失败：服务器没有接受文件")
                return
            }

            val verification = WebDavManager.verifyUploadedFile(config, remotePath, localFile.length()).getOrThrow()
            if (!verification.exists || !verification.sizeMatches) {
                val detail = verification.message.ifBlank { "远端文件不存在或大小不一致" }
                markMediaUploadFailed(photo, localFile, "NAS 上传后验证失败：$detail")
                return
            }

            run {
                val baseUrl = config.url.trimEnd('/')
                val remoteUrl = "$baseUrl/$remotePath".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
                _photos.value = _photos.value.map {
                    if (it.id == photo.id) it.copy(url = remoteUrl) else it
                }.toMutableList()
                database?.photoDao()?.insert(
                    photo.copy(url = remoteUrl).toPhotoEntity(
                        remoteUrl = remoteUrl,
                        remotePath = remotePath,
                        localOriginalPath = localFile.absolutePath,
                        uploadStatus = "SYNCED",
                        sha256 = localFile.sha256()
                    )
                )
                savePhotos()
                val successMessage = "已上传并验证：$remoteName"
                _mediaUploadEvents.emit(MediaUploadEvent(photo.id, true, successMessage, remoteUrl))
                android.util.Log.d("DataManager", "Media upload to WebDAV succeeded and verified: $remoteName")
            }
        } catch (e: Exception) {
            android.util.Log.e("DataManager", "Failed to upload media to WebDAV", e)
            markMediaUploadFailed(photo, localFile, "NAS 上传失败：${e.message ?: "未知错误"}")
        }
    }

    private suspend fun markMediaUploadFailed(photo: PhotoEntry, localFile: File, message: String) {
        database?.photoDao()?.insert(
            photo.toPhotoEntity(
                localOriginalPath = localFile.absolutePath,
                uploadStatus = "UPLOAD_FAILED",
                sha256 = localFile.takeIf { it.exists() }?.sha256()
            )
        )
        _mediaUploadEvents.emit(MediaUploadEvent(photo.id, false, message))
        android.util.Log.e("DataManager", message)
    }

    private fun mediaRootPath(config: WebDavManager.WebDavConfig): String {
        val configuredPath = config.dataPath.trim().trimEnd('/').ifBlank { DATA_PATH }
        val rootPath = if (configuredPath.endsWith("/data")) {
            configuredPath.removeSuffix("/data")
        } else {
            configuredPath
        }
        return "${rootPath.trimEnd('/')}/media"
    }

    fun deletePhoto(id: String) {
        _photos.value = _photos.value.filter { it.id != id }.toMutableList()
        appScope.launch {
            database?.photoDao()?.deleteById(id)
        }
        savePhotos()
    }

    fun deletePhotos(ids: Set<String>) {
        _photos.value = _photos.value.filter { it.id !in ids }.toMutableList()
        appScope.launch {
            ids.forEach { id -> database?.photoDao()?.deleteById(id) }
        }
        savePhotos()
    }

    fun updatePhoto(id: String, caption: String? = null, date: String? = null) {
        _photos.value = _photos.value.map {
            if (it.id == id) {
                var p = it
                if (caption != null) p = p.copy(caption = caption)
                if (date != null) p = p.copy(date = date)
                p
            } else it
        }.toMutableList()
        savePhotos()
    }

    // --- Categories ---
    fun addCategory(cat: CategoryConfig) {
        _customCategories.value = (_customCategories.value + cat).toMutableList()
        saveSettings()
    }

    fun removeCategory(id: String) {
        _customCategories.value = _customCategories.value.filter { it.id != id }.toMutableList()
        saveSettings()
    }

    fun updateCategory(categoryId: String, updates: CategoryConfig.() -> CategoryConfig) {
        val updated = _customCategories.value.map {
            if (it.id == categoryId) it.updates() else it
        }.toMutableList()
        _customCategories.value = updated
        saveSettings()
    }

    fun addSubCategory(categoryId: String, name: String) {
        val cat = _customCategories.value.find { it.id == categoryId } ?: return
        val sub = CategoryConfig.SubCategory(
            id = "sub-${UUID.randomUUID().toString().take(8)}",
            name = name
        )
        val updated = cat.copy(children = cat.children + sub)
        updateCategory(categoryId) { updated }
    }

    // --- AI Config ---
    fun updateAIConfig(config: AIConfig) {
        _aiConfig.value = config
        saveSettings()
    }

    val isAIConfigured: Boolean get() {
        val profile = _aiProfiles.value.firstOrNull { it.isActive }
        return profile != null && profile.apiKey.isNotEmpty()
    }

    // --- AI Profile Management ---
    fun addAIProfile(profile: AIProfile) {
        _aiProfiles.value = (_aiProfiles.value + profile).toMutableList()
        saveAiProfiles()
    }

    fun updateAIProfile(profile: AIProfile) {
        _aiProfiles.value = _aiProfiles.value.map {
            if (it.id == profile.id) profile else it
        }.toMutableList()
        saveAiProfiles()
    }

    fun deleteAIProfile(profileId: String) {
        _aiProfiles.value = _aiProfiles.value.filter { it.id != profileId }.toMutableList()
        if (_aiProfiles.value.none { it.isActive } && _aiProfiles.value.isNotEmpty()) {
            val first = _aiProfiles.value.first()
            _aiProfiles.value = _aiProfiles.value.map {
                if (it.id == first.id) it.copy(isActive = true) else it
            }.toMutableList()
        }
        saveAiProfiles()
    }

    fun setActiveAIProfile(profileId: String) {
        _aiProfiles.value = _aiProfiles.value.map {
            it.copy(isActive = it.id == profileId)
        }.toMutableList()
        saveAiProfiles()
    }

    fun testAIProfileConnection(profile: AIProfile, onResult: (Result<Boolean>) -> Unit) {
        appScope.launch {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("${profile.apiBaseUrl.trimEnd('/')}/v1/chat/completions")
                    .header("Authorization", "Bearer ${profile.apiKey}")
                    .header("Content-Type", "application/json")
                    .post(okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        """{"model":"${profile.model}","messages":[{"role":"user","content":"test"}],"max_tokens":1}"""
                    ))
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
        // Auto-send to AI if user message
        if (msg.role == "user") {
            sendToAI(msg.content)
        }
    }

    private fun sendToAI(userInput: String) {
        val profile = activeAIProfile ?: return
        val systemPrompt = profile.systemPrompt.ifBlank { "你是一个专业的育儿助手。" }

        // Build messages array (system + history + user)
        val historyMessages = _chatMessages.value
            .filter { it.role == "user" || it.role == "assistant" }
            .map { mapOf("role" to it.role, "content" to it.content) }

        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt)
        ) + historyMessages

        val requestBody = mapOf(
            "model" to profile.model,
            "messages" to messages,
            "temperature" to profile.temperature.toDouble(),
            "top_p" to profile.topP.toDouble(),
            "max_tokens" to profile.maxTokens,
            "stream" to false
        )

        appScope.launch {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("${profile.apiBaseUrl.trimEnd('/')}/v1/chat/completions")
                    .header("Authorization", "Bearer ${profile.apiKey}")
                    .header("Content-Type", "application/json")
                    .post(okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        Gson().toJson(requestBody)
                    ))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val reply = extractContent(body)
                    val aiMsg = ChatMessage(
                        id = "msg-${System.currentTimeMillis()}",
                        role = "assistant",
                        content = reply,
                        timestamp = System.currentTimeMillis()
                    )
                    _chatMessages.value = (_chatMessages.value + aiMsg).toMutableList()
                } else {
                    android.util.Log.e("DataManager", "AI chat failed: HTTP ${response.code}, body: $body")
                    val errorMsg = ChatMessage(
                        id = "msg-${System.currentTimeMillis()}",
                        role = "assistant",
                        content = "抱歉，请求失败 (HTTP ${response.code})。请检查 API 配置。",
                        timestamp = System.currentTimeMillis()
                    )
                    _chatMessages.value = (_chatMessages.value + errorMsg).toMutableList()
                }
            } catch (e: Exception) {
                android.util.Log.e("DataManager", "AI chat error", e)
                val errorMsg = ChatMessage(
                    id = "msg-${System.currentTimeMillis()}",
                    role = "assistant",
                    content = "抱歉，连接失败：${e.message}",
                    timestamp = System.currentTimeMillis()
                )
                _chatMessages.value = (_chatMessages.value + errorMsg).toMutableList()
            }
        }
    }

    private fun extractContent(json: String): String {
        return try {
            val obj = Gson().fromJson(json, Map::class.java)
            val choices = obj["choices"] as? List<*> ?: return json.take(200)
            val first = choices.firstOrNull() as? Map<*, *> ?: return json.take(200)
            val message = first["message"] as? Map<*, *> ?: return json.take(200)
            message["content"]?.toString() ?: json.take(200)
        } catch (e: Exception) {
            json.take(200)
        }
    }

    fun clearMessages() { _chatMessages.value = emptyList() }

    // --- Theme ---
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        saveSettings()
    }

    // --- WebDAV ---
    fun saveWebDavConfig(config: WebDavManager.WebDavConfig) {
        _webDavConfig.value = config
        saveLocalStorageConfig()
        saveSettings()
    }

    fun clearWebDavConfig() {
        _webDavConfig.value = null
        saveLocalStorageConfig()
        saveSettings()
    }

    fun testWebDavConnection(config: WebDavManager.WebDavConfig, onResult: (Result<WebDavManager.ConnectionTestResult>) -> Unit) {
        appScope.launch {
            val result = WebDavManager.testConnection(config)
            withContext(Dispatchers.Main) { onResult(result) }
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
            "settings" to buildSettingsMap()
        )
        return gson.toJson(export)
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
                    saveBabies()
                }
                withContext(Dispatchers.Main) { onResult(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun resetAllData(onComplete: () -> Unit) {
        appScope.launch {
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
            // Also overwrite all JSON files with empty data
            saveBabies()
            saveTimeline()
            savePhotos()
            saveSettings()
            saveAiProfiles()
            database?.photoDao()?.deleteAll()
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

    private fun PhotoEntry.toPhotoEntity(
        remoteUrl: String? = null,
        remotePath: String? = null,
        localOriginalPath: String? = null,
        localThumbPath: String? = null,
        localPreviewPath: String? = null,
        uploadStatus: String = "SYNCED",
        sha256: String? = null
    ): PhotoEntity {
        val now = System.currentTimeMillis()
        val resolvedRemoteUrl = remoteUrl ?: url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return PhotoEntity(
            id = id,
            babyId = babyId,
            url = url,
            caption = caption,
            date = date,
            timelineRecordId = timelineRecordId,
            tags = Gson().toJson(tags),
            mediaType = if (tags.any { it == "视频" || it.equals("video", ignoreCase = true) }) "video" else "photo",
            remoteUrl = resolvedRemoteUrl,
            remotePath = remotePath,
            localOriginalPath = localOriginalPath,
            localThumbPath = localThumbPath,
            localPreviewPath = localPreviewPath,
            uploadStatus = uploadStatus,
            sha256 = sha256,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun PhotoEntity.toPhotoEntry(): PhotoEntry {
        val tagList = try {
            Gson().fromJson<List<String>>(tags, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return PhotoEntry(
            id = id,
            babyId = babyId,
            url = remoteUrl ?: url,
            caption = caption,
            date = date,
            timelineRecordId = timelineRecordId,
            tags = if (mediaType == "video" && tagList.none { it == "视频" }) tagList + "视频" else tagList
        )
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
