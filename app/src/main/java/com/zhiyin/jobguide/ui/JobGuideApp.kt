package com.zhiyin.jobguide.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhiyin.jobguide.data.AppConfig
import com.zhiyin.jobguide.data.GreetingTemplate
import com.zhiyin.jobguide.data.JobPlatform
import com.zhiyin.jobguide.data.JobRecord
import com.zhiyin.jobguide.data.JobSourceMode
import com.zhiyin.jobguide.data.KeywordSetting
import com.zhiyin.jobguide.data.LogLevel
import com.zhiyin.jobguide.data.ProfileConfig
import com.zhiyin.jobguide.data.RunLog
import com.zhiyin.jobguide.data.RunMode
import com.zhiyin.jobguide.data.RulesConfig
import com.zhiyin.jobguide.data.TaskStatus
import com.zhiyin.jobguide.data.displayText
import com.zhiyin.jobguide.data.splitInput
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppTab(val label: String) {
    Home("执行"),
    Keywords("关键词"),
    Rules("规则"),
    Templates("模板"),
    Logs("日志"),
    Profile("我的")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobGuideApp(viewModel: JobGuideViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibilityStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("职引助手", style = MaterialTheme.typography.titleLarge)
                        Text("多招聘平台求职自动化控制台", style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEAF7FF),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color(0xFFFFFFFF),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.Home -> Icons.Filled.Home
                                    AppTab.Keywords -> Icons.Filled.Key
                                    AppTab.Rules -> Icons.Filled.Rule
                                    AppTab.Templates -> Icons.Filled.Textsms
                                    AppTab.Logs -> Icons.Filled.ViewList
                                    AppTab.Profile -> Icons.Filled.Person
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(futureBackgroundBrush())
        ) {
            when (selectedTab) {
                AppTab.Home -> HomeScreen(state, viewModel)
                AppTab.Keywords -> KeywordsScreen(state.config.keywords, viewModel)
                AppTab.Rules -> RulesScreen(state.config.rules, viewModel)
                AppTab.Templates -> TemplatesScreen(state.config.templates, viewModel)
                AppTab.Logs -> LogsScreen(state.logs, state.records, viewModel)
                AppTab.Profile -> ProfileScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun futureBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            Color(0xFFF7FCFF),
            Color(0xFFE6F5FF)
        )
    )
}

@Composable
private fun HomeScreen(state: JobGuideUiState, viewModel: JobGuideViewModel) {
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限结果不影响任务执行 */ }

    // Request notification permission on app start (before task runs),
    // so the permission dialog doesn't interrupt the automation flow.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PageHeader(
                title = "执行控制台",
                subtitle = "跟踪今日进度、当前关键词和自动化状态"
            )
        }
        item {
            StatusPanel(state)
        }
        item {
            ActionPanel(state, viewModel)
        }
        item {
            JobPlatformPanel(state.config, viewModel)
        }
        item {
            JobSourcePanel(state.config, viewModel)
        }
        item {
            RunModePanel(state.config, viewModel)
        }
        item {
            SummaryPanel(state.config)
        }
        item {
            SafetyPanel(state.accessibilityEnabled, state.accessibilityWasEnabled, viewModel)
        }
    }
}

