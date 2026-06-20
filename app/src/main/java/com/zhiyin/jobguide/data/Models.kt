package com.zhiyin.jobguide.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class TaskStatus(val label: String) {
    Idle("未开始"),
    Running("运行中"),
    Paused("已暂停"),
    Completed("已完成"),
    RiskStopped("风险停止"),
    Stopped("已停止")
}

enum class RunMode(val label: String, val description: String) {
    InputOnly("只输入不发送", "自动进入聊天页并输入模板内容，由用户自行决定后续操作"),
    ConfirmBeforeSend("发送前确认", "输入模板内容后暂停，等待用户人工确认发送"),
    AutoSend("自动打招呼", "在用户主动开启后自动点击发送按钮")
}

enum class JobSourceMode(val label: String, val description: String) {
    KeywordSearch("关键词搜索", "按关键词队列依次搜索岗位，达到每个关键词目标后切换"),
    HomeRecommendations("职位页推荐", "从所选招聘 App 的首页/职位页推荐岗位流依次进入详情并打招呼")
}

enum class JobPlatform(val label: String, val packageName: String) {
    Boss("BOSS 直聘", "com.hpbr.bosszhipin"),
    Job51("前程无忧 51job", "com.job.android"),
    Zhilian("智联招聘", "com.zhaopin.social"),
    Liepin("猎聘", "com.lietou.mishu")
}

enum class LogLevel(val label: String) {
    Info("信息"),
    Success("成功"),
    Warning("提醒"),
    Error("错误")
}

enum class AutomationCommandType {
    None,
    Start,
    Pause,
    Stop
}

data class KeywordSetting(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val maxJobs: Int = 20,
    val maxGreetings: Int = 5,
    val priority: Int = 0
)

data class RulesConfig(
    val targetCities: List<String> = listOf("城市"),
    val salaryMinK: Int = 5,
    val salaryMaxK: Int = 15,
    val requiredKeywords: List<String> = listOf("关键词1", "关键词2"),
    val jobWhitelist: List<String> = listOf("岗位白名单1", "岗位白名单2"),
    val companyWhitelist: List<String> = emptyList(),
    val jobBlacklist: List<String> = listOf("销售", "电销", "客服", "招生", "主播", "课程顾问", "培训", "外包"),
    val companyBlacklist: List<String> = emptyList(),
    val riskStopWords: List<String> = listOf("验证码", "安全验证", "账号异常", "操作频繁", "稍后再试", "登录失效", "请重新登录"),
    val dailyGreetingLimit: Int = 15,
    val minDelaySeconds: Int = 5,
    val maxDelaySeconds: Int = 12,
    val minMatchScore: Int = 40
)

data class GreetingTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val isDefault: Boolean = false,
    val randomEnabled: Boolean = true
)

data class ProfileConfig(
    val name: String = "你的姓名",
    val skills: List<String> = listOf("技能1", "技能2", "技能3"),
    val expectedCities: List<String> = listOf("城市"),
    val expectedSalary: String = "期望薪资"
)

