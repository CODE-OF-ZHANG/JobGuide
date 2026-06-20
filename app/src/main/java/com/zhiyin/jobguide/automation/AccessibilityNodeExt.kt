package com.zhiyin.jobguide.automation

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

fun AccessibilityNodeInfo.visibleTexts(): List<String> {
    val result = mutableListOf<String>()
    walk { node ->
        listOf(node.text, node.contentDescription)
            .mapNotNull { it?.toString()?.trim() }
            .filter { it.isNotBlank() }
            .forEach { result += it }
    }
    return result.distinct()
}

fun AccessibilityNodeInfo.joinedText(): String = visibleTexts().joinToString(" ")

fun AccessibilityNodeInfo.walk(visitor: (AccessibilityNodeInfo) -> Unit) {
    visitor(this)
    for (index in 0 until childCount) {
        getChild(index)?.let { child ->
            child.walk(visitor)
            child.recycle()
        }
    }
}

fun AccessibilityNodeInfo.ownText(): String {
    return listOf(text, contentDescription)
        .mapNotNull { it?.toString()?.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

fun AccessibilityNodeInfo.resourceId(): String = viewIdResourceName.orEmpty()

fun AccessibilityNodeInfo.bounds(): Rect {
    val rect = Rect()
    getBoundsInScreen(rect)
    return rect
}

fun AccessibilityNodeInfo.setTextSafely(value: String): Boolean {
    val args = Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
    }
    return performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
}

fun AccessibilityNodeInfo.findFirst(
    textOptions: List<String>,
    clickable: Boolean? = null,
    classNameContains: String? = null
): AccessibilityNodeInfo? {
    var found: AccessibilityNodeInfo? = null
    walk { node ->
        if (found != null || !node.isEnabled) return@walk
        if (clickable != null && node.isClickable != clickable) return@walk
        if (classNameContains != null && !node.className?.toString().orEmpty().contains(classNameContains, ignoreCase = true)) return@walk
        val text = node.ownText().ifBlank { node.joinedText() }
        if (textOptions.any { it.isNotBlank() && text.contains(it, ignoreCase = true) }) {
            found = AccessibilityNodeInfo.obtain(node)
        }
    }
    return found
}

fun AccessibilityNodeInfo.findFirstNodeByResourceIds(resourceIds: List<String>): AccessibilityNodeInfo? {
    var found: AccessibilityNodeInfo? = null
    walk { node ->
        if (found != null || !node.isEnabled) return@walk
        if (node.matchesResourceId(resourceIds)) {
            found = AccessibilityNodeInfo.obtain(node)
        }
    }
    return found
}

fun AccessibilityNodeInfo.findFirstByResourceIds(resourceIds: List<String>): AccessibilityNodeInfo? {
    val found = findFirstNodeByResourceIds(resourceIds)
    return found?.clickableSelfOrParent(this) ?: found
}

fun AccessibilityNodeInfo.findBestSearchEntry(textOptions: List<String>, resourceIds: List<String>): AccessibilityNodeInfo? {
    findFirstSearchEntryByResourceIds(resourceIds)?.let { return it }
    findFirst(textOptions, clickable = true)?.let { return it }
    findFirst(textOptions, clickable = null)?.clickableSelfOrParent(this)?.let { return it }

    val screen = bounds()
    val candidates = mutableListOf<AccessibilityNodeInfo>()
    walk { node ->
        if (!node.isEnabled || !node.bounds().hasArea()) return@walk
        val rect = node.bounds()
        val id = node.resourceId().lowercase()
        val resourceName = id.substringAfterLast('/')
        val className = node.className?.toString().orEmpty()
        val ownText = node.ownText()
        val text = ownText.ifBlank { node.joinedText() }
        val joinedText = text.lowercase()
        val disqualifiesSearch = (ownText.isNotBlank() && nonSearchEntryWords.any { ownText.contains(it, ignoreCase = true) }) ||
            nonSearchEntryResourceWords.any { resourceName.contains(it, ignoreCase = true) }
        if (disqualifiesSearch) return@walk
        val looksLikeSearch = listOf("search", "query", "keyword", "key", "kw", "position").any { resourceName.contains(it) } ||
            textOptions.any { it.isNotBlank() && text.contains(it, ignoreCase = true) } ||
            listOf("搜索", "职位", "岗位", "公司", "关键字", "关键词", "工作").count { text.contains(it, ignoreCase = true) } >= 2 ||
            listOf("search", "job", "company", "keyword", "position").any { joinedText.contains(it) } ||
            className.contains("EditText", ignoreCase = true)
        val inTopArea = rect.top <= screen.top + screen.height() * 0.46f
        val usableWidth = rect.width() >= screen.width() * 0.20f
        val reasonableHeight = rect.height() in 28..(screen.height() * 0.22f).toInt()
        if (looksLikeSearch && inTopArea && usableWidth && reasonableHeight) {
            candidates += AccessibilityNodeInfo.obtain(node).clickableSelfOrParent(this) ?: AccessibilityNodeInfo.obtain(node)
        }
    }
    return candidates
        .distinctBy { it.resourceId() + it.bounds().flattenToString() }
        .sortedWith(compareBy<AccessibilityNodeInfo> { if (it.isClickable) 0 else 1 }.thenBy { it.bounds().top }.thenByDescending { it.bounds().width() })
        .firstOrNull()
}

private fun AccessibilityNodeInfo.findFirstSearchEntryByResourceIds(resourceIds: List<String>): AccessibilityNodeInfo? {
    var found: AccessibilityNodeInfo? = null
    val screen = bounds()
    walk { node ->
        if (found != null || !node.isEnabled || !node.matchesResourceId(resourceIds)) return@walk
        val rect = node.bounds()
        if (!rect.hasArea()) return@walk
        val resourceName = node.resourceId().lowercase().substringAfterLast('/')
        val ownText = node.ownText()
        val text = ownText.ifBlank { node.joinedText() }
        val disqualifiesSearch = (ownText.isNotBlank() && nonSearchEntryWords.any { ownText.contains(it, ignoreCase = true) }) ||
            nonSearchEntryResourceWords.any { resourceName.contains(it, ignoreCase = true) }
        if (disqualifiesSearch) return@walk
        val inTopArea = rect.top <= screen.top + screen.height() * 0.46f
        val reasonableHeight = rect.height() in 28..(screen.height() * 0.22f).toInt()
        if (inTopArea && reasonableHeight) {
            found = node.clickableSelfOrParent(this) ?: AccessibilityNodeInfo.obtain(node)
        }
    }
    return found
}

fun AccessibilityNodeInfo.findBestSearchInput(resourceIds: List<String>): AccessibilityNodeInfo? {
    findFirstInputByResourceIds(resourceIds)?.let { return it }

    val screen = bounds()
    val candidates = mutableListOf<AccessibilityNodeInfo>()
    walk { node ->
        if (!node.isEnabled || !node.isTextInputNode()) return@walk
        val rect = node.bounds()
        if (!rect.hasArea()) return@walk
        val inTopArea = rect.top <= screen.top + screen.height() * 0.48f
        val usableWidth = rect.width() >= screen.width() * 0.20f
        val reasonableHeight = rect.height() in 28..(screen.height() * 0.20f).toInt()
        if (inTopArea && usableWidth && reasonableHeight) {
            candidates += AccessibilityNodeInfo.obtain(node)
        }
    }

    return candidates
        .distinctBy { it.resourceId() + it.bounds().flattenToString() }
        .sortedWith(compareBy<AccessibilityNodeInfo> { it.bounds().top }.thenByDescending { it.bounds().width() })
        .firstOrNull()
}

fun AccessibilityNodeInfo.findBestSearchButton(
    textOptions: List<String>,
    resourceIds: List<String>
): AccessibilityNodeInfo? {
    findFirstByResourceIds(resourceIds)?.let { return it }

    val screen = bounds()
    val candidates = mutableListOf<AccessibilityNodeInfo>()
    walk { node ->
        if (!node.isEnabled) return@walk
        val text = node.ownText().ifBlank { node.joinedText() }
        if (textOptions.none { it.isNotBlank() && text.contains(it, ignoreCase = true) }) return@walk
        val rect = node.bounds()
        if (!rect.hasArea()) return@walk
        val inTopArea = rect.top <= screen.top + screen.height() * 0.48f
        val onRightSide = rect.centerX() >= screen.left + screen.width() * 0.55f
        val reasonableSize = rect.width() <= screen.width() * 0.36f && rect.height() <= screen.height() * 0.16f
        if (inTopArea && onRightSide && reasonableSize) {
            candidates += node.clickableSelfOrParent(this) ?: AccessibilityNodeInfo.obtain(node)
        }
    }

    return candidates
        .distinctBy { it.resourceId() + it.bounds().flattenToString() }
        .sortedWith(compareByDescending<AccessibilityNodeInfo> { it.bounds().right }.thenBy { it.bounds().top })
        .firstOrNull()
        ?: findFirst(textOptions, clickable = true)
        ?: findFirst(textOptions, clickable = null)?.clickableSelfOrParent(this)
}

fun AccessibilityNodeInfo.looksLikeSearchSuggestionPage(): Boolean {
    val joined = joinedText()
    return searchSuggestionPageWords.any { joined.contains(it, ignoreCase = true) }
}

fun AccessibilityNodeInfo.findFirstEditText(): AccessibilityNodeInfo? {
    var found: AccessibilityNodeInfo? = null
    walk { node ->
        if (found != null || !node.isEnabled) return@walk
        if (node.isTextInputNode()) {
            found = AccessibilityNodeInfo.obtain(node)
        }
    }
    return found
}

fun AccessibilityNodeInfo.findChatEditText(resourceIds: List<String> = emptyList()): AccessibilityNodeInfo? {
    findFirstInputByResourceIds(resourceIds)?.let { return it }

    val candidates = mutableListOf<AccessibilityNodeInfo>()
    val screen = bounds()
    walk { node ->
        if (!node.isEnabled) return@walk
        if (node.isTextInputNode()) {
            candidates += AccessibilityNodeInfo.obtain(node)
        }
    }
    return candidates
        .sortedWith(
            compareBy<AccessibilityNodeInfo> {
                val id = it.resourceId().lowercase()
                if (listOf("message", "msg", "chat", "input", "edit").any { part -> id.contains(part) }) 0 else 1
            }.thenBy {
                val text = it.ownText().lowercase()
                if (listOf("消息", "聊", "message", "content").any { part -> text.contains(part, ignoreCase = true) }) 0 else 1
            }.thenBy {
                val rect = it.bounds()
                if (rect.centerY() >= screen.height() * 0.55f) 0 else 1
            }.thenByDescending { it.bounds().centerY() }
        )
        .firstOrNull()
}

fun AccessibilityNodeInfo.findCommunicateButton(
    resourceIds: List<String>,
    textOptions: List<String>,
    excludedTexts: List<String>
): AccessibilityNodeInfo? {
    findFirstByResourceIds(resourceIds)
        ?.takeUnless { it.containsAnyText(excludedTexts) }
        ?.let { return it }

    val screen = bounds()
    val candidates = mutableListOf<AccessibilityNodeInfo>()
    walk { node ->
        if (!node.isEnabled) return@walk
        val text = node.ownText().ifBlank { node.joinedText() }
        if (textOptions.none { it.isNotBlank() && text.contains(it, ignoreCase = true) }) return@walk
        if (excludedTexts.any { it.isNotBlank() && text.contains(it, ignoreCase = true) }) return@walk
        val target = node.clickableSelfOrParent(this) ?: AccessibilityNodeInfo.obtain(node)
        if (target.containsAnyText(excludedTexts)) return@walk
        candidates += target
    }

    return candidates
        .distinctBy { it.resourceId() + it.bounds().flattenToString() }
        .sortedWith(
            compareBy<AccessibilityNodeInfo> {
                val rect = it.bounds()
                if (rect.centerY() >= screen.top + screen.height() * 0.62f) 0 else 1
            }.thenByDescending { it.bounds().centerY() }.thenBy { it.bounds().left }
        )
        .firstOrNull()
}

fun AccessibilityNodeInfo.findBestSendButton(
    textOptions: List<String>,
    resourceIds: List<String>
): AccessibilityNodeInfo? {
    findFirstByResourceIds(resourceIds)?.let { return it }
    findFirst(textOptions, clickable = true)?.let { return it }
    findFirst(textOptions, clickable = null)?.clickableSelfOrParent(this)?.let { return it }

    val editNode = findChatEditText() ?: findFirstEditText() ?: return null
    val editBounds = editNode.bounds()
    val screen = bounds()
    val rowTop = editBounds.top - maxOf(24, (editBounds.height() * 0.8f).toInt())
    val rowBottom = editBounds.bottom + maxOf(24, (editBounds.height() * 0.8f).toInt())
    val candidates = mutableListOf<AccessibilityNodeInfo>()

    walk { node ->
        if (!node.isEnabled || !node.isClickable) return@walk
        val rect = node.bounds()
        if (!rect.hasArea()) return@walk
        val sameInputRow = rect.centerY() in rowTop..rowBottom
        val rightOfInput = rect.centerX() > editBounds.right || rect.left >= editBounds.centerX()
        val reasonableSize = rect.area() <= screen.area() * 0.12 && rect.width() <= screen.width() * 0.35f
        val insideScreen = rect.right <= screen.right && rect.left >= screen.left
        if (sameInputRow && rightOfInput && reasonableSize && insideScreen) {
            candidates += AccessibilityNodeInfo.obtain(node)
        }
    }

    return candidates
        .distinctBy { it.resourceId() + it.bounds().flattenToString() }
        .sortedWith(
            compareBy<AccessibilityNodeInfo> { abs(it.bounds().centerY() - editBounds.centerY()) }
                .thenByDescending { it.bounds().centerX() }
                .thenBy { it.bounds().area() }
        )
        .firstOrNull()
}

fun AccessibilityNodeInfo.debugSummary(maxItems: Int = 24): String {
    val items = mutableListOf<String>()
    walk { node ->
        if (items.size >= maxItems || !node.isEnabled) return@walk
        val text = node.ownText()
        val id = node.resourceId()
        if (text.isNotBlank() || id.isNotBlank()) {
            val label = listOf(text, id.substringAfterLast('/')).filter { it.isNotBlank() }.joinToString("@")
            if (label.isNotBlank()) items += label.take(40)
        }
    }
    return items.distinct().joinToString(" | ")
}

private fun AccessibilityNodeInfo.clickableSelfOrParent(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    if (isClickable) return this
    val targetBounds = bounds()
    var best: AccessibilityNodeInfo? = null
    root.walk { node ->
        if (!node.isEnabled || !node.isClickable) return@walk
        val rect = node.bounds()
        if (rect.contains(targetBounds) && rect.hasArea()) {
            val current = best
            if (current == null || rect.area() < current.bounds().area()) {
                best = AccessibilityNodeInfo.obtain(node)
            }
        }
    }
    return best
}

private fun AccessibilityNodeInfo.findFirstInputByResourceIds(resourceIds: List<String>): AccessibilityNodeInfo? {
    var found: AccessibilityNodeInfo? = null
    walk { node ->
        if (found != null || !node.isEnabled) return@walk
        if (node.matchesResourceId(resourceIds) && node.isTextInputNode()) {
            found = AccessibilityNodeInfo.obtain(node)
        }
    }
    return found
}

private fun AccessibilityNodeInfo.matchesResourceId(resourceIds: List<String>): Boolean {
    val id = resourceId()
    if (id.isBlank()) return false
    return resourceIds.any { target ->
        val normalized = target.trim()
        normalized.isNotBlank() && (id == normalized || id.endsWith(normalized))
    }
}

private fun AccessibilityNodeInfo.isTextInputNode(): Boolean {
    val className = className?.toString().orEmpty()
    return isEditable ||
        className.contains("EditText", ignoreCase = true) ||
        className.contains("AutoCompleteTextView", ignoreCase = true)
}

private fun AccessibilityNodeInfo.containsAnyText(textOptions: List<String>): Boolean {
    val text = ownText().ifBlank { joinedText() }
    return textOptions.any { it.isNotBlank() && text.contains(it, ignoreCase = true) }
}

private fun Rect.hasArea(): Boolean = width() > 0 && height() > 0

private fun Rect.area(): Int = width() * height()

private val nonSearchEntryWords = listOf(
    "求职币", "金币", "奖励", "浏览", "签到", "领取", "新人福利", "简历快推",
    "快速入职", "快速入职岗位", "找工作热榜", "更多会场", "会场"
)

private val nonSearchEntryResourceWords = listOf(
    "coin", "gold", "bonus", "reward", "welfare", "benefit", "wallet"
)

private val candidateSalaryRegex = Regex("""\d+(?:\.\d+)?\s*[-~至]\s*\d+(?:\.\d+)?\s*[kK千]?""")

private val candidateJobWords = listOf(
    "开发", "工程师", "程序员", "后端", "前端", "测试", "软件", "算法", "数据",
    "Java", "Python", "Android", "Kotlin", "AI", "运维", "实习", "全栈", "客户端",
    "产品", "运营", "设计", "UI", "项目经理"
)

private val nonJobCardStopWords = listOf(
    "广告", "推广", "赞助", "会员", "权益", "开通", "领取", "课程", "直播", "测评",
    "活动", "专题", "订阅", "求职助手", "职场资讯", "面试辅导", "面试题",
    "简历优化", "简历诊断", "简历模板", "简历刷新", "简历曝光", "上传简历",
    "完善简历", "投递加速", "训练营", "培训班", "立即报名", "查看报告",
    "推荐卡片", "相关推荐", "猜你喜欢", "历史记录", "搜索发现", "更多会场",
    "找工作热榜", "会场"
)

private val searchSuggestionPageWords = listOf(
    "历史记录", "搜索发现"
)

fun AccessibilityNodeInfo.looksLikeJob51TopicListPage(): Boolean {
    val joined = joinedText()
    val titleHit = listOf(
        "快速入职岗位",
        "快速入职",
        "简历快推",
        "热门城市高薪急招岗位",
        "热门城市高薪急招",
        "高薪急招岗位"
    ).any { joined.contains(it, ignoreCase = true) }
    if (!titleHit) return false

    val actionHints = listOf("聊聊", "投递", "查看详情")
        .count { joined.contains(it, ignoreCase = true) }
    return actionHints >= 2
}

fun AccessibilityNodeInfo.findJobCandidates(keyword: String): List<AccessibilityNodeInfo> {
    if (looksLikeSearchSuggestionPage()) return emptyList()

    val candidates = mutableListOf<AccessibilityNodeInfo>()
    val screen = bounds()
    walk { node ->
        if (!node.isEnabled || !node.isClickable) return@walk
        val rect = node.bounds()
        if (rect.width() < screen.width() * 0.35f) return@walk
        if (rect.height() < 150 || rect.height() > screen.height() * 0.5f) return@walk
        val texts = node.visibleTexts()
        val cleanTexts = texts
            .flatMap(::candidateTextSegments)
            .map(::normalizeCandidateCardText)
            .filter { it.isNotBlank() }
            .distinct()
        if (cleanTexts.isEmpty()) return@walk
        val joined = cleanTexts.joinToString(" ")

        val hasSalary = candidateSalaryRegex.containsMatchIn(joined)
        val hasJobTitle = cleanTexts.any(::looksLikeCandidateJobTitle) ||
            candidateJobWords.any { joined.contains(it, ignoreCase = true) }
        val hasKeywordHit = keyword.split(" ", "　").any { it.isNotBlank() && joined.contains(it, ignoreCase = true) }
        if (looksLikeNonJobCard(joined, hasJobTitle && (hasSalary || hasKeywordHit))) return@walk
        val looksLikeJob = hasJobTitle && (hasSalary || hasKeywordHit)
        if (looksLikeJob) candidates += AccessibilityNodeInfo.obtain(node)
    }
    return candidates.sortedWith(compareBy<AccessibilityNodeInfo> { it.bounds().top }.thenByDescending { it.bounds().height() })
}

fun AccessibilityNodeInfo.findJobListSwipeBounds(keyword: String): Rect? {
    val screen = bounds()
    val candidateRects = findJobCandidates(keyword)
        .map { it.bounds() }
        .filter { rect ->
            rect.hasArea() &&
                rect.centerY() > screen.top + screen.height() * 0.16f &&
                rect.centerY() < screen.bottom - screen.height() * 0.05f
        }

    if (candidateRects.isEmpty()) return null

    val topGuard = screen.top + (screen.height() * 0.18f).toInt()
    val bottomGuard = screen.bottom - maxOf(96, (screen.height() * 0.08f).toInt())
    val left = maxOf(screen.left + 12, candidateRects.minOf { it.left })
    val right = minOf(screen.right - 12, candidateRects.maxOf { it.right })
    val top = maxOf(topGuard, candidateRects.minOf { it.top })
    var bottom = bottomGuard.coerceAtMost(screen.bottom - 8)

    if (bottom <= top + 220) {
        bottom = candidateRects.maxOf { it.bottom }.coerceAtMost(screen.bottom - 8)
    }
    if (right <= left + 120 || bottom <= top + 220) return null

    return Rect(left, top, right, bottom)
}

private fun normalizeCandidateCardText(value: String): String {
    return value
        .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun candidateTextSegments(value: String): List<String> {
    val normalized = value
        .replace("\r", "\n")
        .replace(Regex("[｜|·•]"), "\n")
    return (listOf(value) + normalized.split("\n", "  "))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun looksLikeCandidateJobTitle(value: String): Boolean {
    if (value.length !in 2..50) return false
    if (value == "岗位" || value == "职位") return false
    if (candidateSalaryRegex.containsMatchIn(value)) return false
    if (nonJobCardStopWords.filterNot { it == "广告" }.any { value.contains(it, ignoreCase = true) }) return false
    return candidateJobWords.any { value.contains(it, ignoreCase = true) }
}

private fun looksLikeNonJobCard(joined: String, strongJobSignal: Boolean): Boolean {
    if (joined.isBlank()) return true
    val stopWords = if (strongJobSignal) {
        nonJobCardStopWords.filterNot { it == "广告" }
    } else {
        nonJobCardStopWords
    }
    return stopWords.any { joined.contains(it, ignoreCase = true) }
}