@Composable
private fun StatusPanel(state: JobGuideUiState) {
    val dailyLimit = state.config.rules.dailyGreetingLimit.coerceAtLeast(1)
    val progress = (state.stats.todayGreetings.toFloat() / dailyLimit).coerceIn(0f, 1f)
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("当前任务", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    state.stats.currentStep,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            StatusPill(state.stats.status)
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = statusAccent(state.stats.status),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("已处理岗位", "${state.stats.todayProcessed}", Modifier.weight(1f))
            MetricTile("成功沟通", "${state.stats.todayGreetings}", Modifier.weight(1f))
            MetricTile("今日剩余", "${state.remainingGreetings}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        KeyValue("当前来源/关键词", state.stats.currentKeyword.ifBlank { "等待开始" })
        KeyValue("今日总上限", "${state.config.rules.dailyGreetingLimit} 次")
        if (state.stats.riskReason.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            WarningText(state.stats.riskReason)
        }
    }
}

@Composable
private fun ActionPanel(state: JobGuideUiState, viewModel: JobGuideViewModel) {
    SectionCard {
        SectionHeading("执行操作", "开始前请确认无障碍权限和运行模式")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::startTask,
            enabled = state.stats.status != TaskStatus.Running,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "开始")
            Spacer(Modifier.width(6.dp))
            Text("开始执行")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = viewModel::pauseTask,
                enabled = state.stats.status == TaskStatus.Running,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Pause, contentDescription = "暂停")
                Spacer(Modifier.width(4.dp))
                Text("暂停")
            }
            OutlinedButton(
                onClick = viewModel::stopTask,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "停止")
                Spacer(Modifier.width(4.dp))
                Text("停止")
            }
        }
    }
}

@Composable
private fun JobPlatformPanel(config: AppConfig, viewModel: JobGuideViewModel) {
    SectionCard {
        SectionHeading("招聘平台", "单次任务只会在当前选中的 App 中执行")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            JobPlatform.entries.forEach { platform ->
                JobPlatformOption(
                    platform = platform,
                    selected = config.jobPlatform == platform,
                    onClick = { viewModel.updateJobPlatform(platform) }
                )
            }
        }
    }
}

