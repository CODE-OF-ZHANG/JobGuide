package com.zhiyin.jobguide.automation

import com.zhiyin.jobguide.data.JobPlatform

object PageHeuristics {
    private val searchSuggestionHints = listOf("历史记录", "搜索发现")
    private val searchResultHints = listOf("推荐", "附近", "最新", "急招", "筛选")
    private val job51SearchEntryHints = listOf(
        "搜索职位/公司",
        "职位名/公司名",
        "关键字/职位/公司",
        "搜索职位、公司、关键字",
        "搜索职位、公司或关键字",
        "搜职位/公司",
        "职位、公司、关键字",
        "职位 公司 关键字",
        "想搜什么职位",
        "搜索工作",
        "请输入职位或公司"
    )
    private val job51DetailHints = listOf(
        "职位详情",
        "职位描述",
        "岗位职责",
        "岗位要求",
        "任职要求",
        "职位要求",
        "职位诱惑",
        "职位福利",
        "公司信息",
        "公司介绍",
        "工作地址",
        "福利待遇"
    )
    private val job51ChatActions = listOf("去聊聊", "立即沟通", "继续沟通", "沟通", "打招呼", "聊一聊")
    private val job51DetailOnlyHints = listOf("职位详情", "职位描述", "岗位职责", "岗位要求", "任职要求", "工作地址", "职位要求")
    private val liepinDetailHints = listOf("职位详情", "职位介绍", "公司信息", "工作地址", "职位要求", "任职要求", "福利待遇", "薪资待遇")
    private val liepinChatActions = listOf("聊一聊", "立即沟通", "继续沟通", "沟通", "打招呼", "立即聊", "联系HR", "联系招聘者")
    private val liepinDetailOnlyHints = listOf("职位详情", "职位介绍", "工作地址", "职位要求", "任职要求")
    private val liepinChatHints = listOf("发送", "在线", "输入消息", "聊过", "继续沟通", "沟通记录")
    private val bossDetailHints = listOf("职位详情", "职位描述", "岗位职责", "任职要求", "公司信息", "工商信息", "福利待遇", "薪资待遇", "工作地址")
    private val bossDetailOnlyHints = listOf("职位详情", "职位描述", "岗位职责", "任职要求")
    private val bossChatHints = listOf("发送", "在线", "输入消息", "聊过", "继续沟通", "沟通记录", "我:", "对方:")
    private val zhilianDetailHints = listOf("职位描述", "更新于", "求职安全中心", "企业汇款认证", "校招网申", "已认证")
    private val zhilianDetailActionHints = listOf("聊一聊", "立即投递", "投简历", "投递简历")
    private val zhilianChatHints = listOf("发送", "在线", "输入消息", "聊过", "继续沟通", "沟通记录", "我:", "对方:")

    fun canUseJob51SearchCoordinateFallback(joinedText: String): Boolean {
        val normalized = joinedText.normalizePageText()
        if (searchSuggestionHints.any { normalized.contains(it) }) return false
        if (isJob51JobDetailPage(normalized)) return false
        val hasSearchEntry = job51SearchEntryHints.any { normalized.contains(it.normalizePageText()) }
        val hasHomeTabs = listOf("首页", "工作", "消息", "我的").count { normalized.contains(it) } >= 3
        return hasSearchEntry || hasHomeTabs
    }

    fun isJob51JobDetailPage(joinedText: String): Boolean {
        val normalized = joinedText.normalizePageText()
        val detailHitCount = job51DetailHints.count { normalized.contains(it) }
        val hasChatAction = job51ChatActions.any { normalized.contains(it) }
        if (detailHitCount >= 2 && hasChatAction) return true
        val detailOnlyHitCount = job51DetailOnlyHints.count { normalized.contains(it) }
        if (detailOnlyHitCount >= 2) return true
        return false
    }

