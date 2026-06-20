# 职引助手 JobGuide

职引助手是一个 Android 原生求职自动化工具原型，项目名 `JobGuide`，包名 `com.zhiyin.jobguide`。它通过 Android 无障碍服务读取 BOSS 直聘 App 的屏幕可见内容，并在用户主动开始任务后模拟点击、输入和有限自动化流程。

本项目不是招聘平台官方客户端，不调用非公开接口，不保存账号密码，不绕过验证码、登录、安全验证或风控。检测到验证码、登录异常、安全验证、账号异常、操作频繁、稍后再试等风险提示时，会停止或暂停任务。

## 1. 产品功能说明

- 配置岗位搜索关键词、每个关键词处理岗位数、每个关键词打招呼上限、今日总打招呼上限。
- 配置目标城市、薪资范围、必备关键词、岗位/公司白名单、岗位/公司黑名单、风险停止词和随机操作间隔。
- 配置多个打招呼模板，支持 `{name}`、`{job_title}`、`{company}`、`{city}`、`{salary}`、`{skills}`、`{keyword}` 变量。
- 支持三种运行模式：只输入不发送、发送前确认、自动打招呼。
- 自动打招呼必须由用户主动开启，默认使用发送前确认。
- 支持开始、暂停、停止任务。
- 支持本地运行日志、岗位处理记录、JSON 配置导入导出。
- 首次使用引导用户开启无障碍权限，并说明权限用途。

## 2. 页面结构设计

- 执行页：首页状态看板，显示任务状态、今日已处理、今日已打招呼、剩余额度、当前关键词、当前步骤、开始/暂停/停止按钮、运行模式和安全说明。
- 关键词页：新增、删除、启用/停用、上移/下移关键词，配置每个关键词处理数量、打招呼数量和优先级。
- 规则页：目标城市、薪资范围、必备关键词、白名单、黑名单、风险停止词、每日上限、随机间隔。
- 模板页：新增、编辑、删除模板，设置默认模板，配置是否参与随机使用，展示变量列表。
- 日志页：展示每一步自动化日志、岗位记录、跳过原因、失败原因和风险停止原因。
- 我的页：求职者姓名、技能标签、期望城市、期望薪资、无障碍权限状态、导入导出配置、清理统计和记录、隐私说明、风险说明。

## 3. 用户使用流程

1. 安装并打开职引助手。
2. 在“我的”页开启无障碍权限。
3. 在关键词页配置岗位关键词，例如后端开发实习、Python开发、测试开发、AI应用开发。
4. 在规则页配置城市、薪资、白名单、黑名单、风险停止词和打招呼上限。
5. 在模板页配置打招呼文案。
6. 回到执行页选择运行模式。建议先用“发送前确认”或“只输入不发送”。
7. 点击“开始执行”。
8. App 打开 BOSS 直聘，按关键词搜索、筛选岗位、进入详情、输入打招呼内容。
9. 若选择自动打招呼且已打开自动开关，才会点击发送。
10. 所有关键词完成或达到限额后任务结束；遇到风险提示或关键控件缺失时暂停/停止。

## 4. AccessibilityService 自动化流程设计

核心实现位于：

- `app/src/main/java/com/zhiyin/jobguide/automation/JobGuideAccessibilityService.kt`
- `app/src/main/java/com/zhiyin/jobguide/automation/AccessibilityNodeExt.kt`
- `app/src/main/java/com/zhiyin/jobguide/automation/JobMatcher.kt`

流程：

1. 主 App 写入 `Start/Pause/Stop` 命令到 DataStore。
2. 无障碍服务监听命令。
3. `Start` 后打开 BOSS 直聘包 `com.hpbr.bosszhipin`。
4. 查找搜索入口，输入当前关键词，点击搜索。
5. 读取当前窗口无障碍节点的可见文本。
6. 识别候选岗位卡片，并基于关键词、城市、薪资、白名单、黑名单和必备关键词评分。
7. 命中黑名单或低于最低评分的岗位写入“跳过”记录。
8. 符合规则的岗位进入详情页。
9. 查找“立即沟通/继续沟通/打招呼/沟通”按钮。
10. 查找聊天输入框并写入模板文案。
11. 根据运行模式决定暂停等待确认、只输入不发送或自动点击发送。
12. 写入岗位记录和运行日志。
13. 达到关键词处理数量、关键词打招呼上限或今日总上限后切换/结束。