data class AppConfig(
    val keywords: List<KeywordSetting> = defaultKeywords(),
    val rules: RulesConfig = RulesConfig(),
    val templates: List<GreetingTemplate> = defaultTemplates(),
    val profile: ProfileConfig = ProfileConfig(),
    val runMode: RunMode = RunMode.ConfirmBeforeSend,
    val autoGreetingEnabled: Boolean = false,
    val jobPlatform: JobPlatform = JobPlatform.Boss,
    val jobSourceMode: JobSourceMode = JobSourceMode.KeywordSearch,
    val recommendationGreetingTarget: Int = 5
) {
    companion object {
        fun defaultKeywords(): List<KeywordSetting> = listOf(
            KeywordSetting(name = "关键词示例1", maxJobs = 20, maxGreetings = 5),
            KeywordSetting(name = "关键词示例2", maxJobs = 20, maxGreetings = 5),
            KeywordSetting(name = "关键词示例3", maxJobs = 20, maxGreetings = 5),
            KeywordSetting(name = "关键词示例4", maxJobs = 20, maxGreetings = 5)
        )

        fun defaultTemplates(): List<GreetingTemplate> = listOf(
            GreetingTemplate(
                title = "默认模板",
                isDefault = true,
                body = "您好，我是{name}，对贵公司的{job_title}岗位很感兴趣。我熟悉{skills}，希望能有机会进一步沟通。"
            )
        )

        fun fromJson(raw: String): AppConfig = runCatching {
            val root = JSONObject(raw)
            val json = root.optObjectCompat("config", "appConfig", "jobGuide") ?: root
            val keywordArray = json.optArrayCompat("keywords", "keywordSettings", "searchKeywords", "关键词")
            val rulesJson = json.optObjectCompat("rules", "rulesConfig", "ruleConfig", "规则") ?: json
            val profileJson = json.optObjectCompat("profile", "jobSeeker", "seeker", "求职者信息", "我的") ?: json
            val templateArray = json.optArrayCompat("templates", "greetingTemplates", "templateList", "打招呼模板", "模板")
            val templateObject = json.optObjectCompat("template", "greetingTemplate", "defaultTemplate", "打招呼模板", "模板")
            val templateText = json.optStringCompat(
                "",
                "template",
                "greetingTemplate",
                "messageTemplate",
                "打招呼模板",
                "打招呼文案",
                "文案"
            )
            val templates = when {
                templateArray != null -> templateArray.toTemplateList()
                templateObject != null -> listOf(templateObject.toTemplate()).filter { it.body.isNotBlank() }
                templateText.isNotBlank() -> listOf(
                    GreetingTemplate(
                        title = "导入模板",
                        body = templateText,
                        isDefault = true
                    )
                )
                else -> defaultTemplates()
            }.ifEmpty { defaultTemplates() }
            AppConfig(
                keywords = keywordArray?.toKeywordList() ?: defaultKeywords(),
                rules = rulesJson.toRulesConfig(),
                templates = templates,
                profile = profileJson.toProfileConfig(),
                runMode = parseRunMode(json.optStringCompat(RunMode.ConfirmBeforeSend.name, "runMode", "运行模式")),
                autoGreetingEnabled = json.optBooleanCompat(false, "autoGreetingEnabled", "autoSendEnabled", "自动打招呼开关", "自动发送"),
                jobPlatform = parseJobPlatform(
                    json.optStringCompat(
                        JobPlatform.Boss.name,
                        "jobPlatform",
                        "platform",
                        "recruitmentPlatform",
                        "appPlatform",
                        "招聘平台",
                        "平台"
                    )
                ),
                jobSourceMode = parseJobSourceMode(
                    json.optStringCompat(
                        JobSourceMode.KeywordSearch.name,
                        "jobSourceMode",
                        "sourceMode",
                        "jobSource",
                        "岗位来源",
                        "来源模式"
                    )
                ),
                recommendationGreetingTarget = json.optIntCompat(
                    5,
                    "recommendationGreetingTarget",
                    "homeRecommendationTarget",
                    "recommendationTarget",
                    "首页推荐目标",
                    "推荐打招呼次数"
                ).coerceAtLeast(0)
            )
        }.getOrDefault(AppConfig())
    }
}

data class RunStats(
    val status: TaskStatus = TaskStatus.Idle,
    val todayProcessed: Int = 0,
    val todayGreetings: Int = 0,
    val currentKeyword: String = "",
    val currentStep: String = "等待开始",
    val riskReason: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val keywordGreetings: Map<String, Int> = emptyMap()
) {
    companion object {
        fun fromJson(raw: String): RunStats = runCatching {
            val json = JSONObject(raw)
            val keywordMap = mutableMapOf<String, Int>()
            json.optJSONObject("keywordGreetings")?.let { obj ->
                obj.keys().forEach { key -> keywordMap[key] = obj.optInt(key, 0) }
            }
            RunStats(
                status = runCatching { TaskStatus.valueOf(json.optString("status", TaskStatus.Idle.name)) }
                    .getOrDefault(TaskStatus.Idle),
                todayProcessed = json.optInt("todayProcessed", 0),
                todayGreetings = json.optInt("todayGreetings", 0),
                currentKeyword = json.optString("currentKeyword", ""),
                currentStep = json.optString("currentStep", "等待开始"),
                riskReason = json.optString("riskReason", ""),
                startedAt = json.optLong("startedAt", 0L),
                finishedAt = json.optLong("finishedAt", 0L),
                keywordGreetings = keywordMap
            )
        }.getOrDefault(RunStats())
    }
}

