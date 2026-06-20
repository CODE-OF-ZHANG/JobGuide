package com.zhiyin.jobguide.automation

import com.zhiyin.jobguide.data.AppConfig
import com.zhiyin.jobguide.data.JobSnapshot
import com.zhiyin.jobguide.data.stableFingerprint

private val salaryRegex = Regex("""(\d+(?:\.\d+)?)\s*[-~至]\s*(\d+(?:\.\d+)?)\s*[kK千]?""")
private val percentOnlyRegex = Regex("""^\D*\d{1,3}\s*%\D*$""")

fun evaluateJob(texts: List<String>, keyword: String, config: AppConfig): JobSnapshot {
    val cleanTexts = texts
        .map(::normalizeCandidateText)
        .filter(::isUsefulVisibleText)
        .distinct()
    val joined = cleanTexts.joinToString(" ")
    val title = cleanJobTitle(guessTitle(cleanTexts, keyword, config), keyword)
    val salary = cleanTexts.firstOrNull { salaryRegex.containsMatchIn(it) }.orEmpty()
    val city = config.rules.targetCities.firstOrNull { joined.contains(it) }.orEmpty()
    val company = cleanCompany(guessCompany(cleanTexts, title, salary, city))

    var score = 0
    val reasons = mutableListOf<String>()

    val blocked = config.rules.jobBlacklist + config.rules.companyBlacklist
    val blockedHit = blocked.firstOrNull { joined.contains(it, ignoreCase = true) }
    if (blockedHit != null) {
        score -= 100
        reasons += "命中黑名单：$blockedHit"
    }

    if (keyword.isNotBlank() && joined.contains(keyword, ignoreCase = true)) {
        score += 35
        reasons += "包含搜索关键词：$keyword"
    } else {
        val keywordHits = keyword.split(" ", "　").filter { it.isNotBlank() && joined.contains(it, ignoreCase = true) }
        if (keywordHits.isNotEmpty()) {
            score += if (keywordHits.size >= 2) 25 else 15
            reasons += "命中关键词分词：${keywordHits.joinToString("、")}"
        }
    }

    config.rules.jobWhitelist.firstOrNull { joined.contains(it, ignoreCase = true) }?.let {
        score += 25
        reasons += "命中岗位白名单：$it"
    }

    if (config.rules.companyWhitelist.isNotEmpty()) {
        val companyHit = config.rules.companyWhitelist.firstOrNull { joined.contains(it, ignoreCase = true) }
        if (companyHit != null) {
            score += 15
            reasons += "命中公司白名单：$companyHit"
        } else {
            score -= 10
            reasons += "未命中公司白名单"
        }
    }

    if (config.rules.targetCities.isEmpty() || config.rules.targetCities.any { joined.contains(it) }) {
        score += 10
        if (city.isNotBlank()) reasons += "城市匹配"
    } else {
        score -= 10
        reasons += "城市不匹配"
    }

    if (salaryOverlaps(salary, config.rules.salaryMinK, config.rules.salaryMaxK)) {
        score += 10
        if (salary.isNotBlank()) reasons += "薪资范围匹配"
    } else {
        score -= 20
        reasons += "薪资范围不匹配"
    }

    if (config.rules.requiredKeywords.isNotEmpty()) {
        val requiredHit = config.rules.requiredKeywords.firstOrNull { joined.contains(it, ignoreCase = true) }
        if (requiredHit != null) {
            score += 15
            reasons += "命中必备关键词：$requiredHit"
        } else {
            score -= 20
            reasons += "未命中必备关键词"
        }
    }

    val fingerprint = stableFingerprint(listOf(title, company, city, salary).ifEmpty { cleanTexts.take(8) })
    return JobSnapshot(title, company, city, salary, cleanTexts, score, reasons, fingerprint)
}

fun isAllowed(snapshot: JobSnapshot, config: AppConfig, minScoreOverride: Int? = null): Pair<Boolean, String> {
    val joined = snapshot.texts.joinToString(" ")
    val blocked = config.rules.jobBlacklist + config.rules.companyBlacklist
    val blockedHit = blocked.firstOrNull { joined.contains(it, ignoreCase = true) }
    if (blockedHit != null) return false to "命中黑名单：$blockedHit"

    if (config.rules.jobWhitelist.isNotEmpty()) {
        val titleHit = config.rules.jobWhitelist.firstOrNull { joined.contains(it, ignoreCase = true) }
        if (titleHit == null) return false to "未命中岗位白名单"
    }

    if (config.rules.companyWhitelist.isNotEmpty()) {
        val companyHit = config.rules.companyWhitelist.firstOrNull { joined.contains(it, ignoreCase = true) }
        if (companyHit == null) return false to "未命中公司白名单"
    }

    val minScore = minScoreOverride ?: config.rules.minMatchScore
    if (snapshot.score < minScore) {
        return false to "匹配分不足：${snapshot.score} < $minScore"
    }

    return true to "符合白名单和评分规则"
}

