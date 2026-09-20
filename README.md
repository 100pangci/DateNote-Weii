<p align="center">
  <img src="docs/weilai-logo.svg" alt="维来可期 Logo" width="180">
</p>

<h1 align="center">维来可期</h1>

<p align="center">把每一份期待，都好好安排</p>

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
- 首次填写昵称后先说明通知用途，只有用户主动选择“开启提醒”才请求 Android 13+ 通知权限；拒绝通知不会影响本地排期、日历或 AI 功能。
- 设置中的“提醒可靠性”会读取真实的通知/通知渠道状态，并提供官方通知设置、电池设置、应用详情和国产 ROM 自启动说明。应用不会申请统一不存在的“自启动权限”，也不会强制关闭电池优化。
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

## 提醒可靠性与后台限制

普通排期提醒使用 WorkManager 的唯一一次性任务：修改排期会替换旧任务，删除或完成排期会取消任务。应用不使用常驻后台服务、保活进程、开机广播、一分钟轮询或长期 WakeLock；WorkManager 负责系统调度以及系统重启后的恢复。

通知权限、后台运行、电池优化和自启动是不同设置：

- Android 13 及以上仅在用户主动点击“开启提醒”或主动检查通知设置时请求 `POST_NOTIFICATIONS`；Android 12 及以下不申请该运行时权限。
- 通知被拒绝、应用通知被关闭或通知渠道被关闭时，排期仍会保存，但不会创建无法展示的通知任务，并会明确提示通知当前不可用。
- 部分手机系统可能会限制后台提醒。如果通知经常延迟，可以允许维来可期自启动，并将电池使用设置为“不受限制”。不同品牌的设置名称可能不同，需要在系统设置中手动确认。

项目只声明实际使用的 `INTERNET`、`ACCESS_NETWORK_STATE` 和 Android 13+ 的 `POST_NOTIFICATIONS`，不申请系统日历、传统存储、精确闹钟或直接忽略电池优化等权限。

## 构建与安装

使用 Android Studio 打开项目根目录，等待 Gradle 同步后运行 `app` 配置即可。

### GitHub Actions 与自动发布

- 推送到 `main` 会自动执行单元测试、Lint 和 Debug 构建，并上传 Debug APK artifact。
- 推送形如 `v0.1.0` 的 Tag 会自动构建 Release APK，并使用 `CHANGELOG.md` 创建 GitHub Release。
- Release Tag 会同步应用版本号：例如 `v1.2.3` 会生成应用内 `1.2.3`，并自动计算对应的 Android `versionCode`。
- 如需让 GitHub Actions 生成签名 APK，请在仓库 Secrets 中配置 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS` 和 `ANDROID_KEY_PASSWORD`。未配置时仍会生成 Release 构建产物，但不会使用正式签名。

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

### 为指定用户预置 AI 配置

如果需要给特定用户生成已经配置好 AI 的 APK，可以在仓库根目录创建 `.env`。`.env` 不会提交到 Git。请使用下面三个固定键名：

```text
WEII_AI_BASE_URL = "https://example.com/v1"
WEII_AI_API_KEY = "your-api-key"
WEII_AI_MODEL = "your-model"
WEII_NICKNAME = "她的昵称"
```

使用 `-Pweii` 编译时才会读取这些键。`WEII_NICKNAME` 可选；首次进入引导页时会预填，但用户仍可以修改：

```bash
./gradlew -Pweii assembleRelease
```

该配置会写入 APK，并在首次启动且设备尚未配置 AI 时导入本地设置。API Key 一旦写入 APK 就不再是秘密，只适合个人分发，不要把生成的 APK 上传到公开渠道。

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

提醒权限的人工验收应覆盖：Android 13+ 首次允许、拒绝和划走通知权限；拒绝后仍可保存排期；已允许时不会重复弹窗；通知渠道关闭；Android 12 及以下不出现运行时通知权限框；从系统设置返回后设置页状态刷新；不存在的系统设置 Intent 安全回退；旋转/重组不重复请求；首次昵称引导先于通知引导；冷启动排期提示不与权限框同时出现；以及无网络时本地排期和通知状态检查仍可用。

## 开源注意事项

不要提交 API Key、签名文件、`local.properties`、Gradle 缓存、构建产物或真实备份文件。发布正式版本时请替换自己的 applicationId、签名配置和应用图标，并在 GitHub Release 中提供校验和。