@Composable
private fun JobPlatformOption(platform: JobPlatform, selected: Boolean, onClick: () -> Unit) {
    val accent = when (platform) {
        JobPlatform.Boss -> MaterialTheme.colorScheme.primary
        JobPlatform.Job51 -> MaterialTheme.colorScheme.secondary
        JobPlatform.Zhilian -> MaterialTheme.colorScheme.tertiary
        JobPlatform.Liepin -> Color(0xFF15803D)
    }
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val backgroundBrush = if (selected) {
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = 0.20f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundBrush)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = if (selected) 0.22f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = platform.label,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(platform.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(platform.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun JobSourcePanel(config: AppConfig, viewModel: JobGuideViewModel) {
    SectionCard {
        SectionHeading("岗位来源", "选择从关键词搜索还是当前职位页推荐流开始")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            JobSourceMode.entries.forEach { mode ->
                JobSourceOption(
                    mode = mode,
                    selected = config.jobSourceMode == mode,
                    onClick = { viewModel.updateJobSourceMode(mode) }
                )
            }
        }
        if (config.jobSourceMode == JobSourceMode.HomeRecommendations) {
            Divider(Modifier.padding(vertical = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CompactNumberInput(
                    label = "推荐成功目标",
                    value = config.recommendationGreetingTarget,
                    modifier = Modifier.weight(1f),
                    fieldKey = "recommendationGreetingTarget",
                    onValueChange = viewModel::updateRecommendationGreetingTarget
                )
                Text(
                    if (config.recommendationGreetingTarget == 0) "不执行" else "达标后结束",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun JobSourceOption(mode: JobSourceMode, selected: Boolean, onClick: () -> Unit) {
    val accent = when (mode) {
        JobSourceMode.KeywordSearch -> MaterialTheme.colorScheme.primary
        JobSourceMode.HomeRecommendations -> MaterialTheme.colorScheme.tertiary
    }
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val backgroundBrush = if (selected) {
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = 0.20f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundBrush)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = if (selected) 0.22f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = mode.label,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RunModePanel(config: AppConfig, viewModel: JobGuideViewModel) {
    SectionCard {
        SectionHeading("运行模式", "默认建议使用发送前确认或只输入不发送")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RunMode.entries.forEach { mode ->
                RunModeOption(
                    mode = mode,
                    selected = config.runMode == mode,
                    onClick = { viewModel.updateRunMode(mode) }
                )
            }
        }
        Divider(Modifier.padding(vertical = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("自动打招呼开关", fontWeight = FontWeight.SemiBold)
                Text("仅在运行模式为自动打招呼时生效", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = config.autoGreetingEnabled,
                onCheckedChange = viewModel::updateAutoGreeting
            )
        }
    }
}

@Composable
private fun RunModeOption(mode: RunMode, selected: Boolean, onClick: () -> Unit) {
    val accent = when (mode) {
        RunMode.InputOnly -> MaterialTheme.colorScheme.secondary
        RunMode.ConfirmBeforeSend -> MaterialTheme.colorScheme.primary
        RunMode.AutoSend -> MaterialTheme.colorScheme.tertiary
    }
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val backgroundBrush = if (selected) {
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = 0.20f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundBrush)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = if (selected) 0.22f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = mode.label,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryPanel(config: AppConfig) {
    SectionCard {
        SectionHeading(
            "任务配置",
            if (config.jobSourceMode == JobSourceMode.HomeRecommendations) {
                "执行时会从 ${config.jobPlatform.label} 当前首页/职位页推荐流开始"
            } else {
                "执行时会在 ${config.jobPlatform.label} 按关键词顺序依次搜索"
            }
        )
        Spacer(Modifier.height(12.dp))
        KeyValue("招聘平台", config.jobPlatform.label)
        KeyValue("岗位来源", config.jobSourceMode.label)
        if (config.jobSourceMode == JobSourceMode.HomeRecommendations) {
            KeyValue("推荐成功目标", "${config.recommendationGreetingTarget} 次")
        } else {
            KeyValue("启用关键词", config.keywords.filter { it.enabled }.joinToString("、") { it.name })
            KeyValue("关键词成功目标", "${config.keywords.firstOrNull()?.maxGreetings ?: 5} 次")
        }
        KeyValue("今日总上限", "${config.rules.dailyGreetingLimit} 次")
        KeyValue("城市", config.rules.targetCities.displayText())
        KeyValue("白名单", config.rules.jobWhitelist.displayText())
        KeyValue("黑名单", config.rules.jobBlacklist.displayText())
    }
}

@Composable
private fun SafetyPanel(accessibilityEnabled: Boolean, accessibilityWasEnabled: Boolean, viewModel: JobGuideViewModel) {
    val context = LocalContext.current
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("无障碍权限", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (accessibilityEnabled) "已开启" else if (accessibilityWasEnabled) "服务被系统关闭，请重新开启" else "首次使用需开启后才能执行自动化",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            OutlinedButton(
                onClick = { viewModel.openAccessibilitySettings(context) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (accessibilityEnabled) "查看" else "去设置")
            }
        }
        if (!accessibilityEnabled && accessibilityWasEnabled) {
            Spacer(Modifier.height(8.dp))
            WarningText("无障碍服务被系统关闭了！请在设置中重新开启，或在手机设置中关闭对职引助手的后台限制")
            Spacer(Modifier.height(8.dp))
            Text(
                "提示：部分手机（小米、华为、OPPO、vivo）清理后台时会自动关闭无障碍权限。" +
                "请在手机设置 → 应用管理 → 电池优化/自启动管理中允许职引助手自启动和后台运行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "权限用途：读取页面文字、点击按钮、输入文字和执行自动化流程。遇到验证码、登录异常、安全验证、操作频繁等提示会自动停止。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeywordsScreen(keywords: List<KeywordSetting>, viewModel: JobGuideViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "关键词队列",
                subtitle = "按顺序执行搜索，达到目标后自动切换"
            )
        }
        item {
            Button(
                onClick = viewModel::addKeyword,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
                Spacer(Modifier.width(6.dp))
                Text("新增关键词")
            }
        }
        items(keywords, key = { it.id }) { keyword ->
            KeywordEditor(keyword, viewModel)
        }
    }
}

@Composable
private fun KeywordEditor(keyword: KeywordSetting, viewModel: JobGuideViewModel) {
    var nameField by remember(keyword.id) {
        mutableStateOf(
            TextFieldValue(
                text = keyword.name,
                selection = TextRange(keyword.name.length)
            )
        )
    }

    LaunchedEffect(keyword.id, keyword.name) {
        if (keyword.name != nameField.text) {
            nameField = TextFieldValue(
                text = keyword.name,
                selection = TextRange(keyword.name.length)
            )
        }
    }

    LaunchedEffect(keyword.id, nameField.text) {
        delay(350)
        if (keyword.name != nameField.text) {
            viewModel.updateKeyword(keyword.copy(name = nameField.text))
        }
    }

    CompactSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (keyword.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(Modifier.width(8.dp))
            CompactTextInput(
                value = nameField,
                onValueChange = { nameField = it },
                label = "关键词",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = keyword.enabled,
                onCheckedChange = { viewModel.updateKeyword(keyword.copy(enabled = it)) }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CompactNumberInput(
                label = "成功目标",
                value = keyword.maxGreetings,
                modifier = Modifier.weight(1f),
                fieldKey = "${keyword.id}-maxGreetings",
                onValueChange = { viewModel.updateKeyword(keyword.copy(maxGreetings = it.coerceAtLeast(0))) }
            )
            Text(
                if (keyword.maxGreetings == 0) "跳过" else "达标后切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { viewModel.moveKeyword(keyword.id, -1) }) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "上移")
            }
            IconButton(onClick = { viewModel.moveKeyword(keyword.id, 1) }) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "下移")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.deleteKeyword(keyword.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RulesScreen(rules: RulesConfig, viewModel: JobGuideViewModel) {
    val textMap = rules.toEditableTextMap()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "筛选规则",
                subtitle = "控制岗位匹配、跳过条件和安全停止"
            )
        }
        item {
            SectionCard {
                SectionHeading("基础筛选", "城市、薪资、每日上限和操作节奏")
                Spacer(Modifier.height(6.dp))
                Text(
                    "规则用于决定哪些岗位值得进入详情页，以及什么时候必须停止任务：白名单提高匹配，黑名单直接跳过，风险停止词一出现就停。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                ListTextField("目标城市", textMap.getValue("targetCities"), fieldKey = "rules-targetCities") {
                    viewModel.updateRules(rules.withListField("targetCities", it))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("最低薪资K", rules.salaryMinK, Modifier.weight(1f), fieldKey = "rules-salaryMinK") {
                        viewModel.updateRules(rules.copy(salaryMinK = it))
                    }
                    NumberField("最高薪资K", rules.salaryMaxK, Modifier.weight(1f), fieldKey = "rules-salaryMaxK") {
                        viewModel.updateRules(rules.copy(salaryMaxK = it))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("每日总上限", rules.dailyGreetingLimit, Modifier.weight(1f), fieldKey = "rules-dailyGreetingLimit") {
                        viewModel.updateRules(rules.copy(dailyGreetingLimit = it.coerceAtLeast(0)))
                    }
                    NumberField("最低评分", rules.minMatchScore, Modifier.weight(1f), fieldKey = "rules-minMatchScore") {
                        viewModel.updateRules(rules.copy(minMatchScore = it))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("最小间隔秒", rules.minDelaySeconds, Modifier.weight(1f), fieldKey = "rules-minDelaySeconds") {
                        viewModel.updateRules(rules.copy(minDelaySeconds = it.coerceAtLeast(1)))
                    }
                    NumberField("最大间隔秒", rules.maxDelaySeconds, Modifier.weight(1f), fieldKey = "rules-maxDelaySeconds") {
                        viewModel.updateRules(rules.copy(maxDelaySeconds = it.coerceAtLeast(rules.minDelaySeconds)))
                    }
                }
            }
        }
        item {
            SectionCard {
                SectionHeading("关键词规则", "白名单命中后进入详情，黑名单命中后跳过")
                Spacer(Modifier.height(12.dp))
                ListTextField("必备关键词", textMap.getValue("requiredKeywords"), fieldKey = "rules-requiredKeywords") {
                    viewModel.updateRules(rules.withListField("requiredKeywords", it))
                }
                ListTextField("岗位白名单", textMap.getValue("jobWhitelist"), fieldKey = "rules-jobWhitelist") {
                    viewModel.updateRules(rules.withListField("jobWhitelist", it))
                }
                ListTextField("公司白名单", textMap.getValue("companyWhitelist"), fieldKey = "rules-companyWhitelist") {
                    viewModel.updateRules(rules.withListField("companyWhitelist", it))
                }
                ListTextField("岗位黑名单", textMap.getValue("jobBlacklist"), fieldKey = "rules-jobBlacklist") {
                    viewModel.updateRules(rules.withListField("jobBlacklist", it))
                }
                ListTextField("公司黑名单", textMap.getValue("companyBlacklist"), fieldKey = "rules-companyBlacklist") {
                    viewModel.updateRules(rules.withListField("companyBlacklist", it))
                }
                ListTextField("风险停止词", textMap.getValue("riskStopWords"), fieldKey = "rules-riskStopWords") {
                    viewModel.updateRules(rules.withListField("riskStopWords", it))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatesScreen(templates: List<GreetingTemplate>, viewModel: JobGuideViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "打招呼模板",
                subtitle = "模板变量会自动替换为求职者和岗位信息"
            )
        }
        item {
            Button(
                onClick = viewModel::addTemplate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
                Spacer(Modifier.width(6.dp))
                Text("新增模板")
            }
        }
        items(templates, key = { it.id }) { template ->
            TemplateEditor(template, viewModel)
        }
        item {
            SectionCard {
                SectionHeading("可用变量", "可直接写进模板正文")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("{name}", "{job_title}", "{company}", "{city}", "{salary}", "{skills}", "{keyword}").forEach {
                        AssistChip(onClick = {}, label = { Text(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateEditor(template: GreetingTemplate, viewModel: JobGuideViewModel) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = template.title,
                onValueChange = { viewModel.updateTemplate(template.copy(title = it)) },
                label = { Text("模板名称") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.deleteTemplate(template.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = template.body,
            onValueChange = { viewModel.updateTemplate(template.copy(body = it)) },
            label = { Text("打招呼文案") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { viewModel.setDefaultTemplate(template.id) },
                enabled = !template.isDefault,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (template.isDefault) "默认模板" else "设为默认")
            }
            Spacer(Modifier.weight(1f))
            Text("随机使用", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = template.randomEnabled,
                onCheckedChange = { viewModel.updateTemplate(template.copy(randomEnabled = it)) }
            )
        }
    }
}

@Composable
private fun LogsScreen(logs: List<RunLog>, records: List<JobRecord>, viewModel: JobGuideViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PageHeader(
                title = "运行日志",
                subtitle = "查看自动化步骤、跳过原因和岗位记录"
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = viewModel::clearLogs, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("清空日志")
                }
                OutlinedButton(onClick = viewModel::clearRecords, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("清空岗位记录")
                }
            }
        }
        item {
            SectionHeading("步骤日志", "${logs.size} 条")
        }
        if (logs.isEmpty()) {
            item { EmptyText("暂无运行日志") }
        } else {
            items(logs, key = { it.id }) { log -> LogRow(log) }
        }
        item {
            SectionHeading("岗位记录", "${records.size} 条")
        }
        if (records.isEmpty()) {
            item { EmptyText("暂无岗位记录") }
        } else {
            items(records, key = { it.fingerprint + it.timestamp }) { record -> RecordRow(record) }
        }
    }
}

@Composable
private fun ProfileScreen(state: JobGuideUiState, viewModel: JobGuideViewModel) {
    val context = LocalContext.current
    var importDialogVisible by remember { mutableStateOf(false) }
    var exportDialogVisible by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importConfig(readTextFromUri(context, it)) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeTextToUri(context, it, state.exportedJson) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "我的配置",
                subtitle = "维护模板变量、权限状态和本地数据"
            )
        }
        item {
            ProfileEditor(state.config.profile, viewModel)
        }
        item {
            SectionCard {
                SectionHeading("权限与数据", "配置导入导出和本地记录清理")
                Spacer(Modifier.height(10.dp))
                KeyValue("无障碍权限", when {
                    state.accessibilityEnabled -> "已开启"
                    state.accessibilityWasEnabled -> "已关闭（需重新开启）"
                    else -> "未开启"
                })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入")
                        Spacer(Modifier.width(4.dp))
                        Text("导入")
                    }
                    OutlinedButton(
                        onClick = { exportLauncher.launch("jobguide-config.json") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "导出")
                        Spacer(Modifier.width(4.dp))
                        Text("导出")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { importDialogVisible = true }, modifier = Modifier.weight(1f)) {
                        Text("粘贴导入")
                    }
                    TextButton(onClick = { exportDialogVisible = true }, modifier = Modifier.weight(1f)) {
                        Text("查看 JSON")
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::clearTodayStats, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("清空今日统计")
                    }
                    OutlinedButton(onClick = viewModel::clearRecords, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("清空岗位记录")
                    }
                }
            }
        }
        item {
            SectionCard {
                SectionHeading("隐私说明", "本地保存，不保存账号密码")
                Spacer(Modifier.height(8.dp))
                Text("本应用只保存本地配置、运行日志和岗位处理记录，不保存招聘平台账号密码，不调用非公开接口。", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionCard {
                SectionHeading("风险说明", "遇到风险提示会停止")
                Spacer(Modifier.height(8.dp))
                Text("检测到验证码、登录、安全验证、账号异常、操作频繁、稍后再试等提示时会停止任务。找不到关键按钮时会暂停，避免盲目点击。", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (importDialogVisible) {
        ImportDialog(
            onDismiss = { importDialogVisible = false },
            onImport = {
                viewModel.importConfig(it)
                importDialogVisible = false
            }
        )
    }
    if (exportDialogVisible) {
        ExportDialog(
            json = state.exportedJson,
            onDismiss = { exportDialogVisible = false }
        )
    }
}

@Composable
private fun ProfileEditor(profile: ProfileConfig, viewModel: JobGuideViewModel) {
    SectionCard {
        SectionHeading("求职者信息", "用于模板变量替换")
        Spacer(Modifier.height(6.dp))
        Text(
            "这里的信息会填入打招呼模板变量，比如 {name}、{skills}；期望城市和薪资用于后续筛选参考。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        StableTextField(
            value = profile.name,
            onValueChange = { viewModel.updateProfile(profile.copy(name = it)) },
            label = "姓名",
            fieldKey = "profile-name",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        ListTextField("技能标签", profile.skills.displayText(), fieldKey = "profile-skills") {
            viewModel.updateProfile(profile.copy(skills = splitInput(it)))
        }
        ListTextField("期望城市", profile.expectedCities.displayText(), fieldKey = "profile-cities") {
            viewModel.updateProfile(profile.copy(expectedCities = splitInput(it)))
        }
        StableTextField(
            value = profile.expectedSalary,
            onValueChange = { viewModel.updateProfile(profile.copy(expectedSalary = it)) },
            label = "期望薪资",
            fieldKey = "profile-salary",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 JSON 配置") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ExportDialog(json: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("JSON 配置") },
        text = {
            OutlinedTextField(
                value = json,
                onValueChange = {},
                minLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String = "") {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun CompactSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            content = content
        )
    }
}

@Composable
private fun StatusPill(status: TaskStatus) {
    val color = statusAccent(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(status.label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun statusAccent(status: TaskStatus): Color = when (status) {
    TaskStatus.Running -> MaterialTheme.colorScheme.primary
    TaskStatus.Completed -> Color(0xFF15803D)
    TaskStatus.RiskStopped -> MaterialTheme.colorScheme.error
    TaskStatus.Paused -> Color(0xFFB45309)
    TaskStatus.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
    TaskStatus.Idle -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "未设置" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun WarningText(value: String) {
    Text(
        value,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
            .padding(10.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    fieldKey: String = label,
    onValueChange: (Int) -> Unit
) {
    var fieldValue by remember(fieldKey) {
        mutableStateOf(
            TextFieldValue(
                text = value.coerceAtLeast(0).toString(),
                selection = TextRange(value.coerceAtLeast(0).toString().length)
            )
        )
    }

    LaunchedEffect(fieldKey, value) {
        val normalized = value.coerceAtLeast(0).toString()
        if (fieldValue.text != normalized) {
            fieldValue = TextFieldValue(
                text = normalized,
                selection = TextRange(normalized.length)
            )
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { raw ->
            val digits = raw.text.filter { it.isDigit() }
            val normalized = digits.trimStart('0').ifEmpty { "0" }
            val number = normalized.toIntOrNull()?.coerceAtLeast(0) ?: 0
            fieldValue = TextFieldValue(
                text = normalized,
                selection = TextRange(normalized.length)
            )
            onValueChange(number)
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun CompactTextInput(
    label: String,
    value: TextFieldValue,
    modifier: Modifier = Modifier,
    onValueChange: (TextFieldValue) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun CompactNumberInput(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    fieldKey: String = label,
    onValueChange: (Int) -> Unit
) {
    var fieldValue by remember(fieldKey) {
        val text = value.coerceAtLeast(0).toString()
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }

    LaunchedEffect(fieldKey, value) {
        val normalized = value.coerceAtLeast(0).toString()
        if (fieldValue.text != normalized) {
            fieldValue = TextFieldValue(text = normalized, selection = TextRange(normalized.length))
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = fieldValue,
            onValueChange = { raw ->
                val normalized = raw.text.filter { it.isDigit() }.trimStart('0').ifEmpty { "0" }
                val number = normalized.toIntOrNull()?.coerceAtLeast(0) ?: 0
                fieldValue = TextFieldValue(text = normalized, selection = TextRange(normalized.length))
                onValueChange(number)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun StableTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    fieldKey: String = label,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    var isFocused by remember(fieldKey) { mutableStateOf(false) }
    var fieldValue by remember(fieldKey) {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }

    LaunchedEffect(fieldKey, value) {
        if (!isFocused && value != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    LaunchedEffect(fieldKey, fieldValue.text) {
        delay(300)
        if (value != fieldValue.text) {
            onValueChange(fieldValue.text)
        }
    }

    LaunchedEffect(fieldKey, isFocused) {
        if (!isFocused && value != fieldValue.text) {
            onValueChange(fieldValue.text)
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { fieldValue = it },
        label = { Text(label) },
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        singleLine = singleLine,
        minLines = minLines
    )
}

@Composable
private fun ListTextField(label: String, value: String, fieldKey: String = label, onValueChange: (String) -> Unit) {
    StableTextField(
        label = label,
        value = value,
        fieldKey = fieldKey,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        minLines = 1
    )
}

@Composable
private fun LogRow(log: RunLog) {
    val color = when (log.level) {
        LogLevel.Info -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLevel.Success -> Color(0xFF15803D)
        LogLevel.Warning -> Color(0xFFB45309)
        LogLevel.Error -> MaterialTheme.colorScheme.error
    }
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(9.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
            )
            SelectionContainer(Modifier.weight(1f)) {
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(log.level.label, style = MaterialTheme.typography.labelLarge, color = color)
                        Text(formatTime(log.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (log.keyword.isNotBlank()) {
                        Text(log.keyword, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(log.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: JobRecord) {
    SectionCard {
        SelectionContainer {
            Column {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(record.action, style = MaterialTheme.typography.labelLarge, color = recordActionColor(record.action))
                    Text(formatTime(record.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(record.title.ifBlank { "未知岗位" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(record.platform.label, record.company, record.city, record.salary).filter { it.isNotBlank() }.joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(record.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun recordActionColor(action: String): Color = when {
    action.contains("打招呼") || action.contains("成功") -> Color(0xFF15803D)
    action.contains("跳过") || action.contains("暂停") || action.contains("已打过") -> Color(0xFFB45309)
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun EmptyText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(timestamp))
}

private fun readTextFromUri(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun writeTextToUri(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
}
