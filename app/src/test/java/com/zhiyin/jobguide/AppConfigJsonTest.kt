package com.zhiyin.jobguide

import com.zhiyin.jobguide.data.AppConfig
import com.zhiyin.jobguide.data.JobPlatform
import com.zhiyin.jobguide.data.GreetingTemplate
import com.zhiyin.jobguide.data.JobSourceMode
import com.zhiyin.jobguide.data.JobSnapshot
import com.zhiyin.jobguide.data.ProfileConfig
import com.zhiyin.jobguide.data.parseRecords
import com.zhiyin.jobguide.data.renderGreetingTemplate
import com.zhiyin.jobguide.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigJsonTest {
    @Test
    fun oldJsonDefaultsToKeywordSearchSource() {
        val config = AppConfig.fromJson(
            """
            {
              "runMode": "ConfirmBeforeSend",
              "autoGreetingEnabled": false
            }
            """.trimIndent()
        )

        assertEquals(JobSourceMode.KeywordSearch, config.jobSourceMode)
        assertEquals(JobPlatform.Boss, config.jobPlatform)
        assertEquals(5, config.recommendationGreetingTarget)
    }

    @Test
    fun recommendationSourceRoundTripsThroughJson() {
        val source = AppConfig(
            jobSourceMode = JobSourceMode.HomeRecommendations,
            recommendationGreetingTarget = 3
        )

        val parsed = AppConfig.fromJson(source.toJson())

        assertEquals(JobSourceMode.HomeRecommendations, parsed.jobSourceMode)
        assertEquals(3, parsed.recommendationGreetingTarget)
    }

    @Test
    fun platformsRoundTripThroughJson() {
        JobPlatform.entries.forEach { platform ->
            val source = AppConfig(jobPlatform = platform)

            val parsed = AppConfig.fromJson(source.toJson())

            assertEquals(platform, parsed.jobPlatform)
        }
    }

    @Test
    fun platformAliasesParseFromJson() {
        assertEquals(JobPlatform.Job51, AppConfig.fromJson("""{"platform":"前程无忧"}""").jobPlatform)
        assertEquals(JobPlatform.Zhilian, AppConfig.fromJson("""{"招聘平台":"智联招聘"}""").jobPlatform)
        assertEquals(JobPlatform.Liepin, AppConfig.fromJson("""{"jobPlatform":"com.lietou.mishu"}""").jobPlatform)
    }

    @Test
    fun oldRecordDefaultsToBossPlatform() {
        val records = parseRecords(
            """
            [
              {
                "fingerprint": "abc",
                "keyword": "Java",
                "title": "Java 开发",
                "company": "测试公司",
                "city": "长沙",
                "salary": "5-8K",
                "action": "已打招呼",
                "reason": "旧记录"
              }
            ]
            """.trimIndent()
        )

        assertEquals(JobPlatform.Boss, records.single().platform)
    }

    @Test
    fun greetingTemplateReplacesVariables() {
        val template = GreetingTemplate(
            title = "测试模板",
            body = "老师您好，我是{name}，想了解{job_title}。"
        )

        val rendered = renderGreetingTemplate(
            template = template,
            snapshot = JobSnapshot(
                title = "C++开发",
                company = "测试公司",
                city = "长沙",
                salary = "8-13K",
                texts = emptyList(),
                score = 100,
                reasons = emptyList(),
                fingerprint = "snapshot"
            ),
            keyword = "C++",
            profile = ProfileConfig(name = "张旭")
        )

        assertEquals("老师您好，我是张旭，想了解C++开发。", rendered)
    }

    @Test
    fun greetingTemplateReplacesAllVariables() {
        val template = GreetingTemplate(
            title = "完整模板",
            body = "{name}申请{company}的{job_title}，{city}{salary}，擅长{skills}，对{keyword}感兴趣"
        )

        val rendered = renderGreetingTemplate(
            template = template,
            snapshot = JobSnapshot(
                title = "Java开发",
                company = "某科技公司",
                city = "北京",
                salary = "15-25K",
                texts = emptyList(),
                score = 100,
                reasons = emptyList(),
                fingerprint = "snapshot"
            ),
            keyword = "Java开发",
            profile = ProfileConfig(
                name = "李明",
                skills = listOf("Kotlin", "Spring Boot", "MySQL")
            )
        )

        assertEquals("李明申请某科技公司的Java开发，北京15-25K，擅长Kotlin、Spring Boot、MySQL，对Java开发感兴趣", rendered)
    }

    @Test
    fun greetingTemplateUsesFallbacksForEmptyValues() {
        val template = GreetingTemplate(
            title = "兜底模板",
            body = "我是{name}，看到{company}的{job_title}岗位"
        )

        val rendered = renderGreetingTemplate(
            template = template,
            snapshot = JobSnapshot(
                title = "",
                company = "",
                city = "",
                salary = "",
                texts = emptyList(),
                score = 100,
                reasons = emptyList(),
                fingerprint = "snapshot"
            ),
            keyword = "开发",
            profile = ProfileConfig(name = "张旭")
        )

        assertEquals("我是张旭，看到贵公司的该岗位岗位", rendered)
    }
}
