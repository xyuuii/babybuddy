package com.yueming.baby.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yueming.baby.data.cloud.CloudManager
import com.yueming.baby.data.cloud.CloudStorageConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DataManager {
    private var appContext: Context? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    // Data paths
    private const val DATA_PATH = "/sata1-15529232180/yueming/data"
    private const val MEDIA_PATH = "/sata1-15529232180/yueming/media"

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

    // --- WebDAV Config helper ---
    private fun getWebDavConfig(): WebDavManager.WebDavConfig {
        val wd = _webDavConfig.value
        if (wd != null) return wd
        val cs = _cloudStorageConfig.value
        return WebDavManager.WebDavConfig(
            url = "${cs.host}:${cs.port}",
            username = cs.username,
            password = cs.password,
            backupPath = cs.webdavPath
        )
    }

    // --- Initialization ---
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        android.util.Log.d("DataManager", "Initializing with WebDAV backend")
        appScope.launch {
            initWebDavData()
        }
    }

    private suspend fun initWebDavData() {
        val config = getWebDavConfig()
        android.util.Log.d("DataManager", "WebDAV: ${config.url}, data path: $DATA_PATH")

        // Ensure directory structure exists
        WebDavManager.createDirectoryChain(config, DATA_PATH)
        WebDavManager.createDirectoryChain(config, "$MEDIA_PATH/photos")
        WebDavManager.createDirectoryChain(config, "$MEDIA_PATH/videos")

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
                    android.util.Log.d("DataManager", "Loaded ${list.size} photos from WebDAV")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse photos.json", e)
                    _photos.value = emptyList()
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "photos.json not found, starting fresh")
                _photos.value = emptyList()
            }
        )
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
                    WebDavManager.writeJson(config, "$DATA_PATH/babies.json", json)
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
                    WebDavManager.writeJson(config, "$DATA_PATH/timeline.json", json)
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
                    WebDavManager.writeJson(config, "$DATA_PATH/photos.json", json)
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
                    WebDavManager.writeJson(config, "$DATA_PATH/settings.json", json)
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
                    WebDavManager.writeJson(config, "$DATA_PATH/ai_profiles.json", json)
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to save AI profiles", e)
                }
            }
        }
    }

    // --- Cloud Storage Config ---
    fun saveCloudStorageConfig(config: CloudStorageConfig) {
        _cloudStorageConfig.value = config
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
        savePhotos()

        // Upload media file to WebDAV if it's a local file
        appScope.launch {
            val localFile = File(p.url)
            if (localFile.exists()) {
                try {
                    val config = getWebDavConfig()
                    val ext = p.url.substringAfterLast(".", "jpg")
                    val remoteName = "photos/${p.id}.$ext"
                    val mimeType = when (ext.lowercase()) {
                        "mp4" -> "video/mp4"
                        "webm" -> "video/webm"
                        "png" -> "image/png"
                        else -> "image/jpeg"
                    }
                    val data = localFile.readBytes()
                    val uploadResult = WebDavManager.uploadFile(config, "$MEDIA_PATH/$remoteName", data, mimeType)
                    if (uploadResult.isSuccess && uploadResult.getOrThrow()) {
                        val remoteUrl = "http://${config.url}/$MEDIA_PATH/$remoteName".replace("//", "/").replace("http:/", "http://")
                        _photos.value = _photos.value.map {
                            if (it.id == p.id) it.copy(url = remoteUrl) else it
                        }.toMutableList()
                        savePhotos()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to upload photo to WebDAV", e)
                }
            }
        }
    }

    fun deletePhoto(id: String) {
        _photos.value = _photos.value.filter { it.id != id }.toMutableList()
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
        saveSettings()
    }

    // --- WebDAV ---
    fun saveWebDavConfig(config: WebDavManager.WebDavConfig) {
        _webDavConfig.value = config
        saveSettings()
    }

    fun clearWebDavConfig() {
        _webDavConfig.value = null
        saveSettings()
    }

    fun testWebDavConnection(config: WebDavManager.WebDavConfig, onResult: (Result<WebDavManager.ConnectionTestResult>) -> Unit) {
        appScope.launch {
            val result = WebDavManager.testConnection(config)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun uploadBackup(onProgress: (String) -> Unit, onComplete: (Result<Boolean>) -> Unit) {
        val config = _webDavConfig.value ?: getWebDavConfig()
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
            "settings" to buildSettingsMap()
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
        val config = _webDavConfig.value ?: getWebDavConfig()
        appScope.launch {
            onProgress("正在从 WebDAV 下载备份...")
            val result = WebDavManager.downloadBackup(config, filename)
            withContext(Dispatchers.Main) { onComplete(result) }
        }
    }

    fun refreshBackupList(onResult: (Result<List<String>>) -> Unit) {
        val config = _webDavConfig.value ?: getWebDavConfig()
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
                        }
                        _babies.value = newBabies
                        val active = newBabies.find { it.id == activeBabyId } ?: newBabies.firstOrNull()
                        if (active != null) _activeBaby.value = active
                        saveBabies()
                    }

                    // Import timeline
                    val timelineList = map["timeline"] as? List<*>
                    if (timelineList != null) {
                        val newTimeline = mutableListOf<TimelineRecord>()
                        timelineList.mapNotNull { item ->
                            val m = item as? Map<*, *> ?: return@mapNotNull null
                            TimelineRecord(
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
                        }.forEach { newTimeline.add(it) }
                        _timeline.value = newTimeline
                        saveTimeline()
                    }

                    // Import photos
                    val photosList = map["photos"] as? List<*>
                    if (photosList != null) {
                        val newPhotos = mutableListOf<PhotoEntry>()
                        photosList.mapNotNull { item ->
                            val m = item as? Map<*, *> ?: return@mapNotNull null
                            PhotoEntry(
                                id = m["id"] as? String ?: "",
                                babyId = m["babyId"] as? String ?: "",
                                url = m["url"] as? String ?: "",
                                caption = m["caption"] as? String ?: "",
                                date = m["date"] as? String ?: "",
                                timelineRecordId = m["timelineRecordId"] as? String,
                                tags = (m["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            )
                        }.forEach { newPhotos.add(it) }
                        _photos.value = newPhotos
                        savePhotos()
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
