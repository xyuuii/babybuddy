package com.yueming.baby.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DataManager {
    data class MediaUploadEvent(
        val photoId: String,
        val success: Boolean,
        val message: String,
        val remoteUrl: String? = null
    )

    data class SyncStatus(
        val isConfigured: Boolean = false,
        val isSyncing: Boolean = false,
        val hasUnsyncedChanges: Boolean = false,
        val pendingMediaCount: Int = 0,
        val lastMessage: String = "NAS 未配置",
        val lastUpdatedAt: Long = 0L
    )

    private data class SyncMetadata(
        val schemaVersion: Int = 1,
        val revision: Long = 0L,
        val updatedAt: Long = 0L,
        val deviceId: String = ""
    )

    internal object CoilImageLoaderHolder {
        @Volatile var instance: ImageLoader? = null
    }

    private fun ensureImageLoader(context: Context) {
        if (CoilImageLoaderHolder.instance != null) return
        CoilImageLoaderHolder.instance = ImageLoader.Builder(context.applicationContext)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(context.applicationContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.applicationContext.cacheDir, "coil_cache"))
                    .maxSizeBytes(150 * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
            .respectCacheHeaders(false)
            .build()
    }

    private var appContext: Context? = null
    private var database: AppDatabase? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val gson = com.google.gson.Gson()

    // Data paths
    private const val DATA_PATH = "/babybuddy/data"
    private const val MEDIA_PATH = "/babybuddy/media"
    private const val LOCAL_CONFIG_PREFS = "babybuddy_local_storage_config"
    private const val KEY_CLOUD_STORAGE_CONFIG = "cloud_storage_config"
    private const val KEY_WEBDAV_CONFIG = "webdav_config"
    private const val KEY_AI_CONFIG = "ai_config"
    private const val KEY_AI_PROFILES = "ai_profiles"
    private const val LOCAL_DATA_PREFS = "babybuddy_local_data_snapshot"
    private const val KEY_LOCAL_BABIES = "babies"
    private const val KEY_LOCAL_TIMELINE = "timeline"
    private const val KEY_LOCAL_PHOTOS = "photos"
    private const val KEY_LOCAL_SETTINGS = "settings"
    private const val KEY_LOCAL_FEEDING = "feeding"
    private const val KEY_LOCAL_VACCINE = "vaccine_statuses"
    private const val KEY_LOCAL_REMINDERS = "reminders"
    private const val KEY_LOCAL_CHAT_MESSAGES = "chat_messages"
    private const val KEY_LOCAL_DIRTY = "has_unsynced_changes"
    private const val KEY_SYNC_DEVICE_ID = "sync_device_id"
    private const val KEY_SYNC_REVISION = "sync_revision"
    private const val SYNC_META_FILE = "sync_meta.json"
    private const val NAS_SYNC_WORK_NAME = "yueming_nas_sync"
    private const val REMINDER_WORK_PREFIX = "yueming_reminder_"
    private const val STARTUP_REMOTE_SYNC_DELAY_MS = 1_200L
    private const val AI_CONTEXT_MESSAGE_LIMIT = 24

    // --- Photo cache helper (copy content URI to temp for upload) ---
    fun copyPhotoToInternalStorage(uri: android.net.Uri): String? {
        val context = appContext ?: return null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val photoDir = File(context.filesDir, "photos").also { it.mkdirs() }
            val ext = context.contentResolver.getType(uri)?.let { mimeToExtension(it) } ?: "jpg"
            val photoFile = File(photoDir, "photo-${java.util.UUID.randomUUID()}.$ext")
            inputStream.use { input ->
                photoFile.outputStream().use { output -> input.copyTo(output) }
            }
            photoFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("DataManager", "Failed to copy photo", e)
            null
        }
    }

    private fun mimeToExtension(mimeType: String): String = when {
        mimeType.contains("png") -> "png"
        mimeType.contains("webp") -> "webp"
        mimeType.contains("gif") -> "gif"
        mimeType.contains("heic") || mimeType.contains("heif") -> "heic"
        else -> "jpg"
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

    private fun isForActiveBaby(babyId: String?): Boolean {
        return belongsToBaby(babyId, activeBabyId)
    }

    private fun isForBaby(babyId: String?, targetBabyId: String): Boolean {
        return belongsToBaby(babyId, targetBabyId)
    }

    private fun isStrictlyForBaby(babyId: String?, targetBabyId: String): Boolean {
        return targetBabyId.isNotBlank() && babyId == targetBabyId
    }

    private fun FeedingRecord.withDefaultBabyId(defaultBabyId: String): FeedingRecord {
        val currentBabyId: String? = babyId
        return if (currentBabyId.isNullOrBlank() && defaultBabyId.isNotBlank()) {
            copy(babyId = defaultBabyId)
        } else {
            this
        }
    }

    private fun TimelineRecord.withDefaultBabyId(defaultBabyId: String): TimelineRecord {
        val currentBabyId: String? = babyId
        return if (currentBabyId.isNullOrBlank() && defaultBabyId.isNotBlank()) {
            copy(babyId = defaultBabyId)
        } else {
            this
        }
    }

    private fun PhotoEntry.withDefaultBabyId(defaultBabyId: String): PhotoEntry {
        val currentBabyId: String? = babyId
        return if (currentBabyId.isNullOrBlank() && defaultBabyId.isNotBlank()) {
            copy(babyId = defaultBabyId)
        } else {
            this
        }
    }

    private fun PhotoEntry.safeTags(): List<String> = tags ?: emptyList()

    private fun PhotoEntry.resolvedMediaType(): String {
        mediaType?.lowercase()?.takeIf { it == "photo" || it == "video" }?.let { return it }
        val tagSaysVideo = safeTags().any { it == "视频" || it.equals("video", ignoreCase = true) }
        return if (tagSaysVideo || looksLikeVideoPath(url) || looksLikeVideoPath(remotePath.orEmpty())) {
            "video"
        } else {
            "photo"
        }
    }

    private fun PhotoEntry.normalizedTags(mediaKind: String): List<String> {
        val currentTags = safeTags()
        return if (mediaKind == "video" && currentTags.none { it == "视频" || it.equals("video", ignoreCase = true) }) {
            currentTags + "视频"
        } else {
            currentTags
        }
    }

    private fun PhotoEntry.withCurrentWebDavUrl(config: WebDavManager.WebDavConfig): PhotoEntry {
        val resolvedRemotePath = remotePath?.takeIf { it.isNotBlank() }
            ?: remotePathFromStoredReference(url, config)
            ?: remoteUrl?.let { remotePathFromStoredReference(it, config) }
        val mediaKind = resolvedMediaType()
        val displayUrl = if (!resolvedRemotePath.isNullOrBlank()) {
            webDavUrlForRemotePath(config, resolvedRemotePath)
        } else {
            url
        }
        return copy(
            url = displayUrl,
            remotePath = resolvedRemotePath,
            remoteUrl = remoteUrl ?: url.takeIf { isHttpUrl(it) },
            mediaType = mediaKind,
            tags = normalizedTags(mediaKind)
        ).withDefaultBabyId(activeBabyId)
    }

    private fun PhotoEntry.stableMediaKey(config: WebDavManager.WebDavConfig): String {
        return remotePath?.takeIf { it.isNotBlank() }
            ?: remotePathFromStoredReference(url, config)
            ?: remoteUrl?.let { remotePathFromStoredReference(it, config) }
            ?: id
    }

    private fun VaccineStatus.withDefaultBabyId(defaultBabyId: String): VaccineStatus {
        val currentBabyId: String? = babyId
        return if (currentBabyId.isNullOrBlank() && defaultBabyId.isNotBlank()) {
            copy(babyId = defaultBabyId)
        } else {
            this
        }
    }

    private fun Reminder.withDefaultBabyId(defaultBabyId: String): Reminder {
        val currentBabyId: String? = babyId
        return if (currentBabyId.isNullOrBlank() && defaultBabyId.isNotBlank()) {
            copy(babyId = defaultBabyId)
        } else {
            this
        }
    }

    private val _timeline = MutableStateFlow<List<TimelineRecord>>(emptyList())
    val timeline: StateFlow<List<TimelineRecord>> = _timeline.asStateFlow()

    private val _photos = MutableStateFlow<List<PhotoEntry>>(emptyList())
    val photos: StateFlow<List<PhotoEntry>> = _photos.asStateFlow()

    private val _feedingRecords = MutableStateFlow(emptyList<FeedingRecord>())
    val feedingRecords: StateFlow<List<FeedingRecord>> = _feedingRecords.asStateFlow()

    private val _vaccineStatuses = MutableStateFlow(emptyList<VaccineStatus>())
    val vaccineStatuses: StateFlow<List<VaccineStatus>> = _vaccineStatuses.asStateFlow()

    private val _reminders = MutableStateFlow(emptyList<Reminder>())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

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

    private val _isAIProcessing = MutableStateFlow(false)
    val isAIProcessing: StateFlow<Boolean> = _isAIProcessing.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Cloud storage config
    private val _cloudStorageConfig = MutableStateFlow(CloudStorageConfig())
    val cloudStorageConfig: StateFlow<CloudStorageConfig> = _cloudStorageConfig.asStateFlow()
    private val _mediaUploadEvents = MutableSharedFlow<MediaUploadEvent>(extraBufferCapacity = 16)
    val mediaUploadEvents: SharedFlow<MediaUploadEvent> = _mediaUploadEvents.asSharedFlow()
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // Legacy WebDAV state (forward-compat)
    private val _webDavConfig = MutableStateFlow<WebDavManager.WebDavConfig?>(null)
    val webDavConfig: StateFlow<WebDavManager.WebDavConfig?> = _webDavConfig.asStateFlow()

    // --- WebDAV Config helper ---
    private fun getWebDavConfigOrNull(): WebDavManager.WebDavConfig? {
        val wd = _webDavConfig.value
        if (wd != null && wd.url.isNotBlank()) return wd
        val cs = _cloudStorageConfig.value
        if (cs.host.isBlank()) return null
        return cs.toWebDavConfig()
    }

    private fun loadLocalStorageConfig() {
        val context = appContext ?: return

        val legacyPrefs = context.getSharedPreferences(LOCAL_CONFIG_PREFS, Context.MODE_PRIVATE)

        fun readSecureOrMigrate(key: String): String? {
            val secure = SecureLocalStore.getString(context, key)
            if (secure != null) return secure
            val legacy = legacyPrefs.getString(key, null) ?: return null
            runCatching { SecureLocalStore.putString(context, key, legacy) }
                .onFailure { android.util.Log.e("DataManager", "Failed to migrate secure config: $key", it) }
            legacyPrefs.edit().remove(key).apply()
            return legacy
        }

        readSecureOrMigrate(KEY_CLOUD_STORAGE_CONFIG)?.let { json ->
            runCatching {
                gson.fromJson(json, CloudStorageConfig::class.java)
            }.getOrNull()?.let { _cloudStorageConfig.value = it }
        }
        readSecureOrMigrate(KEY_WEBDAV_CONFIG)?.let { json ->
            runCatching {
                gson.fromJson(json, WebDavManager.WebDavConfig::class.java)
            }.getOrNull()?.let { _webDavConfig.value = it }
        }
        readSecureOrMigrate(KEY_AI_CONFIG)?.let { json ->
            runCatching {
                gson.fromJson(json, AIConfig::class.java)
            }.getOrNull()?.let { _aiConfig.value = it }
        }
        readSecureOrMigrate(KEY_AI_PROFILES)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<AIProfile>>() {}.type
                gson.fromJson<List<AIProfile>>(json, type)
            }.getOrNull()?.let { _aiProfiles.value = it }
        }
    }

    private fun saveLocalStorageConfig() {
        val context = appContext ?: return

        SecureLocalStore.putString(context, KEY_CLOUD_STORAGE_CONFIG, gson.toJson(_cloudStorageConfig.value))
        val webDavConfig = _webDavConfig.value
        if (webDavConfig == null) SecureLocalStore.remove(context, KEY_WEBDAV_CONFIG)
        else SecureLocalStore.putString(context, KEY_WEBDAV_CONFIG, gson.toJson(webDavConfig))
        context.getSharedPreferences(LOCAL_CONFIG_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CLOUD_STORAGE_CONFIG)
            .remove(KEY_WEBDAV_CONFIG)
            .apply()
    }

    private fun saveLocalAISecrets() {
        val context = appContext ?: return

        SecureLocalStore.putString(context, KEY_AI_CONFIG, gson.toJson(_aiConfig.value))
        SecureLocalStore.putString(context, KEY_AI_PROFILES, gson.toJson(_aiProfiles.value))
    }

    private fun loadLocalDataSnapshot() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(LOCAL_DATA_PREFS, Context.MODE_PRIVATE)


        prefs.getString(KEY_LOCAL_SETTINGS, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
            }.getOrNull()?.let { applySettingsMap(it) }
        }
        prefs.getString(KEY_LOCAL_BABIES, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<BabyInfo>>() {}.type
                gson.fromJson<List<BabyInfo>>(json, type) ?: emptyList()
            }.getOrNull()?.let { list ->
                _babies.value = list
                val activeId = _activeBaby.value.id
                _activeBaby.value = list.find { it.id == activeId } ?: list.firstOrNull() ?: BabyInfo()
            }
        }
        prefs.getString(KEY_LOCAL_TIMELINE, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<TimelineRecord>>() {}.type
                gson.fromJson<List<TimelineRecord>>(json, type) ?: emptyList()
            }.getOrNull()?.let { list ->
                _timeline.value = list.map { it.withDefaultBabyId(activeBabyId) }
            }
        }
        prefs.getString(KEY_LOCAL_PHOTOS, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<PhotoEntry>>() {}.type
                gson.fromJson<List<PhotoEntry>>(json, type) ?: emptyList()
            }.getOrNull()?.let { list ->
                _photos.value = list.map { it.withDefaultBabyId(activeBabyId) }
            }
        }
        prefs.getString(KEY_LOCAL_FEEDING, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<FeedingRecord>>() {}.type
                gson.fromJson<List<FeedingRecord>>(json, type) ?: emptyList()
            }.getOrNull()?.let { list ->
                _feedingRecords.value = list.map { it.withDefaultBabyId(activeBabyId) }
            }
        }
        prefs.getString(KEY_LOCAL_VACCINE, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<VaccineStatus>>() {}.type
                gson.fromJson<List<VaccineStatus>>(json, type) ?: emptyList()
            }.getOrNull()?.let { list ->
                _vaccineStatuses.value = list.map { it.withDefaultBabyId(activeBabyId) }
            }
        }
        prefs.getString(KEY_LOCAL_REMINDERS, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<Reminder>>() {}.type
                gson.fromJson<List<Reminder>>(json, type) ?: emptyList()
            }.getOrNull()?.let { list ->
                _reminders.value = list.map { it.withDefaultBabyId(activeBabyId) }
                rescheduleReminderNotifications()
            }
        }
        prefs.getString(KEY_LOCAL_CHAT_MESSAGES, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<ChatMessage>>() {}.type
                gson.fromJson<List<ChatMessage>>(json, type) ?: emptyList()
            }.getOrNull()?.let { list ->
                _chatMessages.value = list
            }
        }
    }

    private fun saveLocalDataSnapshot() {
        val context = appContext ?: return

        context.getSharedPreferences(LOCAL_DATA_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCAL_BABIES, gson.toJson(_babies.value))
            .putString(KEY_LOCAL_TIMELINE, gson.toJson(_timeline.value))
            .putString(KEY_LOCAL_PHOTOS, gson.toJson(_photos.value))
            .putString(KEY_LOCAL_FEEDING, gson.toJson(_feedingRecords.value))
            .putString(KEY_LOCAL_VACCINE, gson.toJson(_vaccineStatuses.value))
            .putString(KEY_LOCAL_REMINDERS, gson.toJson(_reminders.value))
            .putString(KEY_LOCAL_CHAT_MESSAGES, gson.toJson(_chatMessages.value))
            .putString(KEY_LOCAL_SETTINGS, gson.toJson(buildSettingsMap()))
            .apply()
    }

    private fun hasUnsyncedLocalChanges(): Boolean {
        val context = appContext ?: return false
        return context.getSharedPreferences(LOCAL_DATA_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCAL_DIRTY, false)
    }

    private fun markUnsyncedLocalChanges(dirty: Boolean = true) {
        setUnsyncedLocalChangesFlag(dirty)
        refreshSyncStatusAsync()
        if (dirty) enqueueBackgroundSync()
    }

    private fun setUnsyncedLocalChangesFlag(dirty: Boolean) {
        val context = appContext ?: return
        context.getSharedPreferences(LOCAL_DATA_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOCAL_DIRTY, dirty)
            .apply()
    }

    private fun enqueueBackgroundSync() {
        val context = appContext ?: return
        if (getWebDavConfigOrNull() == null) return
        val request = OneTimeWorkRequestBuilder<NasSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAS_SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun refreshSyncStatus(message: String? = null, isSyncing: Boolean = _syncStatus.value.isSyncing) {
        val pendingMedia = database?.photoDao()?.getAllOnce()
            ?.count { it.uploadStatus != "SYNCED" && !it.localOriginalPath.isNullOrBlank() }
            ?: 0
        val current = _syncStatus.value
        _syncStatus.value = current.copy(
            isConfigured = getWebDavConfigOrNull() != null,
            isSyncing = isSyncing,
            hasUnsyncedChanges = hasUnsyncedLocalChanges(),
            pendingMediaCount = pendingMedia,
            lastMessage = message ?: current.lastMessage,
            lastUpdatedAt = System.currentTimeMillis()
        )
    }

    private fun refreshSyncStatusAsync(message: String? = null, isSyncing: Boolean = _syncStatus.value.isSyncing) {
        appScope.launch { refreshSyncStatus(message, isSyncing) }
    }

    private fun localDataPrefs() =
        appContext?.getSharedPreferences(LOCAL_DATA_PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateSyncDeviceId(): String {
        val prefs = localDataPrefs() ?: return "unknown-device"
        prefs.getString(KEY_SYNC_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SYNC_DEVICE_ID, id).apply()
        return id
    }

    private fun knownRemoteRevision(): Long =
        localDataPrefs()?.getLong(KEY_SYNC_REVISION, 0L) ?: 0L

    private fun rememberRemoteRevision(revision: Long) {
        localDataPrefs()?.edit()?.putLong(KEY_SYNC_REVISION, revision)?.apply()
    }

    private suspend fun readRemoteSyncMetadata(config: WebDavManager.WebDavConfig): SyncMetadata? {
        val json = WebDavManager.readJson(config, "${dataRootPath(config)}/$SYNC_META_FILE").getOrNull()
            ?: return null
        return runCatching { gson.fromJson(json, SyncMetadata::class.java) }.getOrNull()
    }

    private suspend fun rememberRemoteMetadataIfPresent(config: WebDavManager.WebDavConfig) {
        readRemoteSyncMetadata(config)?.let { metadata ->
            rememberRemoteRevision(maxOf(knownRemoteRevision(), metadata.revision))
        }
    }

    private fun hasAnyLocalUserData(): Boolean {
        return _babies.value.isNotEmpty() ||
            _timeline.value.isNotEmpty() ||
            _photos.value.isNotEmpty() ||
            _feedingRecords.value.isNotEmpty() ||
            _vaccineStatuses.value.isNotEmpty() ||
            _reminders.value.isNotEmpty() ||
            _chatMessages.value.isNotEmpty()
    }

    private suspend fun remoteHasAnyUserData(config: WebDavManager.WebDavConfig): Boolean {
        val dataPath = dataRootPath(config)
        val files = listOf(
            "babies.json",
            "timeline.json",
            "photos.json",
            "feeding.json",
            "vaccine_statuses.json",
            "reminders.json",
            "chat_messages.json"
        )
        return files.any { fileName ->
            val json = WebDavManager.readJson(config, "$dataPath/$fileName").getOrNull()?.trim()
            !json.isNullOrBlank() && json != "[]" && json != "{}"
        }
    }

    private suspend fun ensureRemoteNotAhead(config: WebDavManager.WebDavConfig): Boolean {
        val remote = readRemoteSyncMetadata(config) ?: run {
            if (knownRemoteRevision() == 0L && hasAnyLocalUserData() && remoteHasAnyUserData(config)) {
                setUnsyncedLocalChangesFlag(true)
                refreshSyncStatus(
                    "NAS 已有旧版数据，已暂停自动覆盖；请先确认以本机或 NAS 为准",
                    isSyncing = false
                )
                return false
            }
            return true
        }
        val localRevision = knownRemoteRevision()
        val deviceId = getOrCreateSyncDeviceId()
        if (remote.revision > localRevision && remote.deviceId != deviceId) {
            setUnsyncedLocalChangesFlag(true)
            refreshSyncStatus(
                "NAS 上已有其他设备的新版本，已停止自动覆盖；请先手动同步或导出备份",
                isSyncing = false
            )
            return false
        }
        rememberRemoteRevision(maxOf(localRevision, remote.revision))
        return true
    }

    private suspend fun recordRemoteWrite(config: WebDavManager.WebDavConfig) {
        val revision = System.currentTimeMillis()
        val metadata = SyncMetadata(
            schemaVersion = 1,
            revision = revision,
            updatedAt = revision,
            deviceId = getOrCreateSyncDeviceId()
        )
        WebDavManager.writeJson(config, "${dataRootPath(config)}/$SYNC_META_FILE", gson.toJson(metadata))
        rememberRemoteRevision(revision)
    }

    private suspend fun writeDataJsonWithConflictGuard(
        config: WebDavManager.WebDavConfig,
        fileName: String,
        json: String
    ): Boolean {
        if (!ensureRemoteNotAhead(config)) return false
        val ok = WebDavManager.writeJson(config, "${dataRootPath(config)}/$fileName", json).getOrDefault(false)
        if (ok) recordRemoteWrite(config)
        return ok
    }

    // --- Initialization ---
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        android.util.Log.d("DataManager", "Initializing with local media index + WebDAV backend")
        appScope.launch {
            try {
                database = AppDatabase.getInstance(context.applicationContext)
                ensureImageLoader(context.applicationContext)
                loadLocalStorageConfig()
                loadLocalDataSnapshot()
                restorePhotosFromLocalIndexIfEmpty()
                refreshSyncStatus("已加载本地快照", isSyncing = false)
                _isLoading.value = false

                // Render the local-first home screen before remote sync starts emitting state updates.
                delay(STARTUP_REMOTE_SYNC_DELAY_MS)
                val initialized = withTimeoutOrNull(12_000) {
                    initWebDavData()
                    true
                } ?: false
                if (!initialized) {
                    android.util.Log.w("DataManager", "NAS initialization timed out; falling back to local snapshot")
                    refreshSyncStatus("NAS 连接超时，已先使用本地快照", isSyncing = false)
                    if (hasUnsyncedLocalChanges()) enqueueBackgroundSync()
                }
            } catch (e: Exception) {
                android.util.Log.e("DataManager", "DataManager init failed", e)
                refreshSyncStatus("初始化失败，已保留本地默认状态", isSyncing = false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun initWebDavData() {
        val config = getWebDavConfigOrNull() ?: run {
            android.util.Log.i("DataManager", "Cloud storage is not configured; using local snapshot only")
            refreshSyncStatus("NAS 未配置，当前使用本地快照", isSyncing = false)
            return
        }
        refreshSyncStatus("正在连接 NAS...", isSyncing = true)
        val dataPath = dataRootPath(config)
        val mediaPath = mediaRootPath(config)
        android.util.Log.d("DataManager", "WebDAV: ${config.url}, data path: $dataPath")

        if (!verifyNasConnection(config)) {
            if (hasUnsyncedLocalChanges()) enqueueBackgroundSync()
            return
        }

        // Ensure directory structure exists
        WebDavManager.createDirectoryChain(config, dataPath)
        WebDavManager.createDirectoryChain(config, "$mediaPath/photos")
        WebDavManager.createDirectoryChain(config, "$mediaPath/videos")

        if (hasUnsyncedLocalChanges()) {
            android.util.Log.i("DataManager", "Local snapshot has unsynced changes; pushing local data before reading remote")
            if (!flushLocalSnapshotToWebDav(config)) {
                mergeRemoteMediaIndexIntoLocal(config)
                return
            }
            uploadPendingMedia(config)
            reconcilePhotosWithRemoteSafely(config)
            refreshSyncStatus("本地改动已尝试同步到 NAS", isSyncing = false)
            return
        }

        // Load settings first (contains activeBabyId, theme, etc.)
        loadSettingsFromWebDav(config)

        // Load data files
        loadBabiesFromWebDav(config)
        loadTimelineFromWebDav(config)
        loadPhotosFromWebDav(config)
        reconcilePhotosWithRemoteSafely(config)
        loadFeedingFromWebDav(config)
        loadVaccinesFromWebDav(config)
        loadRemindersFromWebDav(config)
        loadAiProfilesFromWebDav(config)
        loadChatMessagesFromWebDav(config)
        rememberRemoteMetadataIfPresent(config)
        saveLocalDataSnapshot()
        rescheduleReminderNotifications()
        uploadPendingMedia(config)
        refreshSyncStatus("NAS 同步完成", isSyncing = false)
    }

    private suspend fun verifyNasConnection(config: WebDavManager.WebDavConfig): Boolean {
        val testResult = WebDavManager.testConnection(config).getOrElse { error ->
            refreshSyncStatus("NAS 连接失败：${error.message}", isSyncing = false)
            return false
        }
        if (!testResult.success) {
            refreshSyncStatus(testResult.message.ifBlank { "NAS 连接失败：读写验证未通过" }, isSyncing = false)
            return false
        }
        refreshSyncStatus("NAS 已连接，正在同步...", isSyncing = true)
        return true
    }

    private suspend fun flushLocalSnapshotToWebDav(config: WebDavManager.WebDavConfig): Boolean {
        return writeMutex.withLock {
            try {
                if (!ensureRemoteNotAhead(config)) return@withLock false
        
                val dataPath = dataRootPath(config)
                val babiesOk = WebDavManager.writeJson(config, "$dataPath/babies.json", gson.toJson(_babies.value)).getOrDefault(false)
                val timelineOk = WebDavManager.writeJson(config, "$dataPath/timeline.json", gson.toJson(_timeline.value)).getOrDefault(false)
                val photosOk = WebDavManager.writeJson(config, "$dataPath/photos.json", gson.toJson(remoteSafePhotos())).getOrDefault(false)
                val settingsOk = WebDavManager.writeJson(config, "$dataPath/settings.json", gson.toJson(buildSettingsMap())).getOrDefault(false)
                val aiOk = WebDavManager.writeJson(config, "$dataPath/ai_profiles.json", gson.toJson(_aiProfiles.value.map { it.copy(apiKey = "") })).getOrDefault(false)
                val chatOk = WebDavManager.writeJson(config, "$dataPath/chat_messages.json", gson.toJson(_chatMessages.value)).getOrDefault(false)
                val feedingOk = WebDavManager.writeJson(config, "$dataPath/feeding.json", gson.toJson(_feedingRecords.value)).getOrDefault(false)
                val vaccineOk = WebDavManager.writeJson(config, "$dataPath/vaccine_statuses.json", gson.toJson(_vaccineStatuses.value)).getOrDefault(false)
                val remindersOk = WebDavManager.writeJson(config, "$dataPath/reminders.json", gson.toJson(_reminders.value)).getOrDefault(false)
                val allOk = babiesOk && timelineOk && photosOk && settingsOk && aiOk && chatOk && feedingOk && vaccineOk && remindersOk
                if (allOk) recordRemoteWrite(config)
                markUnsyncedLocalChanges(!allOk)
                refreshSyncStatus(
                    if (allOk) "本地快照已同步到 NAS" else "部分数据同步失败，已保留本地快照",
                    isSyncing = false
                )
                allOk
            } catch (e: Exception) {
                android.util.Log.e("DataManager", "Failed to flush local snapshot to WebDAV", e)
                markUnsyncedLocalChanges(true)
                false.also {
                refreshSyncStatus("NAS 同步失败，已保留本地快照", isSyncing = false)
                }
            }
        }
    }

    private suspend fun loadBabiesFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/babies.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<BabyInfo>>() {}.type
                    val list: List<BabyInfo> = gson.fromJson(json, type)
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
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "babies.json unavailable, keeping local snapshot")
            }
        )
    }

    private suspend fun loadTimelineFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/timeline.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<TimelineRecord>>() {}.type
                    val list: List<TimelineRecord> = gson.fromJson(json, type)
                    val normalized = list.map { it.withDefaultBabyId(activeBabyId) }
                    _timeline.value = normalized
                    android.util.Log.d("DataManager", "Loaded ${normalized.size} timeline records from WebDAV")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse timeline.json", e)
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "timeline.json unavailable, keeping local snapshot")
            }
        )
    }

    private suspend fun loadPhotosFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/photos.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<PhotoEntry>>() {}.type
                    val list: List<PhotoEntry> = gson.fromJson(json, type)
                    val normalized = list.map { it.withCurrentWebDavUrl(config) }
                    val recovered = if (normalized.isEmpty()) rebuildPhotoIndexFromRemoteMedia(config) else emptyList()
                    val localRecovered = if (normalized.isEmpty() && recovered.isEmpty()) loadPhotoEntriesFromLocalIndex() else emptyList()
                    val resolved = when {
                        normalized.isNotEmpty() -> normalized
                        recovered.isNotEmpty() -> recovered
                        else -> localRecovered
                    }
                    _photos.value = resolved
                    upsertPhotoIndex(resolved.map { it.toPhotoEntity(uploadStatus = "SYNCED") })
                    if (normalized.isEmpty() && recovered.isNotEmpty()) {
                        savePhotos()
                        refreshSyncStatus("已从 NAS 媒体目录恢复 ${recovered.size} 条媒体记录", isSyncing = false)
                    } else if (normalized.isEmpty() && localRecovered.isNotEmpty()) {
                        savePhotos()
                        refreshSyncStatus("已从本地媒体索引恢复 ${localRecovered.size} 条媒体记录", isSyncing = false)
                    }
                    android.util.Log.d("DataManager", "Loaded ${resolved.size} photos from WebDAV")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse photos.json", e)
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "photos.json unavailable, keeping local snapshot")
                appScope.launch {
                    val recovered = restorePhotosFromLocalIndexIfEmpty()
                    if (recovered.isNotEmpty()) savePhotos()
                }
            }
        )
    }

    private suspend fun mergeRemoteMediaIndexIntoLocal(config: WebDavManager.WebDavConfig) {
        val remoteFromJson = WebDavManager.readJson(config, "${dataRootPath(config)}/photos.json")
            .getOrNull()
            ?.let { json ->
                runCatching {
                    val type = object : TypeToken<List<PhotoEntry>>() {}.type
                    gson.fromJson<List<PhotoEntry>>(json, type).orEmpty()
                        .map { it.withCurrentWebDavUrl(config) }
                }.getOrDefault(emptyList())
            }
            .orEmpty()

        val remoteFromFolders = if (remoteFromJson.isEmpty()) {
            rebuildPhotoIndexFromRemoteMedia(config)
        } else {
            emptyList()
        }
        val remotePhotos = remoteFromJson + remoteFromFolders
        if (remotePhotos.isEmpty()) return

        val merged = (remotePhotos + _photos.value)
            .distinctBy { it.stableMediaKey(config) }
            .sortedWith(
                compareByDescending<PhotoEntry> { runCatching { LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() ?: LocalDate.MIN }
                    .thenByDescending { it.id }
            )

        _photos.value = merged
        upsertPhotoIndex(merged.map { it.toPhotoEntity(uploadStatus = "SYNCED") })
        saveLocalDataSnapshot()
        refreshSyncStatus("已只读合并 NAS 媒体索引 ${remotePhotos.size} 条，本地改动仍待处理", isSyncing = false)
    }

    private suspend fun loadFeedingFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/feeding.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<FeedingRecord>>() {}.type
                    val list: List<FeedingRecord> = gson.fromJson(json, type)
                    if (list.isNotEmpty()) {
                        _feedingRecords.value = list.map { it.withDefaultBabyId(activeBabyId) }
                        android.util.Log.d("DataManager", "Loaded ${list.size} feeding records from WebDAV")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse feeding.json", e)
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "feeding.json unavailable, keeping local snapshot")
            }
        )
    }

    private suspend fun loadVaccinesFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/vaccine_statuses.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<VaccineStatus>>() {}.type
                    val list: List<VaccineStatus> = gson.fromJson(json, type)
                    if (list.isNotEmpty()) {
                        _vaccineStatuses.value = list.map { it.withDefaultBabyId(activeBabyId) }
                        android.util.Log.d("DataManager", "Loaded ${list.size} vaccine statuses from WebDAV")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse vaccine_statuses.json", e)
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "vaccine_statuses.json unavailable, keeping local snapshot")
            }
        )
    }

    private suspend fun loadRemindersFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/reminders.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<Reminder>>() {}.type
                    val list: List<Reminder> = gson.fromJson(json, type) ?: emptyList()
                    if (list.isNotEmpty()) {
                        _reminders.value = list.map { it.withDefaultBabyId(activeBabyId) }
                        android.util.Log.d("DataManager", "Loaded ${list.size} reminders from WebDAV")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse reminders.json", e)
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "reminders.json unavailable, keeping local snapshot")
            }
        )
    }

    private suspend fun reconcilePhotosWithRemote(config: WebDavManager.WebDavConfig) {
        try {
            val mediaPath = mediaRootPath(config)

            val photoFiles = WebDavManager.listDirectory(config, "$mediaPath/photos").getOrNull() ?: emptySet()
            val videoFiles = WebDavManager.listDirectory(config, "$mediaPath/videos").getOrNull() ?: emptySet()

            val allRemoteFiles = photoFiles.map { "photos/$it" }.toSet() + videoFiles.map { "videos/$it" }.toSet()
            val baseUrl = config.url.trimEnd('/')
            val mediaPrefix = mediaPath.trimStart('/') + "/"
            val context = appContext

            val currentPhotos = _photos.value
            val orphaned = mutableListOf<PhotoEntry>()

            for (photo in currentPhotos) {
                if (photo.url.startsWith(baseUrl, ignoreCase = true)) {
                    // NAS photo — check against directory listing, or fall back to HEAD
                    val urlPath = photo.url.substring(baseUrl.length).trimStart('/')
                    val idx = urlPath.indexOf(mediaPrefix)
                    if (idx < 0) continue
                    val relativePath = urlPath.substring(idx + mediaPrefix.length)

                    val exists = if (allRemoteFiles.isNotEmpty()) {
                        relativePath in allRemoteFiles
                    } else {
                        // PROPFIND unavailable — fall back to individual HEAD check
                        val remotePath = "/$mediaPath/$relativePath".replace("//", "/")
                        WebDavManager.fileExists(config, remotePath)
                    }
                    if (!exists) {
                        orphaned.add(photo)
                    }
                } else if (context != null && photo.url.isNotBlank()) {
                    // Local photo — check if the source file still readable
                    if (!localFileExists(context, photo.url)) {
                        orphaned.add(photo)
                    }
                }
            }

            if (orphaned.isNotEmpty()) {
                android.util.Log.i("DataManager", "Reconciliation: removing ${orphaned.size} orphaned photo entries")
                _photos.value = currentPhotos.filter { it !in orphaned }.toMutableList()
                orphaned.forEach { photo ->
                    database?.photoDao()?.deleteById(photo.id)
                }
                savePhotos()
                refreshSyncStatus("已清理 ${orphaned.size} 条失效媒体记录", isSyncing = false)
            }
        } catch (e: Exception) {
            android.util.Log.w("DataManager", "Photo reconciliation failed: ${e.message}")
        }
    }

    private suspend fun reconcilePhotosWithRemoteSafely(config: WebDavManager.WebDavConfig) {
        try {
            val mediaPath = mediaRootPath(config)
            val photoFiles = WebDavManager.listDirectory(config, "$mediaPath/photos").getOrNull() ?: emptySet()
            val videoFiles = WebDavManager.listDirectory(config, "$mediaPath/videos").getOrNull() ?: emptySet()
            val allRemoteFiles = photoFiles.map { "photos/$it" }.toSet() + videoFiles.map { "videos/$it" }.toSet()
            val mediaPrefix = mediaPath.trimStart('/') + "/"
            val context = appContext
            val currentPhotos = _photos.value
            val orphaned = mutableListOf<PhotoEntry>()

            for (photo in currentPhotos) {
                val remotePath = photo.remotePath?.takeIf { it.isNotBlank() }
                    ?: remotePathFromStoredReference(photo.url, config)
                if (remotePath != null) {
                    // Compare NAS media by stable path so LAN/public WebDAV host changes do not hide media.
                    val normalizedRemotePath = remotePath.trimStart('/')
                    val idx = normalizedRemotePath.indexOf(mediaPrefix, ignoreCase = true)
                    if (idx < 0) continue
                    val relativePath = normalizedRemotePath.substring(idx + mediaPrefix.length)
                    val exists = when {
                        allRemoteFiles.isNotEmpty() -> relativePath in allRemoteFiles
                        isCurrentWebDavUrl(photo.url, config) -> WebDavManager.fileExists(config, remotePath)
                        else -> true
                    }
                    if (!exists) orphaned.add(photo)
                } else if (isHttpUrl(photo.url)) {
                    android.util.Log.i("DataManager", "Preserving remote media with unknown WebDAV path: ${photo.id}")
                } else if (context != null && photo.url.isNotBlank() && !localFileExists(context, photo.url)) {
                    orphaned.add(photo)
                }
            }

            if (orphaned.isNotEmpty()) {
                android.util.Log.i("DataManager", "Reconciliation: removing ${orphaned.size} orphaned photo entries")
                _photos.value = currentPhotos.filter { it !in orphaned }.toMutableList()
                orphaned.forEach { photo ->
                    database?.photoDao()?.deleteById(photo.id)
                }
                savePhotos()
                refreshSyncStatus("已清理 ${orphaned.size} 条失效媒体记录", isSyncing = false)
            }
        } catch (e: Exception) {
            android.util.Log.w("DataManager", "Photo reconciliation failed: ${e.message}")
        }
    }

    private suspend fun rebuildPhotoIndexFromRemoteMedia(config: WebDavManager.WebDavConfig): List<PhotoEntry> {
        val mediaPath = mediaRootPath(config)
        val photoFiles = WebDavManager.listDirectory(config, "$mediaPath/photos").getOrNull().orEmpty()
        val videoFiles = WebDavManager.listDirectory(config, "$mediaPath/videos").getOrNull().orEmpty()
        if (photoFiles.isEmpty() && videoFiles.isEmpty()) return emptyList()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val photos = photoFiles.map { fileName ->
            recoveredRemoteMediaEntry(config, "$mediaPath/photos/$fileName", fileName, today, isVideo = false)
        }
        val videos = videoFiles.map { fileName ->
            recoveredRemoteMediaEntry(config, "$mediaPath/videos/$fileName", fileName, today, isVideo = true)
        }
        return (photos + videos).sortedByDescending { it.date }
    }

    private fun recoveredRemoteMediaEntry(
        config: WebDavManager.WebDavConfig,
        remotePath: String,
        fileName: String,
        date: String,
        isVideo: Boolean
    ): PhotoEntry {
        val normalizedRemotePath = remotePath.ensureLeadingSlash().replace(Regex("/{2,}"), "/")
        val url = "${config.url.trimEnd('/')}/${normalizedRemotePath.trimStart('/')}"
        val stableId = UUID.nameUUIDFromBytes(normalizedRemotePath.toByteArray()).toString()
        val mediaKind = if (isVideo) "video" else "photo"
        return PhotoEntry(
            id = "remote-$stableId",
            babyId = activeBabyId,
            url = url,
            caption = fileName.substringBeforeLast('.', fileName),
            date = date,
            tags = if (isVideo) listOf("视频") else emptyList(),
            mediaType = mediaKind,
            remotePath = normalizedRemotePath,
            remoteUrl = url
        )
    }

    private suspend fun restorePhotosFromLocalIndexIfEmpty(): List<PhotoEntry> {
        if (_photos.value.isNotEmpty()) return emptyList()
        val recovered = loadPhotoEntriesFromLocalIndex()
        if (recovered.isEmpty()) return emptyList()
        _photos.value = recovered.toMutableList()
        saveLocalDataSnapshot()
        refreshSyncStatus("已从本地媒体索引恢复 ${recovered.size} 条媒体记录", isSyncing = false)
        return recovered
    }

    private suspend fun loadPhotoEntriesFromLocalIndex(): List<PhotoEntry> {
        return database?.photoDao()?.getAllOnce()
            ?.map { it.toPhotoEntry().withDefaultBabyId(activeBabyId) }
            ?.distinctBy { it.id }
            .orEmpty()
    }

    private fun localFileExists(context: Context, url: String): Boolean {
        return try {
            when {
                url.startsWith("content://") -> {
                    context.contentResolver.openInputStream(android.net.Uri.parse(url))?.use { stream ->
                        // Actually read a byte to verify the data is accessible
                        stream.read() >= 0
                    } ?: false
                }
                url.startsWith("file://") -> {
                    File(java.net.URI(url)).exists()
                }
                else -> File(url).exists()
            }
        } catch (_: Exception) {
            false
        }
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
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/settings.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val map: Map<String, String> = gson.fromJson(json, type) ?: emptyMap()
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
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/ai_profiles.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<AIProfile>>() {}.type
                    val remoteProfiles: List<AIProfile> = gson.fromJson(json, type)
                    val localSecrets = _aiProfiles.value.associateBy { it.id }
                    _aiProfiles.value = remoteProfiles.map { remote ->
                        val local = localSecrets[remote.id]
                        if (local != null) remote.copy(apiKey = local.apiKey) else remote
                    }
                    android.util.Log.d("DataManager", "Loaded ${remoteProfiles.size} AI profiles from WebDAV")
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse ai_profiles.json", e)
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "ai_profiles.json not found, keeping local AI profiles")
            }
        )
    }

    private suspend fun loadChatMessagesFromWebDav(config: WebDavManager.WebDavConfig) {
        val result = WebDavManager.readJson(config, "${dataRootPath(config)}/chat_messages.json")
        result.fold(
            onSuccess = { json ->
                try {
                    val type = object : TypeToken<List<ChatMessage>>() {}.type
                    val remoteMessages: List<ChatMessage> = gson.fromJson(json, type) ?: emptyList()
                    if (remoteMessages.isNotEmpty()) {
                        _chatMessages.value = remoteMessages
                        android.util.Log.d("DataManager", "Loaded ${remoteMessages.size} chat messages from WebDAV")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to parse chat_messages.json", e)
                }
            },
            onFailure = {
                android.util.Log.i("DataManager", "chat_messages.json not found, keeping local chat history")
            }
        )
    }

    private fun applySettingsMap(map: Map<String, String>) {

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
        // One-time migration for older settings.json files that already contain secrets.
        // New saves no longer write these keys back to WebDAV.
        map["ai_config"]?.takeIf { _aiConfig.value.apiKey.isBlank() }?.let {
            try {
                _aiConfig.value = gson.fromJson(it, AIConfig::class.java) ?: AIConfig()
                saveLocalAISecrets()
            } catch (_: Exception) {}
        }
        map["webdav_config"]?.takeIf { _webDavConfig.value == null && _cloudStorageConfig.value.host.isBlank() }?.let {
            try {
                _webDavConfig.value = gson.fromJson(it, WebDavManager.WebDavConfig::class.java)
                saveLocalStorageConfig()
            } catch (_: Exception) {}
        }
        map["cloud_storage_config"]?.takeIf { _cloudStorageConfig.value.host.isBlank() }?.let {
            try {
                _cloudStorageConfig.value = gson.fromJson(it, CloudStorageConfig::class.java)
                saveLocalStorageConfig()
            } catch (_: Exception) {}
        }
    }

    private fun buildSettingsMap(): Map<String, String> {

        return mapOf(
            "theme_mode" to _themeMode.value.name,
            "active_baby_id" to _activeBaby.value.id,
            "custom_categories" to gson.toJson(_customCategories.value)
        )
    }

    private fun remoteSafePhotos(): List<PhotoEntry> {
        val config = getWebDavConfigOrNull()
        return _photos.value.map { photo ->
            val resolvedRemotePath = config?.let {
                photo.remotePath?.takeIf { path -> path.isNotBlank() }
                    ?: remotePathFromStoredReference(photo.url, it)
                    ?: photo.remoteUrl?.let { remoteUrl -> remotePathFromStoredReference(remoteUrl, it) }
            }
            val mediaKind = photo.resolvedMediaType()
            val stableUrl = resolvedRemotePath ?: photo.url
            photo.copy(
                url = stableUrl,
                thumbnailPath = null,
                mediaType = mediaKind,
                remotePath = resolvedRemotePath,
                remoteUrl = photo.remoteUrl ?: photo.url.takeIf { isHttpUrl(it) },
                tags = photo.normalizedTags(mediaKind)
            )
        }
    }

    // --- Save helpers (async, serialized via Mutex) ---

    private fun saveBabies() {
        saveLocalDataSnapshot()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(_babies.value)
                    val result = writeDataJsonWithConflictGuard(config, "babies.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    else refreshSyncStatus("宝宝资料已同步到 NAS", isSyncing = false)
                    android.util.Log.d("DataManager", "Saved ${_babies.value.size} babies to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save babies", e)
                }
            }
        }
    }

    private fun saveTimeline() {
        saveLocalDataSnapshot()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(_timeline.value)
                    val result = writeDataJsonWithConflictGuard(config, "timeline.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    else refreshSyncStatus("时间线已同步到 NAS", isSyncing = false)
                    android.util.Log.d("DataManager", "Saved ${_timeline.value.size} timeline records to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save timeline", e)
                }
            }
        }
    }

    private fun savePhotos() {
        saveLocalDataSnapshot()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(remoteSafePhotos())
                    val result = writeDataJsonWithConflictGuard(config, "photos.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    else refreshSyncStatus("媒体索引已同步到 NAS", isSyncing = false)
                    android.util.Log.d("DataManager", "Saved ${_photos.value.size} photos to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save photos", e)
                }
            }
        }
    }

    private fun saveSettings() {
        saveLocalDataSnapshot()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(buildSettingsMap())
                    val result = writeDataJsonWithConflictGuard(config, "settings.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    else refreshSyncStatus("设置已同步到 NAS", isSyncing = false)
                    android.util.Log.d("DataManager", "Saved settings to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save settings", e)
                }
            }
        }
    }

    private fun saveAiProfiles() {
        saveLocalAISecrets()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(_aiProfiles.value.map { it.copy(apiKey = "") })
                    val result = writeDataJsonWithConflictGuard(config, "ai_profiles.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    else refreshSyncStatus("AI 配置索引已同步到 NAS", isSyncing = false)
                    android.util.Log.d("DataManager", "Saved ${_aiProfiles.value.size} AI profiles to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save AI profiles", e)
                }
            }
        }
    }

    private fun saveChatMessages() {
        saveLocalDataSnapshot()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(_chatMessages.value)
                    val result = writeDataJsonWithConflictGuard(config, "chat_messages.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    else refreshSyncStatus("AI 对话历史已同步到 NAS", isSyncing = false)
                    android.util.Log.d("DataManager", "Saved ${_chatMessages.value.size} chat messages to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save chat messages", e)
                }
            }
        }
    }

    private fun saveFeeding() {
        saveLocalDataSnapshot()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(_feedingRecords.value)
                    val result = writeDataJsonWithConflictGuard(config, "feeding.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    android.util.Log.d("DataManager", "Saved ${_feedingRecords.value.size} feeding records to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save feeding records", e)
                }
            }
        }
    }

    private fun saveVaccines() {
        saveLocalDataSnapshot()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(_vaccineStatuses.value)
                    val result = writeDataJsonWithConflictGuard(config, "vaccine_statuses.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    android.util.Log.d("DataManager", "Saved ${_vaccineStatuses.value.size} vaccine statuses to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save vaccine statuses", e)
                }
            }
        }
    }

    private fun saveReminders() {
        saveLocalDataSnapshot()
        appScope.launch {
            writeMutex.withLock {
                try {
                    val config = getWebDavConfigOrNull() ?: run {
                        markUnsyncedLocalChanges()
                        return@withLock
                    }
                    val json = gson.toJson(_reminders.value)
                    val result = writeDataJsonWithConflictGuard(config, "reminders.json", json)
                    if (!result) markUnsyncedLocalChanges()
                    android.util.Log.d("DataManager", "Saved ${_reminders.value.size} reminders to WebDAV, result: $result")
                } catch (e: Exception) {
                    markUnsyncedLocalChanges()
                    android.util.Log.e("DataManager", "Failed to save reminders", e)
                }
            }
        }
    }

    // --- Cloud Storage Config ---
    fun saveCloudStorageConfig(config: CloudStorageConfig) {
        _cloudStorageConfig.value = config
        _webDavConfig.value = null
        saveLocalStorageConfig()
        reconnectAndSync()
    }

    private fun reconnectAndSync() {
        enqueueBackgroundSync()
        appScope.launch {
            initWebDavData()
        }
    }

    fun retrySyncNow() {
        enqueueBackgroundSync()
        reconnectAndSync()
    }

    suspend fun runBackgroundSync(context: Context): Boolean = withContext(Dispatchers.IO) {
        ensureInitializedForWorker(context)
        val config = getWebDavConfigOrNull() ?: run {
            refreshSyncStatus("NAS 未配置，后台同步跳过", isSyncing = false)
            return@withContext true
        }
        refreshSyncStatus("后台同步正在运行...", isSyncing = true)
        try {
            if (!verifyNasConnection(config)) {
                return@withContext false
            }
            WebDavManager.createDirectoryChain(config, dataRootPath(config))
            WebDavManager.createDirectoryChain(config, "${mediaRootPath(config)}/photos")
            WebDavManager.createDirectoryChain(config, "${mediaRootPath(config)}/videos")
            if (hasUnsyncedLocalChanges() && !flushLocalSnapshotToWebDav(config)) {
                mergeRemoteMediaIndexIntoLocal(config)
                return@withContext false
            }
            uploadPendingMedia(config)
            reconcilePhotosWithRemoteSafely(config)
            refreshSyncStatus("后台同步完成", isSyncing = false)
            !hasUnsyncedLocalChanges() && (_syncStatus.value.pendingMediaCount == 0)
        } catch (e: Exception) {
            android.util.Log.e("DataManager", "Background NAS sync failed", e)
            markUnsyncedLocalChanges()
            refreshSyncStatus("后台同步失败，等待自动重试", isSyncing = false)
            false
        }
    }

    private fun ensureInitializedForWorker(context: Context) {
        if (appContext != null && database != null) return
        appContext = context.applicationContext
        database = AppDatabase.getInstance(context.applicationContext)
        loadLocalStorageConfig()
        loadLocalDataSnapshot()
    }

    private fun scheduleReminderNotification(reminder: Reminder) {
        val context = appContext ?: return
        if (!reminder.notify || reminder.isCompleted) {
            cancelReminderNotification(reminder.id)
            return
        }
        val delayMs = maxOf(0L, reminder.dueAt - System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderNotificationWorker.KEY_REMINDER_ID to reminder.id,
                    ReminderNotificationWorker.KEY_TITLE to reminder.title,
                    ReminderNotificationWorker.KEY_NOTES to reminder.notes,
                    ReminderNotificationWorker.KEY_DUE_AT to reminder.dueAt
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$REMINDER_WORK_PREFIX${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelReminderNotification(reminderId: String) {
        val context = appContext ?: return
        WorkManager.getInstance(context).cancelUniqueWork("$REMINDER_WORK_PREFIX$reminderId")
    }

    private fun rescheduleReminderNotifications() {
        _reminders.value.forEach { scheduleReminderNotification(it) }
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
        val deletedPhotos = _photos.value.filter { isStrictlyForBaby(it.babyId, babyId) }
        _babies.value = _babies.value.filter { it.id != babyId }.toMutableList()
        _timeline.value = _timeline.value.filter { !isStrictlyForBaby(it.babyId, babyId) }.toMutableList()
        _photos.value = _photos.value.filter { !isStrictlyForBaby(it.babyId, babyId) }.toMutableList()
        _feedingRecords.value = _feedingRecords.value.filter { !isStrictlyForBaby(it.babyId, babyId) }
        _vaccineStatuses.value = _vaccineStatuses.value.filter { !isStrictlyForBaby(it.babyId, babyId) }
        _reminders.value
            .filter { isStrictlyForBaby(it.babyId, babyId) }
            .forEach { cancelReminderNotification(it.id) }
        _reminders.value = _reminders.value.filter { !isStrictlyForBaby(it.babyId, babyId) }
        saveBabies()
        saveTimeline()
        savePhotos()
        saveFeeding()
        saveVaccines()
        saveReminders()
        appScope.launch {
            cleanupDeletedMedia(deletedPhotos)
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
        val r = record.withDefaultBabyId(_activeBaby.value.id)
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
                tags = listOf(record.category),
                mediaType = "photo"
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
                tags = listOf(record.category, "视频"),
                mediaType = "video"
            ))
        }
        if (newPhotos.isNotEmpty()) {
            // Deduplicate: don't add if URL already exists in photos
            val existingUrls = _photos.value.map { it.url }.toSet()
            val deduped = newPhotos.filter { it.url !in existingUrls }
            if (deduped.isNotEmpty()) {
                deduped.forEach { addPhoto(it) }
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
        val p = photo.withDefaultBabyId(_activeBaby.value.id)
        _photos.value = (listOf(p) + _photos.value).toMutableList()
        saveLocalDataSnapshot()
        appScope.launch {
            val localFile = File(p.url)
            database?.photoDao()?.insert(
                p.toPhotoEntity(
                    localOriginalPath = p.url.takeIf { localFile.exists() },
                    localThumbPath = p.thumbnailPath,
                    uploadStatus = if (localFile.exists()) "PENDING_UPLOAD" else "SYNCED",
                    sha256 = localFile.takeIf { it.exists() }?.sha256()
                )
            )
            if (localFile.exists()) {
                val config = getWebDavConfigOrNull()
                if (config == null) {
                    markUnsyncedLocalChanges()
                    _mediaUploadEvents.emit(MediaUploadEvent(p.id, false, "NAS 未配置，媒体已保留在本机，配置后会自动补传"))
                    return@launch
                }
                uploadLocalMedia(p, localFile, config)
            } else {
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
            val mediaKind = photo.resolvedMediaType()
            val mediaFolder = if (mediaKind == "video") "videos" else "photos"
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
                val remoteUrl = "$baseUrl/$remotePath"
                val hash = localFile.sha256()
                val uploadedPhoto = photo.copy(
                    url = remoteUrl,
                    remoteUrl = remoteUrl,
                    remotePath = remotePath,
                    mediaType = mediaKind,
                    sha256 = hash,
                    updatedAt = System.currentTimeMillis()
                )
                val currentPhotos = _photos.value
                val hadExistingPhoto = currentPhotos.any { it.id == photo.id }
                _photos.value = if (hadExistingPhoto) {
                    currentPhotos.map { if (it.id == photo.id) uploadedPhoto else it }.toMutableList()
                } else {
                    (listOf(uploadedPhoto) + currentPhotos).toMutableList()
                }
                if (replaceTimelineMediaUrl(photo.url, remoteUrl)) {
                    saveTimeline()
                }
                database?.photoDao()?.insert(
                    uploadedPhoto.toPhotoEntity(
                        remoteUrl = remoteUrl,
                        remotePath = remotePath,
                        localOriginalPath = localFile.absolutePath,
                        localThumbPath = photo.thumbnailPath,
                        uploadStatus = "SYNCED",
                        sha256 = hash
                    )
                )
                savePhotos()
                val successMessage = "已上传并验证：$remoteName"
                _mediaUploadEvents.emit(MediaUploadEvent(photo.id, true, successMessage, remoteUrl))
                refreshSyncStatus("媒体已上传并验证：$remoteName", isSyncing = false)
                android.util.Log.d("DataManager", "Media upload to WebDAV succeeded and verified: $remoteName")
            }
        } catch (e: Exception) {
            android.util.Log.e("DataManager", "Failed to upload media to WebDAV", e)
            markMediaUploadFailed(photo, localFile, "NAS 上传失败：${e.message ?: "未知错误"}")
        }
    }

    private fun replaceTimelineMediaUrl(localUrl: String, remoteUrl: String): Boolean {
        if (localUrl == remoteUrl) return false
        var changed = false
        _timeline.value = _timeline.value.map { record ->
            var recordChanged = false
            val photos = record.photos.map {
                if (it == localUrl) {
                    changed = true
                    recordChanged = true
                    remoteUrl
                } else {
                    it
                }
            }
            val videos = record.videos.map {
                if (it == localUrl) {
                    changed = true
                    recordChanged = true
                    remoteUrl
                } else {
                    it
                }
            }
            if (recordChanged) {
                record.copy(photos = photos, videos = videos)
            } else {
                record
            }
        }.toMutableList()
        return changed
    }

    private suspend fun markMediaUploadFailed(photo: PhotoEntry, localFile: File, message: String) {
        markUnsyncedLocalChanges()
        database?.photoDao()?.insert(
            photo.toPhotoEntity(
                localOriginalPath = localFile.absolutePath,
                localThumbPath = photo.thumbnailPath,
                uploadStatus = "UPLOAD_FAILED",
                sha256 = localFile.takeIf { it.exists() }?.sha256()
            )
        )
        _mediaUploadEvents.emit(MediaUploadEvent(photo.id, false, message))
        refreshSyncStatus(message, isSyncing = false)
        android.util.Log.e("DataManager", message)
    }

    private fun mediaRootPath(config: WebDavManager.WebDavConfig): String {
        val base = config.dataPath.trim().trimEnd('/').ifBlank { DATA_PATH }
        return "$base/media"
    }

    private fun dataRootPath(config: WebDavManager.WebDavConfig): String {
        return config.dataPath.trim().trimEnd('/').ifBlank { DATA_PATH }
    }

    fun deletePhoto(id: String) {
        val deletedPhotos = _photos.value.filter { it.id == id }
        _photos.value = _photos.value.filter { it.id != id }.toMutableList()
        appScope.launch {
            cleanupDeletedMedia(deletedPhotos)
        }
        savePhotos()
    }

    fun deletePhotos(ids: Set<String>) {
        val deletedPhotos = _photos.value.filter { it.id in ids }
        _photos.value = _photos.value.filter { it.id !in ids }.toMutableList()
        appScope.launch {
            cleanupDeletedMedia(deletedPhotos)
        }
        savePhotos()
    }

    private suspend fun cleanupDeletedMedia(photos: List<PhotoEntry>) {
        if (photos.isEmpty()) return
        val config = getWebDavConfigOrNull()
        var remoteDeleteFailed = false
        photos.forEach { photo ->
            val entity = database?.photoDao()?.getById(photo.id)
            val remotePath = entity?.remotePath
                ?: photo.remotePath
                ?: config?.let { remotePathFromStoredReference(photo.url, it) }
            if (config != null && !remotePath.isNullOrBlank()) {
                val deleted = WebDavManager.deleteFile(config, remotePath).getOrDefault(false)
                remoteDeleteFailed = remoteDeleteFailed || !deleted
            }
            deleteLocalFileIfOwned(entity?.localOriginalPath)
            deleteLocalFileIfOwned(entity?.localThumbPath)
            deleteLocalFileIfOwned(entity?.localPreviewPath)
            deleteLocalFileIfOwned(photo.url)
            database?.photoDao()?.deleteById(photo.id)
        }
        if (remoteDeleteFailed) {
            markUnsyncedLocalChanges()
            refreshSyncStatus("部分远端媒体删除失败，已先移除本地索引", isSyncing = false)
        } else {
            refreshSyncStatus("媒体已删除", isSyncing = false)
        }
    }

    private fun remotePathFromUrl(url: String, config: WebDavManager.WebDavConfig): String? {
        val baseUrl = config.url.trimEnd('/')
        if (!isHttpUrl(url)) return null
        if (url.startsWith(baseUrl, ignoreCase = true)) {
            return "/" + url.substring(baseUrl.length).trimStart('/')
        }

        val normalizedPath = runCatching {
            java.net.URI(url).rawPath.orEmpty()
        }.getOrDefault("")
            .let { path -> runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrDefault(path) }
            .replace(Regex("/{2,}"), "/")

        if (normalizedPath.isBlank()) return null

        val mediaRoot = mediaRootPath(config).trim('/').replace(Regex("/{2,}"), "/")
        val configuredNeedle = "/$mediaRoot/"
        val configuredIndex = normalizedPath.indexOf(configuredNeedle, ignoreCase = true)
        if (configuredIndex >= 0) {
            return normalizedPath.substring(configuredIndex).ensureLeadingSlash()
        }

        val genericIndex = normalizedPath.indexOf("/media/", ignoreCase = true)
        if (genericIndex >= 0) {
            val relativeMediaPath = normalizedPath.substring(genericIndex + "/media/".length)
            return "/$mediaRoot/$relativeMediaPath".replace(Regex("/{2,}"), "/")
        }

        return null
    }

    private fun isCurrentWebDavUrl(url: String, config: WebDavManager.WebDavConfig): Boolean {
        return isHttpUrl(url) && url.startsWith(config.url.trimEnd('/'), ignoreCase = true)
    }

    private fun remotePathFromStoredReference(reference: String, config: WebDavManager.WebDavConfig): String? {
        if (reference.isBlank()) return null
        if (isHttpUrl(reference)) return remotePathFromUrl(reference, config)

        val normalizedPath = reference.trim()
            .replace("\\", "/")
            .replace(Regex("/{2,}"), "/")
            .takeIf { it.startsWith("/") }
            ?: return null

        val mediaRoot = mediaRootPath(config).trim('/').replace(Regex("/{2,}"), "/")
        val configuredNeedle = "/$mediaRoot/"
        val configuredIndex = normalizedPath.indexOf(configuredNeedle, ignoreCase = true)
        if (configuredIndex >= 0) {
            return normalizedPath.substring(configuredIndex).ensureLeadingSlash()
        }

        val genericIndex = normalizedPath.indexOf("/media/", ignoreCase = true)
        if (genericIndex >= 0) {
            val relativeMediaPath = normalizedPath.substring(genericIndex + "/media/".length)
            return "/$mediaRoot/$relativeMediaPath".replace(Regex("/{2,}"), "/")
        }

        return null
    }

    private fun looksLikeVideoPath(path: String): Boolean {
        val cleanPath = path.substringBefore("?").substringBefore("#").lowercase()
        return cleanPath.endsWith(".mp4") ||
            cleanPath.endsWith(".webm") ||
            cleanPath.endsWith(".3gp") ||
            cleanPath.endsWith(".mkv") ||
            cleanPath.endsWith(".mov")
    }

    private fun isHttpUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
    }

    private fun String.ensureLeadingSlash(): String {
        return if (startsWith("/")) this else "/$this"
    }

    private fun webDavUrlForRemotePath(config: WebDavManager.WebDavConfig, remotePath: String): String {
        return "${config.url.trimEnd('/')}/${remotePath.trimStart('/')}"
    }

    private fun deleteLocalFileIfOwned(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val context = appContext ?: return
            val file = File(path)
            if (!file.exists()) return
            val filesRoot = context.filesDir.canonicalFile
            val target = file.canonicalFile
            if (target.path.startsWith(filesRoot.path)) {
                target.delete()
            }
        }.onFailure {
            android.util.Log.w("DataManager", "Failed to delete local media file: $path", it)
        }
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
        saveLocalAISecrets()
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
                    .post("""{"model":"${profile.model}","messages":[{"role":"user","content":"test"}],"max_tokens":1}"""
                        .toRequestBody("application/json".toMediaType()))
                    .build()
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    when (response.code) {
                        401 -> onResult(Result.failure(Exception("API Key 无效 (401 Unauthorized)")))
                        403 -> onResult(Result.failure(Exception("API 访问被拒绝 (403 Forbidden)")))
                        else -> {
                            if (response.isSuccessful) onResult(Result.success(true))
                            else onResult(Result.failure(Exception("HTTP ${response.code}")))
                        }
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
        saveChatMessages()
        if (msg.role == "user") {
            sendToAIStreaming(msg.content)
        }
    }

    private fun buildBabyContext(): String {
        val baby = _activeBaby.value
        if (baby.id.isEmpty()) return ""
        val age = getAgeInMonths(baby.birthDate)
        val ageDays = getAgeInDays(baby.birthDate)

        // Last 30 timeline records only — full list can exceed token limits
        val recentRecords = _timeline.value
            .filter { isForActiveBaby(it.babyId) }
            .sortedByDescending { it.date }
            .take(30)
            .joinToString("\n") {
                "- ${it.date} | ${it.category}: ${it.title}" +
                    if (it.description.isNotEmpty()) " - ${it.description}" else ""
            }

        // Vaccine statuses with schedule info
        val vaccineContext = buildVaccineContext(age)

        return buildString {
            append("当前宝宝信息：\n")
            append("- 名字：${baby.name}（${baby.nickname}）\n")
            append("- 性别：${if (baby.gender == "boy") "男孩" else "女孩"}\n")
            append("- 出生日期：${baby.birthDate}\n")
            append("- 月龄：${age}个月\n")
            append("- 日龄：${ageDays}天\n")
            if (recentRecords.isNotEmpty()) {
                append("\n最近成长记录（共${_timeline.value.size}条，显示最近30条）：\n")
                append(recentRecords)
            }
            if (vaccineContext.isNotEmpty()) {
                append("\n\n$vaccineContext")
            }
        }.trim()
    }

    private fun buildVaccineContext(ageMonths: Int): String {
        val statuses = _vaccineStatuses.value.filter { isForActiveBaby(it.babyId) }
        val done = statuses.filter { it.isDone }.map { it.vaccineId }.toSet()
        val upcoming = CHINA_VACCINE_SCHEDULE
            .filter { it.id !in done && it.scheduledAgeMonths >= ageMonths - 1 }
            .sortedBy { it.scheduledAgeMonths }
            .take(10)
        val doneItems = CHINA_VACCINE_SCHEDULE.filter { it.id in done }
        val overdue = CHINA_VACCINE_SCHEDULE
            .filter { it.id !in done && it.scheduledAgeMonths < ageMonths && it.scheduledAgeMonths >= 0 }
            .sortedBy { it.scheduledAgeMonths }

        if (done.isEmpty() && upcoming.isEmpty()) return ""

        return buildString {
            if (doneItems.isNotEmpty()) {
                append("已接种疫苗（${doneItems.size}种）：\n")
                doneItems.forEach { v ->
                    val s = statuses.find { it.vaccineId == v.id }
                    val date = s?.administeredDate ?: ""
                    append("- ${v.name}（第${v.doseNumber}剂）- ${v.diseasePrevented}${if (date.isNotEmpty()) " [接种日期: $date]" else ""}\n")
                }
                append("\n")
            }
            if (overdue.isNotEmpty()) {
                append("[!] 超期未接种（${overdue.size}种）：\n")
                overdue.forEach { v ->
                    append("- ${v.name}（第${v.doseNumber}剂）- ${v.diseasePrevented} - 应在${v.scheduledAgeMonths}月龄接种\n")
                }
                append("\n")
            }
            if (upcoming.isNotEmpty()) {
                append("即将需要接种：\n")
                upcoming.forEach { v ->
                    val inMonths = v.scheduledAgeMonths - ageMonths
                    val timing = if (inMonths <= 0) "现在或近期" else "${inMonths}个月后"
                    val tag = if (v.isRequired) "一类(免费)" else "二类(自费)"
                    append("- ${v.name}（第${v.doseNumber}剂）- ${v.diseasePrevented} - 应在${v.scheduledAgeMonths}月龄接种（$timing）- $tag\n")
                }
            }
        }.trim()
    }

    private fun sendToAIStreaming(userInput: String) {
        val profile = activeAIProfile
        if (profile == null) {
            _chatMessages.value = (_chatMessages.value + ChatMessage(
                id = "msg-${System.currentTimeMillis()}",
                role = "assistant",
                content = "AI 助手尚未激活，请在顶部下拉菜单中选择一个 AI Profile。",
                timestamp = System.currentTimeMillis()
            )).toMutableList()
            saveChatMessages()
            return
        }
        _isAIProcessing.value = true
        val babyContext = buildBabyContext()
        val systemPrompt = buildString {
            append(profile.systemPrompt.ifBlank { "你是一个专业的育儿助手。" })
            if (babyContext.isNotEmpty()) {
                append("\n\n$babyContext")
            }
        }

        val historyMessages = _chatMessages.value
            .filter { it.role in listOf("user", "assistant") }
            .dropLast(1)  // exclude the just-added user message
            .takeLast(AI_CONTEXT_MESSAGE_LIMIT)
            .map { mapOf("role" to it.role, "content" to it.content) }

        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt)
        ) + historyMessages + listOf(
            mapOf("role" to "user", "content" to userInput)
        )

        val requestBody = mapOf(
            "model" to profile.model,
            "messages" to messages,
            "temperature" to profile.temperature.toDouble(),
            "top_p" to profile.topP.toDouble(),
            "max_tokens" to profile.maxTokens,
            "stream" to true
        )

        val aiMsgId = "msg-${System.currentTimeMillis()}"
        _chatMessages.value = (_chatMessages.value + ChatMessage(
            id = aiMsgId, role = "assistant", content = "", timestamp = System.currentTimeMillis()
        )).toMutableList()
        saveLocalDataSnapshot()

        appScope.launch {
            try {
                val fullContent = StringBuilder()
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url("${profile.apiBaseUrl.trimEnd('/')}/v1/chat/completions")
                        .header("Authorization", "Bearer ${profile.apiKey}")
                        .header("Content-Type", "application/json")
                        .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        updateAiMessage(aiMsgId, "请求失败 (HTTP ${response.code})", persist = true)
                        return@launch
                    }

                    val body = response.body ?: run {
                        updateAiMessage(aiMsgId, "响应为空", persist = true)
                        return@launch
                    }

                    body.source().use { source ->
                        var lastUiUpdate = 0L
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: continue
                            if (!line.startsWith("data: ")) continue
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            if (data.isEmpty()) continue

                            try {
                                val chunk = gson.fromJson(data, StreamChunk::class.java)
                                val content = chunk.choices
                                    ?.firstOrNull()
                                    ?.delta
                                    ?.content
                                    ?: ""
                                if (content.isNotEmpty()) {
                                    fullContent.append(content)
                                    val now = System.currentTimeMillis()
                                    if (now - lastUiUpdate >= 120) {
                                        updateAiMessage(aiMsgId, fullContent.toString())
                                        lastUiUpdate = now
                                        yield()
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("DataManager", "SSE parse skipped: $data", e)
                            }
                        }
                        // Final update with complete content
                        updateAiMessage(aiMsgId, fullContent.toString(), persist = true)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "AI stream error", e)
                    updateAiMessage(aiMsgId, "连接失败: ${e.message}", persist = true)
                }
            } finally {
                _isAIProcessing.value = false
            }
        }
    }

    private fun updateAiMessage(id: String, content: String, persist: Boolean = false) {
        _chatMessages.value = _chatMessages.value.map {
            if (it.id == id) it.copy(content = content) else it
        }.toMutableList()
        if (persist) saveChatMessages()
    }

    fun clearMessages() {
        _chatMessages.value = emptyList()
        saveChatMessages()
    }

    // --- Theme ---
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        saveSettings()
    }

    // --- WebDAV ---
    fun saveWebDavConfig(config: WebDavManager.WebDavConfig) {
        _webDavConfig.value = config
        saveLocalStorageConfig()
        reconnectAndSync()
    }

    fun clearWebDavConfig() {
        _webDavConfig.value = null
        saveLocalStorageConfig()
    }

    fun testWebDavConnection(config: WebDavManager.WebDavConfig, onResult: (Result<WebDavManager.ConnectionTestResult>) -> Unit) {
        appScope.launch {
            val result = WebDavManager.testConnection(config)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    private fun exportCurrentDataToJson(): String {

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
                    "tags" to it.tags,
                    "mediaType" to it.mediaType,
                    "remotePath" to it.remotePath,
                    "remoteUrl" to it.remoteUrl,
                    "sha256" to it.sha256,
                    "createdAt" to it.createdAt,
                    "updatedAt" to it.updatedAt
                )
            },
            "feeding" to _feedingRecords.value.map {
                mapOf(
                    "id" to it.id, "babyId" to it.babyId, "timestamp" to it.timestamp,
                    "type" to it.type, "volumeMl" to it.volumeMl,
                    "durationMin" to it.durationMin, "notes" to it.notes
                )
            },
            "vaccineStatuses" to _vaccineStatuses.value.map {
                mapOf(
                    "vaccineId" to it.vaccineId, "babyId" to it.babyId,
                    "administeredDate" to it.administeredDate,
                    "batchNumber" to it.batchNumber, "notes" to it.notes
                )
            },
            "reminders" to _reminders.value.map {
                mapOf(
                    "id" to it.id,
                    "babyId" to it.babyId,
                    "title" to it.title,
                    "dueAt" to it.dueAt,
                    "category" to it.category,
                    "notes" to it.notes,
                    "notify" to it.notify,
                    "calendarSynced" to it.calendarSynced,
                    "completedAt" to it.completedAt,
                    "createdAt" to it.createdAt
                )
            },
            "chatMessages" to _chatMessages.value.map {
                mapOf(
                    "id" to it.id,
                    "role" to it.role,
                    "content" to it.content,
                    "timestamp" to it.timestamp
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
                val map = gson.fromJson(json, Map::class.java) ?: run {
                    withContext(Dispatchers.Main) { onResult(false) }
                    return@launch
                }

                var imported = false

                // Import babies
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
                    if (newBabies.isNotEmpty()) {
                        _babies.value = newBabies
                        _activeBaby.value = newBabies.first()
                        saveBabies()
                        imported = true
                    }
                }

                // Import timeline
                val timelineList = map["timeline"] as? List<*>
                if (timelineList != null) {
                    val records = timelineList.mapNotNull { item ->
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
                    }
                    if (records.isNotEmpty()) {
                        _timeline.value = records.map { it.withDefaultBabyId(activeBabyId) }
                        saveTimeline()
                        imported = true
                    }
                }

                // Import photos
                val photosList = map["photos"] as? List<*>
                if (photosList != null) {
                    val entries = photosList.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        PhotoEntry(
                            id = m["id"] as? String ?: "",
                            babyId = m["babyId"] as? String ?: "",
                            url = m["url"] as? String ?: "",
                            caption = m["caption"] as? String ?: "",
                            date = m["date"] as? String ?: "",
                            timelineRecordId = m["timelineRecordId"] as? String,
                            tags = (m["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            mediaType = m["mediaType"] as? String,
                            remotePath = m["remotePath"] as? String,
                            remoteUrl = m["remoteUrl"] as? String,
                            sha256 = m["sha256"] as? String,
                            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L
                        )
                    }
                    if (entries.isNotEmpty()) {
                        val normalized = entries.map { it.withDefaultBabyId(activeBabyId) }
                        _photos.value = normalized
                        upsertPhotoIndex(normalized.map { it.toPhotoEntity(uploadStatus = "SYNCED") })
                        savePhotos()
                        imported = true
                    }
                }

                val feedingList = map["feeding"] as? List<*>
                if (feedingList != null) {
                    val records = feedingList.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        FeedingRecord(
                            id = m["id"] as? String ?: "",
                            babyId = m["babyId"] as? String ?: _activeBaby.value.id,
                            timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L,
                            type = m["type"] as? String ?: "formula",
                            volumeMl = (m["volumeMl"] as? Number)?.toInt() ?: 0,
                            durationMin = (m["durationMin"] as? Number)?.toInt() ?: 0,
                            notes = m["notes"] as? String ?: ""
                        )
                    }
                    if (records.isNotEmpty()) {
                        _feedingRecords.value = records.map { it.withDefaultBabyId(activeBabyId) }
                        saveFeeding()
                        imported = true
                    }
                }

                val vaccineList = map["vaccineStatuses"] as? List<*>
                if (vaccineList != null) {
                    val statuses = vaccineList.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        VaccineStatus(
                            vaccineId = m["vaccineId"] as? String ?: return@mapNotNull null,
                            babyId = m["babyId"] as? String ?: _activeBaby.value.id,
                            administeredDate = m["administeredDate"] as? String,
                            batchNumber = m["batchNumber"] as? String ?: "",
                            notes = m["notes"] as? String ?: ""
                        )
                    }
                    if (statuses.isNotEmpty()) {
                        _vaccineStatuses.value = statuses.map { it.withDefaultBabyId(activeBabyId) }
                        saveVaccines()
                        imported = true
                    }
                }

                val reminderList = map["reminders"] as? List<*>
                if (reminderList != null) {
                    val importedReminders = reminderList.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        Reminder(
                            id = m["id"] as? String ?: return@mapNotNull null,
                            babyId = m["babyId"] as? String ?: _activeBaby.value.id,
                            title = m["title"] as? String ?: return@mapNotNull null,
                            dueAt = (m["dueAt"] as? Number)?.toLong() ?: return@mapNotNull null,
                            category = m["category"] as? String ?: REMINDER_CATEGORY_CHECKUP,
                            notes = m["notes"] as? String ?: "",
                            notify = m["notify"] as? Boolean ?: true,
                            calendarSynced = m["calendarSynced"] as? Boolean ?: false,
                            completedAt = (m["completedAt"] as? Number)?.toLong(),
                            createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    }
                    if (importedReminders.isNotEmpty()) {
                        _reminders.value = importedReminders.map { it.withDefaultBabyId(activeBabyId) }
                        rescheduleReminderNotifications()
                        saveReminders()
                        imported = true
                    }
                }

                // Import settings
                val settingsMap = map["settings"] as? Map<*, *>
                if (settingsMap != null) {
                    (settingsMap["active_baby_id"] as? String)?.let { id ->
                        _babies.value.find { it.id == id }?.let { _activeBaby.value = it }
                    }
                    (settingsMap["theme_mode"] as? String)?.let {
                        try { _themeMode.value = ThemeMode.valueOf(it) }
                        catch (_: Exception) {}
                    }
                    (settingsMap["custom_categories"] as? String)?.let { catsJson ->
                        try {
                            val type = object : TypeToken<List<CategoryConfig>>() {}.type
                            val cats: List<CategoryConfig> = gson.fromJson(catsJson, type)
                            _customCategories.value = cats
                        } catch (_: Exception) {}
                    }
                    saveSettings()
                    imported = true
                }

                val chatList = map["chatMessages"] as? List<*>
                if (chatList != null) {
                    val importedMessages = chatList.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        ChatMessage(
                            id = m["id"] as? String ?: "msg-${System.currentTimeMillis()}",
                            role = m["role"] as? String ?: return@mapNotNull null,
                            content = m["content"] as? String ?: "",
                            timestamp = (m["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    }
                    _chatMessages.value = importedMessages
                    saveChatMessages()
                    imported = true
                }

                saveLocalDataSnapshot()
                withContext(Dispatchers.Main) { onResult(imported) }
            } catch (e: Exception) {
                android.util.Log.e("DataManager", "importFromJson failed", e)
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun resetAllData(onComplete: () -> Unit) {
        appScope.launch {
            val remoteConfig = getWebDavConfigOrNull()
            _timeline.value = emptyList()
            _photos.value = emptyList()
            _babies.value = emptyList()
            _activeBaby.value = BabyInfo()
            _customCategories.value = emptyList()
            _aiConfig.value = AIConfig()
            _aiProfiles.value = emptyList()
            _chatMessages.value = emptyList()
            _feedingRecords.value = emptyList()
            _vaccineStatuses.value = emptyList()
            _reminders.value.forEach { cancelReminderNotification(it.id) }
            _reminders.value = emptyList()
            _themeMode.value = ThemeMode.SYSTEM
            _webDavConfig.value = null
            _cloudStorageConfig.value = CloudStorageConfig()
            saveLocalStorageConfig()
            saveLocalAISecrets()
            saveLocalDataSnapshot()
            database?.photoDao()?.deleteAll()
            if (remoteConfig != null) {
                try {
                    writeMutex.withLock {
                        val emptyListJson = gson.toJson(emptyList<Any>())
                        val dataPath = dataRootPath(remoteConfig)
                        val cleared = listOf(
                            WebDavManager.writeJson(remoteConfig, "$dataPath/babies.json", emptyListJson).getOrDefault(false),
                            WebDavManager.writeJson(remoteConfig, "$dataPath/timeline.json", emptyListJson).getOrDefault(false),
                            WebDavManager.writeJson(remoteConfig, "$dataPath/photos.json", emptyListJson).getOrDefault(false),
                            WebDavManager.writeJson(remoteConfig, "$dataPath/ai_profiles.json", emptyListJson).getOrDefault(false),
                            WebDavManager.writeJson(remoteConfig, "$dataPath/chat_messages.json", emptyListJson).getOrDefault(false),
                            WebDavManager.writeJson(remoteConfig, "$dataPath/settings.json", gson.toJson(buildSettingsMap())).getOrDefault(false),
                            WebDavManager.writeJson(remoteConfig, "$dataPath/feeding.json", emptyListJson).getOrDefault(false),
                            WebDavManager.writeJson(remoteConfig, "$dataPath/vaccine_statuses.json", emptyListJson).getOrDefault(false),
                            WebDavManager.writeJson(remoteConfig, "$dataPath/reminders.json", emptyListJson).getOrDefault(false)
                        ).all { it }
                        if (cleared) recordRemoteWrite(remoteConfig)
                        markUnsyncedLocalChanges(!cleared)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DataManager", "Failed to clear remote data", e)
                    markUnsyncedLocalChanges()
                }
            } else {
                markUnsyncedLocalChanges(false)
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    // --- Age calculations ---
    fun getAgeInMonths(birthDate: String): Int {
        if (birthDate.isBlank()) return 0
        val birth = try { java.time.LocalDate.parse(birthDate) } catch (e: Exception) { return 0 }
        val now = java.time.LocalDate.now()
        var months = (now.year - birth.year) * 12 + (now.monthValue - birth.monthValue)
        if (now.dayOfMonth < birth.dayOfMonth) months--
        return maxOf(0, months)
    }

    fun getAgeInDays(birthDate: String): Int {
        if (birthDate.isBlank()) return 0
        val birth = try { java.time.LocalDate.parse(birthDate) } catch (e: Exception) { return 0 }
        return maxOf(0, java.time.temporal.ChronoUnit.DAYS.between(birth, java.time.LocalDate.now()).toInt())
    }

    fun getRecentRecords(count: Int = 5): List<TimelineRecord> {
        return _timeline.value.filter { isForActiveBaby(it.babyId) }.sortedByDescending { it.date }.take(count)
    }

    fun getMilestoneCount(): Int {
        return _timeline.value.count { isForActiveBaby(it.babyId) && it.category == "milestone" }
    }

    // --- Feeding CRUD ---
    fun addFeedingRecord(record: FeedingRecord) {
        val scopedRecord = record.withDefaultBabyId(activeBabyId)
        _feedingRecords.value = (_feedingRecords.value + scopedRecord).sortedByDescending { it.timestamp }
        saveFeeding()
    }

    fun deleteFeedingRecord(id: String) {
        _feedingRecords.value = _feedingRecords.value.filter { it.id != id }
        saveFeeding()
    }

    fun getTodayFeedingSummary(): Pair<Int, Int> {
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000
        val todayRecords = _feedingRecords.value.filter { isForActiveBaby(it.babyId) && it.timestamp >= todayStart }
        val formulaMl = todayRecords.filter { it.type == "formula" || it.type == "water" }.sumOf { it.volumeMl }
        val breastCount = todayRecords.count { it.type == "breast" }
        val totalCount = todayRecords.size
        return totalCount to formulaMl
    }

    // --- Reminder CRUD ---
    fun upsertReminder(reminder: Reminder) {
        val scopedReminder = reminder.withDefaultBabyId(activeBabyId)
        val existing = _reminders.value.toMutableList()
        val index = existing.indexOfFirst { it.id == scopedReminder.id }
        if (index >= 0) existing[index] = scopedReminder else existing.add(scopedReminder)
        _reminders.value = existing.sortedWith(compareBy<Reminder> { it.isCompleted }.thenBy { it.dueAt })
        scheduleReminderNotification(scopedReminder)
        saveReminders()
    }

    fun completeReminder(id: String) {
        val completedAt = System.currentTimeMillis()
        _reminders.value = _reminders.value.map { reminder ->
            if (reminder.id == id) reminder.copy(completedAt = completedAt) else reminder
        }
        cancelReminderNotification(id)
        saveReminders()
    }

    fun reopenReminder(id: String) {
        val reminder = _reminders.value.firstOrNull { it.id == id } ?: return
        val reopened = reminder.copy(completedAt = null)
        _reminders.value = _reminders.value.map { if (it.id == id) reopened else it }
        scheduleReminderNotification(reopened)
        saveReminders()
    }

    fun deleteReminder(id: String) {
        _reminders.value = _reminders.value.filter { it.id != id }
        cancelReminderNotification(id)
        saveReminders()
    }

    // --- Vaccine CRUD ---
    fun markVaccineDone(vaccineId: String, date: String, batch: String = "", notes: String = "") {
        val newStatus = VaccineStatus(
            vaccineId = vaccineId,
            babyId = activeBabyId,
            administeredDate = date,
            batchNumber = batch,
            notes = notes
        )
        val existing = _vaccineStatuses.value.toMutableList()
        val idx = existing.indexOfFirst { it.vaccineId == vaccineId && isForActiveBaby(it.babyId) }
        if (idx >= 0) existing[idx] = newStatus else existing.add(newStatus)
        _vaccineStatuses.value = existing
        saveVaccines()

        // Also create a timeline record for this vaccine
        val vaccineItem = CHINA_VACCINE_SCHEDULE.find { it.id == vaccineId }
        if (vaccineItem != null) {
            // Remove any existing vax timeline record for this vaccine (so it's replace, not duplicate)
            val existingVaxRecords = _timeline.value.filter { it.id.startsWith("vax-${vaccineId}-") && isForActiveBaby(it.babyId) }
            var timelineValue = _timeline.value.toMutableList()
            if (existingVaxRecords.isNotEmpty()) {
                timelineValue = timelineValue.filter { !(it.id.startsWith("vax-${vaccineId}-") && isForActiveBaby(it.babyId)) }.toMutableList()
            }
            val descParts = mutableListOf<String>()
            if (vaccineItem.diseasePrevented.isNotEmpty()) descParts.add("预防：${vaccineItem.diseasePrevented}")
            if (batch.isNotEmpty()) descParts.add("批号：$batch")
            if (notes.isNotEmpty()) descParts.add("备注：$notes")
            val description = descParts.joinToString("；")
            val vaxRecord = TimelineRecord(
                id = "vax-${vaccineId}-${System.currentTimeMillis()}",
                babyId = _activeBaby.value.id,
                date = date,
                title = "${vaccineItem.name} 第${vaccineItem.doseNumber}剂",
                description = description,
                category = "health",
                tags = listOf("疫苗")
            )
            timelineValue.add(vaxRecord)
            _timeline.value = timelineValue
            saveTimeline()
        }
    }

    fun undoVaccine(vaccineId: String) {
        _vaccineStatuses.value = _vaccineStatuses.value.filter { !(it.vaccineId == vaccineId && isForActiveBaby(it.babyId)) }
        saveVaccines()
        // Remove the corresponding timeline record
        _timeline.value = _timeline.value.filter { !(it.id.startsWith("vax-${vaccineId}-") && isForActiveBaby(it.babyId)) }.toMutableList()
        saveTimeline()
    }

    fun getVaccineStatus(vaccineId: String): VaccineStatus? {
        return _vaccineStatuses.value.find { it.vaccineId == vaccineId && isForActiveBaby(it.babyId) }
    }

    fun getNextVaccine(birthDate: String): Pair<VaccineItem, Int>? {
        val ageMonths = getAgeInMonths(birthDate)
        val done = _vaccineStatuses.value.filter { it.isDone && isForActiveBaby(it.babyId) }.map { it.vaccineId }.toSet()
        val upcoming = CHINA_VACCINE_SCHEDULE
            .filter { it.id !in done && it.scheduledAgeMonths >= ageMonths }
            .sortedBy { it.scheduledAgeMonths }
        return upcoming.firstOrNull()?.let { it to (it.scheduledAgeMonths - ageMonths) }
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
        val resolvedRemoteUrl = remoteUrl ?: this.remoteUrl ?: url.takeIf { isHttpUrl(it) }
        val resolvedRemotePath = remotePath
            ?: this.remotePath
            ?: getWebDavConfigOrNull()?.let { remotePathFromStoredReference(url, it) }
        val mediaKind = resolvedMediaType()
        return PhotoEntity(
            id = id,
            babyId = babyId,
            url = url,
            caption = caption,
            date = date,
            timelineRecordId = timelineRecordId,
            tags = gson.toJson(normalizedTags(mediaKind)),
            mediaType = mediaKind,
            remoteUrl = resolvedRemoteUrl,
            remotePath = resolvedRemotePath,
            localOriginalPath = localOriginalPath,
            localThumbPath = localThumbPath,
            localPreviewPath = localPreviewPath,
            uploadStatus = uploadStatus,
            sha256 = sha256 ?: this.sha256,
            createdAt = createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
    }

    private fun PhotoEntity.toPhotoEntry(): PhotoEntry {
        val tagList = try {
            gson.fromJson<List<String>>(tags, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val config = getWebDavConfigOrNull()
        val storedRemoteUrl = remoteUrl ?: url
        val resolvedRemotePath = remotePath ?: config?.let { remotePathFromStoredReference(storedRemoteUrl, it) }
        val displayUrl = if (config != null && !resolvedRemotePath.isNullOrBlank()) {
            webDavUrlForRemotePath(config, resolvedRemotePath)
        } else {
            storedRemoteUrl
        }
        return PhotoEntry(
            id = id,
            babyId = babyId,
            url = displayUrl,
            caption = caption,
            date = date,
            timelineRecordId = timelineRecordId,
            tags = if (mediaType == "video" && tagList.none { it == "视频" }) tagList + "视频" else tagList,
            thumbnailPath = localThumbPath,
            mediaType = mediaType,
            remotePath = resolvedRemotePath,
            remoteUrl = remoteUrl,
            sha256 = sha256,
            createdAt = createdAt,
            updatedAt = updatedAt
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

    // SSE stream deserialization types
    private data class StreamChunk(val choices: List<StreamChoice>?)
    private data class StreamChoice(val delta: StreamDelta?)
    private data class StreamDelta(val content: String?)
}