data class RunLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.Info,
    val keyword: String = "",
    val message: String
) {
    companion object {
        fun fromJson(json: JSONObject): RunLog = RunLog(
            id = json.optString("id", UUID.randomUUID().toString()),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            level = runCatching { LogLevel.valueOf(json.optString("level", LogLevel.Info.name)) }
                .getOrDefault(LogLevel.Info),
            keyword = json.optString("keyword", ""),
            message = json.optString("message", "")
        )
    }
}

data class JobRecord(
    val fingerprint: String,
    val aliases: List<String> = emptyList(),
    val platform: JobPlatform = JobPlatform.Boss,
    val timestamp: Long = System.currentTimeMillis(),
    val keyword: String,
    val title: String,
    val company: String,
    val city: String,
    val salary: String,
    val action: String,
    val reason: String
) {
    fun matchesFingerprint(value: String): Boolean = fingerprint == value || aliases.contains(value)

    companion object {
        fun fromJson(json: JSONObject): JobRecord = JobRecord(
            fingerprint = json.optString("fingerprint"),
            aliases = json.optJSONArray("aliases")?.toStringList().orEmpty(),
            platform = parseJobPlatform(json.optStringCompat(JobPlatform.Boss.name, "platform", "jobPlatform", "招聘平台", "平台")),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            keyword = json.optString("keyword"),
            title = json.optString("title"),
            company = json.optString("company"),
            city = json.optString("city"),
            salary = json.optString("salary"),
            action = json.optString("action"),
            reason = json.optString("reason")
        )
    }
}

data class AutomationCommand(
    val type: AutomationCommandType = AutomationCommandType.None,
    val issuedAt: Long = System.currentTimeMillis(),
    val nonce: String = UUID.randomUUID().toString()
) {
    companion object {
        fun fromJson(raw: String): AutomationCommand = runCatching {
            val json = JSONObject(raw)
            AutomationCommand(
                type = runCatching { AutomationCommandType.valueOf(json.optString("type", AutomationCommandType.None.name)) }
                    .getOrDefault(AutomationCommandType.None),
                issuedAt = json.optLong("issuedAt", System.currentTimeMillis()),
                nonce = json.optString("nonce", UUID.randomUUID().toString())
            )
        }.getOrDefault(AutomationCommand())
    }
}

data class JobSnapshot(
    val title: String,
    val company: String,
    val city: String,
    val salary: String,
    val texts: List<String>,
    val score: Int,
    val reasons: List<String>,
    val fingerprint: String
) {
    val summary: String
        get() = listOf(title, company, city, salary).filter { it.isNotBlank() }.joinToString(" / ")
}

fun AppConfig.toJson(): String = JSONObject()
    .put("keywords", keywords.toJsonArray { it.toJsonObject() })
    .put("rules", rules.toJsonObject())
    .put("templates", templates.toJsonArray { it.toJsonObject() })
    .put("profile", profile.toJsonObject())
    .put("runMode", runMode.name)
    .put("autoGreetingEnabled", autoGreetingEnabled)
    .put("jobPlatform", jobPlatform.name)
    .put("jobSourceMode", jobSourceMode.name)
    .put("recommendationGreetingTarget", recommendationGreetingTarget)
    .toString(2)

fun RunStats.toJson(): String = JSONObject()
    .put("status", status.name)
    .put("todayProcessed", todayProcessed)
    .put("todayGreetings", todayGreetings)
    .put("currentKeyword", currentKeyword)
    .put("currentStep", currentStep)
    .put("riskReason", riskReason)
    .put("startedAt", startedAt)
    .put("finishedAt", finishedAt)
    .put("keywordGreetings", JSONObject().also { obj ->
        keywordGreetings.forEach { (key, value) -> obj.put(key, value) }
    })
    .toString()

fun RunLog.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("timestamp", timestamp)
    .put("level", level.name)
    .put("keyword", keyword)
    .put("message", message)

fun JobRecord.toJsonObject(): JSONObject = JSONObject()
    .put("fingerprint", fingerprint)
    .put("aliases", aliases.toJsonArray())
    .put("platform", platform.name)
    .put("timestamp", timestamp)
    .put("keyword", keyword)
    .put("title", title)
    .put("company", company)
    .put("city", city)
    .put("salary", salary)
    .put("action", action)
    .put("reason", reason)