private fun guessTitle(texts: List<String>, keyword: String, config: AppConfig): String {
    val titleHints = listOf(keyword) + config.rules.jobWhitelist + config.rules.requiredKeywords
    return texts.firstOrNull { text ->
        looksLikeJobTitle(text) && titleHints.any { it.isNotBlank() && text.contains(it, ignoreCase = true) }
    } ?: texts.firstOrNull {
        looksLikeJobTitle(it)
    }.orEmpty()
}

private fun guessCompany(texts: List<String>, title: String, salary: String, city: String): String {
    return texts.firstOrNull {
        it != title &&
            it != salary &&
            it != city &&
            !looksLikeJobTitle(it) &&
            !looksLikeNavigation(it) &&
            !looksLikeLongDescription(it) &&
            !looksLikeUiNoise(it)
    }.orEmpty()
}

private fun normalizeCandidateText(value: String): String {
    return value
        .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim { char -> !char.isLetterOrDigit() && char !in setOf('+', '#') }
}

private fun isUsefulVisibleText(value: String): Boolean {
    if (value.isBlank()) return false
    if (!value.any { it.isLetterOrDigit() }) return false
    if (percentOnlyRegex.matches(value)) return false
    if (value.contains("�")) return false
    if (looksLikeUiNoise(value) && !salaryRegex.containsMatchIn(value)) return false
    return true
}

private fun looksLikeJobTitle(value: String): Boolean {
    if (value.length !in 2..50) return false
    if (value == "岗位" || value == "职位") return false
    if (salaryRegex.containsMatchIn(value)) return false
    if (looksLikeNavigation(value) || looksLikeLongDescription(value) || looksLikeUiNoise(value)) return false

    val jobWords = listOf(
        "开发", "工程师", "程序员", "后端", "前端", "测试", "软件", "算法", "数据",
        "Java", "Python", "Android", "Kotlin", "AI", "运维", "实习", "全栈", "客户端"
    )
    return jobWords.any { value.contains(it, ignoreCase = true) }
}

private fun cleanJobTitle(value: String, keyword: String): String {
    val normalized = normalizeCandidateText(value)
    return if (looksLikeJobTitle(normalized)) normalized else keyword
}

private fun cleanCompany(value: String): String {
    val normalized = normalizeCandidateText(value)
    if (normalized.isBlank()) return ""
    if (normalized.contains("%") || normalized.contains("@") || normalized.contains("�")) return ""
    if (looksLikeUiNoise(normalized) || looksLikeNavigation(normalized) || looksLikeLongDescription(normalized)) return ""
    return normalized
}

private fun salaryOverlaps(value: String, minK: Int, maxK: Int): Boolean {
    val match = salaryRegex.find(value) ?: return true
    val low = match.groupValues[1].toFloatOrNull() ?: return true
    val high = match.groupValues[2].toFloatOrNull() ?: return true
    val min = minOf(low, high)
    val max = maxOf(low, high)
    return max >= minK && min <= maxK
}

private fun looksLikeNavigation(value: String): Boolean {
    val navigation = listOf("首页", "消息", "我的", "推荐", "附近", "筛选", "搜索", "职位", "公司")
    return value in navigation
}

private fun looksLikeLongDescription(value: String): Boolean {
    if (value.length > 80) return true
    return listOf("岗位职责", "任职要求", "职位描述", "工作内容", "查看更多").any { value.contains(it) }
}

private fun looksLikeUiNoise(value: String): Boolean {
    if (percentOnlyRegex.matches(value)) return true
    if (value.contains("%") && listOf("匹配", "合适", "简历").any { value.contains(it) }) return true
    if (value.length <= 2 && value.none { it.isLetter() }) return true
    return listOf(
        "立即沟通", "继续沟通", "打招呼", "发送", "收藏", "分享", "举报", "返回",
        "在线", "刚刚活跃", "今日活跃", "招聘中", "BOSS直聘", "请选择", "为你推荐"
    ).any { value.contains(it, ignoreCase = true) }
}
