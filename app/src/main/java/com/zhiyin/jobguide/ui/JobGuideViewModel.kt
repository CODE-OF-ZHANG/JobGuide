package com.zhiyin.jobguide.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhiyin.jobguide.automation.JobGuideAccessibilityService
import com.zhiyin.jobguide.data.AppConfig
import com.zhiyin.jobguide.data.AutomationCommandType
import com.zhiyin.jobguide.data.GreetingTemplate
import com.zhiyin.jobguide.data.JobGuideRepository
import com.zhiyin.jobguide.data.JobPlatform
import com.zhiyin.jobguide.data.JobRecord
import com.zhiyin.jobguide.data.JobSourceMode
import com.zhiyin.jobguide.data.KeywordSetting
import com.zhiyin.jobguide.data.LogLevel
import com.zhiyin.jobguide.data.ProfileConfig
import com.zhiyin.jobguide.data.RunLog
import com.zhiyin.jobguide.data.RunMode
import com.zhiyin.jobguide.data.RunStats
import com.zhiyin.jobguide.data.RulesConfig
import com.zhiyin.jobguide.data.ServiceLocator
import com.zhiyin.jobguide.data.TaskStatus
import com.zhiyin.jobguide.notification.StopNotifier
import com.zhiyin.jobguide.data.displayText
import com.zhiyin.jobguide.data.splitInput
import com.zhiyin.jobguide.data.toJson
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class JobGuideUiState(
    val config: AppConfig = AppConfig(),
    val stats: RunStats = RunStats(),
    val logs: List<RunLog> = emptyList(),
    val records: List<JobRecord> = emptyList(),
    val accessibilityEnabled: Boolean = false,
    val accessibilityWasEnabled: Boolean = false,
    val exportedJson: String = "",
    val importText: String = ""
) {
    val remainingGreetings: Int
        get() = (config.rules.dailyGreetingLimit - stats.todayGreetings).coerceAtLeast(0)
}

class JobGuideViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: JobGuideRepository = ServiceLocator.repository(application)
    private val accessibilityRefresh = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            var previousStatus: TaskStatus = TaskStatus.Idle
            repository.stats.collect { stats ->
                val shouldNotify = previousStatus == TaskStatus.Running && stats.status.let {
                    it == TaskStatus.Paused || it == TaskStatus.RiskStopped ||
                        it == TaskStatus.Stopped || it == TaskStatus.Completed
                }
                if (shouldNotify) {
                    val title = when (stats.status) {
                        TaskStatus.RiskStopped -> "职引助手 - 风险停止"
                        TaskStatus.Paused -> "职引助手 - 任务暂停"
                        TaskStatus.Stopped -> "职引助手 - 任务停止"
                        TaskStatus.Completed -> "职引助手 - 任务完成"
                        else -> "职引助手"
                    }
                    StopNotifier.showStopNotification(application, title, stats.currentStep)
                }
                previousStatus = stats.status
            }
        }
    }

    val uiState: StateFlow<JobGuideUiState> = combine(
        repository.config,
        repository.stats,
        repository.logs,
        repository.records,
        accessibilityRefresh
    ) { config, stats, logs, records, _ ->
        JobGuideUiState(
            config = config,
            stats = stats,
            logs = logs,
            records = records,
            accessibilityEnabled = isAccessibilityServiceEnabled(getApplication()),
            exportedJson = config.toJson()
        )
    }.combine(repository.accessibilityWasEnabled) { state, wasEnabled ->
        state.copy(accessibilityWasEnabled = wasEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobGuideUiState())

    fun startTask() {
        viewModelScope.launch {
            val appContext = getApplication<Application>()
            StopNotifier.cancelNotification(appContext)
            val accessibilityEnabled = isAccessibilityServiceEnabled(appContext)
            accessibilityRefresh.value += 1
            if (!accessibilityEnabled) {
                repository.updateStats {
                    it.copy(status = TaskStatus.Paused, currentStep = "请先开启无障碍权限")
                }
                repository.appendLog(RunLog(level = LogLevel.Warning, message = "请先开启无障碍权限"))
                openAccessibilitySettings(appContext)
                return@launch
            }
            val state = uiState.value
            if (state.config.runMode == RunMode.AutoSend && !state.config.autoGreetingEnabled) {
                repository.updateStats {
                    it.copy(status = TaskStatus.Paused, currentStep = "自动打招呼开关未开启")
                }
                repository.appendLog(RunLog(level = LogLevel.Warning, message = "自动打招呼开关未开启，已阻止自动发送"))
                return@launch
            }
            val platform = state.config.jobPlatform
            if (!launchPlatformApp(appContext, platform)) {
                repository.updateStats {
                    it.copy(status = TaskStatus.Paused, currentStep = "未安装或无法打开 ${platform.label}")
                }
                repository.appendLog(RunLog(level = LogLevel.Error, message = "未安装或无法打开 ${platform.label} App"))
                return@launch
            }
            repository.issueCommand(AutomationCommandType.Start)
        }
    }

    fun pauseTask() {
        viewModelScope.launch { repository.issueCommand(AutomationCommandType.Pause) }
    }

    fun stopTask() {
        viewModelScope.launch { repository.issueCommand(AutomationCommandType.Stop) }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun refreshAccessibilityStatus() {
        accessibilityRefresh.value += 1
    }

    fun updateRunMode(mode: RunMode) {
        viewModelScope.launch { repository.updateConfig { it.copy(runMode = mode) } }
    }

    fun updateJobSourceMode(mode: JobSourceMode) {
        viewModelScope.launch { repository.updateConfig { it.copy(jobSourceMode = mode) } }
    }

    fun updateJobPlatform(platform: JobPlatform) {
        viewModelScope.launch { repository.updateConfig { it.copy(jobPlatform = platform) } }
    }

    fun updateRecommendationGreetingTarget(value: Int) {
        viewModelScope.launch { repository.updateConfig { it.copy(recommendationGreetingTarget = value.coerceAtLeast(0)) } }
    }

    fun updateAutoGreeting(enabled: Boolean) {
        viewModelScope.launch { repository.updateConfig { it.copy(autoGreetingEnabled = enabled) } }
    }

    fun addKeyword() {
        viewModelScope.launch {
            repository.updateConfig {
                val nextIndex = it.keywords.size + 1
                val keyword = KeywordSetting(
                    id = UUID.randomUUID().toString(),
                    name = "新关键词 $nextIndex"
                )
                it.copy(
                    keywords = listOf(keyword) + it.keywords
                )
            }
            repository.appendLog(RunLog(level = LogLevel.Success, message = "已新增关键词，请在关键词页顶部编辑"))
        }
    }

    fun updateKeyword(keyword: KeywordSetting) {
        viewModelScope.launch {
            repository.updateConfig { config ->
                config.copy(keywords = config.keywords.map { if (it.id == keyword.id) keyword else it })
            }
        }
    }

    fun deleteKeyword(id: String) {
        viewModelScope.launch {
            repository.updateConfig { config -> config.copy(keywords = config.keywords.filterNot { it.id == id }) }
        }
    }

    fun moveKeyword(id: String, delta: Int) {
        viewModelScope.launch {
            repository.updateConfig { config ->
                val list = config.keywords.toMutableList()
                val index = list.indexOfFirst { it.id == id }
                val target = (index + delta).coerceIn(0, list.lastIndex)
                if (index >= 0 && index != target) {
                    val item = list.removeAt(index)
                    list.add(target, item)
                }
                config.copy(keywords = list)
            }
        }
    }

    fun updateRules(rules: RulesConfig) {
        viewModelScope.launch { repository.updateConfig { it.copy(rules = rules) } }
    }

    fun addTemplate() {
        viewModelScope.launch {
            repository.updateConfig { config ->
                config.copy(
                    templates = config.templates + GreetingTemplate(
                        title = "新模板",
                        body = "老师您好，我是{name}，对贵公司的{job_title}岗位很感兴趣，熟悉{skills}，期待沟通。"
                    )
                )
            }
        }
    }

    fun updateTemplate(template: GreetingTemplate) {
        viewModelScope.launch {
            repository.updateConfig { config ->
                config.copy(templates = config.templates.map { if (it.id == template.id) template else it })
            }
        }
    }

    fun setDefaultTemplate(id: String) {
        viewModelScope.launch {
            repository.updateConfig { config ->
                config.copy(templates = config.templates.map { it.copy(isDefault = it.id == id) })
            }
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            repository.updateConfig { config ->
                val next = config.templates.filterNot { it.id == id }
                config.copy(templates = next.ifEmpty { AppConfig.defaultTemplates() })
            }
        }
    }

    fun updateProfile(profile: ProfileConfig) {
        viewModelScope.launch { repository.updateConfig { it.copy(profile = profile) } }
    }

    fun importConfig(raw: String) {
        viewModelScope.launch { repository.importConfigJson(raw) }
    }

    fun clearTodayStats() {
        viewModelScope.launch { repository.clearTodayStats() }
    }

    fun clearRecords() {
        viewModelScope.launch { repository.clearRecords() }
    }

    fun clearLogs() {
        viewModelScope.launch { repository.clearLogs() }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, JobGuideAccessibilityService::class.java)
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val enabledServices = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        if (enabledServices.any { serviceInfo ->
                val service = serviceInfo.resolveInfo?.serviceInfo
                service?.packageName == expected.packageName && service.name == expected.className
            }
        ) {
            return true
        }

        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledSetting)
        for (service in splitter) {
            val component = ComponentName.unflattenFromString(service)
            if (component != null) {
                if (component.packageName == expected.packageName && component.className == expected.className) {
                    return true
                }
            } else if (service.equals(expected.flattenToString(), ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun launchPlatformApp(context: Context, platform: JobPlatform): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(platform.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}

fun RulesConfig.toEditableTextMap(): Map<String, String> = mapOf(
    "targetCities" to targetCities.displayText(),
    "requiredKeywords" to requiredKeywords.displayText(),
    "jobWhitelist" to jobWhitelist.displayText(),
    "companyWhitelist" to companyWhitelist.displayText(),
    "jobBlacklist" to jobBlacklist.displayText(),
    "companyBlacklist" to companyBlacklist.displayText(),
    "riskStopWords" to riskStopWords.displayText()
)

fun RulesConfig.withListField(key: String, value: String): RulesConfig {
    val parsed = splitInput(value)
    return when (key) {
        "targetCities" -> copy(targetCities = parsed)
        "requiredKeywords" -> copy(requiredKeywords = parsed)
        "jobWhitelist" -> copy(jobWhitelist = parsed)
        "companyWhitelist" -> copy(companyWhitelist = parsed)
        "jobBlacklist" -> copy(jobBlacklist = parsed)
        "companyBlacklist" -> copy(companyBlacklist = parsed)
        "riskStopWords" -> copy(riskStopWords = parsed)
        else -> this
    }
}