fun AutomationCommand.toJson(): String = JSONObject()
    .put("type", type.name)
    .put("issuedAt", issuedAt)
    .put("nonce", nonce)
    .toString()

fun List<RunLog>.toLogJson(): String = toJsonArray { it.toJsonObject() }.toString()

fun List<JobRecord>.toRecordJson(): String = toJsonArray { it.toJsonObject() }.toString()

fun parseLogs(raw: String): List<RunLog> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) add(RunLog.fromJson(array.getJSONObject(index)))
    }
}.getOrDefault(emptyList())

fun parseRecords(raw: String): List<JobRecord> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) add(JobRecord.fromJson(array.getJSONObject(index)))
    }
}.getOrDefault(emptyList())

fun renderGreetingTemplate(
    template: GreetingTemplate,
    snapshot: JobSnapshot,
    keyword: String,
    profile: ProfileConfig
): String {
    val name = cleanTemplateValue(profile.name, "求职者")
    val jobTitle = cleanTemplateValue(snapshot.title, "该岗位")
    val company = cleanTemplateValue(snapshot.company, "贵公司")
    val city = cleanTemplateValue(snapshot.city, "")
    val salary = cleanTemplateValue(snapshot.salary, "")
    val skills = profile.skills.joinToString("、").ifBlank { "相关技能" }
    val keywordValue = keyword.ifBlank { "相关岗位" }

    return template.body
        .replace("{name}", name)
        .replace("{job_title}", jobTitle)
        .replace("{company}", company)
        .replace("{city}", city)
        .replace("{salary}", salary)
        .replace("{skills}", skills)
        .replace("{keyword}", keywordValue)
}

private fun cleanTemplateValue(value: String, fallback: String): String {
    val normalized = value
        .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim { char -> !char.isLetterOrDigit() && char !in setOf('+', '#') }

    if (normalized.isBlank()) return fallback
    if (normalized.contains("�")) return fallback
    if (normalized.contains("@") || normalized.contains("%")) return fallback
    if (normalized.length <= 1) return fallback
    return normalized
}

fun splitInput(value: String): List<String> = value
    .split(",", "，", "\n", "、")
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

fun List<String>.displayText(): String = joinToString("、")

