package com.zhiyin.jobguide.automation

import android.app.ActivityManager
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.zhiyin.jobguide.data.AppConfig
import com.zhiyin.jobguide.data.AutomationCommand
import com.zhiyin.jobguide.data.AutomationCommandType
import com.zhiyin.jobguide.data.JobRecord
import com.zhiyin.jobguide.data.JobPlatform
import com.zhiyin.jobguide.data.JobSourceMode
import com.zhiyin.jobguide.data.JobSnapshot
import com.zhiyin.jobguide.data.LogLevel
import com.zhiyin.jobguide.data.RunLog
import com.zhiyin.jobguide.data.RunMode
import com.zhiyin.jobguide.data.ServiceLocator
import com.zhiyin.jobguide.data.TaskStatus
import com.zhiyin.jobguide.data.renderGreetingTemplate
import com.zhiyin.jobguide.data.stableFingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

class JobGuideAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { ServiceLocator.repository(this) }
    private var activeJob: Job? = null
    private var lastCommandNonce: String = ""
    private val runtimeProcessedFingerprints = mutableSetOf<String>()
    private val runtimeDuplicateLogFingerprints = mutableSetOf<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundNotification()
        serviceScope.launch {
            repository.setAccessibilityWasEnabled(true)
            repository.appendLog(RunLog(level = LogLevel.Success, message = "无障碍服务已连接"))
        }
        serviceScope.launch {
            repository.command.collect { command -> handleCommand(command) }
        }
    }

    override fun onDestroy() {
        activeJob?.cancel()
        serviceScope.launch {
            repository.updateStats {
                it.copy(status = TaskStatus.Stopped, currentStep = "无障碍服务已停止")
            }
            repository.appendLog(RunLog(level = LogLevel.Warning, message = "无障碍服务已停止"))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "服务运行通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示无障碍服务运行状态"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, com.zhiyin.jobguide.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("职引助手")
            .setContentText("无障碍服务运行中")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        activeJob?.cancel()
        serviceScope.launch {
            repository.updateStats {
                it.copy(status = TaskStatus.Paused, currentStep = "无障碍服务被系统中断")
            }
            repository.appendLog(RunLog(level = LogLevel.Warning, message = "无障碍服务被系统中断，任务已暂停"))
        }
    }

    private fun handleCommand(command: AutomationCommand) {
        if (command.nonce == lastCommandNonce) return
        lastCommandNonce = command.nonce
        when (command.type) {
            AutomationCommandType.Start -> startAutomation()
            AutomationCommandType.Pause -> pauseAutomation("用户暂停任务")
            AutomationCommandType.Stop -> stopAutomation("用户停止任务")
            AutomationCommandType.None -> Unit
        }
    }

    private fun startAutomation() {
        if (activeJob?.isActive == true) return
        runtimeProcessedFingerprints.clear()
        runtimeDuplicateLogFingerprints.clear()
        activeJob = serviceScope.launch {
            val config = repository.config.first()
            val spec = PlatformAutomationSpec.forPlatform(config.jobPlatform)
            repository.updateStats {
                it.copy(
                    status = TaskStatus.Running,
                    currentStep = "准备启动 ${spec.platform.label}",
                    riskReason = "",
                    startedAt = System.currentTimeMillis(),
                    finishedAt = 0L
                )
            }
            repository.appendLog(RunLog(message = "任务启动：默认遵循风险词停止、限额停止和不盲点规则"))
            try {
                runAutomation(config)
                repository.updateStats {
                    it.copy(
                        status = TaskStatus.Completed,
                        currentStep = if (config.jobSourceMode == JobSourceMode.HomeRecommendations) {
                            "${spec.platform.label} 职位页推荐任务完成"
                        } else {
                            "${spec.platform.label} 所有关键词处理完成"
                        },
                        finishedAt = System.currentTimeMillis()
                    )
                }
                repository.appendLog(RunLog(level = LogLevel.Success, message = "全部任务完成"))
            } catch (error: StopAutomation) {
                repository.updateStats {
                    it.copy(
                        status = if (error.isRisk) TaskStatus.RiskStopped else TaskStatus.Paused,
                        currentStep = error.message.orEmpty(),
                        riskReason = if (error.isRisk) error.message.orEmpty() else it.riskReason,
                        finishedAt = System.currentTimeMillis()
                    )
                }
                repository.appendLog(
                    RunLog(
                        level = if (error.isRisk) LogLevel.Error else LogLevel.Warning,
                        keyword = error.keyword,
                        message = error.message.orEmpty()
                    )
                )
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                repository.updateStats {
                    it.copy(
                        status = TaskStatus.Paused,
                        currentStep = "自动化异常：${error.message.orEmpty()}",
                        finishedAt = System.currentTimeMillis()
                    )
                }
                repository.appendLog(RunLog(level = LogLevel.Error, message = "自动化异常：${error.message.orEmpty()}"))
            }
        }
    }

    private fun pauseAutomation(reason: String) {
        activeJob?.cancel()
        activeJob = null
        serviceScope.launch {
            repository.updateStats { it.copy(status = TaskStatus.Paused, currentStep = reason) }
            repository.appendLog(RunLog(level = LogLevel.Warning, message = reason))
        }
    }

    private fun stopAutomation(reason: String) {
        activeJob?.cancel()
        activeJob = null
        serviceScope.launch {
            repository.updateStats {
                it.copy(status = TaskStatus.Stopped, currentStep = reason, finishedAt = System.currentTimeMillis())
            }
            repository.appendLog(RunLog(level = LogLevel.Warning, message = reason))
        }
    }

    private suspend fun runAutomation(config: AppConfig) {
        val spec = PlatformAutomationSpec.forPlatform(config.jobPlatform)
        if (!launchPlatformApp(spec)) {
            throw StopAutomation("未安装或无法打开 ${spec.platform.label} App", isRisk = false)
        }
        waitRandom(config, "等待 ${spec.platform.label} 启动")
        preparePlatformAfterLaunch(config, spec)
        when (config.jobSourceMode) {
            JobSourceMode.KeywordSearch -> runKeywordSearch(config, spec)
            JobSourceMode.HomeRecommendations -> runHomeRecommendations(config, spec)
        }
    }

    private suspend fun runKeywordSearch(config: AppConfig, spec: PlatformAutomationSpec) {
        val enabledKeywords = config.keywords
            .filter { it.enabled && it.name.isNotBlank() }
        if (enabledKeywords.isEmpty()) throw StopAutomation("没有启用的搜索关键词", isRisk = false)

        for (keyword in enabledKeywords) {
            if (keyword.maxGreetings <= 0) {
                repository.appendLog(RunLog(level = LogLevel.Warning, keyword = keyword.name, message = "成功打招呼目标为 0，跳过该关键词"))
                continue
            }
            repository.updateStats { it.copy(currentKeyword = keyword.name, currentStep = "准备搜索 ${keyword.name}") }
            repository.appendLog(RunLog(keyword = keyword.name, message = "开始在 ${spec.platform.label} 处理关键词：${keyword.name}"))
            searchKeyword(keyword.name, config, spec)
            var processedForKeyword = 0
            var emptyRounds = 0
            var sentThisRun = 0
            var initialWait = true
            val scanLimit = maxOf(keyword.maxJobs, keyword.maxGreetings * 8, 30)
            while (processedForKeyword < scanLimit) {
                val stats = repository.stats.first()
                if (stats.todayGreetings >= config.rules.dailyGreetingLimit) {
                    repository.appendLog(RunLog(level = LogLevel.Success, keyword = keyword.name, message = "已达到今日总打招呼上限"))
                    return
                }
                if (sentThisRun >= keyword.maxGreetings) {
                    repository.appendLog(RunLog(level = LogLevel.Success, keyword = keyword.name, message = "当前关键词已达到成功打招呼目标"))
                    break
                }

                if (initialWait) {
                    waitRandom(config, "等待搜索结果加载", messageSent = false)
                    initialWait = false
                }

                val root = currentRootOrStop(keyword.name)
                assertNoRisk(root, config, keyword.name)
                val candidates = root.findJobCandidates(keyword.name)
                val records = repository.records.first()
                val evaluatedCandidates = candidates.map { node ->
                    node to evaluateJob(node.visibleTexts(), keyword.name, config)
                }
                evaluatedCandidates.forEach { (_, snapshot) ->
                    val existing = processedRecord(snapshot, records, spec.platform)
                    if (existing != null) logDuplicateSkip(keyword.name, snapshot, existing)
                }
                val next = evaluatedCandidates.firstOrNull { (_, snapshot) ->
                    processedRecord(snapshot, records, spec.platform) == null
                }?.first
                if (next == null) {
                    repository.appendLog(RunLog(keyword = keyword.name, message = "当前列表没有新的可处理岗位，滚动加载下一页"))
                    val before = resultPageKey(root)
                    val scrolled = scrollForward(root, keyword.name)
                    delay(900)
                    val afterRoot = currentRootOrStop(keyword.name)
                    assertNoRisk(afterRoot, config, keyword.name)
                    val changed = before != resultPageKey(afterRoot)
                    if (!scrolled || !changed) {
                        emptyRounds += 1
                    } else {
                        emptyRounds = 0
                    }
                    if (emptyRounds >= 2) {
                        repository.appendLog(RunLog(level = LogLevel.Warning, keyword = keyword.name, message = "连续滚动后没有新岗位，切换下一个关键词"))
                        break
                    }
                    continue
                }
                emptyRounds = 0
                val listSnapshot = evaluateJob(next.visibleTexts(), keyword.name, config)
                runtimeProcessedFingerprints += listSnapshot.fingerprint
                val beforeSent = repository.stats.first().keywordGreetings[keyword.name] ?: 0
                processCandidate(next, listSnapshot, keyword.name, config, spec)
                val afterSent = repository.stats.first().keywordGreetings[keyword.name] ?: 0
                if (afterSent > beforeSent) {
                    sentThisRun += afterSent - beforeSent
                }
                processedForKeyword += 1
                repository.updateStats {
                    it.copy(todayProcessed = it.todayProcessed + 1)
                }
                waitRandom(config, "等待下一次操作", messageSent = false)
            }
        }
    }

    private suspend fun runHomeRecommendations(config: AppConfig, spec: PlatformAutomationSpec) {
        val target = config.recommendationGreetingTarget.coerceAtLeast(0)
        repository.updateStats { it.copy(currentKeyword = RECOMMENDATION_SOURCE_LABEL, currentStep = "准备处理 ${spec.platform.label} 当前职位页岗位") }
        repository.appendLog(RunLog(keyword = RECOMMENDATION_SOURCE_LABEL, message = "开始处理 ${spec.platform.label} 当前职位页/推荐流岗位，目标 $target 次"))
        if (target <= 0) {
            repository.appendLog(RunLog(level = LogLevel.Warning, keyword = RECOMMENDATION_SOURCE_LABEL, message = "推荐流成功目标为 0，任务不执行"))
            return
        }

        ensureRecommendationList(config, spec)
        waitRandom(config, "等待职位页岗位加载", messageSent = false)
        var processedForRecommendations = 0
        var emptyRounds = 0
        var sentThisRun = 0
        val scanLimit = maxOf(target * 8, 30)
        while (processedForRecommendations < scanLimit) {
            val stats = repository.stats.first()
            if (stats.todayGreetings >= config.rules.dailyGreetingLimit) {
                repository.appendLog(RunLog(level = LogLevel.Success, keyword = RECOMMENDATION_SOURCE_LABEL, message = "已达到今日总打招呼上限"))
                return
            }
            if (sentThisRun >= target) {
                repository.appendLog(RunLog(level = LogLevel.Success, keyword = RECOMMENDATION_SOURCE_LABEL, message = "当前职位页/推荐流已达到成功打招呼目标"))
                return
            }

            val root = currentRootOrStop(RECOMMENDATION_SOURCE_LABEL)
            assertNoRisk(root, config, RECOMMENDATION_SOURCE_LABEL)
            val candidates = root.findJobCandidates("")
            val records = repository.records.first()
            val evaluatedCandidates = candidates.map { node ->
                node to evaluateJob(node.visibleTexts(), "", config)
            }
            evaluatedCandidates.forEach { (_, snapshot) ->
                val existing = processedRecord(snapshot, records, spec.platform)
                if (existing != null) logDuplicateSkip(RECOMMENDATION_SOURCE_LABEL, snapshot, existing)
            }
            val next = evaluatedCandidates.firstOrNull { (_, snapshot) ->
                processedRecord(snapshot, records, spec.platform) == null
            }?.first
            if (next == null) {
                repository.appendLog(RunLog(keyword = RECOMMENDATION_SOURCE_LABEL, message = "当前职位页没有新的可处理岗位，滚动加载下一批"))
                val before = resultPageKey(root)
                val scrolled = scrollForward(root, RECOMMENDATION_SOURCE_LABEL, "")
                delay(900)
                val afterRoot = currentRootOrStop(RECOMMENDATION_SOURCE_LABEL)
                assertNoRisk(afterRoot, config, RECOMMENDATION_SOURCE_LABEL)
                val changed = before != resultPageKey(afterRoot)
                if (!scrolled || !changed) {
                    emptyRounds += 1
                } else {
                    emptyRounds = 0
                }
                if (emptyRounds >= 2) {
                    repository.appendLog(RunLog(level = LogLevel.Warning, keyword = RECOMMENDATION_SOURCE_LABEL, message = "连续滚动后没有新的职位页岗位，任务结束"))
                    return
                }
                continue
            }
            emptyRounds = 0
            val listSnapshot = evaluateJob(next.visibleTexts(), "", config)
            runtimeProcessedFingerprints += listSnapshot.fingerprint
            val beforeSent = repository.stats.first().keywordGreetings[RECOMMENDATION_SOURCE_LABEL] ?: 0
            processCandidate(
                node = next,
                listSnapshot = listSnapshot,
                keyword = RECOMMENDATION_SOURCE_LABEL,
                config = config,
                spec = spec,
                matchKeyword = "",
                templateKeyword = "",
                statKey = RECOMMENDATION_SOURCE_LABEL,
                skipMatchFilter = true
            )
            val afterSent = repository.stats.first().keywordGreetings[RECOMMENDATION_SOURCE_LABEL] ?: 0
            if (afterSent > beforeSent) {
                sentThisRun += afterSent - beforeSent
            }
            processedForRecommendations += 1
            repository.updateStats {
                it.copy(todayProcessed = it.todayProcessed + 1)
            }
            waitRandom(config, "等待下一次职位页岗位操作", messageSent = false)
        }

        repository.appendLog(RunLog(level = LogLevel.Warning, keyword = RECOMMENDATION_SOURCE_LABEL, message = "当前职位页/推荐流扫描达到处理上限，任务结束"))
    }

    private suspend fun launchPlatformApp(spec: PlatformAutomationSpec): Boolean {
        val packageName = spec.platform.packageName
        // If the app is already running, force-stop it first so it starts from a clean state.
        // This ensures consistent behavior regardless of whether the user manually opened the app before.
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val isRunning = activityManager.runningAppProcesses?.any {
            it.processName == packageName || it.processName.startsWith("$packageName:")
        } == true
        if (isRunning) {
            repository.appendLog(RunLog(keyword = "", message = "${spec.platform.label} 已在运行，先关闭再重新打开"))
            // Press Home first to move the app to background (killBackgroundProcesses only works for background apps)
            performGlobalAction(GLOBAL_ACTION_HOME)
            delay(500)
            activityManager.killBackgroundProcesses(packageName)
            delay(800)
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        return true
    }

    private suspend fun preparePlatformAfterLaunch(config: AppConfig, spec: PlatformAutomationSpec) {
        repository.updateStats { it.copy(currentStep = "等待 ${spec.platform.label} 首页稳定") }
        repeat(14) {
            val root = rootInActiveWindow
            if (root != null) {
                if (spec.platform == JobPlatform.Job51 && config.jobSourceMode == JobSourceMode.KeywordSearch && root.looksLikeJob51TopicListPage()) {
                    repository.updateStats { it.copy(currentStep = "退出 ${spec.platform.label} 专题页") }
                    repository.appendLog(RunLog(level = LogLevel.Warning, message = "检测到 51job 专题页，尝试点击左上角返回后继续关键词搜索"))
                    if (clickJob51TopicBack(root)) {
                        delay(800)
                        return@repeat
                    }
                }
                if (root.looksReadyForSearchOrJobs(spec)) return
                if (dismissTransientObstruction(root, spec)) {
                    repository.appendLog(RunLog(message = "已尝试关闭 ${spec.platform.label} 启动浮层"))
                    delay(700)
                    return@repeat
                }
            }
            delay(500)
        }
        waitRandom(config, "继续检查 ${spec.platform.label} 当前页面")
    }

    private suspend fun ensureRecommendationList(config: AppConfig, spec: PlatformAutomationSpec) {
        // Try up to 3 times: the app may have just been launched and the list may not be loaded yet.
        repeat(3) { attempt ->
            val root = currentRootOrStop(RECOMMENDATION_SOURCE_LABEL)
            assertNoRisk(root, config, RECOMMENDATION_SOURCE_LABEL)
            if (root.findJobCandidates("").isNotEmpty()) {
                repository.appendLog(RunLog(keyword = RECOMMENDATION_SOURCE_LABEL, message = "已在当前职位页识别到岗位列表，直接开始处理"))
                return
            }

            val homeTab = root.findBottomNavigationItem(spec.recommendationTabTexts)
            if (homeTab != null) {
                repository.updateStats { it.copy(currentStep = "点击 ${spec.platform.label} 职位页入口") }
                if (clickNode(homeTab)) {
                    waitRandom(config, "等待职位页岗位加载")
                    val retryRoot = currentRootOrStop(RECOMMENDATION_SOURCE_LABEL)
                    if (retryRoot.findJobCandidates("").isNotEmpty()) {
                        repository.appendLog(RunLog(keyword = RECOMMENDATION_SOURCE_LABEL, message = "点击职位页入口后识别到岗位列表"))
                        return
                    }
                }
            }

            if (attempt < 2) {
                repository.appendLog(RunLog(
                    level = LogLevel.Warning,
                    keyword = RECOMMENDATION_SOURCE_LABEL,
                    message = "当前页面未识别到岗位列表，等待重试 (${attempt + 1}/3)"
                ))
                delay(1500)
            }
        }

        // Final attempt: try one more time after waiting
        val finalRoot = currentRootOrStop(RECOMMENDATION_SOURCE_LABEL)
        assertNoRisk(finalRoot, config, RECOMMENDATION_SOURCE_LABEL)
        if (finalRoot.findJobCandidates("").isNotEmpty()) {
            repository.appendLog(RunLog(keyword = RECOMMENDATION_SOURCE_LABEL, message = "重试后识别到岗位列表"))
            return
        }

        val finalTab = finalRoot.findBottomNavigationItem(spec.recommendationTabTexts)
        repository.appendLog(
            RunLog(
                level = LogLevel.Warning,
                keyword = RECOMMENDATION_SOURCE_LABEL,
                message = "职位页入口调试信息：${finalRoot.debugSummary()}"
            )
        )
        if (finalTab != null) {
            repository.updateStats { it.copy(currentStep = "点击 ${spec.platform.label} 职位页入口") }
            if (clickNode(finalTab)) {
                waitRandom(config, "等待职位页岗位加载")
                val afterClickRoot = currentRootOrStop(RECOMMENDATION_SOURCE_LABEL)
                if (afterClickRoot.findJobCandidates("").isNotEmpty()) {
                    repository.appendLog(RunLog(keyword = RECOMMENDATION_SOURCE_LABEL, message = "最终尝试后识别到岗位列表"))
                    return
                }
            }
        }
        throw StopAutomation("当前页面没有识别到岗位，也找不到职位/首页入口，已暂停等待用户检查页面", keyword = RECOMMENDATION_SOURCE_LABEL)
    }

    private suspend fun searchKeyword(keyword: String, config: AppConfig, spec: PlatformAutomationSpec) {
        val root = ensureJob51KeywordSearchPage(keyword, config, spec)
        assertNoRisk(root, config, keyword)

        var editText = root.findBestSearchInput(spec.searchInputResourceIds)
        if (editText == null) {
            val searchEntry = findSearchEntryWithHomeRetry(root, keyword, config, spec)
            repository.updateStats { it.copy(currentStep = "点击搜索入口") }
            val clickedSearchEntry =
                searchEntry?.let { clickNode(it) } == true ||
                clickSearchEntryFallback(currentRootOrStop(keyword), spec)
            if (!clickedSearchEntry) {
                val latestRoot = currentRootOrStop(keyword)
                repository.appendLog(
                    RunLog(
                        level = LogLevel.Warning,
                        keyword = keyword,
                        message = "搜索入口调试信息：${latestRoot.debugSummary(40)}"
                    )
                )
                throw StopAutomation("找不到搜索入口，已暂停等待用户检查页面", keyword = keyword)
            }
            editText = waitForSearchInput(keyword, config, spec)
        }

        editText = editText
            ?: findOrFocusSearchInput(currentRootOrStop(keyword), spec, keyword)
            ?: throw StopAutomation("找不到搜索输入框，已暂停", keyword = keyword)
        if (!setTextWithFocus(editText, keyword)) {
            throw StopAutomation("找到搜索输入框但无法输入关键词，已暂停", keyword = keyword)
        }
        repository.updateStats { it.copy(currentStep = "已输入关键词：$keyword") }
        repository.appendLog(RunLog(keyword = keyword, message = "已输入搜索关键词：$keyword"))
        waitRandom(config, "等待搜索按钮")

        val searchButtonRoot = currentRootOrStop(keyword)
        val searchButton = searchButtonRoot.findBestSearchButton(spec.searchButtonTexts, spec.searchButtonResourceIds)
        val clickedSearchButton = searchButton?.let { clickNode(it) } == true ||
            clickKnownTarget(searchButtonRoot, spec.searchButtonTapTargets)
        if (!clickedSearchButton) {
            repository.appendLog(
                RunLog(
                    level = LogLevel.Warning,
                    keyword = keyword,
                    message = "搜索按钮调试信息：${searchButtonRoot.debugSummary(40)}"
                )
            )
            throw StopAutomation("找不到搜索按钮，已暂停", keyword = keyword)
        }
        repository.updateStats { it.copy(currentStep = "等待搜索结果") }
        waitForSearchResults(keyword, config, spec)
    }

    private suspend fun waitForSearchResults(
        keyword: String,
        config: AppConfig,
        spec: PlatformAutomationSpec
    ) {
        repeat(14) {
            val root = currentRootOrStop(keyword)
            assertNoRisk(root, config, keyword)
            if (root.looksLikeSearchResultsPage(keyword, spec)) return
            delay(500)
        }

        val latestRoot = currentRootOrStop(keyword)
        repository.appendLog(
            RunLog(
                level = LogLevel.Warning,
                keyword = keyword,
                message = "搜索结果页调试信息：${latestRoot.debugSummary(40)}"
            )
        )
        throw StopAutomation("点击搜索后没有进入岗位结果页，已暂停等待用户检查页面", keyword = keyword)
    }

    private suspend fun waitForSearchInput(
        keyword: String,
        config: AppConfig,
        spec: PlatformAutomationSpec
    ): AccessibilityNodeInfo? {
        repository.updateStats { it.copy(currentStep = "等待搜索输入框") }
        val attempts = if (spec.platform == JobPlatform.Job51) 24 else 10
        repeat(attempts) { attempt ->
            val root = ensureJob51KeywordSearchPage(keyword, config, spec)
            assertNoRisk(root, config, keyword)
            root.findBestSearchInput(spec.searchInputResourceIds)?.let { return it }

            if (spec.platform == JobPlatform.Job51) {
                dismissTransientObstruction(root, spec)
                val searchEntry = root.findPlatformSearchEntry(spec)
                if (attempt >= 1 && searchEntry != null) {
                    clickNode(searchEntry) ||
                        clickSearchEntryFallback(root, spec)
                }
            } else if (attempt >= 2) {
                clickKnownTarget(root, spec.searchInputTapTargets)
            }
            delay(450)
        }
        return null
    }

    private suspend fun ensureJob51KeywordSearchPage(
        keyword: String,
        config: AppConfig,
        spec: PlatformAutomationSpec
    ): AccessibilityNodeInfo {
        if (spec.platform != JobPlatform.Job51) return currentRootOrStop(keyword)

        repeat(3) {
            val root = currentRootOrStop(keyword)
            assertNoRisk(root, config, keyword)
            if (!root.looksLikeJob51TopicListPage()) return root

            repository.appendLog(
                RunLog(
                    level = LogLevel.Warning,
                    keyword = keyword,
                    message = "当前停留在 51job 专题页，尝试点击左上角返回后进入搜索"
                )
            )
            if (!clickJob51TopicBack(root)) return root
            delay(800)
        }

        return currentRootOrStop(keyword)
    }

    private suspend fun findSearchEntryWithHomeRetry(
        root: AccessibilityNodeInfo,
        keyword: String,
        config: AppConfig,
        spec: PlatformAutomationSpec
    ): AccessibilityNodeInfo? {
        root.findPlatformSearchEntry(spec)?.let { return it }
        if (spec.platform == JobPlatform.Job51 && PageHeuristics.canUseJob51SearchCoordinateFallback(root.joinedText())) {
            return null
        }

        val homeTab = root.findBottomNavigationItem(spec.searchHomeTabTexts) ?: return null
        repository.updateStats { it.copy(currentStep = "点击 ${spec.platform.label} 首页/职位入口后重试搜索") }
        if (!clickNode(homeTab)) return null
        waitRandom(config, "等待 ${spec.platform.label} 首页/职位页加载")
        val retryRoot = currentRootOrStop(keyword)
        assertNoRisk(retryRoot, config, keyword)
        return retryRoot.findPlatformSearchEntry(spec)
    }

    private suspend fun findOrFocusSearchInput(
        root: AccessibilityNodeInfo,
        spec: PlatformAutomationSpec,
        keyword: String
    ): AccessibilityNodeInfo? {
        root.findBestSearchInput(spec.searchInputResourceIds)?.let { return it }
        if (spec.platform == JobPlatform.Job51) {
            root.findPlatformSearchEntry(spec)?.let { entry ->
                if (clickNode(entry)) {
                    delay(350)
                    return currentRootOrStop(keyword).findBestSearchInput(spec.searchInputResourceIds)
                }
            }
            if (!PageHeuristics.canUseJob51SearchCoordinateFallback(root.joinedText())) return null
        }
        if (!clickKnownTarget(root, spec.searchInputTapTargets)) return null
        delay(350)
        return currentRootOrStop(keyword).findBestSearchInput(spec.searchInputResourceIds)
    }

    private fun clickSearchEntryFallback(root: AccessibilityNodeInfo, spec: PlatformAutomationSpec): Boolean {
        if (spec.searchEntryTapTargets.isEmpty()) return false
        if (spec.platform == JobPlatform.Job51) {
            if (!PageHeuristics.canUseJob51SearchCoordinateFallback(root.joinedText())) return false
        }
        return clickKnownTarget(root, spec.searchEntryTapTargets)
    }

    private fun AccessibilityNodeInfo.findPlatformSearchEntry(spec: PlatformAutomationSpec): AccessibilityNodeInfo? {
        if (spec.platform != JobPlatform.Job51) {
            return findBestSearchEntry(spec.searchEntryTexts, spec.searchEntryResourceIds)
        }
        findFirstByResourceIds(spec.searchEntryResourceIds)?.let { return it }
        findFirst(spec.searchEntryTexts, clickable = true)?.let { return it }
        return findFirst(spec.searchEntryTexts, clickable = null)?.let { node ->
            findSmallestClickableContainer(node.bounds()) ?: node
        }
    }

    private suspend fun findOrFocusChatInput(
        root: AccessibilityNodeInfo,
        spec: PlatformAutomationSpec,
        keyword: String
    ): AccessibilityNodeInfo? {
        if (spec.platform == JobPlatform.Liepin) {
            clickKnownTarget(root, spec.chatInputTapTargets)
            delay(350)
            val refreshedRoot = currentRootOrStop(keyword)
            return refreshedRoot.findChatEditText(spec.chatInputResourceIds)
                ?: refreshedRoot.findFirstEditText()
        }
        root.findChatEditText(spec.chatInputResourceIds)?.let { return it }
        if (!clickKnownTarget(root, spec.chatInputTapTargets)) return null
        delay(350)
        return currentRootOrStop(keyword).findChatEditText(spec.chatInputResourceIds)
            ?: currentRootOrStop(keyword).findFirstEditText()
    }

    private suspend fun setTextWithFocus(node: AccessibilityNodeInfo, value: String): Boolean {
        if (node.setTextSafely(value)) return true
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        clickNode(node)
        delay(250)
        if (node.setTextSafely(value)) return true
        delay(250)
        return node.setTextSafely(value)
    }

    private suspend fun ensureLiepinMessageReady(
        keyword: String,
        spec: PlatformAutomationSpec,
        message: String
    ): Boolean {
        val snippet = message
            .replace(Regex("\\s+"), "")
            .take(8)
            .trim()
        repeat(3) { attempt ->
            val root = currentRootOrStop(keyword)
            var input = root.findChatEditText(spec.chatInputResourceIds) ?: root.findFirstEditText()
            if (input == null && attempt == 0) {
                clickKnownTarget(root, spec.chatInputTapTargets)
                delay(250)
                val refreshedRoot = currentRootOrStop(keyword)
                input = refreshedRoot.findChatEditText(spec.chatInputResourceIds) ?: refreshedRoot.findFirstEditText()
            }
            val inputText = input?.text?.toString().orEmpty().replace(Regex("\\s+"), "")
            val inputDesc = input?.contentDescription?.toString().orEmpty().replace(Regex("\\s+"), "")
            if (inputText.contains(snippet, ignoreCase = true) || inputDesc.contains(snippet, ignoreCase = true)) {
                return true
            }
            if (input != null) {
                if (setTextWithFocus(input, message)) {
                    delay(300)
                }
                val refreshedRoot = currentRootOrStop(keyword)
                val verifiedInput = refreshedRoot.findChatEditText(spec.chatInputResourceIds)
                    ?: refreshedRoot.findFirstEditText()
                    ?: input
                val afterText = verifiedInput.text?.toString().orEmpty().replace(Regex("\\s+"), "")
                val afterDesc = verifiedInput.contentDescription?.toString().orEmpty().replace(Regex("\\s+"), "")
                if (afterText.contains(snippet, ignoreCase = true) || afterDesc.contains(snippet, ignoreCase = true)) {
                    return true
                }
            }
            delay(250)
        }
        return false
    }

    private suspend fun returnToCandidateList(
        keyword: String,
        spec: PlatformAutomationSpec,
        matchKeyword: String = keyword
    ) {
        // After sending a greeting, the soft keyboard may still be open.
        // The first BACK only dismisses the keyboard without leaving the chat page.
        // So we must first close the keyboard explicitly, then navigate back through
        // chat → detail → list.
        if (spec.platform == JobPlatform.Liepin || spec.platform == JobPlatform.Boss || spec.platform == JobPlatform.Job51 || spec.platform == JobPlatform.Zhilian) {
            dismissSoftKeyboard()
            delay(400)
        }

        // Liepin and Boss need more back presses: chat → detail → list (3 levels)
        // Job51 may also need more: detail → list (2 levels) or chat → detail → list (3 levels)
        // Zhilian: chat → detail → list (3 levels)
        val maxAttempts = when (spec.platform) {
            JobPlatform.Liepin -> 6
            JobPlatform.Boss -> 5
            JobPlatform.Job51 -> 4
            JobPlatform.Zhilian -> 6
        }
        repeat(maxAttempts) { attempt ->
            val root = currentRootOrStop(keyword)
            // For Boss, check if we're still on a chat page
            if (spec.platform == JobPlatform.Boss && PageHeuristics.isBossChatPage(
                    root.joinedText(),
                    root.findChatEditText(spec.chatInputResourceIds) != null || root.findFirstEditText() != null,
                    root.findBestSendButton(spec.sendTexts, spec.sendResourceIds) != null
                )
            ) {
                repository.appendLog(RunLog(keyword = keyword, message = "检测到BOSS直聘聊天页面，继续返回"))
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(if (attempt == 0) 800 else 600)
                return@repeat
            }
            // For Boss, check if we're stuck on a job detail page and need to go back further
            if (spec.platform == JobPlatform.Boss && PageHeuristics.isBossJobDetailPage(root.joinedText())) {
                repository.appendLog(RunLog(keyword = keyword, message = "检测到BOSS直聘岗位详情页，继续返回"))
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(if (attempt == 0) 800 else 600)
                return@repeat
            }
            // For 51job, check if we're still on a chat page (has chat input and send button)
            val has51ChatInput = spec.platform == JobPlatform.Job51 &&
                (root.findChatEditText(spec.chatInputResourceIds) != null || root.findFirstEditText() != null)
            val has51SendButton = spec.platform == JobPlatform.Job51 &&
                root.findBestSendButton(spec.sendTexts, spec.sendResourceIds) != null
            if (spec.platform == JobPlatform.Job51 && has51ChatInput && has51SendButton) {
                repository.appendLog(RunLog(keyword = keyword, message = "检测到51job聊天页面，继续返回"))
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(if (attempt == 0) 800 else 600)
                return@repeat
            }
            // For 51job, check if we're stuck on a job detail page and need to go back further
            if (spec.platform == JobPlatform.Job51 && PageHeuristics.isJob51JobDetailPage(root.joinedText())) {
                repository.appendLog(RunLog(keyword = keyword, message = "检测到51job岗位详情页，继续返回"))
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(if (attempt == 0) 800 else 600)
                return@repeat
            }
            // For Liepin, check if we're still on a chat page (after sending, the send button
            // may revert to a "+" icon so we also check without requiring a send button)
            if (spec.platform == JobPlatform.Liepin && PageHeuristics.isLiepinChatPage(
                    root.joinedText(),
                    root.findChatEditText(spec.chatInputResourceIds) != null || root.findFirstEditText() != null,
                    root.findBestSendButton(spec.sendTexts, spec.sendResourceIds) != null
                )
            ) {
                repository.appendLog(RunLog(keyword = keyword, message = "检测到猎聘聊天页面，继续返回"))
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(if (attempt == 0) 800 else 600)
                return@repeat
            }
            // For Liepin, check if we're stuck on a job detail page and need to go back further
            if (spec.platform == JobPlatform.Liepin && PageHeuristics.isLiepinJobDetailPage(root.joinedText())) {
                repository.appendLog(RunLog(keyword = keyword, message = "检测到猎聘岗位详情页，继续返回"))
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(if (attempt == 0) 800 else 600)
                return@repeat
            }
            // For Zhilian, check if we're still on a chat page (has chat input and send button)
            val hasZLChatInput = spec.platform == JobPlatform.Zhilian &&
                (root.findChatEditText(spec.chatInputResourceIds) != null || root.findFirstEditText() != null)
            val hasZLSendButton = spec.platform == JobPlatform.Zhilian &&
                root.findBestSendButton(spec.sendTexts, spec.sendResourceIds) != null
            if (spec.platform == JobPlatform.Zhilian && PageHeuristics.isZhilianChatPage(
                    root.joinedText(),
                    hasZLChatInput,
                    hasZLSendButton
                )
            ) {
                repository.appendLog(RunLog(keyword = keyword, message = "检测到智联招聘聊天页面，继续返回"))
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(if (attempt == 0) 800 else 600)
                return@repeat
            }
            // For Zhilian, check if we're stuck on a job detail page and need to go back further
            if (spec.platform == JobPlatform.Zhilian && PageHeuristics.isZhilianJobDetailPage(root.joinedText())) {
                repository.appendLog(RunLog(keyword = keyword, message = "检测到智联招聘岗位详情页，继续返回"))
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(if (attempt == 0) 800 else 600)
                return@repeat
            }
            if (root.looksLikeSearchResultsPage(matchKeyword, spec)) return
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(if (attempt == 0) 800 else 600)
        }

        val finalRoot = currentRootOrStop(keyword)
        if (finalRoot.looksLikeSearchResultsPage(matchKeyword, spec)) return
        repository.appendLog(
            RunLog(
                level = LogLevel.Warning,
                keyword = keyword,
                message = "返回岗位列表调试信息：${finalRoot.debugSummary()}"
            )
        )
        throw StopAutomation("返回岗位列表失败，已暂停", keyword = keyword)
    }

    private fun dismissSoftKeyboard() {
        // Clear focus on any focused EditText node to dismiss the soft keyboard.
        // This is more reliable than InputMethodManager in an AccessibilityService
        // context since we don't have a direct window token.
        val root = rootInActiveWindow ?: return
        var focusedEdit: AccessibilityNodeInfo? = null
        root.walk { node ->
            if (focusedEdit != null) return@walk
            if (node.isEditable && node.isFocused) {
                focusedEdit = AccessibilityNodeInfo.obtain(node)
            }
        }
        focusedEdit?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
            it.recycle()
        }
    }

    private suspend fun processCandidate(
        node: AccessibilityNodeInfo,
        listSnapshot: JobSnapshot,
        keyword: String,
        config: AppConfig,
        spec: PlatformAutomationSpec,
        matchKeyword: String = keyword,
        templateKeyword: String = keyword,
        statKey: String = keyword,
        skipMatchFilter: Boolean = false
    ) {
        repository.appendLog(RunLog(keyword = keyword, message = "进入岗位：${listSnapshot.summary.ifBlank { "未知岗位" }}"))
        if (!clickNode(node)) {
            throw StopAutomation("找到岗位卡片但点击失败，已暂停", keyword = keyword)
        }
        waitRandom(config, "等待岗位详情")

        // Wait for the detail page to fully load. On some platforms (especially Boss),
        // the page may briefly show a transition state before the detail content appears.
        var detailRoot = currentRootOrStop(keyword)
        assertNoRisk(detailRoot, config, keyword)

        // For Boss, 51job, Liepin and Zhilian, check if we're on a detail page BEFORE checking if we're
        // on a search results page. The detail page may have job recommendation cards at the
        // bottom that make findJobCandidates return non-empty results, causing
        // looksLikeSearchResultsPage to incorrectly return true for a detail page.
        if (spec.platform == JobPlatform.Boss || spec.platform == JobPlatform.Job51 || spec.platform == JobPlatform.Liepin || spec.platform == JobPlatform.Zhilian) {
            var isDetailPage = false
            if (spec.platform == JobPlatform.Boss) {
                isDetailPage = PageHeuristics.isBossJobDetailPage(detailRoot.joinedText())
            } else if (spec.platform == JobPlatform.Job51) {
                isDetailPage = PageHeuristics.isJob51JobDetailPage(detailRoot.joinedText())
            } else if (spec.platform == JobPlatform.Liepin) {
                isDetailPage = PageHeuristics.isLiepinJobDetailPage(detailRoot.joinedText())
            } else if (spec.platform == JobPlatform.Zhilian) {
                isDetailPage = PageHeuristics.isZhilianJobDetailPage(detailRoot.joinedText())
            }
            // If we just clicked a job card but the page doesn't look like a detail page yet,
            // wait a moment and re-check — the detail content may still be loading.
            if (!isDetailPage && !detailRoot.looksLikeSearchResultsPage(matchKeyword, spec)) {
                repository.appendLog(RunLog(keyword = keyword, message = "页面尚未完全加载，等待详情页内容"))
                delay(800)
                detailRoot = currentRootOrStop(keyword)
                if (spec.platform == JobPlatform.Boss) {
                    isDetailPage = PageHeuristics.isBossJobDetailPage(detailRoot.joinedText())
                } else if (spec.platform == JobPlatform.Job51) {
                    isDetailPage = PageHeuristics.isJob51JobDetailPage(detailRoot.joinedText())
                } else if (spec.platform == JobPlatform.Liepin) {
                    isDetailPage = PageHeuristics.isLiepinJobDetailPage(detailRoot.joinedText())
                } else if (spec.platform == JobPlatform.Zhilian) {
                    isDetailPage = PageHeuristics.isZhilianJobDetailPage(detailRoot.joinedText())
                }
            }
            if (isDetailPage) {
                // We are on a detail page — proceed to process it regardless of
                // looksLikeSearchResultsPage, which may give false positives on detail pages.
                repository.appendLog(RunLog(keyword = keyword, message = "检测到岗位详情页，继续处理"))
            } else if (detailRoot.looksLikeSearchResultsPage(matchKeyword, spec)) {
                markSnapshotProcessed(listSnapshot, listSnapshot)
                repository.appendLog(
                    RunLog(
                        level = LogLevel.Warning,
                        keyword = keyword,
                        message = "点击岗位后仍停留在列表页，已跳过当前卡片：${listSnapshot.summary.ifBlank { "未知岗位" }}"
                    )
                )
                return
            }
            // If neither detail page nor search results page, fall through to normal processing
        } else {
            if (detailRoot.looksLikeSearchResultsPage(matchKeyword, spec)) {
                markSnapshotProcessed(listSnapshot, listSnapshot)
                repository.appendLog(
                    RunLog(
                        level = LogLevel.Warning,
                        keyword = keyword,
                        message = "点击岗位后仍停留在列表页，已跳过当前卡片：${listSnapshot.summary.ifBlank { "未知岗位" }}"
                    )
                )
                return
            }
        }

        val detailSnapshot = mergeSnapshots(listSnapshot, evaluateJob(detailRoot.visibleTexts(), matchKeyword, config))
        if (spec.platform == JobPlatform.Zhilian && detailRoot.shouldSkipZhilianDelivery()) {
            markSnapshotProcessed(listSnapshot, detailSnapshot)
            repository.addRecord(
                detailSnapshot.toRecord(
                    keyword,
                    "跳过",
                    "智联招聘当前岗位只有立即投递/投简历入口，跳过",
                    spec.platform,
                    listSnapshot.aliasesFor(detailSnapshot)
                )
            )
            repository.appendLog(
                RunLog(
                    level = LogLevel.Warning,
                    keyword = keyword,
                    message = "跳过智联投递岗位：${detailSnapshot.summary.ifBlank { "未知岗位" }}"
                )
            )
            returnToCandidateList(keyword, spec, matchKeyword)
        }
        if (!skipMatchFilter) {
            val minScoreOverride = when (spec.platform) {
                JobPlatform.Liepin -> minOf(config.rules.minMatchScore, 20)
                else -> null
            }
            val (allowed, reason) = isAllowed(detailSnapshot, config, minScoreOverride)
            if (!allowed) {
                markSnapshotProcessed(listSnapshot, detailSnapshot)
                repository.addRecord(detailSnapshot.toRecord(keyword, "跳过", reason, spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
                repository.appendLog(RunLog(level = LogLevel.Warning, keyword = keyword, message = "跳过岗位：$reason / ${detailSnapshot.summary}"))
                returnToCandidateList(keyword, spec, matchKeyword)
            }
        }

        val alreadyCommunicated = detailRoot.findFirst(spec.alreadyCommunicatedTexts, clickable = null)
        if (alreadyCommunicated != null) {
            markSnapshotProcessed(listSnapshot, detailSnapshot)
            repository.addRecord(detailSnapshot.toRecord(keyword, "已打过招呼", "详情页显示已沟通，跳过重复发送", spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
            repository.appendLog(
                RunLog(
                    level = LogLevel.Warning,
                    keyword = keyword,
                    message = "已打过招呼，跳过重复发送：${detailSnapshot.summary.ifBlank { "未知岗位" }}"
                )
            )
            returnToCandidateList(keyword, spec, matchKeyword)
        }

        // Retry finding the communicate button with polling, as the detail page may still be loading.
        // Some platforms (especially Boss) may take longer to render the bottom action bar.
        var communicateButton: AccessibilityNodeInfo? = null
        var communicateRetryCount = 0
        val communicateMaxRetries = when (spec.platform) {
            JobPlatform.Boss -> 6
            JobPlatform.Zhilian -> 4
            else -> 2
        }
        while (communicateButton == null && communicateRetryCount < communicateMaxRetries) {
            val currentRoot = if (communicateRetryCount == 0) detailRoot else currentRootOrStop(keyword)
            if ((spec.platform == JobPlatform.Boss || spec.platform == JobPlatform.Zhilian) && currentRoot.looksLikeSearchResultsPage(matchKeyword, spec)) {
                markSnapshotProcessed(listSnapshot, detailSnapshot)
                repository.appendLog(
                    RunLog(
                        level = LogLevel.Warning,
                        keyword = keyword,
                        message = "查找沟通按钮时已回到岗位列表，跳过当前卡片：${detailSnapshot.summary.ifBlank { "未知岗位" }}"
                    )
                )
                return
            }
            // Re-check already-communicated state in case it appeared during polling
            val alreadyCommunicatedRetry = currentRoot.findFirst(spec.alreadyCommunicatedTexts, clickable = null)
            if (alreadyCommunicatedRetry != null) {
                markSnapshotProcessed(listSnapshot, detailSnapshot)
                repository.addRecord(detailSnapshot.toRecord(keyword, "已打过招呼", "详情页显示已沟通，跳过重复发送", spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
                repository.appendLog(
                    RunLog(
                        level = LogLevel.Warning,
                        keyword = keyword,
                        message = "已打过招呼，跳过重复发送：${detailSnapshot.summary.ifBlank { "未知岗位" }}"
                    )
                )
                returnToCandidateList(keyword, spec, matchKeyword)
            }
            communicateButton = currentRoot.findCommunicateButton(
                resourceIds = spec.communicateResourceIds,
                textOptions = spec.communicateTexts,
                excludedTexts = spec.excludedCommunicateTexts
            )
            if (communicateButton == null && communicateRetryCount < communicateMaxRetries - 1) {
                repository.appendLog(RunLog(level = LogLevel.Warning, keyword = keyword, message = "未找到沟通按钮，等待重试 (${communicateRetryCount + 1}/$communicateMaxRetries)"))
                delay(600)
            }
            communicateRetryCount++
        }
        // If still not found after polling, try scrolling down to reveal it.
        // On some platforms (especially Boss), the button may be below the visible area.
        // Try up to 2 scroll attempts with re-reading the page each time.
        if (communicateButton == null) {
            val maxScrollAttempts = if (spec.platform == JobPlatform.Boss) 2 else 1
            repeat(maxScrollAttempts) { scrollAttempt ->
                if (communicateButton != null) return@repeat
                repository.appendLog(RunLog(level = LogLevel.Warning, keyword = keyword, message = "轮询后未找到沟通按钮，尝试向下滚动 (${scrollAttempt + 1}/$maxScrollAttempts)"))
                val scrollRoot = currentRootOrStop(keyword)
                if ((spec.platform == JobPlatform.Boss || spec.platform == JobPlatform.Job51 || spec.platform == JobPlatform.Liepin || spec.platform == JobPlatform.Zhilian) && scrollRoot.looksLikeSearchResultsPage(matchKeyword, spec)) {
                    markSnapshotProcessed(listSnapshot, detailSnapshot)
                    repository.appendLog(
                        RunLog(
                            level = LogLevel.Warning,
                            keyword = keyword,
                            message = "准备滚动查找沟通按钮时已回到岗位列表，跳过当前卡片：${detailSnapshot.summary.ifBlank { "未知岗位" }}"
                        )
                    )
                    return
                }
                val screen = scrollRoot.bounds()
                val scrollable = mutableListOf<AccessibilityNodeInfo>()
                scrollRoot.walk { node ->
                    if (!node.isEnabled || !node.isScrollable) return@walk
                    val rect = node.bounds()
                    if (rect.width() >= screen.width() * 0.5f && rect.height() >= screen.height() * 0.35f) {
                        scrollable += AccessibilityNodeInfo.obtain(node)
                    }
                }
                val didScroll = scrollable
                    .sortedWith(compareByDescending<AccessibilityNodeInfo> { it.bounds().height() }.thenBy { it.bounds().top })
                    .firstOrNull()
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
                if (didScroll) {
                    delay(800)
                    val scrolledRoot = currentRootOrStop(keyword)
                    // Re-check already-communicated after scrolling
                    val alreadyCommunicatedAfterScroll = scrolledRoot.findFirst(spec.alreadyCommunicatedTexts, clickable = null)
                    if (alreadyCommunicatedAfterScroll != null) {
                        markSnapshotProcessed(listSnapshot, detailSnapshot)
                        repository.addRecord(detailSnapshot.toRecord(keyword, "已打过招呼", "详情页显示已沟通，跳过重复发送", spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
                        repository.appendLog(
                            RunLog(
                                level = LogLevel.Warning,
                                keyword = keyword,
                                message = "已打过招呼，跳过重复发送：${detailSnapshot.summary.ifBlank { "未知岗位" }}"
                            )
                        )
                        returnToCandidateList(keyword, spec, matchKeyword)
                    }
                    communicateButton = scrolledRoot.findCommunicateButton(
                        resourceIds = spec.communicateResourceIds,
                        textOptions = spec.communicateTexts,
                        excludedTexts = spec.excludedCommunicateTexts
                    )
                }
            }
        }
        val latestRoot = currentRootOrStop(keyword)
        if ((spec.platform == JobPlatform.Boss || spec.platform == JobPlatform.Job51 || spec.platform == JobPlatform.Liepin || spec.platform == JobPlatform.Zhilian) && latestRoot.looksLikeSearchResultsPage(matchKeyword, spec)) {
            markSnapshotProcessed(listSnapshot, detailSnapshot)
            repository.appendLog(
                RunLog(
                    level = LogLevel.Warning,
                    keyword = keyword,
                    message = "未找到沟通按钮前页面已回到岗位列表，跳过当前卡片：${detailSnapshot.summary.ifBlank { "未知岗位" }}"
                )
            )
            return
        }
        val clickedCommunicate = communicateButton?.let { clickNode(it) } == true ||
            (latestRoot.containsAnyVisibleText(spec.communicateTexts) && clickKnownTarget(latestRoot, spec.communicateTapTargets))
        if (!clickedCommunicate) {
            val noCommunicateReason = latestRoot.noCommunicateReason(spec)
            if (noCommunicateReason != null) {
                markSnapshotProcessed(listSnapshot, detailSnapshot)
                repository.addRecord(detailSnapshot.toRecord(keyword, "跳过", noCommunicateReason, spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
                repository.appendLog(
                    RunLog(
                        level = LogLevel.Warning,
                        keyword = keyword,
                        message = "跳过不可沟通岗位：$noCommunicateReason / ${detailSnapshot.summary.ifBlank { "未知岗位" }}"
                    )
                )
                returnToCandidateList(keyword, spec, matchKeyword)
            }

                markSnapshotProcessed(listSnapshot, detailSnapshot)
                repository.addRecord(detailSnapshot.toRecord(keyword, "暂停", "找不到立即沟通/打招呼按钮", spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
                repository.appendLog(
                    RunLog(
                        level = LogLevel.Warning,
                        keyword = keyword,
                        message = "沟通按钮调试信息：${latestRoot.debugSummary()}"
                    )
                )
                throw StopAutomation("找不到立即沟通或打招呼按钮，已暂停", keyword = keyword)
        }

        if (spec.platform == JobPlatform.Zhilian) {
            markSnapshotProcessed(listSnapshot, detailSnapshot)
            repository.addRecord(detailSnapshot.toRecord(keyword, "已点聊一聊", "智联招聘已点击聊一聊", spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
            repository.updateStats {
                it.copy(
                    todayGreetings = it.todayGreetings + 1,
                    keywordGreetings = it.keywordGreetings + (statKey to ((it.keywordGreetings[statKey] ?: 0) + 1)),
                    currentStep = "已点击聊一聊"
                )
            }
            repository.appendLog(RunLog(level = LogLevel.Success, keyword = keyword, message = "已点击聊一聊：${detailSnapshot.summary}"))
            waitRandom(config, "智联招聘聊一聊后等待", messageSent = true)
            returnToCandidateList(keyword, spec, matchKeyword)
            return
        }

        waitRandom(config, "等待聊天输入框")

        val chatRoot = currentRootOrStop(keyword)
        assertNoRisk(chatRoot, config, keyword)
        val input = findOrFocusChatInput(chatRoot, spec, keyword)
            ?: throw StopAutomation("找不到聊天输入框，已暂停", keyword = keyword)
        val template = config.templates.filter { it.randomEnabled }.randomOrNull()
            ?: config.templates.firstOrNull { it.isDefault }
            ?: config.templates.first()
        val message = renderGreetingTemplate(template, detailSnapshot, templateKeyword, config.profile)
        val filled = if (spec.platform == JobPlatform.Liepin) {
            ensureLiepinMessageReady(keyword, spec, message)
        } else {
            setTextWithFocus(input, message)
        }
        if (!filled) {
            repository.appendLog(
                RunLog(
                    level = LogLevel.Warning,
                    keyword = keyword,
                    message = "聊天输入框调试信息：${chatRoot.debugSummary()}"
                )
            )
            throw StopAutomation("找到聊天输入框但无法输入打招呼内容，已暂停", keyword = keyword)
        }
        repository.appendLog(RunLog(keyword = keyword, message = "已输入打招呼内容：${message.take(80)}"))

        when {
            config.runMode == RunMode.AutoSend && config.autoGreetingEnabled -> {
                waitRandom(config, "发送前间隔")
                if (spec.platform == JobPlatform.Liepin) {
                    val sendReadyRoot = currentRootOrStop(keyword)
                    val sendInput = sendReadyRoot.findChatEditText(spec.chatInputResourceIds) ?: sendReadyRoot.findFirstEditText()
                    val sendText = sendInput?.text?.toString().orEmpty().replace(Regex("\\s+"), "")
                    val sendDesc = sendInput?.contentDescription?.toString().orEmpty().replace(Regex("\\s+"), "")
                    val snippet = message.replace(Regex("\\s+"), "").take(8)
                    val hasTypedMessage = sendText.contains(snippet, ignoreCase = true) || sendDesc.contains(snippet, ignoreCase = true)
                    if (!hasTypedMessage) {
                        repository.appendLog(
                            RunLog(
                                level = LogLevel.Warning,
                                keyword = keyword,
                                message = "猎聘聊天输入框内容未写入，已暂停避免误点 + 号"
                            )
                        )
                        throw StopAutomation("猎聘聊天输入框内容未写入，已暂停", keyword = keyword)
                    }
                }
                val sendRoot = currentRootOrStop(keyword)
                val sendButton = sendRoot.findBestSendButton(spec.sendTexts, spec.sendResourceIds)
                val clickedSend = sendButton?.let { clickNode(it) } == true ||
                    (sendRoot.containsAnyVisibleText(spec.sendTexts) && clickKnownTarget(sendRoot, spec.sendTapTargets))
                if (!clickedSend) {
                        repository.appendLog(
                            RunLog(
                                level = LogLevel.Warning,
                                keyword = keyword,
                                message = "发送按钮调试信息：${sendRoot.debugSummary()}"
                            )
                        )
                        throw StopAutomation("找不到发送按钮，已暂停，避免盲目点击", keyword = keyword)
                }
                markSnapshotProcessed(listSnapshot, detailSnapshot)
                repository.addRecord(detailSnapshot.toRecord(keyword, "已打招呼", "自动发送成功", spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
                repository.updateStats {
                    it.copy(
                        todayGreetings = it.todayGreetings + 1,
                        keywordGreetings = it.keywordGreetings + (statKey to ((it.keywordGreetings[statKey] ?: 0) + 1)),
                        currentStep = "已发送打招呼"
                    )
                }
                repository.appendLog(RunLog(level = LogLevel.Success, keyword = keyword, message = "已打招呼：${detailSnapshot.summary}"))
                waitRandom(config, "发送后间隔", messageSent = true)
                returnToCandidateList(keyword, spec, matchKeyword)
            }

            config.runMode == RunMode.ConfirmBeforeSend -> {
                markSnapshotProcessed(listSnapshot, detailSnapshot)
                repository.addRecord(detailSnapshot.toRecord(keyword, "待确认", "已输入，等待用户确认发送", spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
                throw StopAutomation("已输入打招呼内容，等待用户确认发送", keyword = keyword)
            }

            else -> {
                markSnapshotProcessed(listSnapshot, detailSnapshot)
                repository.addRecord(detailSnapshot.toRecord(keyword, "只输入", "已输入但未自动发送", spec.platform, listSnapshot.aliasesFor(detailSnapshot)))
                repository.appendLog(RunLog(level = LogLevel.Success, keyword = keyword, message = "已输入未发送：${detailSnapshot.summary}"))
                returnToCandidateList(keyword, spec, matchKeyword)
            }
        }
    }

    private suspend fun currentRootOrStop(keyword: String): AccessibilityNodeInfo {
        repeat(12) {
            rootInActiveWindow?.let { return it }
            delay(250)
        }
        throw StopAutomation("读取不到当前页面内容，已暂停", keyword = keyword)
    }

    private fun assertNoRisk(root: AccessibilityNodeInfo, config: AppConfig, keyword: String) {
        val hit = findRiskStopWord(root, config.rules.riskStopWords)
        if (hit != null) {
            throw StopAutomation("检测到风险提示：$hit，任务已停止", keyword = keyword, isRisk = true)
        }
    }

    private fun findRiskStopWord(root: AccessibilityNodeInfo, riskStopWords: List<String>): String? {
        val joined = root.joinedText()
        return riskStopWords.firstOrNull { word ->
            val normalized = word.trim()
            when {
                normalized.isBlank() -> false
                normalized == "登录" -> containsLoginSecurityRisk(joined)
                normalized == "风险" -> false
                else -> joined.contains(normalized, ignoreCase = true)
            }
        }
    }

    private fun containsLoginSecurityRisk(joinedText: String): Boolean {
        return listOf(
            "登录已过期",
            "登录失效",
            "登录异常",
            "请重新登录",
            "重新登录",
            "异地登录"
        ).any { joinedText.contains(it, ignoreCase = true) }
    }

    private suspend fun waitRandom(
        config: AppConfig,
        step: String,
        messageSent: Boolean = false
    ) {
        repository.updateStats { it.copy(currentStep = step) }
        val low = minOf(config.rules.minDelaySeconds, config.rules.maxDelaySeconds).coerceAtLeast(1)
        val high = maxOf(config.rules.minDelaySeconds, config.rules.maxDelaySeconds).coerceAtLeast(low)
        val seconds = if (messageSent) Random.nextInt(low, high + 1) else Random.nextInt(1, minOf(3, high) + 1)
        delay(seconds * 1000L)
    }

    private suspend fun scrollForward(root: AccessibilityNodeInfo, keyword: String, candidateKeyword: String = keyword): Boolean {
        val listBounds = root.findJobListSwipeBounds(candidateKeyword)
        repository.updateStats { it.copy(currentStep = "下滑岗位列表加载更多") }
        if (listBounds != null) {
            repository.appendLog(RunLog(keyword = keyword, message = "在岗位列表区域下滑加载更多"))
            if (swipeUp(listBounds)) return true
        }

        val screen = root.bounds()
        val scrollables = mutableListOf<AccessibilityNodeInfo>()
        root.walk { node ->
            if (!node.isEnabled || !node.isScrollable) return@walk
            val rect = node.bounds()
            val belowHeader = rect.centerY() > screen.top + screen.height() * 0.28f
            val usefulSize = rect.width() >= screen.width() * 0.5f && rect.height() >= screen.height() * 0.35f
            if (belowHeader && usefulSize) {
                scrollables += AccessibilityNodeInfo.obtain(node)
            }
        }
        val target = scrollables
            .sortedWith(compareByDescending<AccessibilityNodeInfo> { it.bounds().height() }.thenBy { it.bounds().top })
            .firstOrNull()
        if (target?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) return true
        val bounds = target?.bounds() ?: Rect(
            screen.left + 12,
            screen.top + (screen.height() * 0.28f).toInt(),
            screen.right - 12,
            screen.bottom - maxOf(96, (screen.height() * 0.08f).toInt())
        )
        return swipeUp(bounds).also { success ->
            if (!success) {
                repository.appendLog(RunLog(level = LogLevel.Warning, keyword = keyword, message = "没有找到可滚动列表，也无法手势滚动"))
            }
        }
    }

    private fun mergeSnapshots(listSnapshot: JobSnapshot, detailSnapshot: JobSnapshot): JobSnapshot {
        val title = detailSnapshot.title.ifBlank { listSnapshot.title }
        val company = detailSnapshot.company.ifBlank { listSnapshot.company }
        val city = detailSnapshot.city.ifBlank { listSnapshot.city }
        val salary = detailSnapshot.salary.ifBlank { listSnapshot.salary }
        val texts = (detailSnapshot.texts + listSnapshot.texts).distinct()
        return detailSnapshot.copy(
            title = title,
            company = company,
            city = city,
            salary = salary,
            texts = texts,
            score = maxOf(detailSnapshot.score, listSnapshot.score),
            reasons = (detailSnapshot.reasons + listSnapshot.reasons).distinct()
        )
    }

    private fun JobSnapshot.toRecord(
        keyword: String,
        action: String,
        reason: String,
        platform: JobPlatform,
        aliases: List<String> = emptyList()
    ): JobRecord {
        return JobRecord(
            fingerprint = fingerprint,
            aliases = aliases,
            platform = platform,
            keyword = keyword,
            title = title,
            company = company,
            city = city,
            salary = salary,
            action = action,
            reason = reason
        )
    }

    private fun isProcessed(snapshot: JobSnapshot, records: List<JobRecord>, platform: JobPlatform): Boolean {
        return processedRecord(snapshot, records, platform) != null
    }

    private fun processedRecord(snapshot: JobSnapshot, records: List<JobRecord>, platform: JobPlatform): JobRecord? {
        if (snapshot.fingerprint in runtimeProcessedFingerprints) {
            return JobRecord(
                fingerprint = snapshot.fingerprint,
                platform = platform,
                keyword = "",
                title = snapshot.title,
                company = snapshot.company,
                city = snapshot.city,
                salary = snapshot.salary,
                action = "本次已处理",
                reason = "本次运行中已进入过该岗位"
            )
        }
        return records.firstOrNull {
            it.platform == platform &&
                it.action != "暂停" &&
                it.matchesFingerprint(snapshot.fingerprint)
        } ?: records.firstOrNull {
            it.platform == platform &&
                platform == JobPlatform.Job51 &&
                it.action != "暂停" &&
                it.sameStableIdentity(snapshot)
        }
    }

    private suspend fun logDuplicateSkip(keyword: String, snapshot: JobSnapshot, existing: JobRecord) {
        val key = existing.fingerprint.ifBlank { snapshot.fingerprint }
        if (!runtimeDuplicateLogFingerprints.add(key)) return
        val summary = snapshot.summary.ifBlank { existing.summary().ifBlank { "未知岗位" } }
        repository.appendLog(
            RunLog(
                level = LogLevel.Warning,
                keyword = keyword,
                message = "已处理过，跳过：$summary（${existing.action}）"
            )
        )
    }

    private fun markSnapshotProcessed(listSnapshot: JobSnapshot, detailSnapshot: JobSnapshot) {
        runtimeProcessedFingerprints += listSnapshot.fingerprint
        runtimeProcessedFingerprints += detailSnapshot.fingerprint
    }

    private fun JobSnapshot.aliasesFor(detailSnapshot: JobSnapshot): List<String> {
        return listOf(fingerprint, listAliasFingerprint())
            .filter { it.isNotBlank() && it != detailSnapshot.fingerprint }
            .distinct()
    }

    private fun JobSnapshot.listAliasFingerprint(): String {
        return stableFingerprint(texts.take(8))
    }

    private fun resultPageKey(root: AccessibilityNodeInfo): String {
        return stableFingerprint(root.visibleTexts().take(40))
    }

    private fun JobRecord.summary(): String {
        return listOf(title, company, city, salary).filter { it.isNotBlank() }.joinToString(" / ")
    }

    private fun JobRecord.sameStableIdentity(snapshot: JobSnapshot): Boolean {
        val sameTitle = title.isNotBlank() &&
            snapshot.title.isNotBlank() &&
            title.trim().equals(snapshot.title.trim(), ignoreCase = true)
        val sameCompany = company.isNotBlank() &&
            snapshot.company.isNotBlank() &&
            company.trim().equals(snapshot.company.trim(), ignoreCase = true)
        val compatibleCity = city.isBlank() ||
            snapshot.city.isBlank() ||
            city.trim().equals(snapshot.city.trim(), ignoreCase = true)
        return sameTitle && sameCompany && compatibleCity
    }

    private fun AccessibilityNodeInfo.containsAnyVisibleText(textOptions: List<String>): Boolean {
        val joined = joinedText()
        return textOptions.any { it.isNotBlank() && joined.contains(it, ignoreCase = true) }
    }

    private fun AccessibilityNodeInfo.firstVisibleText(textOptions: List<String>): String? {
        val joined = joinedText()
        return textOptions.firstOrNull { it.isNotBlank() && joined.contains(it, ignoreCase = true) }
    }

    private fun AccessibilityNodeInfo.looksReadyForSearchOrJobs(spec: PlatformAutomationSpec): Boolean {
        if (spec.platform == JobPlatform.Job51 && looksLikeJob51TopicListPage()) return true
        if (findBestSearchInput(spec.searchInputResourceIds) != null) return true
        if (findPlatformSearchEntry(spec) != null) return true
        if (spec.platform == JobPlatform.Job51 && PageHeuristics.canUseJob51SearchCoordinateFallback(joinedText())) return true
        if (spec.platform == JobPlatform.Job51) return false
        if (findJobCandidates("").isNotEmpty()) return true
        if (findBottomNavigationItem(spec.searchHomeTabTexts) != null) return true

        val joined = joinedText()
        val jobListHints = listOf("推荐", "附近", "最新", "急招", "筛选", "职位")
        return jobListHints.count { joined.contains(it, ignoreCase = true) } >= 2
    }

    private fun AccessibilityNodeInfo.looksLikeSearchResultsPage(keyword: String, spec: PlatformAutomationSpec): Boolean {
        if (spec.platform == JobPlatform.Job51 && looksLikeJob51TopicListPage()) return false
        if (spec.platform == JobPlatform.Zhilian && looksLikeZhilianJobDetailPage()) return false
        if (spec.platform == JobPlatform.Zhilian && PageHeuristics.isZhilianChatPage(
                joinedText(),
                findChatEditText(spec.chatInputResourceIds) != null || findFirstEditText() != null,
                findBestSendButton(spec.sendTexts, spec.sendResourceIds) != null
            )) return false
        val pageText = joinedText()
        val pageHasJobCandidates = findJobCandidates(keyword).isNotEmpty()
        val pageHasSearchInput = findBestSearchInput(spec.searchInputResourceIds) != null
        val pageHasChatInput = findChatEditText(spec.chatInputResourceIds) != null || findFirstEditText() != null
        val pageHasSendButton = findBestSendButton(spec.sendTexts, spec.sendResourceIds) != null
        return PageHeuristics.looksLikeSearchResultsPage(
            platform = spec.platform,
            joinedText = pageText,
            keyword = keyword,
            hasJobCandidates = pageHasJobCandidates,
            hasSearchInput = pageHasSearchInput,
            hasChatInput = pageHasChatInput,
            hasSendButton = pageHasSendButton
        )
    }

    private fun AccessibilityNodeInfo.noCommunicateReason(spec: PlatformAutomationSpec): String? {
        firstVisibleText(spec.alreadyCommunicatedTexts)?.let {
            return "详情页显示 $it，跳过重复发送"
        }
        firstVisibleText(spec.excludedCommunicateTexts)?.let {
            return "详情页只有 $it，未提供直接沟通入口"
        }
        firstVisibleText(NO_COMMUNICATE_STATUS_TEXTS)?.let {
            return "详情页显示 $it，暂不可沟通"
        }
        if (spec.platform == JobPlatform.Job51 && looksLikeJob51TopicListPage()) {
            return "当前页面是 51job 专题列表页，不是岗位详情"
        }
        return null
    }

    private fun AccessibilityNodeInfo.shouldSkipZhilianDelivery(): Boolean {
        if (!looksLikeZhilianJobDetailPage()) return false
        val joined = joinedText()
        val hasDeliveryOnly = listOf("立即投递", "投简历", "投递简历").any { joined.contains(it, ignoreCase = true) }
        val hasChat = listOf("聊一聊", "立即沟通", "继续沟通").any { joined.contains(it, ignoreCase = true) }
        return hasDeliveryOnly && !hasChat
    }

    private fun AccessibilityNodeInfo.looksLikeZhilianJobDetailPage(): Boolean {
        val joined = joinedText()
        val detailHints = listOf("职位描述", "更新于", "求职安全中心", "企业汇款认证", "校招网申", "已认证")
        val actionHints = listOf("聊一聊", "立即投递", "投简历", "投递简历")
        return detailHints.count { joined.contains(it, ignoreCase = true) } >= 2 &&
            actionHints.any { joined.contains(it, ignoreCase = true) }
    }

    private fun dismissTransientObstruction(root: AccessibilityNodeInfo, spec: PlatformAutomationSpec): Boolean {
        val dismissButton = root.findTransientDismissButton()
        if (dismissButton != null && clickNode(dismissButton)) return true
        return false
    }

    private fun clickJob51TopicBack(root: AccessibilityNodeInfo): Boolean {
        root.findTopLeftNavigationButton()?.let { button ->
            if (clickNode(button)) return true
        }
        return clickKnownTarget(root, listOf(KnownTapTarget(18, 108, 146, 248)))
    }

    private fun AccessibilityNodeInfo.findTransientDismissButton(): AccessibilityNodeInfo? {
        findFirstByResourceIds(TRANSIENT_DISMISS_RESOURCE_IDS)?.let { return it }

        val screen = bounds()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        walk { node ->
            if (!node.isEnabled) return@walk
            val text = node.ownText().ifBlank { node.joinedText() }
            if (TRANSIENT_DISMISS_TEXTS.none { it.isNotBlank() && text.contains(it, ignoreCase = true) }) return@walk
            val rect = node.bounds()
            if (rect.width() <= 0 || rect.height() <= 0) return@walk
            val reasonableSize = rect.width() * rect.height() <= screen.width() * screen.height() * 0.12
            if (reasonableSize) {
                candidates += findSmallestClickableContainer(node.bounds()) ?: AccessibilityNodeInfo.obtain(node)
            }
        }
        return candidates
            .distinctBy { it.resourceId() + it.bounds().flattenToString() }
            .sortedWith(
                compareBy<AccessibilityNodeInfo> {
                    val rect = it.bounds()
                    if (rect.centerY() <= screen.top + screen.height() * 0.45f || rect.centerX() >= screen.left + screen.width() * 0.55f) 0 else 1
                }.thenByDescending { it.bounds().right }.thenBy { it.bounds().top }
            )
            .firstOrNull()
    }

    private fun AccessibilityNodeInfo.findBottomNavigationItem(textOptions: List<String>): AccessibilityNodeInfo? {
        val screen = bounds()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        walk { node ->
            if (!node.isEnabled) return@walk
            val rect = node.bounds()
            if (rect.width() <= 0 || rect.height() <= 0) return@walk
            val inBottomArea = rect.centerY() >= screen.top + screen.height() * 0.68f
            val reasonableSize = rect.width() <= screen.width() * 0.42f && rect.height() <= screen.height() * 0.24f
            if (!inBottomArea || !reasonableSize) return@walk
            val text = node.ownText().ifBlank { node.joinedText() }
            if (textOptions.any { option -> option.isNotBlank() && text.contains(option, ignoreCase = true) }) {
                candidates += findSmallestClickableContainer(node.bounds()) ?: AccessibilityNodeInfo.obtain(node)
            }
        }
        return candidates
            .distinctBy { it.resourceId() + it.bounds().flattenToString() }
            .sortedWith(compareByDescending<AccessibilityNodeInfo> { it.bounds().centerY() }.thenBy { it.bounds().left })
            .firstOrNull()
    }

    private fun AccessibilityNodeInfo.findTopLeftNavigationButton(): AccessibilityNodeInfo? {
        val screen = bounds()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        walk { node ->
            if (!node.isEnabled) return@walk
            val rect = node.bounds()
            if (rect.width() <= 0 || rect.height() <= 0) return@walk
            val inHeaderLeft = rect.centerX() <= screen.left + screen.width() * 0.25f &&
                rect.centerY() in (screen.top + (screen.height() * 0.03f).toInt())..(screen.top + (screen.height() * 0.18f).toInt())
            val reasonableSize = rect.width() <= screen.width() * 0.20f && rect.height() <= screen.height() * 0.12f
            if (!inHeaderLeft || !reasonableSize) return@walk
            val text = node.ownText().ifBlank { node.joinedText() }
            val looksLikeBack = text.contains("返回", ignoreCase = true) ||
                text.contains("关闭", ignoreCase = true) ||
                text.contains("退出", ignoreCase = true) ||
                text.contains("back", ignoreCase = true)
            val clickable = findSmallestClickableContainer(rect) ?: if (node.isClickable) AccessibilityNodeInfo.obtain(node) else null
            if (clickable != null) {
                candidates += clickable
                if (looksLikeBack) return@walk
            }
        }
        return candidates
            .distinctBy { it.resourceId() + it.bounds().flattenToString() }
            .sortedWith(
                compareBy<AccessibilityNodeInfo> { it.bounds().top }
                    .thenBy { it.bounds().left }
                    .thenBy {
                        val rect = it.bounds()
                        rect.width() * rect.height()
                    }
            )
            .firstOrNull()
    }

    private fun AccessibilityNodeInfo.findSmallestClickableContainer(targetBounds: Rect): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        walk { node ->
            if (!node.isEnabled || !node.isClickable) return@walk
            val rect = node.bounds()
            if (!rect.contains(targetBounds) || rect.width() <= 0 || rect.height() <= 0) return@walk
            val current = best
            if (current == null || rect.width() * rect.height() < current.bounds().width() * current.bounds().height()) {
                best = AccessibilityNodeInfo.obtain(node)
            }
        }
        return best
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val rect = node.bounds()
        if (rect.width() <= 0 || rect.height() <= 0) return false
        return tapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    private fun clickKnownTarget(root: AccessibilityNodeInfo, targets: List<KnownTapTarget>): Boolean {
        val screen = root.bounds()
        if (screen.width() <= 0 || screen.height() <= 0) return false
        return targets.any { target ->
            val x = screen.left + screen.width() * target.centerX / target.sourceWidth
            val y = screen.top + screen.height() * target.centerY / target.sourceHeight
            tapAt(x, y)
        }
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun swipeUp(bounds: Rect): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 200) return false
        val x = bounds.centerX().toFloat()
        val startY = bounds.top + bounds.height() * 0.78f
        val endY = bounds.top + bounds.height() * 0.28f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 420))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private class StopAutomation(
        override val message: String,
        val keyword: String = "",
        val isRisk: Boolean = false
    ) : RuntimeException(message)

    private data class KnownTapTarget(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val sourceWidth: Float = 1200f,
        val sourceHeight: Float = 2664f
    ) {
        val centerX: Float = (left + right) / 2f
        val centerY: Float = (top + bottom) / 2f
    }

    private data class PlatformAutomationSpec(
        val platform: JobPlatform,
        val searchEntryResourceIds: List<String>,
        val searchInputResourceIds: List<String>,
        val searchButtonResourceIds: List<String>,
        val communicateResourceIds: List<String>,
        val chatInputResourceIds: List<String>,
        val searchEntryTexts: List<String>,
        val searchButtonTexts: List<String>,
        val recommendationTabTexts: List<String>,
        val searchHomeTabTexts: List<String>,
        val alreadyCommunicatedTexts: List<String>,
        val communicateTexts: List<String>,
        val excludedCommunicateTexts: List<String>,
        val sendResourceIds: List<String>,
        val sendTexts: List<String>,
        val searchEntryTapTargets: List<KnownTapTarget>,
        val searchInputTapTargets: List<KnownTapTarget>,
        val searchButtonTapTargets: List<KnownTapTarget>,
        val communicateTapTargets: List<KnownTapTarget>,
        val chatInputTapTargets: List<KnownTapTarget>,
        val sendTapTargets: List<KnownTapTarget>
    ) {
        companion object {
            fun forPlatform(platform: JobPlatform): PlatformAutomationSpec {
                val packageName = platform.packageName
                return PlatformAutomationSpec(
                    platform = platform,
                    searchEntryResourceIds = packageSpecificIds(packageName, BASE_SEARCH_ENTRY_RESOURCE_IDS + when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf(
                            "searchView",
                            "search_view",
                            "search_text",
                            "searchText",
                            "search_keyword",
                            "keyWord",
                            "keyword_edit",
                            "job_search",
                            "jobSearch",
                            "home_search",
                            "homeSearch",
                            "search_layout",
                            "searchLayout",
                            "ll_search",
                            "rl_search"
                        )
                        JobPlatform.Zhilian -> listOf(
                            "ll_search",
                            "tv_search_scroll"
                        )
                        JobPlatform.Liepin -> emptyList()
                    }),
                    searchInputResourceIds = packageSpecificIds(packageName, BASE_SEARCH_INPUT_RESOURCE_IDS + when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf("inputText")
                        JobPlatform.Zhilian -> listOf("keywordEditText")
                        JobPlatform.Liepin -> emptyList()
                    }),
                    searchButtonResourceIds = packageSpecificIds(packageName, BASE_SEARCH_BUTTON_RESOURCE_IDS + when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf("tv_search")
                        JobPlatform.Zhilian -> listOf("search_button")
                        JobPlatform.Liepin -> emptyList()
                    }),
                    communicateResourceIds = packageSpecificIds(packageName, BASE_COMMUNICATE_RESOURCE_IDS + when (platform) {
                        JobPlatform.Boss -> listOf("tv_chat", "btn_chat", "chat_btn")
                        JobPlatform.Job51 -> listOf("chatButton")
                        JobPlatform.Zhilian -> listOf("tv_a_chat")
                        JobPlatform.Liepin -> emptyList()
                    }),
                    chatInputResourceIds = packageSpecificIds(packageName, BASE_CHAT_INPUT_RESOURCE_IDS + when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf("etContent")
                        JobPlatform.Zhilian -> listOf("editTextMessage")
                        JobPlatform.Liepin -> emptyList()
                    }),
                    searchEntryTexts = BASE_SEARCH_ENTRY_TEXTS + when (platform) {
                        JobPlatform.Boss -> listOf("搜索职位、公司", "搜职位")
                        JobPlatform.Job51 -> listOf(
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
                        JobPlatform.Zhilian -> listOf("搜索职位/公司", "职位、公司")
                        JobPlatform.Liepin -> listOf("搜索职位/公司", "搜索职位、公司", "搜索")
                    },
                    searchButtonTexts = listOf("搜索", "搜 索", "Search"),
                    recommendationTabTexts = BASE_RECOMMENDATION_TAB_TEXTS + when (platform) {
                        JobPlatform.Boss -> listOf("职业")
                        JobPlatform.Job51 -> listOf("工作", "职位")
                        JobPlatform.Zhilian -> listOf("职位", "找工作")
                        JobPlatform.Liepin -> listOf("职位", "求职")
                    },
                    searchHomeTabTexts = BASE_HOME_TAB_TEXTS + when (platform) {
                        JobPlatform.Boss -> listOf("职位", "职业")
                        JobPlatform.Job51 -> listOf("首页", "工作", "职位", "找工作")
                        JobPlatform.Zhilian -> listOf("首页", "职位", "找工作")
                        JobPlatform.Liepin -> listOf("首页", "职位", "求职")
                    },
                    alreadyCommunicatedTexts = BASE_ALREADY_COMMUNICATED_TEXTS,
                    communicateTexts = BASE_COMMUNICATE_TEXTS + when (platform) {
                        JobPlatform.Boss -> listOf("立即开聊", "开聊", "继续开聊")
                        JobPlatform.Job51 -> listOf("去聊聊")
                        JobPlatform.Zhilian -> listOf("聊一聊")
                        JobPlatform.Liepin -> listOf("聊一聊")
                    },
                    excludedCommunicateTexts = BASE_EXCLUDED_COMMUNICATE_TEXTS,
                    sendResourceIds = packageSpecificIds(packageName, BASE_SEND_RESOURCE_IDS + when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> emptyList()
                        JobPlatform.Zhilian -> listOf("buttonSendMessage")
                        JobPlatform.Liepin -> emptyList()
                    }),
                    sendTexts = BASE_SEND_TEXTS,
                    searchEntryTapTargets = when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf(KnownTapTarget(240, 151, 722, 270))
                        JobPlatform.Zhilian -> listOf(KnownTapTarget(589, 152, 979, 269))
                        JobPlatform.Liepin -> listOf(KnownTapTarget(520, 185, 1200, 328))
                    },
                    searchInputTapTargets = when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf(KnownTapTarget(182, 152, 995, 269))
                        JobPlatform.Zhilian -> listOf(KnownTapTarget(312, 155, 925, 272))
                        JobPlatform.Liepin -> listOf(KnownTapTarget(387, 204, 953, 321))
                    },
                    searchButtonTapTargets = when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf(KnownTapTarget(995, 161, 1138, 259))
                        JobPlatform.Zhilian -> listOf(KnownTapTarget(925, 169, 1083, 257))
                        JobPlatform.Liepin -> listOf(KnownTapTarget(1005, 230, 1148, 295))
                    },
                    communicateTapTargets = when (platform) {
                        JobPlatform.Boss -> listOf(KnownTapTarget(600, 2429, 1148, 2572))
                        JobPlatform.Job51 -> listOf(KnownTapTarget(294, 2429, 1148, 2572))
                        JobPlatform.Zhilian -> listOf(KnownTapTarget(78, 2429, 572, 2572))
                        JobPlatform.Liepin -> listOf(KnownTapTarget(784, 2397, 1109, 2553))
                    },
                    chatInputTapTargets = when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf(KnownTapTarget(221, 2424, 914, 2546))
                        JobPlatform.Zhilian -> listOf(KnownTapTarget(247, 2438, 880, 2566))
                        JobPlatform.Liepin -> listOf(
                            KnownTapTarget(195, 2452, 888, 2553),
                            KnownTapTarget(195, 1542, 849, 1643)
                        )
                    },
                    sendTapTargets = when (platform) {
                        JobPlatform.Boss -> emptyList()
                        JobPlatform.Job51 -> listOf(KnownTapTarget(982, 2433, 1138, 2534))
                        JobPlatform.Zhilian -> listOf(KnownTapTarget(1056, 2450, 1160, 2554))
                        JobPlatform.Liepin -> listOf(
                            KnownTapTarget(1005, 1526, 1135, 1630),
                            KnownTapTarget(1004, 2449, 1148, 2566)
                        )
                    }
                )
            }

            private fun packageSpecificIds(packageName: String, ids: List<String>): List<String> {
                return (ids + ids.map { "$packageName:id/$it" }).distinct()
            }
        }
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val FOREGROUND_CHANNEL_ID = "jobguide_foreground"
        private const val RECOMMENDATION_SOURCE_LABEL = "职位页推荐"
        private val BASE_SEARCH_ENTRY_RESOURCE_IDS = listOf(
            "ly_menu",
            "search_tip_vf",
            "tv_desc",
            "searchInputContainerView",
            "fl_search_input_container",
            "constraintLayout_search_input",
            "tv_search_hint",
            "search",
            "search_bar",
            "searchBar",
            "search_input",
            "searchInput",
            "et_search",
            "edit_search",
            "keyword",
            "query"
        )
        private val BASE_SEARCH_INPUT_RESOURCE_IDS = listOf(
            "inputText",
            "keywordEditText",
            "search_input",
            "searchInput",
            "et_search",
            "edit_search",
            "keyword",
            "query"
        )
        private val BASE_SEARCH_BUTTON_RESOURCE_IDS = listOf(
            "tv_search",
            "search_button",
            "btn_search",
            "searchButton"
        )
        private val BASE_SEARCH_ENTRY_TEXTS = listOf(
            "搜索",
            "搜索职位",
            "搜索职位/公司",
            "搜索职位、公司",
            "职位/公司",
            "职位或公司",
            "请输入职位",
            "请输入关键词"
        )
        private val BASE_RECOMMENDATION_TAB_TEXTS = listOf("首页", "推荐", "职位", "岗位", "工作", "求职")
        private val BASE_HOME_TAB_TEXTS = listOf("首页", "职位", "工作", "找工作", "推荐")
        private val BASE_ALREADY_COMMUNICATED_TEXTS = listOf(
            "继续沟通",
            "已沟通",
            "已打招呼",
            "沟通过",
            "已联系",
            "已聊",
            "继续聊",
            "聊过",
            "刚刚聊过",
            "最近聊过",
            "已交换",
            "已开聊",
            "已沟通过",
            "已投递",
            "已申请"
        )
        private val BASE_COMMUNICATE_RESOURCE_IDS = listOf(
            "chatButton",
            "tv_a_chat",
            "btn_chat",
            "chat_button",
            "communicate",
            "contact"
        )
        private val BASE_CHAT_INPUT_RESOURCE_IDS = listOf(
            "etContent",
            "editTextMessage",
            "message_input",
            "input_message",
            "edit_message",
            "chat_input",
            "msg_input"
        )
        private val BASE_COMMUNICATE_TEXTS = listOf(
            "立即沟通",
            "继续沟通",
            "打招呼",
            "沟通",
            "聊一聊",
            "立即聊",
            "联系HR",
            "联系 HR",
            "联系招聘者",
            "立即联系"
        )
        private val BASE_EXCLUDED_COMMUNICATE_TEXTS = listOf(
            "投简历",
            "投递简历",
            "申请职位",
            "申请岗位",
            "立即申请",
            "发送简历",
            "发简历",
            "投递",
            "申请"
        )
        private val NO_COMMUNICATE_STATUS_TEXTS = listOf(
            "停止招聘",
            "已暂停招聘",
            "职位已下线",
            "岗位已下线",
            "已结束招聘",
            "已招满",
            "不合适",
            "暂不匹配",
            "无法沟通",
            "暂不可沟通",
            "请先完善简历",
            "需要先投递简历"
        )
        private val BASE_SEND_RESOURCE_IDS = listOf(
            "tv_send",
            "btn_send",
            "send",
            "iv_send",
            "message_send",
            "msg_send",
            "send_btn",
            "button_send"
        )
        private val BASE_SEND_TEXTS = listOf("发送", "发 送", "Send", "send", "确定")
        private val TRANSIENT_DISMISS_RESOURCE_IDS = listOf(
            "iv_close",
            "img_close",
            "close",
            "close_btn",
            "btn_close",
            "iv_cancel",
            "cancel",
            "skip",
            "tv_skip",
            "com.job.android:id/iv_close",
            "com.job.android:id/img_close",
            "com.job.android:id/close",
            "com.job.android:id/close_btn",
            "com.job.android:id/btn_close",
            "com.job.android:id/iv_cancel",
            "com.job.android:id/cancel",
            "com.job.android:id/skip",
            "com.job.android:id/tv_skip"
        )
        private val TRANSIENT_DISMISS_TEXTS = listOf(
            "关闭",
            "跳过",
            "稍后再说",
            "暂不",
            "以后再说",
            "我知道了",
            "取消",
            "Not now",
            "Skip",
            "Close"
        )
    }
}
