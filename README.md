<div align="center">

# 职引助手 JobGuide

<p>
  <img src="https://img.shields.io/badge/platform-Android-34A853?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/minSDK-24-00BCD4" alt="minSDK 24" />
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="License" />
</p>

**Android 求职自动化辅助工具** — 基于 AccessibilityService 的多平台批量打招呼

[功能亮点](#-功能亮点) · [支持平台](#-支持平台) · [截图预览](#-截图预览) · [快速开始](#-快速开始) · [配置说明](#-配置说明) · [安全机制](#-安全机制) · [技术架构](#-技术架构) · [贡献指南](#-贡献指南)

</div>

---

## ✨ 功能亮点

- 🔍 **多关键词队列** — 按顺序搜索多个关键词，每个关键词可设置处理数量和打招呼上限
- 🎯 **智能筛选** — 基于城市、薪资、白名单/黑名单、必备关键词的评分机制，自动跳过不匹配的岗位
- 💬 **模板打招呼** — 支持多个打招呼模板，自动填充 `{name}`、`{job_title}`、`{company}`、`{city}`、`{salary}`、`{skills}`、`{keyword}` 变量
- 🛡️ **风险检测** — 检测到验证码、安全验证、账号异常、操作频繁等风险提示时自动停止
- 🔒 **三种运行模式** — 只输入不发送 / 发送前确认 / 自动打招呼（需手动开启）
- 📊 **实时统计** — 今日处理数、打招呼数、剩余额度一目了然
- 💾 **配置持久化** — 所有设置自动保存，重启不丢失
- 📤 **JSON 导入导出** — 一键备份和恢复完整配置

## 📱 支持平台

| 平台 | 包名 | 搜索模式 | 推荐模式 |
|:---:|:---|:---:|:---:|
| BOSS 直聘 | `com.hpbr.bosszhipin` | ✅ 关键词搜索 / 推荐流 | 发送前确认 |
| 前程无忧 51job | `com.job.android` | ✅ 关键词搜索 | 只输入不发送 |
| 智联招聘 | `com.zhaopin.social` | ✅ 关键词搜索 | 只输入不发送 |
| 猎聘 | `com.lietou.mishu` | ✅ 关键词搜索 | 发送前确认 |

> ⚠️ 本项目不是招聘平台官方客户端，不调用非公开接口，不保存账号密码，不绕过验证码。

## 📸 截图预览

| 执行页 | 关键词页 | 规则页 |
|:---:|:---:|:---:|
| 执行控制台 | 关键词队列配置 | 筛选规则配置 |

| 模板页 | 日志页 | 我的页 |
|:---:|:---:|:---:|
| 打招呼模板 | 运行日志和岗位记录 | 求职者信息和权限 |

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- Android 设备（API 24+，即 Android 7.0 以上）
- 目标招聘平台 App 已安装

### 编译安装

```bash
# 克隆项目
git clone https://github.com/你的用户名/JobGuide.git
cd JobGuide

# 编译 Debug APK
./gradlew assembleDebug

# APK 输出位置
# app/build/outputs/apk/debug/app-debug.apk
```

或用 Android Studio 直接打开项目，等待 Gradle 同步完成后点击 Run。

### 使用步骤

1. **安装并打开**职引助手
2. **开启无障碍权限** — 在"我的"页或执行页点击"去设置"，在系统设置中开启职引助手的无障碍服务
3. **配置关键词** — 在关键词页添加搜索关键词，设置每个关键词的打招呼上限
4. **配置规则** — 在规则页设置目标城市、薪资范围、白名单、黑名单、风险停止词
5. **配置模板** — 在模板页编写打招呼文案，使用 `{name}`、`{job_title}` 等变量
6. **选择模式** — 建议先用"发送前确认"模式熟悉流程
7. **点击开始** — 自动打开招聘平台 App 并执行自动化流程

## ⚙️ 配置说明

### 关键词配置

每个关键词支持：
- 启用/停用开关
- 处理岗位数量上限
- 打招呼数量上限
- 优先级排序

### 规则配置

| 配置项 | 说明 | 默认值 |
|:---|:---|:---|
| 目标城市 | 只处理指定城市的岗位 | `城市` |
| 薪资范围 | 过滤不在范围内的岗位 | 5K - 15K |
| 必备关键词 | 岗位必须包含的关键词 | `关键词1, 关键词2` |
| 岗位白名单 | 包含白名单词的岗位加分 | `岗位白名单1, 岗位白名单2` |
| 岗位黑名单 | 包含黑名单词的岗位直接跳过 | `销售, 电销, 外包...` |
| 风险停止词 | 检测到时立即停止任务 | `验证码, 安全验证...` |
| 每日总上限 | 每天最多打招呼次数 | 15 |
| 随机间隔 | 两次操作之间的随机等待秒数 | 5-12 秒 |
| 最低评分 | 低于此分数的岗位跳过 | 40 |

### 打招呼模板

支持以下变量自动替换：

| 变量 | 替换为 |
|:---|:---|
| `{name}` | 求职者姓名 |
| `{job_title}` | 岗位名称 |
| `{company}` | 公司名称 |
| `{city}` | 城市 |
| `{salary}` | 薪资范围 |
| `{skills}` | 技能标签 |
| `{keyword}` | 当前搜索关键词 |

### JSON 配置导入导出

在"我的"页可以导入/导出完整的 JSON 配置，方便在多台设备间同步设置。

## 🛡️ 安全机制

- **风险检测**：遇到验证码、安全验证、账号异常、操作频繁等提示时自动停止
- **盲目防护**：找不到关键按钮时暂停，不进行盲目点击
- **限额控制**：每日总打招呼上限 + 每个关键词上限，双重保护
- **指纹去重**：已处理岗位通过指纹去重，避免重复打招呼
- **手动确认**：自动发送需同时开启运行模式和自动打招呼开关
- **随机间隔**：操作之间加入随机等待，降低风控风险

## 🏗️ 技术架构

```
┌─────────────────────────────────────────┐
│              Jetpack Compose UI          │
│  (JobGuideApp · JobGuideViewModel)      │
├─────────────────────────────────────────┤
│              DataStore Preferences       │
│  (AppConfig · RunStats · Logs · Records)│
├─────────────────────────────────────────┤
│           AccessibilityService           │
│  (JobGuideAccessibilityService)          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │PageHeuristics│ │JobMatcher│ │NodeExt  │ │
│  └──────────┘ └──────────┘ └──────────┘ │
├─────────────────────────────────────────┤
│         目标招聘平台 App                  │
│  (BOSS直聘 · 51job · 智联 · 猎聘)       │
└─────────────────────────────────────────┘
```

### 核心模块

| 模块 | 文件 | 职责 |
|:---|:---|:---|
| 无障碍服务 | `JobGuideAccessibilityService.kt` | 读取屏幕、模拟点击、自动化流程 |
| 页面识别 | `PageHeuristics.kt` | 基于文本特征判断当前页面类型 |
| 节点扩展 | `AccessibilityNodeExt.kt` | 无障碍节点查找、点击、输入工具方法 |
| 岗位匹配 | `JobMatcher.kt` | 基于规则和评分的岗位筛选逻辑 |
| 数据模型 | `Models.kt` | 配置、状态、日志、记录等数据类 |
| 数据仓库 | `JobGuideRepository.kt` | DataStore 读写和状态管理 |
| 视图模型 | `JobGuideViewModel.kt` | UI 状态管理和业务逻辑 |
| 界面 | `JobGuideApp.kt` | Compose UI 全部页面 |

### 通信机制

UI（ViewModel）和 AccessibilityService 之间通过 DataStore 命令通道通信：

```
ViewModel ──issueCommand──▶ DataStore ──collect──▶ AccessibilityService
         ◀──stats/logs/records── DataStore ◀──write──
```

## 🧪 测试

```bash
# 编译验证
./gradlew assembleDebug

# 运行单元测试
./gradlew test

# 测试覆盖：页面识别、岗位匹配、薪资解析、JSON导入导出
```

## 📁 项目结构

```
app/src/main/java/com/zhiyin/jobguide/
├── MainActivity.kt                    # 主 Activity
├── automation/
│   ├── JobGuideAccessibilityService.kt  # 无障碍服务核心逻辑
│   ├── AccessibilityNodeExt.kt          # 节点操作扩展
│   ├── JobMatcher.kt                    # 岗位评分匹配
│   └── PageHeuristics.kt                # 页面类型识别
├── data/
│   ├── Models.kt                        # 数据模型和JSON序列化
│   ├── JobGuideRepository.kt            # DataStore 数据仓库
│   └── ServiceLocator.kt                # 依赖注入
├── notification/
│   └── StopNotifier.kt                  # 任务停止通知
└── ui/
    ├── JobGuideApp.kt                   # Compose 全部页面
    ├── JobGuideViewModel.kt             # 视图模型
    └── theme/                            # 主题配置
```

## 🤝 贡献指南

1. Fork 本仓库
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'feat: add your feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

### 开发规范

- Kotlin 优先，遵循 MVVM / Clean Architecture
- 禁止使用 `!!` 非空断言，优先使用 `?.let`、`as?` 等空安全操作
- 网络/数据库/文件操作必须在子线程
- 新增权限须在 README 中说明是否需要动态申请

## ⚠️ 免责声明

本项目仅供学习和研究目的。使用者需遵守相关招聘平台的使用条款和当地法律法规。作者不对因使用本工具产生的任何后果负责。

## 📄 开源协议

[MIT License](LICENSE)

---

<div align="center">

如果这个项目对你有帮助，请给一个 ⭐ Star！

</div>