fun stableFingerprint(parts: List<String>): String {
    val normalized = parts.joinToString("|")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
    val digest = MessageDigest.getInstance("SHA-1").digest(normalized.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun KeywordSetting.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("enabled", enabled)
    .put("maxJobs", maxJobs)
    .put("maxGreetings", maxGreetings)
    .put("priority", priority)

private fun RulesConfig.toJsonObject(): JSONObject = JSONObject()
    .put("targetCities", targetCities.toJsonArray())
    .put("salaryMinK", salaryMinK)
    .put("salaryMaxK", salaryMaxK)
    .put("requiredKeywords", requiredKeywords.toJsonArray())
    .put("jobWhitelist", jobWhitelist.toJsonArray())
    .put("companyWhitelist", companyWhitelist.toJsonArray())
    .put("jobBlacklist", jobBlacklist.toJsonArray())
    .put("companyBlacklist", companyBlacklist.toJsonArray())
    .put("riskStopWords", riskStopWords.toJsonArray())
    .put("dailyGreetingLimit", dailyGreetingLimit)
    .put("minDelaySeconds", minDelaySeconds)
    .put("maxDelaySeconds", maxDelaySeconds)
    .put("minMatchScore", minMatchScore)

private fun GreetingTemplate.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("body", body)
    .put("isDefault", isDefault)
    .put("randomEnabled", randomEnabled)

private fun ProfileConfig.toJsonObject(): JSONObject = JSONObject()
    .put("name", name)
    .put("skills", skills.toJsonArray())
    .put("expectedCities", expectedCities.toJsonArray())
    .put("expectedSalary", expectedSalary)

private fun JSONObject.toKeyword(): KeywordSetting {
    val targetGreetings = optIntCompat(5, "maxGreetings", "greetingTarget", "targetGreetings", "maxGreetingCount", "成功打招呼目标", "打招呼次数").coerceAtLeast(0)
    return KeywordSetting(
        id = optStringCompat(UUID.randomUUID().toString(), "id"),
        name = optStringCompat("", "name", "keyword", "value", "title", "关键词"),
        enabled = optBooleanCompat(true, "enabled", "enable", "启用"),
        maxJobs = optIntCompat(maxOf(targetGreetings * 8, 30), "maxJobs", "scanLimit", "处理数量").coerceAtLeast(0),
        maxGreetings = targetGreetings,
        priority = optIntCompat(0, "priority", "优先级")
    )
}

private fun JSONObject.toRulesConfig(): RulesConfig {
    val defaults = RulesConfig()
    return RulesConfig(
        targetCities = optStringListCompat(defaults.targetCities, "targetCities", "cities", "city", "目标城市", "城市"),
        salaryMinK = optIntCompat(defaults.salaryMinK, "salaryMinK", "minSalaryK", "salaryMin", "最低薪资K", "最低薪资"),
        salaryMaxK = optIntCompat(defaults.salaryMaxK, "salaryMaxK", "maxSalaryK", "salaryMax", "最高薪资K", "最高薪资"),
        requiredKeywords = optStringListCompat(defaults.requiredKeywords, "requiredKeywords", "required", "mustKeywords", "必备关键词"),
        jobWhitelist = optStringListCompat(defaults.jobWhitelist, "jobWhitelist", "jobWhiteList", "whitelist", "whiteList", "岗位白名单", "白名单"),
        companyWhitelist = optStringListCompat(defaults.companyWhitelist, "companyWhitelist", "companyWhiteList", "公司白名单"),
        jobBlacklist = optStringListCompat(defaults.jobBlacklist, "jobBlacklist", "jobBlackList", "blacklist", "blackList", "岗位黑名单", "黑名单"),
        companyBlacklist = optStringListCompat(defaults.companyBlacklist, "companyBlacklist", "companyBlackList", "公司黑名单"),
        riskStopWords = optStringListCompat(defaults.riskStopWords, "riskStopWords", "riskWords", "stopWords", "风险停止词", "风险词"),
        dailyGreetingLimit = optIntCompat(defaults.dailyGreetingLimit, "dailyGreetingLimit", "dailyLimit", "totalGreetingLimit", "今日总上限", "每日总上限", "总上限"),
        minDelaySeconds = optIntCompat(defaults.minDelaySeconds, "minDelaySeconds", "minDelay", "minIntervalSeconds", "最小间隔秒", "最小间隔"),
        maxDelaySeconds = optIntCompat(defaults.maxDelaySeconds, "maxDelaySeconds", "maxDelay", "maxIntervalSeconds", "最大间隔秒", "最大间隔"),
        minMatchScore = optIntCompat(defaults.minMatchScore, "minMatchScore", "minScore", "最低评分")
    )
}

private fun JSONObject.toTemplate(): GreetingTemplate = GreetingTemplate(
    id = optStringCompat(UUID.randomUUID().toString(), "id"),
    title = optStringCompat("未命名模板", "title", "name", "templateName", "模板名称"),
    body = optStringCompat("", "body", "content", "text", "template", "message", "greeting", "打招呼文案", "文案"),
    isDefault = optBooleanCompat(false, "isDefault", "default", "默认模板"),
    randomEnabled = optBooleanCompat(true, "randomEnabled", "random", "随机使用")
)

private fun JSONObject.toProfileConfig(): ProfileConfig {
    val defaults = ProfileConfig()
    return ProfileConfig(
        name = optStringCompat(defaults.name, "name", "姓名"),
        skills = optStringListCompat(defaults.skills, "skills", "skillTags", "技能标签", "技能"),
        expectedCities = optStringListCompat(defaults.expectedCities, "expectedCities", "expectedCity", "期望城市"),
        expectedSalary = optStringCompat(defaults.expectedSalary, "expectedSalary", "salary", "期望薪资")
    )
}

private fun JSONArray.toKeywordList(): List<KeywordSetting> = buildList {
    for (index in 0 until length()) {
        val keyword = optJSONObject(index)?.toKeyword() ?: KeywordSetting(name = optString(index).trim())
        if (keyword.name.isNotBlank()) add(keyword)
    }
}.ifEmpty { AppConfig.defaultKeywords() }

private fun JSONArray.toTemplateList(): List<GreetingTemplate> = buildList {
    for (index in 0 until length()) {
        val template = optJSONObject(index)?.toTemplate()
            ?: GreetingTemplate(title = "导入模板 ${index + 1}", body = optString(index).trim(), isDefault = index == 0)
        add(template)
    }
}.filter { it.body.isNotBlank() }

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        val value = optString(index).trim()
        if (value.isNotBlank()) add(value)
    }
}.distinct()