## 5. 数据结构设计

主要数据模型位于 `app/src/main/java/com/zhiyin/jobguide/data/Models.kt`。

- `AppConfig`：完整配置，包括关键词、规则、模板、个人资料、运行模式、自动打招呼开关。
- `KeywordSetting`：关键词、启用状态、处理数量、打招呼数量、优先级。
- `RulesConfig`：城市、薪资、必备关键词、白名单、黑名单、风险停止词、每日上限、随机间隔。
- `GreetingTemplate`：模板标题、正文、默认模板、随机使用开关。
- `ProfileConfig`：姓名、技能、期望城市、期望薪资。
- `RunStats`：任务状态、今日处理数、今日打招呼数、当前关键词、当前步骤、风险原因。
- `RunLog`：运行日志。
- `JobRecord`：岗位处理记录和去重指纹。
- `AutomationCommand`：开始、暂停、停止命令。

## 6. JSON 配置示例

```json
{
  "keywords": [
    {
      "id": "backend-intern",
      "name": "后端开发实习",
      "enabled": true,
      "maxJobs": 20,
      "maxGreetings": 5,
      "priority": 3
    },
    {
      "id": "python-dev",
      "name": "Python开发",
      "enabled": true,
      "maxJobs": 20,
      "maxGreetings": 5,
      "priority": 2
    }
  ],
  "rules": {
    "targetCities": ["长沙"],
    "salaryMinK": 3,
    "salaryMaxK": 12,
    "requiredKeywords": ["后端", "Python", "Java", "测试", "AI"],
    "jobWhitelist": ["后端", "Python", "Java", "测试开发", "软件开发", "AI应用"],
    "companyWhitelist": [],
    "jobBlacklist": ["销售", "电销", "客服", "招生", "主播", "课程顾问", "培训", "外包", "人力资源"],
    "companyBlacklist": [],
    "riskStopWords": ["验证码", "登录", "安全验证", "账号异常", "操作频繁", "稍后再试"],
    "dailyGreetingLimit": 15,
    "minDelaySeconds": 5,
    "maxDelaySeconds": 12,
    "minMatchScore": 40
  },
  "templates": [
    {
      "id": "default",
      "title": "默认模板",
      "body": "老师您好，我是{name}，看到贵公司的{job_title}岗位后，感觉和我的求职方向比较匹配。我熟悉{skills}，对{keyword}相关工作很感兴趣。方便的话，麻烦您查看一下我的简历，期待有机会进一步沟通，谢谢老师。",
      "isDefault": true,
      "randomEnabled": true
    }
  ],
  "profile": {
    "name": "张旭",
    "skills": ["Kotlin", "Java", "Python", "MySQL", "Linux"],
    "expectedCities": ["长沙"],
    "expectedSalary": "3-8K"
  },
  "runMode": "ConfirmBeforeSend",
  "autoGreetingEnabled": false
}
```

## 7. Android 项目目录结构

```text
app/src/main/java/com/zhiyin/jobguide/
  MainActivity.kt
  automation/
    AccessibilityNodeExt.kt
    JobGuideAccessibilityService.kt
    JobMatcher.kt
  data/
    JobGuideRepository.kt
    Models.kt
    ServiceLocator.kt
  ui/
    JobGuideApp.kt
    JobGuideViewModel.kt
    theme/
      Color.kt
      Theme.kt
      Type.kt
app/src/main/res/xml/
  jobguide_accessibility_service.xml
```

## 8. Kotlin + Jetpack Compose 页面代码示例

核心页面代码在 `JobGuideApp.kt`：

