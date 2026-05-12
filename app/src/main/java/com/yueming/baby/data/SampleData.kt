package com.yueming.baby.data

val sampleBaby = BabyInfo(
    name = "小月亮",
    nickname = "月月",
    birthDate = "2025-01-15",
    gender = "girl"
)

val sampleTimeline = listOf(
    TimelineRecord(
        id = "1", date = "2025-01-15", title = "宝宝出生啦！",
        description = "体重3.4kg，身长50cm，哭声洪亮。Apgar评分9分，一切顺利。",
        photos = listOf("https://images.unsplash.com/photo-1555252333-9f8e92e65df9?w=400&h=300&fit=crop"),
        tags = listOf("出生", "第一次见面"), category = "milestone"
    ),
    TimelineRecord(
        id = "2", date = "2025-01-16", title = "第一次母乳喂养",
        description = "母乳喂养建立成功。每2-3小时喂一次，每次15-20分钟。黄疸值正常。",
        tags = listOf("母乳", "喂养"), category = "feeding"
    ),
    TimelineRecord(
        id = "3", date = "2025-03-01", title = "奶粉补充开始",
        description = "开始混合喂养——母乳为主，晚上睡前加60ml配方奶。品牌：爱他美卓萃1段。月月适应很好，没有乳糖不耐。",
        tags = listOf("配方奶", "爱他美", "混合喂养"), category = "feeding"
    ),
    TimelineRecord(
        id = "4", date = "2025-03-20", title = "第一次微笑",
        description = "月月对着妈妈笑了！外婆说这是\"天使的微笑\"，妈妈感动得不行。",
        photos = listOf("https://images.unsplash.com/photo-1519689680058-324335c77eba?w=400&h=300&fit=crop"),
        tags = listOf("微笑", "社交"), category = "milestone"
    ),
    TimelineRecord(
        id = "5", date = "2025-05-10", title = "三个月体检",
        description = "体重6.2kg，身长62cm，头围40cm。各项指标正常。接种五联疫苗第二针，月月很勇敢。",
        tags = listOf("体检", "疫苗", "健康"), category = "health"
    ),
    TimelineRecord(
        id = "6", date = "2025-06-20", title = "开始添加辅食",
        description = "第一次添加米粉——嘉宝有机米粉。冲调比例：5g米粉 + 40ml温水。月月接受度很好，吃了半碗。准备下周开始加胡萝卜泥。",
        photos = listOf("https://images.unsplash.com/photo-1519689680058-324335c77eba?w=400&h=300&fit=crop"),
        tags = listOf("辅食", "米粉", "嘉宝"), category = "feeding"
    ),
    TimelineRecord(
        id = "7", date = "2025-07-01", title = "第一次翻身",
        description = "在爬行垫上从仰卧翻到俯卧！六个月就会翻身了，太棒了！",
        photos = listOf("https://images.unsplash.com/photo-1555252333-9f8e92e65df9?w=400&h=300&fit=crop"),
        tags = listOf("翻身", "大运动"), category = "milestone"
    ),
    TimelineRecord(
        id = "8", date = "2025-08-15", title = "换奶粉品牌——皇家美素佳儿2段",
        description = "从爱他美1段转到皇家美素佳儿2段（6-12月）。转奶方式：第一天1/3新奶，第三天各半，第五天全换。无不良反应。每天奶量约800ml。",
        tags = listOf("配方奶", "转奶", "皇家美素佳儿"), category = "feeding"
    ),
    TimelineRecord(
        id = "9", date = "2025-09-20", title = "第一次爬行",
        description = "月月今天往前爬了两步！虽然姿势还是\"匍匐前进\"，但这个进步太让人兴奋了。",
        tags = listOf("爬行", "大运动"), category = "milestone"
    ),
    TimelineRecord(
        id = "10", date = "2025-10-15", title = "新辅食：西兰花泥 + 三文鱼泥",
        description = "新增西兰花泥和三文鱼泥。制作方法：西兰花蒸10分钟打泥，三文鱼蒸8分钟去刺打泥。月月很喜欢三文鱼的味道。",
        tags = listOf("辅食", "西兰花", "三文鱼"), category = "feeding"
    ),
    TimelineRecord(
        id = "11", date = "2025-10-28", title = "第一次独立坐稳",
        description = "不用手支撑也能保持平衡了！九个月的进步真的太快了。",
        tags = listOf("学坐", "大运动"), category = "milestone"
    ),
    TimelineRecord(
        id = "12", date = "2025-11-20", title = "换3段奶粉 + 添加手指食物",
        description = "开始皇家美素佳儿3段。同时引入手指食物：蒸软的胡萝卜条、香蕉块。月月自己抓着吃，虽然吃得到处都是但很开心。",
        tags = listOf("配方奶", "手指食物", "皇家美素佳儿"), category = "feeding"
    ),
    TimelineRecord(
        id = "13", date = "2025-12-20", title = "第一次叫\"妈妈\"",
        description = "早上醒来看着妈妈说\"ma ma\"！虽然可能只是无意识发音，但妈妈高兴得跳起来。",
        tags = listOf("说话", "语言发展"), category = "milestone"
    ),
    TimelineRecord(
        id = "14", date = "2026-02-01", title = "一岁体检",
        description = "体重9.8kg，身长76cm。医生评价发育非常好。血红蛋白正常。开始可以喝纯牛奶了，但继续以配方奶为主。",
        tags = listOf("体检", "一岁", "健康"), category = "health"
    ),
    TimelineRecord(
        id = "15", date = "2026-02-14", title = "一岁生日派对",
        description = "温馨的生日派对！爷爷奶奶外公外婆都来了。抓周抓了一本书和一支笔。月月吃蛋糕吃得满脸都是。",
        photos = listOf("https://images.unsplash.com/photo-1476703993599-0035a21b17a9?w=400&h=300&fit=crop"),
        tags = listOf("生日", "一岁", "抓周"), category = "milestone"
    )
)

