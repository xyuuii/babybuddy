package com.yueming.baby.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yueming.baby.data.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object DataManager {
    private var db: AppDatabase? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- StateFlows (keep same API for UI) ---
    private val _babyInfo = MutableStateFlow(sampleBaby)
    val babyInfo: StateFlow<BabyInfo> = _babyInfo.asStateFlow()

    private val _timeline = MutableStateFlow(sampleTimeline.toMutableList())
    val timeline: StateFlow<List<TimelineRecord>> = _timeline.asStateFlow()

    private val _photos = MutableStateFlow(samplePhotos.toMutableList())
    val photos: StateFlow<List<PhotoEntry>> = _photos.asStateFlow()

    private val _customCategories = MutableStateFlow(emptyList<CategoryConfig>())
    val customCategories: StateFlow<List<CategoryConfig>> = _customCategories.asStateFlow()

    val allCategories: List<CategoryConfig>
        get() = getAllCategories(_customCategories.value)

    private val _aiConfig = MutableStateFlow(AIConfig())
    val aiConfig: StateFlow<AIConfig> = _aiConfig.asStateFlow()

    private val _chatMessages = MutableStateFlow(emptyList<ChatMessage>())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // --- Initialization ---
    fun init(context: Context) {
        if (db != null) return
        db = AppDatabase.getInstance(context.applicationContext)
        appScope.launch {
            loadFromRoom()
        }
    }

    private suspend fun loadFromRoom() {
        val database = db ?: return
        val gson = Gson()

        // Load baby
        database.babyDao().getBaby()?.let {
            _babyInfo.value = BabyInfo(
                name = it.name, nickname = it.nickname,
                birthDate = it.birthDate, gender = it.gender,
                avatar = it.avatar
            )
        }

        // Load timeline
        val records = database.timelineDao().getAllOnce().map { entity ->
            TimelineRecord(
                id = entity.id, date = entity.date, title = entity.title,
                description = entity.description, category = entity.category,
                tags = parseJsonList(gson, entity.tags),
                photos = parseJsonList(gson, entity.photos),
                videos = parseJsonList(gson, entity.videos)
            )
        }
        _timeline.value = records.toMutableList()

        // Load photos
        val photos = database.photoDao().getAllOnce().map { entity ->
            PhotoEntry(
                id = entity.id, url = entity.url, caption = entity.caption,
                date = entity.date, timelineRecordId = entity.timelineRecordId,
                tags = parseJsonList(gson, entity.tags)
            )
        }
        _photos.value = photos.toMutableList()

        // Load custom categories
        val catJson = database.settingsDao().get("custom_categories")?.value
        if (catJson != null) {
            try {
                val type = object : TypeToken<List<CategoryConfig>>() {}.type
                val cats: List<CategoryConfig> = gson.fromJson(catJson, type)
                _customCategories.value = cats.toMutableList()
            } catch (_: Exception) {}
        }

        // Load AI config
        val aiJson = database.settingsDao().get("ai_config")?.value
        if (aiJson != null) {
            try {
                _aiConfig.value = gson.fromJson(aiJson, AIConfig::class.java)
            } catch (_: Exception) {}
        }

        // Load theme
        database.settingsDao().get("theme_mode")?.value?.let {
            try { _themeMode.value = ThemeMode.valueOf(it) } catch (_: Exception) {}
        }
    }

    private fun parseJsonList(gson: Gson, json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }

    private fun saveTimelineAsync() {
        val database = db ?: return
        val records = _timeline.value
        val gson = Gson()
        appScope.launch {
            // Delete all and re-insert (simplest for keeping in sync)
            val entities = records.map { record ->
                TimelineEntity(
                    id = record.id, date = record.date, title = record.title,
                    description = record.description, category = record.category,
                    tags = gson.toJson(record.tags),
                    photos = gson.toJson(record.photos),
                    videos = gson.toJson(record.videos)
                )
            }
            database.timelineDao().run {
                // Check count; if mismatch, rebuild
                val all = getAllOnce()
                if (all.size != entities.size) {
                    all.forEach { deleteById(it.id) }
                    entities.forEach { insert(it) }
                } else {
                    entities.forEach { insert(it) }
                }
            }
        }
    }

    private fun savePhotosAsync() {
        val database = db ?: return
        val photos = _photos.value
        val gson = Gson()
        appScope.launch {
            val entities = photos.map { photo ->
                PhotoEntity(
                    id = photo.id, url = photo.url, caption = photo.caption,
                    date = photo.date, timelineRecordId = photo.timelineRecordId,
                    tags = gson.toJson(photo.tags)
                )
            }
            database.photoDao().run {
                val all = getAllOnce()
                all.forEach { deleteById(it.id) }
                entities.forEach { insert(it) }
            }
        }
    }

    // --- Baby Info ---
    fun updateBabyInfo(info: BabyInfo) {
        _babyInfo.value = info
        db?.let { database ->
            appScope.launch {
                database.babyDao().upsert(BabyEntity(
                    name = info.name, nickname = info.nickname,
                    birthDate = info.birthDate, gender = info.gender,
                    avatar = info.avatar
                ))
            }
        }
    }

    // --- Timeline CRUD ---
    fun addRecord(record: TimelineRecord) {
        _timeline.value = (_timeline.value + record).toMutableList()
        db?.let { database ->
            val gson = Gson()
            appScope.launch {
                database.timelineDao().insert(TimelineEntity(
                    id = record.id, date = record.date, title = record.title,
                    description = record.description, category = record.category,
                    tags = gson.toJson(record.tags),
                    photos = gson.toJson(record.photos),
                    videos = gson.toJson(record.videos)
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

    // --- Photos CRUD ---
    fun addPhoto(photo: PhotoEntry) {
        _photos.value = (listOf(photo) + _photos.value).toMutableList()
        db?.let { database ->
            val gson = Gson()
            appScope.launch {
                database.photoDao().insert(PhotoEntity(
                    id = photo.id, url = photo.url, caption = photo.caption,
                    date = photo.date, timelineRecordId = photo.timelineRecordId,
                    tags = gson.toJson(photo.tags)
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

    // --- AI Config ---
    fun updateAIConfig(config: AIConfig) { _aiConfig.value = config
        db?.let { database ->
            val json = Gson().toJson(config)
            appScope.launch {
                database.settingsDao().upsert(SettingsEntity("ai_config", json))
            }
        }
    }
    val isAIConfigured: Boolean get() = _aiConfig.value.apiKey.isNotEmpty()

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

    // --- Export / Import ---
    fun exportToJson(onResult: (String) -> Unit) {
        db?.let { database ->
            appScope.launch {
                val json = database.exportToJson()
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
                _babyInfo.value = sampleBaby
                _customCategories.value = emptyList()
                _aiConfig.value = AIConfig()
                _chatMessages.value = emptyList()
                _themeMode.value = ThemeMode.SYSTEM
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
