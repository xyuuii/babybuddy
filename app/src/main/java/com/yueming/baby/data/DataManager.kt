package com.yueming.baby.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale

object DataManager {
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

    // Baby Info
    fun updateBabyInfo(info: BabyInfo) { _babyInfo.value = info }

    // Timeline CRUD
    fun addRecord(record: TimelineRecord) {
        _timeline.value = (_timeline.value + record).toMutableList()
    }

    fun updateRecord(id: String, updates: (TimelineRecord) -> TimelineRecord) {
        _timeline.value = _timeline.value.map { if (it.id == id) updates(it) else it }.toMutableList()
    }

    fun deleteRecord(id: String) {
        _timeline.value = _timeline.value.filter { it.id != id }.toMutableList()
        _photos.value = _photos.value.filter { it.timelineRecordId != id }.toMutableList()
    }

    // Photos CRUD
    fun addPhoto(photo: PhotoEntry) {
        _photos.value = (listOf(photo) + _photos.value).toMutableList()
    }

    fun deletePhoto(id: String) {
        _photos.value = _photos.value.filter { it.id != id }.toMutableList()
    }

    // Categories
    fun addCategory(cat: CategoryConfig) {
        _customCategories.value = (_customCategories.value + cat).toMutableList()
    }

    fun removeCategory(id: String) {
        _customCategories.value = _customCategories.value.filter { it.id != id }.toMutableList()
    }

    // AI Config
    fun updateAIConfig(config: AIConfig) { _aiConfig.value = config }
    val isAIConfigured: Boolean get() = _aiConfig.value.apiKey.isNotEmpty()

    // Chat
    fun addMessage(msg: ChatMessage) {
        _chatMessages.value = (_chatMessages.value + msg).toMutableList()
    }

    fun clearMessages() { _chatMessages.value = emptyList() }

    // Age calculations
    fun getAgeInMonths(birthDate: String): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val birth = fmt.parse(birthDate) ?: return 0
        val birthCal = java.util.Calendar.getInstance().apply { time = birth }
        val nowCal = java.util.Calendar.getInstance()
        var months = (nowCal.get(java.util.Calendar.YEAR) - birthCal.get(java.util.Calendar.YEAR)) * 12
        months += nowCal.get(java.util.Calendar.MONTH) - birthCal.get(java.util.Calendar.MONTH)
        if (nowCal.get(java.util.Calendar.DAY_OF_MONTH) < birthCal.get(java.util.Calendar.DAY_OF_MONTH)) months--
        return maxOf(0, months)
    }

    fun getAgeInDays(birthDate: String): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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
