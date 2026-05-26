package com.yueming.baby.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("数据模型 & 核心逻辑单元测试")
class DataModelsTest {

    @Nested
    @DisplayName("BabyInfo 数据模型")
    inner class BabyInfoTests {
        @Test
        fun `default BabyInfo 应有空字段`() {
            val baby = BabyInfo()
            assertEquals("", baby.id)
            assertEquals("", baby.name)
            assertEquals("", baby.nickname)
            assertEquals("", baby.birthDate)
            assertEquals("", baby.gender)
            assertNull(baby.avatar)
        }

        @Test
        fun `完整 BabyInfo 应有所有字段`() {
            val baby = BabyInfo(
                id = "baby-1",
                name = "月月",
                nickname = "小月亮",
                birthDate = "2024-06-15",
                gender = "girl",
                avatar = "avatar.png"
            )
            assertEquals("baby-1", baby.id)
            assertEquals("月月", baby.name)
            assertEquals("小月亮", baby.nickname)
            assertEquals("2024-06-15", baby.birthDate)
            assertEquals("girl", baby.gender)
            assertEquals("avatar.png", baby.avatar)
        }

        @Test
        fun `相同 id 的 BabyInfo 应相等`() {
            val a = BabyInfo(id = "1", name = "A", nickname = "a", birthDate = "2024-01-01", gender = "boy")
            val b = BabyInfo(id = "1", name = "A", nickname = "a", birthDate = "2024-01-01", gender = "boy")
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Nested
    @DisplayName("TimelineRecord 数据模型")
    inner class TimelineRecordTests {
        @Test
        fun `TimelineRecord 应包含所有字段`() {
            val record = TimelineRecord(
                id = "t-1",
                babyId = "baby-1",
                date = "2025-05-26",
                title = "第一步",
                description = "月月学会了走路",
                category = "milestone",
                tags = listOf("重要", "成长"),
                photos = listOf("photo1.jpg"),
                videos = listOf("video1.mp4")
            )
            assertEquals("t-1", record.id)
            assertEquals(2, record.tags.size)
            assertEquals(1, record.photos.size)
            assertEquals(1, record.videos.size)
        }

        @Test
        fun `TimelineRecord 默认列表应为空`() {
            val record = TimelineRecord(
                id = "t-2",
                date = "2025-05-26",
                title = "test",
                description = "",
                category = "other"
            )
            assertTrue(record.tags.isEmpty())
            assertTrue(record.photos.isEmpty())
            assertTrue(record.videos.isEmpty())
        }
    }

    @Nested
    @DisplayName("PhotoEntry 数据模型")
    inner class PhotoEntryTests {
        @Test
        fun `PhotoEntry 空 thumbnail 时应为空`() {
            val photo = PhotoEntry(
                id = "p-1",
                url = "https://nas.xyuuii.com/photos/1.jpg",
                caption = "月月的照片",
                date = "2025-05-26"
            )
            assertEquals("p-1", photo.id)
            assertNull(photo.thumbnailPath)
            assertNull(photo.timelineRecordId)
        }
    }

    @Nested
    @DisplayName("FeedingRecord 数据模型")
    inner class FeedingRecordTests {
        @Test
        fun `FeedingRecord 类型应为预设5种之一`() {
            val validTypes = FEEDING_TYPES.map { it.first }.toSet()
            val record = FeedingRecord(
                id = "f-1",
                timestamp = System.currentTimeMillis(),
                type = "breast",
                volumeMl = 120,
                durationMin = 15
            )
            assertTrue(record.type in validTypes, "type 应为 FEEDING_TYPES 之一")
            assertEquals(120, record.volumeMl)
            assertEquals(15, record.durationMin)
        }
    }

    @Nested
    @DisplayName("VaccineStatus 逻辑")
    inner class VaccineStatusTests {
        @Test
        fun `有 administeredDate 时应为已接种`() {
            val done = VaccineStatus(vaccineId = "hepb-1", administeredDate = "2024-06-16")
            assertTrue(done.isDone)
        }

        @Test
        fun `无 administeredDate 时应为未接种`() {
            val pending = VaccineStatus(vaccineId = "hepb-2")
            assertFalse(pending.isDone)
        }

        @Test
        fun `null administeredDate 应等于未接种`() {
            val pending = VaccineStatus(vaccineId = "hepb-1", administeredDate = null)
            assertFalse(pending.isDone)
        }
    }

    @Nested
    @DisplayName("CategoryConfig & 颜色转换")
    inner class CategoryConfigTests {
        @Test
        fun `colorValue 应返回正确的 Color 对象`() {
            val cat = CategoryConfig(
                id = "milestone",
                label = "成长里程碑",
                icon = "Star",
                color = 0xFFf6ba6d,
                isDefault = true
            )
            val color = cat.colorValue()
            assertEquals(0xFFf6ba6d.toInt(), (color.value shr 32).toInt())
        }

        @Test
        fun `DEFAULT_CATEGORIES 应有7个默认分类`() {
            assertEquals(7, DEFAULT_CATEGORIES.size)
        }

        @Test
        fun `getCategoryConfig 应通过 id 找到对应分类`() {
            val cat = getCategoryConfig("health")
            assertNotNull(cat)
            assertEquals("健康记录", cat!!.label)
        }

        @Test
        fun `getCategoryConfig 不存在的 id 应返回 null`() {
            assertNull(getCategoryConfig("nonexistent"))
        }

        @Test
        fun `getAllCategories 应包含自定义分类`() {
            val custom = CategoryConfig(
                id = "custom1",
                label = "自定义",
                icon = "Star",
                color = 0xFFFF0000,
                isDefault = false
            )
            val all = getAllCategories(listOf(custom))
            assertTrue(all.any { it.id == "custom1" })
        }
    }

    @Nested
    @DisplayName("Milestone 里程碑逻辑")
    inner class MilestoneTests {
        @Test
        fun `ALL_MILESTONES 不应为空`() {
            assertTrue(ALL_MILESTONES.isNotEmpty(), "里程碑数据不应为空")
        }

        @Test
        fun `getMilestonesForAge(6) 应包含6月龄里程碑`() {
            val milestones = getMilestonesForAge(6)
            assertTrue(milestones.isNotEmpty())
            // 6月应该有"六月体检"和"开始辅食"
            assertTrue(milestones.any { it.title == "六月体检" })
            assertTrue(milestones.any { it.title == "开始辅食" })
        }

        @Test
        fun `getMilestonesForAge(0) 应只包含新生儿筛查`() {
            val milestones = getMilestonesForAge(0)
            assertEquals(1, milestones.size)
            assertEquals("新生儿筛查", milestones.first().title)
        }

        @Test
        fun `getMilestoneIcon 应返回正确的图标类型`() {
            assertEquals("health", getMilestonesForAge(6).first { it.title == "六月体检" }.category)
        }
    }

    @Nested
    @DisplayName("AI 模型配置")
    inner class AIModelTests {
        @Test
        fun `AI_MODELS 应包含知名提供商`() {
            val providers = AI_MODELS.map { it.provider }.toSet()
            assertTrue(providers.contains("DeepSeek"))
            assertTrue(providers.contains("OpenAI"))
            assertTrue(providers.contains("Anthropic"))
        }

        @Test
        fun `每个 AIModel 应有非空字段`() {
            AI_MODELS.forEach { model ->
                assertTrue(model.id.isNotEmpty(), "${model.name}: id 不应为空")
                assertTrue(model.name.isNotEmpty(), "${model.name}: name 不应为空")
                assertTrue(model.apiBase.isNotEmpty(), "${model.name}: apiBase 不应为空")
            }
        }

        @Test
        fun `AIConfig 默认值应为 DeepSeek`() {
            val config = AIConfig()
            assertEquals("https://api.deepseek.com", config.apiBaseUrl)
            assertEquals("deepseek-v4-pro", config.model)
            assertEquals("", config.apiKey)
        }

        @Test
        fun `AIProfile 的 maxTokens 应为 32768`() {
            val profile = AIProfile()
            assertEquals(32768, profile.maxTokens)
        }
    }
}