    fun isLiepinJobDetailPage(joinedText: String): Boolean {
        val normalized = joinedText.normalizePageText()
        val detailHitCount = liepinDetailHints.count { normalized.contains(it) }
        val hasChatAction = liepinChatActions.any { normalized.contains(it) }
        if (detailHitCount >= 2 && hasChatAction) return true
        // Also detect detail pages that lack chat action buttons (e.g., after communication)
        val detailOnlyHitCount = liepinDetailOnlyHints.count { normalized.contains(it) }
        if (detailOnlyHitCount >= 2) return true
        return false
    }

    fun looksLikeSearchResultsPage(
        platform: JobPlatform,
        joinedText: String,
        keyword: String,
        hasJobCandidates: Boolean,
        hasSearchInput: Boolean,
        hasChatInput: Boolean,
        hasSendButton: Boolean
    ): Boolean {
        val normalized = joinedText.normalizePageText()
        if (searchSuggestionHints.any { normalized.contains(it) }) return false
        if (platform == JobPlatform.Job51 && isJob51JobDetailPage(normalized)) return false
        if (platform == JobPlatform.Liepin && isLiepinJobDetailPage(normalized)) return false
        if (platform == JobPlatform.Liepin && isLiepinChatPage(normalized, hasChatInput, hasSendButton)) return false
        if (platform == JobPlatform.Boss && isBossJobDetailPage(normalized)) return false
        if (platform == JobPlatform.Boss && isBossChatPage(normalized, hasChatInput, hasSendButton)) return false
        if (platform == JobPlatform.Zhilian && isZhilianJobDetailPage(normalized)) return false
        if (platform == JobPlatform.Zhilian && isZhilianChatPage(normalized, hasChatInput, hasSendButton)) return false
        if (hasJobCandidates) return true

        val hasResultTabs = searchResultHints.count { normalized.contains(it) } >= 2
        val hasKeyword = keyword.isBlank() || normalized.contains(keyword, ignoreCase = true)
        return hasResultTabs && hasKeyword && !hasSearchInput
    }

    fun isLiepinChatPage(
        joinedText: String,
        hasChatInput: Boolean,
        hasSendButton: Boolean
    ): Boolean {
        // Primary detection: chat input + send button
        if (hasChatInput && hasSendButton) return true
        // Fallback: if we have a chat input but send button is gone (e.g. after sending,
        // the button reverts to a "+" icon), check for chat-specific text hints.
        // This prevents misidentifying the page after a message has been sent.
        if (hasChatInput && liepinChatHints.any { joinedText.contains(it) }) return true
        return false
    }

    fun isBossJobDetailPage(joinedText: String): Boolean {
        val normalized = joinedText.normalizePageText()
        val detailHitCount = bossDetailHints.count { normalized.contains(it) }
        if (detailHitCount >= 2) return true
        val detailOnlyHitCount = bossDetailOnlyHints.count { normalized.contains(it) }
        if (detailOnlyHitCount >= 2) return true
        return false
    }

    fun isBossChatPage(
        joinedText: String,
        hasChatInput: Boolean,
        hasSendButton: Boolean
    ): Boolean {
        // Primary detection: chat input + send button
        if (hasChatInput && hasSendButton) return true
        // Fallback: chat input with chat-specific text hints
        if (hasChatInput && bossChatHints.any { joinedText.contains(it) }) return true
        return false
    }

    fun isZhilianJobDetailPage(joinedText: String): Boolean {
        val normalized = joinedText.normalizePageText()
        val detailHitCount = zhilianDetailHints.count { normalized.contains(it) }
        val hasActionHint = zhilianDetailActionHints.any { normalized.contains(it) }
        if (detailHitCount >= 2 && hasActionHint) return true
        return false
    }

    fun isZhilianChatPage(
        joinedText: String,
        hasChatInput: Boolean,
        hasSendButton: Boolean
    ): Boolean {
        // Primary detection: chat input + send button
        if (hasChatInput && hasSendButton) return true
        // Fallback: chat input with chat-specific text hints
        if (hasChatInput && zhilianChatHints.any { joinedText.contains(it) }) return true
        return false
    }

    private fun String.normalizePageText(): String {
        return replace(Regex("\\s+"), "")
    }
}
