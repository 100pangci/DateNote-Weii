# 维来可期

> 把每一份期待，都好好安排

“维来可期”取自“未来可期”，是一个支持自然语言录入、日历查看和到期提醒的轻量个人排期应用。

它不需要账号，也不依赖云端即可记录和查看排期。AI 功能是可选的：只有用户主动配置兼容接口并点击“帮我整理”时，输入内容才会发送给所配置的服务。

## 功能

- 首次启动设置昵称，昵称仅保存在当前设备的 DataStore 中。
- 月历首页、每日排期、完成/恢复、推迟一天或一周、编辑和删除。
- 全部排期列表：全部、最近、今天、已逾期、已完成筛选，以及标题/备注搜索。
- “帮我记”：将自然语言或多行备忘录解析成可编辑草稿，确认后才写入本地数据库。
- OpenAI-compatible Chat Completions 配置，支持 Base URL、API Key、模型名称和连接测试。
- API Key 使用 Android Keystore 加密保存，不写入 DataStore、备份文件或日志。
- WorkManager 一般性到期前提醒；Android 13 及以上按系统要求申请通知权限。
- 系统文件选择器 JSON 导出/导入，支持追加和事务替换，不申请传统存储权限。
- 浅色、深色、跟随系统，以及 Android 12+ 动态取色。

## 截图

本地验证截图可放在 `docs/screenshots/`，建议包含：欢迎页、月历首页、排期编辑页、“帮我记”确认页和设置页。项目不提交任何真实用户数据或 API Key。

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- 单 Activity、Navigation Compose、ViewModel、StateFlow、Coroutines
- Room：本地排期数据
- DataStore Preferences：昵称、主题和普通配置
- Android Keystore：API Key 密文
- WorkManager：一般性提醒
- Ktor Client + kotlinx.serialization：OpenAI-compatible 接口及 JSON 协议
- minSdk 26（Android 8.0），compile/target SDK 36

## 构建与安装

使用 Android Studio 打开项目根目录，等待 Gradle 同步后运行 `app` 配置即可。

命令行构建 Debug APK：

```bash
./gradlew :app:assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

本机发布签名约定：Gradle 会读取用户目录下的
`~/.android/date-note-signing.properties`，其中引用同目录的
`date-note-release.jks`。这两个文件不在仓库中，缺少它们时仍可正常构建未签名 Release，适合其他贡献者使用自己的签名配置。

安装到已连接设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

项目 wrapper 会把 Gradle 缓存放在项目内的 `.gradle/`，该目录已被 `.gitignore` 忽略。若 Android Studio 没有自动找到 SDK，请在本机生成未提交的 `local.properties`。

## 配置 AI

1. 打开“设置 → 智能小助手”。
2. 填写 Base URL，例如 `https://api.openai.com/v1`；带不带 `/v1` 均可，程序会规范化路径。
3. 填写 API Key 和模型名称。
4. 点击“保存助手设置”，再点击“试试能不能连上”。
5. 进入“帮我记”，整理结果会先出现在确认页面，用户确认后才会保存。

接口需要兼容：

```text
POST {baseUrl}/chat/completions
Authorization: Bearer {API_KEY}
Content-Type: application/json
```

程序优先请求 `response_format: {"type":"json_object"}`；若服务不支持，会自动以普通 JSON 输出方式重试。昵称不会拼入 system prompt，也不会发送给 AI。

## 数据与隐私

- Room 数据库文件名为 `date_note.db`，排期日期保存为 `LocalDate.toEpochDay()`，具体时间保存为当天分钟数。
- 昵称、主题和 AI 普通配置保存在 DataStore；昵称不写入 Room。
- API Key 只保存为 Android Keystore 保护的 AES-GCM 密文。
- JSON 备份只包含排期和 `schemaVersion`，不包含昵称、API Key 或网络配置。
- 项目没有广告、统计 SDK、遥测、账号和社交功能。

## 项目结构

```text
app/src/main/java/com/datenote/app/
├── data/
│   ├── backup/       # JSON 备份模型
│   ├── local/        # Room Entity、DAO、Database
│   ├── remote/       # Ktor DTO、AI 接口、system prompt
│   ├── repository/   # Room/DataStore 仓库
│   └── secure/       # Android Keystore
├── domain/
│   ├── model/        # 排期状态、日期计算
│   └── parser/       # AI JSON 清理和本地校验
├── reminder/         # WorkManager 与通知
├── ui/               # onboarding、home、editor、all、aiinput、settings
├── DateNoteApplication.kt
└── MainActivity.kt
```

## 测试

纯 Kotlin 测试：

```bash
./gradlew :app:testDebugUnitTest
```

Room Android instrumented 测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

测试覆盖日期/跨年/逾期计算、提醒时间、AI 正常 JSON、空数组、损坏 JSON、Markdown 代码块、非法日期、多条排期、重复检测，以及 Room 增删改查、搜索、批量插入和事务替换。

## 开源注意事项

不要提交 API Key、签名文件、`local.properties`、Gradle 缓存、构建产物或真实备份文件。发布正式版本时请替换自己的 applicationId、签名配置和应用图标，并在 GitHub Release 中提供校验和。
