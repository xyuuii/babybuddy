package com.yueming.baby.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yueming.baby.data.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DataManager {
    private var db: AppDatabase? = null
    private var appContext: Context? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Photo storage helper ---
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

    // --- Video storage helper ---
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
                videoFile.outputStream().use { output ->
                    input.copyTo(output)
                }
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

    // Legacy alias for backward compatibility
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

    // WebDAV state
    private val _webDavConfig = MutableStateFlow<WebDavManager.WebDavConfig?>(null)
    val webDavConfig: StateFlow<WebDavManager.WebDavConfig?> = _webDavConfig.asStateFlow()

    private val _backupFiles = MutableStateFlow<List<String>>(emptyList())
    val backupFiles: StateFlow<List<String>> = _backupFiles.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    // --- Initialization ---
    fun init(context: Context) {
        if (db != null) return
        appContext = context.applicationContext
        db = AppDatabase.getInstance(context.applicationContext)
        android.util.Log.d("DataManager", "Database initialized, starting data load...")
        appScope.launch {
            loadFromRoom()
            android.util.Log.d("DataManager",
                "Data loaded. Babies: ${_babies.value.size}, Timeline: ${_timeline.value.size}, Photos: ${_photos.value.size}")
        }
    }

    private suspend fun loadFromRoom() {
        val database = db ?: return
        val gson = Gson()

        // Load all babies
        val babyEntities = database.babyDao().getAllBabies()
        if (babyEntities.isNotEmpty()) {
            _babies.value = babyEntities.map {
                BabyInfo(
                    id = it.id, name = it.name, nickname = it.nickname,
                    birthDate = it.birthDate, gender = it.gender,
                    avatar = it.avatarUri
                )
            }
            val activeEntity = babyEntities.firstOrNull { it.isActive } ?: babyEntities.first()
            _activeBaby.value = BabyInfo(
                id = activeEntity.id, name = activeEntity.name,
                nickname = activeEntity.nickname, birthDate = activeEntity.birthDate,
                gender = activeEntity.gender, avatar = activeEntity.avatarUri
            )
        } else {
            _babies.value = emptyList()
            _activeBaby.value = BabyInfo()
        }

        // Load data for active baby
        loadBabyData()
        loadGlobalSettings()
    }

    private suspend fun loadBabyData() {
        val database = db ?: return
        val babyId = _activeBaby.value.id
        val gson = Gson()

        if (babyId.isEmpty()) {
            _timeline.value = emptyList<TimelineRecord>().toMutableList()
            _photos.value = emptyList<PhotoEntry>().toMutableList()
            return
        }

        // Load timeline
        val records = database.timelineDao().getAllByBaby(babyId).map { entity ->
            TimelineRecord(
                id = entity.id, babyId = entity.babyId, date = entity.date,
                title = entity.title, description = entity.description,
                category = entity.category,
                tags = parseJsonList(gson, entity.tags),
                photos = parseJsonList(gson, entity.photos),
                videos = parseJsonList(gson, entity.videos)
            )
        }
        _timeline.value = records.toMutableList()

        // Load photos
        val photoList = database.photoDao().getAllByBaby(babyId).map { entity ->
            PhotoEntry(
                id = entity.id, babyId = entity.babyId, url = entity.url,
                caption = entity.caption, date = entity.date,
                timelineRecordId = entity.timelineRecordId,
                tags = parseJsonList(gson, entity.tags)
            )
        }
        _photos.value = photoList.toMutableList()
    }

    private suspend fun loadGlobalSettings() {
        val database = db ?: return
        val gson = Gson()

        // Load custom categories
        val catJson = database.settingsDao().get("custom_categories")?.value
        if (catJson != null) {
            try {
                val type = object : TypeToken<List<CategoryConfig>>() {}.type
                val cats: List<CategoryConfig> = gson.fromJson(catJson, type)
                _customCategories.value = cats.toMutableList()
            } catch (_: Exception) {}
        }

        // Load AI config (legacy)
        val aiJson = database.settingsDao().get("ai_config")?.value
        if (aiJson != null) {
            try {
                _aiConfig.value = gson.fromJson(aiJson, AIConfig::class.java)
            } catch (_: Exception) {}
        }

        // Load AI profiles
        val profilesJson = database.settingsDao().get("ai_profiles")?.value
        if (profilesJson != null) {
            try {
                val type = object : TypeToken<List<AIProfile>>() {}.type
                _aiProfiles.value = gson.fromJson(profilesJson, type)
            } catch (_: Exception) {}
        }

        // If no profiles exist, migrate legacy config
        if (_aiProfiles.value.isEmpty() && _aiConfig.value.apiKey.isNotEmpty()) {
            val legacyProfile = AIProfile(
                name = "默认配置",
                apiBaseUrl = _aiConfig.value.apiBaseUrl,
                apiKey = _aiConfig.value.apiKey,
                model = _aiConfig.value.model,
                isActive = true
            )
            _aiProfiles.value = listOf(legacyProfile)
            persistAIPprofiles()
        }

        // Load theme
        database.settingsDao().get("theme_mode")?.value?.let {
            try { _themeMode.value = ThemeMode.valueOf(it) } catch (_: Exception) {}
        }

        // Load WebDAV config
        val wdJson = database.settingsDao().get("webdav_config")?.value
        if (wdJson != null) {
            try {
                _webDavConfig.value = gson.fromJson(wdJson, WebDavManager.WebDavConfig::class.java)
            } catch (_: Exception) {}
        }
    }

    private fun parseJsonList(gson: Gson, json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }

    // --- Baby Management ---
    fun addBaby(info: BabyInfo) {
        val entity = BabyEntity(
            id = if (info.id.isEmpty()) java.util.UUID.randomUUID().toString() else info.id,
            name = info.name, nickname = info.nickname,
            birthDate = info.birthDate, gender = info.gender,
            avatarUri = info.avatar
        )
        _babies.value = (_babies.value + BabyInfo(
            id = entity.id, name = entity.name, nickname = entity.nickname,
            birthDate = entity.birthDate, gender = entity.gender,
            avatar = entity.avatarUri
        )).toMutableList()
        db?.let { database ->
            appScope.launch { database.babyDao().upsert(entity) }
        }
    }

    fun switchBaby(babyId: String) {
        val babies = _babies.value
        val target = babies.find { it.id == babyId } ?: return
        _activeBaby.value = target
        appScope.launch {
            db?.let { database ->
                database.babyDao().deactivateAll()
                database.babyDao().setActive(babyId)
            }
            loadBabyData()
        }
    }

    fun deleteBaby(babyId: String, onComplete: () -> Unit) {
        if (_babies.value.size <= 1) {
            onComplete()
            return
        }
        _babies.value = _babies.value.filter { it.id != babyId }.toMutableList()
        db?.let { database ->
            appScope.launch {
                database.babyDao().deleteById(babyId)
                database.timelineDao().deleteByBabyId(babyId)
                database.photoDao().deleteByBabyId(babyId)
                // Switch to first remaining baby
                val remaining = database.babyDao().getAllBabies()
                if (remaining.isNotEmpty()) {
                    val first = remaining.first()
                    database.babyDao().setActive(first.id)
                    withContext(Dispatchers.Main) {
                        _activeBaby.value = BabyInfo(
                            id = first.id, name = first.name, nickname = first.nickname,
                            birthDate = first.birthDate, gender = first.gender, avatar = first.avatarUri
                        )
                    }
                    loadBabyData()
                }
                withContext(Dispatchers.Main) { onComplete() }
            }
        } ?: onComplete()
    }

    fun updateBabyInfo(info: BabyInfo) {
        _activeBaby.value = info
        // Update in babies list
        _babies.value = _babies.value.map {
            if (it.id == info.id) info else it
        }.toMutableList()

        db?.let { database ->
            appScope.launch {
                database.babyDao().upsert(BabyEntity(
                    id = info.id, name = info.name, nickname = info.nickname,
                    birthDate = info.birthDate, gender = info.gender,
                    avatarUri = info.avatar
                ))
            }
        }
    }

    // --- Timeline CRUD ---
    fun addRecord(record: TimelineRecord) {
        val r = if (record.babyId.isEmpty()) record.copy(babyId = _activeBaby.value.id) else record
        _timeline.value = (_timeline.value + r).toMutableList()
        db?.let { database ->
            val gson = Gson()
            appScope.launch {
                database.timelineDao().insert(TimelineEntity(
                    id = r.id, babyId = r.babyId, date = r.date,
                    title = r.title, description = r.description,
                    category = r.category,
                    tags = gson.toJson(r.tags),
                    photos = gson.toJson(r.photos),
                    videos = gson.toJson(r.videos)
                ))
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
        db?.let { database ->
            appScope.launch {
                database.timelineDao().deleteById(id)
                database.photoDao().deleteByRecordId(id)
            }
        }
    }

    private fun saveTimelineAsync() {
        val database = db ?: return
        val records = _timeline.value
        val gson = Gson()
        appScope.launch {
            val entities = records.map { record ->
                TimelineEntity(
                    id = record.id, babyId = record.babyId, date = record.date,
                    title = record.title, description = record.description,
                    category = record.category,
                    tags = gson.toJson(record.tags),
                    photos = gson.toJson(record.photos),
                    videos = gson.toJson(record.videos)
                )
            }
            val all = database.timelineDao().getAllOnce()
            if (all.size != entities.size) {
                all.forEach { database.timelineDao().deleteById(it.id) }
                entities.forEach { database.timelineDao().insert(it) }
            } else {
                entities.forEach { database.timelineDao().insert(it) }
            }
        }
    }

    // --- Photos CRUD ---
    fun addPhoto(photo: PhotoEntry) {
        val p = if (photo.babyId.isEmpty()) photo.copy(babyId = _activeBaby.value.id) else photo
        _photos.value = (listOf(p) + _photos.value).toMutableList()
        db?.let { database ->
            val gson = Gson()
            appScope.launch {
                database.photoDao().insert(PhotoEntity(
                    id = p.id, babyId = p.babyId, url = p.url,
                    caption = p.caption, date = p.date,
                    timelineRecordId = p.timelineRecordId,
                    tags = gson.toJson(p.tags)
                ))
            }
        }
    }

    fun deletePhoto(id: String) {
        _photos.value = _photos.value.filter { it.id != id }.toMutableList()
        db?.let { database ->
            appScope.launch { database.photoDao().deleteById(id) }
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
        db?.let { database ->
            val json = Gson().toJson(_customCategories.value)
            appScope.launch {
                database.settingsDao().upsert(SettingsEntity("custom_categories", json))
            }
        }
    }

    // --- AI Config (legacy, kept for backward compat) ---
    fun updateAIConfig(config: AIConfig) { _aiConfig.value = config
        db?.let { database ->
            val json = Gson().toJson(config)
            appScope.launch {
                database.settingsDao().upsert(SettingsEntity("ai_config", json))
            }
        }
    }
    val isAIConfigured: Boolean get() {
        val profile = _aiProfiles.value.firstOrNull { it.isActive }
        return profile != null && profile.apiKey.isNotEmpty()
    }

    // --- AI Profile Management ---
    private fun persistAIPprofiles() {
        db?.let { database ->
            val json = Gson().toJson(_aiProfiles.value)
            appScope.launch {
                database.settingsDao().upsert(SettingsEntity("ai_profiles", json))
            }
        }
    }

    fun addAIProfile(profile: AIProfile) {
        _aiProfiles.value = (_aiProfiles.value + profile).toMutableList()
        persistAIPprofiles()
    }

    fun updateAIProfile(profile: AIProfile) {
        _aiProfiles.value = _aiProfiles.value.map {
            if (it.id == profile.id) profile else it
        }.toMutableList()
        persistAIPprofiles()
    }

    fun deleteAIProfile(profileId: String) {
        _aiProfiles.value = _aiProfiles.value.filter { it.id != profileId }.toMutableList()
        // If we deleted the active profile, activate the first remaining one
        if (_aiProfiles.value.none { it.isActive } && _aiProfiles.value.isNotEmpty()) {
            val first = _aiProfiles.value.first()
            _aiProfiles.value = _aiProfiles.value.map {
                if (it.id == first.id) it.copy(isActive = true) else it
            }.toMutableList()
        }
        persistAIPprofiles()
    }

    fun setActiveAIProfile(profileId: String) {
        _aiProfiles.value = _aiProfiles.value.map {
            it.copy(isActive = it.id == profileId)
        }.toMutableList()
        persistAIPprofiles()
    }

    fun testAIProfileConnection(profile: AIProfile, onResult: (Result<Boolean>) -> Unit) {
        appScope.launch {
            try {
                val baseUrl = profile.apiBaseUrl
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(baseUrl)
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
        db?.let { database ->
            appScope.launch {
                database.settingsDao().upsert(SettingsEntity("theme_mode", mode.name))
            }
        }
    }

    // --- WebDAV ---
    fun saveWebDavConfig(config: WebDavManager.WebDavConfig) {
        _webDavConfig.value = config
        db?.let { database ->
            val json = Gson().toJson(config)
            appScope.launch {
                database.settingsDao().upsert(SettingsEntity("webdav_config", json))
            }
        }
    }

    fun clearWebDavConfig() {
        _webDavConfig.value = null
        db?.let { database ->
            appScope.launch {
                database.settingsDao().delete("webdav_config")
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
                val db = db ?: throw Exception("数据库未初始化")
                val json = db.exportToJson(activeBabyId)
                val jsonBytes = json.toByteArray(Charsets.UTF_8)

                onProgress("正在打包...")
                val zipBytes = createBackupZip(jsonBytes, activeBabyId)

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

    private suspend fun createBackupZip(jsonData: ByteArray, babyId: String): ByteArray = withContext(Dispatchers.IO) {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Add database.json
            val jsonEntry = ZipEntry("database.json")
            zos.putNextEntry(jsonEntry)
            zos.write(jsonData)
            zos.closeEntry()

            // Add photos directory
            val photosEntry = ZipEntry("photos/")
            zos.putNextEntry(photosEntry)
            zos.closeEntry()

            // Collect photo URIs and add them as metadata
            val photoUrls = _photos.value.map { it.url }.filter { it.startsWith("content://") || it.startsWith("file://") }
            if (photoUrls.isNotEmpty()) {
                val photoListEntry = ZipEntry("photos/manifest.json")
                zos.putNextEntry(photoListEntry)
                zos.write(Gson().toJson(photoUrls).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // Add videos directory  
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
                // Extract zip and import
                val jsonStr = extractJsonFromZip(bytes)
                if (jsonStr != null) {
                    db?.let { database ->
                        val success = database.importFromJson(jsonStr)
                        if (success) {
                            loadFromRoom()
                        }
                        withContext(Dispatchers.Main) { onComplete(success) }
                    } ?: withContext(Dispatchers.Main) { onComplete(false) }
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
        } catch (e: Exception) {
            null
        }
    }

    // --- Export / Import (legacy) ---
    fun exportToJson(onResult: (String) -> Unit) {
        db?.let { database ->
            appScope.launch {
                val json = database.exportToJson(activeBabyId)
                withContext(Dispatchers.Main) { onResult(json) }
            }
        }
    }

    fun importFromJson(json: String, onResult: (Boolean) -> Unit) {
        db?.let { database ->
            appScope.launch {
                val success = database.importFromJson(json)
                if (success) loadFromRoom()
                withContext(Dispatchers.Main) { onResult(success) }
            }
        }
    }

    fun resetAllData(onComplete: () -> Unit) {
        db?.let { database ->
            appScope.launch {
                database.timelineDao().run {
                    getAllOnce().forEach { deleteById(it.id) }
                }
                database.photoDao().run {
                    getAllOnce().forEach { deleteById(it.id) }
                }
                database.babyDao().deleteAll()
                database.settingsDao().deleteAll()
                _timeline.value = mutableListOf()
                _photos.value = mutableListOf()
                _babies.value = emptyList()
                _activeBaby.value = BabyInfo()
                _customCategories.value = emptyList()
                _aiConfig.value = AIConfig()
                _aiProfiles.value = emptyList()
                _chatMessages.value = emptyList()
                _themeMode.value = ThemeMode.SYSTEM
                _webDavConfig.value = null
                withContext(Dispatchers.Main) { onComplete() }
            }
        } ?: onComplete()
    }

    // --- Age calculations ---
    fun getAgeInMonths(birthDate: String): Int {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val birth = fmt.parse(birthDate) ?: return 0
        val birthCal = java.util.Calendar.getInstance().apply { time = birth }
        val nowCal = java.util.Calendar.getInstance()
        var months = (nowCal.get(java.util.Calendar.YEAR) - birthCal.get(java.util.Calendar.YEAR)) * 12
        months += nowCal.get(java.util.Calendar.MONTH) - birthCal.get(java.util.Calendar.MONTH)
        if (nowCal.get(java.util.Calendar.DAY_OF_MONTH) < birthCal.get(java.util.Calendar.DAY_OF_MONTH)) months--
        return maxOf(0, months)
    }

    fun getAgeInDays(birthDate: String): Int {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val birth = fmt.parse(birthDate) ?: return 0
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