private fun JSONObject.optObjectCompat(vararg keys: String): JSONObject? {
    for (key in keys) {
        optJSONObject(key)?.let { return it }
    }
    return null
}

private fun JSONObject.optArrayCompat(vararg keys: String): JSONArray? {
    for (key in keys) {
        optJSONArray(key)?.let { return it }
    }
    return null
}

private fun JSONObject.optStringCompat(default: String, vararg keys: String): String {
    for (key in keys) {
        if (has(key)) {
            val value = optString(key, default).trim()
            if (value.isNotBlank() && value != "null") return value
        }
    }
    return default
}

private fun JSONObject.optIntCompat(default: Int, vararg keys: String): Int {
    for (key in keys) {
        if (has(key)) return optInt(key, default)
    }
    return default
}

private fun JSONObject.optBooleanCompat(default: Boolean, vararg keys: String): Boolean {
    for (key in keys) {
        if (has(key)) return optBoolean(key, default)
    }
    return default
}

private fun JSONObject.optStringListCompat(default: List<String>, vararg keys: String): List<String> {
    val key = keys.firstOrNull { has(it) } ?: return default
    optJSONArray(key)?.let { return it.toStringList() }
    return splitInput(optString(key, ""))
}

private fun parseRunMode(value: String): RunMode = when (value.trim()) {
    RunMode.InputOnly.name, RunMode.InputOnly.label, "inputOnly", "只输入" -> RunMode.InputOnly
    RunMode.AutoSend.name, RunMode.AutoSend.label, "autoSend", "自动发送" -> RunMode.AutoSend
    RunMode.ConfirmBeforeSend.name, RunMode.ConfirmBeforeSend.label, "confirmBeforeSend", "确认后发送" -> RunMode.ConfirmBeforeSend
    else -> RunMode.ConfirmBeforeSend
}

fun parseJobPlatform(value: String): JobPlatform {
    val normalized = value.trim().lowercase()
    return when (normalized) {
        JobPlatform.Boss.name.lowercase(),
        JobPlatform.Boss.label.lowercase(),
        JobPlatform.Boss.packageName.lowercase(),
        "boss",
        "boss直聘",
        "boss 直聘",
        "boss zhipin" -> JobPlatform.Boss

        JobPlatform.Job51.name.lowercase(),
        JobPlatform.Job51.label.lowercase(),
        JobPlatform.Job51.packageName.lowercase(),
        "51job",
        "51 job",
        "前程无忧",
        "前程无忧51job",
        "前程无忧 51job" -> JobPlatform.Job51

        JobPlatform.Zhilian.name.lowercase(),
        JobPlatform.Zhilian.label.lowercase(),
        JobPlatform.Zhilian.packageName.lowercase(),
        "zhilian",
        "zhaopin",
        "智联",
        "智联招聘" -> JobPlatform.Zhilian

        JobPlatform.Liepin.name.lowercase(),
        JobPlatform.Liepin.label.lowercase(),
        JobPlatform.Liepin.packageName.lowercase(),
        "liepin",
        "猎聘" -> JobPlatform.Liepin

        else -> JobPlatform.Boss
    }
}

private fun parseJobSourceMode(value: String): JobSourceMode = when (value.trim()) {
    JobSourceMode.HomeRecommendations.name,
    JobSourceMode.HomeRecommendations.label,
    "homeRecommendations",
    "homeRecommendation",
    "recommendations",
    "recommendation",
    "首页推荐",
    "推荐岗位" -> JobSourceMode.HomeRecommendations

    JobSourceMode.KeywordSearch.name,
    JobSourceMode.KeywordSearch.label,
    "keywordSearch",
    "searchKeywords",
    "关键词搜索",
    "搜索岗位" -> JobSourceMode.KeywordSearch

    else -> JobSourceMode.KeywordSearch
}

private fun List<String>.toJsonArray(): JSONArray = JSONArray().also { array ->
    forEach { array.put(it) }
}

private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray = JSONArray().also { array ->
    forEach { array.put(mapper(it)) }
}
