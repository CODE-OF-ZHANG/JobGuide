package com.zhiyin.jobguide.automation

import com.zhiyin.jobguide.data.JobPlatform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageHeuristicsTest {
    @Test
    fun job51HomeJobCardTextIsNotSearchEntry() {
        val joinedText = """
            C++后端开发工程师
            8-13K
            长沙
            经验不限
            去聊聊
            投递简历
        """.trimIndent()

        assertFalse(PageHeuristics.canUseJob51SearchCoordinateFallback(joinedText))
    }

    @Test
    fun job51SearchPageTextAllowsSearchCoordinateFallback() {
        val joinedText = """
            搜索职位/公司
            推荐
            附近
            最新
            筛选
        """.trimIndent()

        assertTrue(PageHeuristics.canUseJob51SearchCoordinateFallback(joinedText))
    }

    @Test
    fun job51HomeSearchEntryAllowsSearchCoordinateFallbackWithoutResultTabs() {
        val joinedText = """
            搜索职位/公司
            首页
            消息
            我的
        """.trimIndent()

        assertTrue(PageHeuristics.canUseJob51SearchCoordinateFallback(joinedText))
    }

    @Test
    fun job51HomeTabsAllowSearchCoordinateFallbackWhenSearchTextIsMissing() {
        val joinedText = """
            首页
            工作
            消息
            我的
            C++后端开发工程师
            8-13K
            长沙
        """.trimIndent()

        assertTrue(PageHeuristics.canUseJob51SearchCoordinateFallback(joinedText))
    }

    @Test
    fun job51JobDetailPageWithGoChatIsNotTreatedAsSearchResults() {
        val joinedText = """
            Java开发工程师
            8-13K
            职位详情
            岗位职责
            公司信息
            工作地址
            去聊聊
        """.trimIndent()

        assertTrue(PageHeuristics.isJob51JobDetailPage(joinedText))
        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Job51,
                joinedText = joinedText,
                keyword = "Java",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun job51JobDetailPageWithCommonSectionsAndGoChatIsNotTreatedAsSearchResults() {
        val joinedText = """
            C++后端开发工程师
            8-13K
            长沙
            岗位要求
            职位诱惑
            公司介绍
            去聊聊
        """.trimIndent()

        assertTrue(PageHeuristics.isJob51JobDetailPage(joinedText))
        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Job51,
                joinedText = joinedText,
                keyword = "C++",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun liepinJobDetailPageIsNotTreatedAsSearchResults() {
        val joinedText = """
            Python开发工程师
            15-25K
            职位详情
            职位介绍
            公司信息
            工作地址
            聊一聊
        """.trimIndent()

        assertTrue(PageHeuristics.isLiepinJobDetailPage(joinedText))
        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Liepin,
                joinedText = joinedText,
                keyword = "Python",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun liepinJobDetailPageWithoutChatActionStillDetected() {
        // After communicating, the detail page may show "继续沟通" or no chat button at all
        val joinedText = """
            Python开发工程师
            15-25K
            职位详情
            职位介绍
            工作地址
        """.trimIndent()

        assertTrue(PageHeuristics.isLiepinJobDetailPage(joinedText))
    }

    @Test
    fun liepinJobDetailPageWithContinueCommunicate() {
        // Detail page showing "继续沟通" after already having chatted
        val joinedText = """
            Python开发工程师
            15-25K
            职位详情
            公司信息
            继续沟通
        """.trimIndent()

        assertTrue(PageHeuristics.isLiepinJobDetailPage(joinedText))
        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Liepin,
                joinedText = joinedText,
                keyword = "Python",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun liepinSearchResultsPageStillCountsAsSearchResults() {
        val joinedText = """
            搜索
            推荐
            附近
            最新
            Python开发工程师
            15-25K
        """.trimIndent()

        assertFalse(PageHeuristics.isLiepinJobDetailPage(joinedText))
        assertTrue(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Liepin,
                joinedText = joinedText,
                keyword = "Python",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun liepinChatPageIsNotTreatedAsSearchResultsEvenIfJobCardTextExists() {
        val joinedText = """
            在线
            继续沟通
            发送
            Python开发工程师
            15-25K
            你好，我对这个岗位很感兴趣
        """.trimIndent()

        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Liepin,
                joinedText = joinedText,
                keyword = "Python",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = true,
                hasSendButton = true
            )
        )
    }

    @Test
    fun bossJobDetailPageIsNotTreatedAsSearchResults() {
        val joinedText = """
            Python开发工程师
            15-25K
            职位详情
            职位描述
            公司信息
            任职要求
            立即沟通
        """.trimIndent()

        assertTrue(PageHeuristics.isBossJobDetailPage(joinedText))
        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Boss,
                joinedText = joinedText,
                keyword = "Python",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun bossJobDetailPageWithMinimalHintsStillDetected() {
        val joinedText = """
            Python开发工程师
            15-25K
            职位详情
            岗位职责
        """.trimIndent()

        assertTrue(PageHeuristics.isBossJobDetailPage(joinedText))
    }

    @Test
    fun bossSearchResultsPageNotTreatedAsDetailPage() {
        val joinedText = """
            推荐
            附近
            最新
            急招
            Python开发工程师
            15-25K
        """.trimIndent()

        assertFalse(PageHeuristics.isBossJobDetailPage(joinedText))
        assertTrue(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Boss,
                joinedText = joinedText,
                keyword = "Python",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun bossSearchResultsPageAfterAutoBackIsRecognizedAsList() {
        val joinedText = """
            推荐
            附近
            最新
            筛选
            Java后端开发工程师
            8-13K
            刚刚活跃
            长沙
        """.trimIndent()

        assertFalse(PageHeuristics.isBossJobDetailPage(joinedText))
        assertTrue(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Boss,
                joinedText = joinedText,
                keyword = "Java",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun bossChatPageIsNotTreatedAsSearchResults() {
        val joinedText = """
            在线
            发送
            Python开发工程师
            15-25K
            你好，我对这个岗位很感兴趣
        """.trimIndent()

        assertTrue(PageHeuristics.isBossChatPage(joinedText, hasChatInput = true, hasSendButton = true))
        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Boss,
                joinedText = joinedText,
                keyword = "Python",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = true,
                hasSendButton = true
            )
        )
    }

    @Test
    fun bossChatPageWithInputOnlyStillDetected() {
        // After sending a message, the send button may revert, but chat input remains
        val joinedText = """
            在线
            我:
            Python开发工程师
        """.trimIndent()

        assertTrue(PageHeuristics.isBossChatPage(joinedText, hasChatInput = true, hasSendButton = false))
    }

    @Test
    fun zhilianJobDetailPageIsNotTreatedAsSearchResults() {
        val joinedText = """
            Java开发工程师
            8-13K
            职位描述
            更新于
            已认证
            聊一聊
        """.trimIndent()

        assertTrue(PageHeuristics.isZhilianJobDetailPage(joinedText))
        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Zhilian,
                joinedText = joinedText,
                keyword = "Java",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun zhilianJobDetailPageWithDeliveryOnlyIsDetected() {
        val joinedText = """
            Java开发工程师
            8-13K
            职位描述
            更新于
            已认证
            立即投递
        """.trimIndent()

        assertTrue(PageHeuristics.isZhilianJobDetailPage(joinedText))
    }

    @Test
    fun zhilianChatPageIsNotTreatedAsSearchResults() {
        val joinedText = """
            在线
            发送
            Java开发工程师
            你好，我对这个岗位很感兴趣
        """.trimIndent()

        assertTrue(PageHeuristics.isZhilianChatPage(joinedText, hasChatInput = true, hasSendButton = true))
        assertFalse(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Zhilian,
                joinedText = joinedText,
                keyword = "Java",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = true,
                hasSendButton = true
            )
        )
    }

    @Test
    fun zhilianSearchResultsPageIsRecognizedAsList() {
        val joinedText = """
            推荐
            附近
            最新
            急招
            Java开发工程师
            8-13K
        """.trimIndent()

        assertFalse(PageHeuristics.isZhilianJobDetailPage(joinedText))
        assertTrue(
            PageHeuristics.looksLikeSearchResultsPage(
                platform = JobPlatform.Zhilian,
                joinedText = joinedText,
                keyword = "Java",
                hasJobCandidates = true,
                hasSearchInput = false,
                hasChatInput = false,
                hasSendButton = false
            )
        )
    }

    @Test
    fun zhilianChatPageWithInputOnlyStillDetected() {
        // After auto-greeting sends, send button may revert but chat input remains
        val joinedText = """
            在线
            我:
            Java开发工程师
        """.trimIndent()

        assertTrue(PageHeuristics.isZhilianChatPage(joinedText, hasChatInput = true, hasSendButton = false))
    }
}