val samplePhotos = listOf(
    PhotoEntry("p1", "https://images.unsplash.com/photo-1555252333-9f8e92e65df9?w=600&h=600&fit=crop",
        "出生第一天的小月亮", "2025-01-15", timelineRecordId = "1", tags = listOf("新生儿", "出生")),
    PhotoEntry("p2", "https://images.unsplash.com/photo-1519689680058-324335c77eba?w=600&h=600&fit=crop",
        "甜甜的睡颜", "2025-02-10", tags = listOf("睡觉", "可爱")),
    PhotoEntry("p3", "https://images.unsplash.com/photo-1476703993599-0035a21b17a9?w=600&h=600&fit=crop",
        "妈妈抱抱", "2025-03-15", tags = listOf("亲子", "拥抱")),
    PhotoEntry("p4", "https://images.unsplash.com/photo-1555252333-9f8e92e65df9?w=600&h=600&fit=crop",
        "三个月的小月亮", "2025-04-15", tags = listOf("成长", "可爱")),
    PhotoEntry("p5", "https://images.unsplash.com/photo-1519689680058-324335c77eba?w=600&h=600&fit=crop",
        "趴着抬头练习", "2025-05-20", tags = listOf("运动", "抬头")),
    PhotoEntry("p6", "https://images.unsplash.com/photo-1476703993599-0035a21b17a9?w=600&h=600&fit=crop",
        "第一次翻身成功！", "2025-07-01", timelineRecordId = "7", tags = listOf("翻身", "里程碑")),
    PhotoEntry("p7", "https://images.unsplash.com/photo-1555252333-9f8e92e65df9?w=600&h=600&fit=crop",
        "吃辅食啦", "2025-09-15", timelineRecordId = "6", tags = listOf("辅食", "吃饭")),
    PhotoEntry("p8", "https://images.unsplash.com/photo-1519689680058-324335c77eba?w=600&h=600&fit=crop",
        "一岁生日派对", "2026-02-14", timelineRecordId = "15", tags = listOf("生日", "一岁"))
)