```kotlin
Scaffold(
    topBar = { TopAppBar(title = { Text("职引助手") }) },
    bottomBar = { NavigationBar { /* 执行、关键词、规则、模板、日志、我的 */ } }
) { padding ->
    when (selectedTab) {
        AppTab.Home -> HomeScreen(state, viewModel)
        AppTab.Keywords -> KeywordsScreen(state.config.keywords, viewModel)
        AppTab.Rules -> RulesScreen(state.config.rules, viewModel)
        AppTab.Templates -> TemplatesScreen(state.config.templates, viewModel)
        AppTab.Logs -> LogsScreen(state.logs, state.records, viewModel)
        AppTab.Profile -> ProfileScreen(state, viewModel)
    }
}
```

## 9. AccessibilityService 核心代码示例

核心入口在 `JobGuideAccessibilityService.kt`：

```kotlin
private fun handleCommand(command: AutomationCommand) {
    when (command.type) {
        AutomationCommandType.Start -> startAutomation()
        AutomationCommandType.Pause -> pauseAutomation("用户暂停任务")
        AutomationCommandType.Stop -> stopAutomation("用户停止任务")
        AutomationCommandType.None -> Unit
    }
}
```

风险检测：

```kotlin
private fun assertNoRisk(root: AccessibilityNodeInfo, config: AppConfig, keyword: String) {
    val joined = root.joinedText()
    val hit = config.rules.riskStopWords.firstOrNull { joined.contains(it, ignoreCase = true) }
    if (hit != null) {
        throw StopAutomation("检测到风险提示：$hit，任务已停止", keyword = keyword, isRisk = true)
    }
}
```

## 10. Room 或 DataStore 保存方案

当前实现使用 DataStore Preferences，原因是配置、日志和导入导出都天然是 JSON 文档形态，适合快速开发和单用户本地存储。

实现位于 `JobGuideRepository.kt`：

- `config_json` 保存 `AppConfig`。
- `stats_json` 保存 `RunStats`。
- `logs_json` 保存最近 300 条日志。
- `records_json` 保存最近 800 条岗位记录。
- `command_json` 保存自动化命令。

后续如果岗位记录需要复杂查询，可以把 `JobRecord` 迁移到 Room，配置仍保留 DataStore。

## 11. 风险检测和停止机制

- 风险停止词由用户配置，默认包含验证码、登录、安全验证、账号异常、操作频繁、稍后再试。
- 每次读取页面后都会执行风险检测。
- 找不到搜索入口、搜索输入框、搜索按钮、沟通按钮、聊天输入框或发送按钮时暂停，不进行盲目点击。
- 每次关键操作之间加入随机等待。
- 今日总打招呼次数不能超过用户设置的上限。
- 每个关键词打招呼次数不能超过用户设置的上限。
- 已处理岗位通过指纹去重，避免重复打招呼。
- 自动发送必须同时满足 `runMode == AutoSend` 和 `autoGreetingEnabled == true`。

## 12. 开发步骤

1. 完成包名、应用名、无障碍服务声明和 DataStore 依赖。
2. 建立 MVVM：`JobGuideViewModel` 汇总配置、状态、日志、记录。
3. 建立数据模型和 JSON 导入导出。
4. 完成 Compose 六个页面。
5. 实现 AccessibilityService 命令监听和 BOSS App 启动。
6. 实现无障碍树读取、节点查找、岗位评分、风险检测和模板输入。
7. 接入限额、去重和运行日志。
8. 真机调试 BOSS 页面控件匹配，根据日志迭代选择器。

## 13. 测试方案

- 构建验证：执行 `.\gradlew.bat :app:assembleDebug`。
- 单元测试：岗位评分、薪资解析、白名单/黑名单、模板渲染、JSON 导入导出。
- 真机冒烟测试：
  - 启动 App，确认六个页面可编辑。
  - 开启无障碍权限。
  - 使用“只输入不发送”模式处理 1 个关键词、1 个岗位。
  - 检查日志和岗位记录。
- 安全测试：
  - 人为把风险停止词设为当前页面可见文字，确认任务停止。
  - 关闭自动打招呼开关，确认自动发送被阻止。
  - 设置今日上限为 0，确认不会发送。
  - 在找不到按钮的页面启动，确认暂停而不是盲点。

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```
