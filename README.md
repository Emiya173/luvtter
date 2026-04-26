# Letter App (v2)

慢节奏信件应用 · Kotlin 全栈

**技术栈(最新版)**

- Gradle 9.1.0, JDK 17
- Kotlin 2.3.20
- AGP 9.1.1 (Android Gradle Plugin)
- Compose Multiplatform 1.10.3
- Ktor 3.4.0 (含 SSE 长连接)
- Exposed 1.0.0, PostgreSQL 16
- Koin 4.1 DI(服务端 + 客户端)
- testcontainers 1.21.3 集成测试
- MinIO 对象存储(预签名 PUT/GET 上传链路已通,附件 / 扫描信 / 手写信均以 objectKey 持久化)
- Coil 3.0.4 KMP(`coil-network-ktor3` 走自定义无 JWT 的 `rawClient`)图片渲染
- FileKit 0.10 KMP 文件选择器(commonMain 单一实现覆盖 Desktop / Android / iOS,Android `MainActivity` 中 `FileKit.init(this)` 一次)

配套设计文档见仓库doc/的两份 markdown 文件。

---

## 项目结构

AGP 9 引入了新的 KMP 库插件(`com.android.kotlin.multiplatform.library`),
要求 KMP 模块与 Android application 模块分离。因此结构如下:

```
letter-app/
├── api-contract/           # KMP 库, DTO 共享(客户端 + 服务端)
├── shared/                 # KMP 库, 客户端共享层(网络、业务逻辑)
├── composeApp/             # KMP 库, Compose UI + 各平台入口
│   ├── commonMain/         # 共享 UI
│   ├── androidMain/        # Android 专属代码(不含入口 Activity)
│   ├── desktopMain/        # Desktop 入口 main 函数
│   └── iosMain/            # iOS 桥接 MainViewController
├── androidApp/             # Android application 独立模块(MainActivity)
├── server/                 # Ktor 服务端
├── docker-compose.yml
├── gradle/libs.versions.toml
└── README.md
```

---

## 前置要求

- **JDK 17+**(必须,AGP 9 和 Gradle 9 硬性要求)
- **Docker + Docker Compose**
- **IntelliJ IDEA Ultimate 2025.3+** 或 **Android Studio Otter 3 Feature Drop (2025.2.3)+**
  (更早版本不支持 AGP 9)
- **Xcode**(仅 iOS 开发需要,macOS 专属)

---

## 首次启动

### 1. 生成 Gradle Wrapper

此仓库未包含 `gradlew` / `gradle-wrapper.jar`,在本地生成:

```bash
# 需要本机已装 Gradle 9.4+
gradle wrapper --gradle-version 9.4.0
```

之后所有命令用 `./gradlew`(Windows `gradlew.bat`)。

### 2. 启动基础设施

```bash
docker compose up -d postgres minio
```

- Postgres: `localhost:5432`(db `letter`, user/pwd `letter`)
- MinIO API: `localhost:9000`, Console: `localhost:9001`(默认 `minioadmin`/`minioadmin`)

### 3. 创建 MinIO Bucket

访问 <http://localhost:9001> 登录,新建名为 `letter` 的 bucket。

### 4. 启动服务端

IDEA 中打开项目,运行 `server/src/main/kotlin/com/luvtter/server/Application.kt` 的 `main`。

或命令行:

```bash
./gradlew :server:run
```

首次启动会自动跑 Flyway 迁移建表。

验证:访问 <http://localhost:8080/api/v1/hello> 应返回:

```json
{
  "data": {
    "message": "Hello from letter-app server",
    "serverTime": "2026-...",
    "version": "0.1.0"
  }
}
```

### 5. 运行客户端

**Desktop**(最快反馈):

```bash
./gradlew :composeApp:run
```

窗口打开后点"Ping 服务端",应显示服务端信息。

**Android**: Android Studio 打开项目 → 选 `androidApp` run configuration → 选模拟器 → Run。

Android 模拟器访问宿主机服务端用 `10.0.2.2:8080`,代码已配置好。

**iOS**: 需要先建 Xcode 项目链接 `ComposeApp.framework`。MVP 阶段可延后。

---

## 常用命令

```bash
./gradlew build                    # 全量构建
./gradlew :server:run              # 跑服务端
./gradlew :composeApp:run          # 跑 Desktop 客户端
./gradlew :androidApp:installDebug # 装 Android 到模拟器
./gradlew clean                    # 清理
./gradlew :server:test             # 跑服务端集成测试(testcontainers 会自动起 Postgres)
```

服务端测试要求本机有可访问的 Docker 守护进程(client API ≥ 1.40)。

---

## AGP 9 的几个关键变化(需要心里有数)

**1. KMP 模块不能同时用 `com.android.application`**

旧版本常见的"一个 composeApp 模块搞定所有平台"的结构在 AGP 9 下不合法。
必须拆 `composeApp`(KMP 库)+ `androidApp`(Android application)。

**2. `androidTarget` 改名 `android`**

新插件的配置块:

```kotlin
kotlin {
    android {               // 不再是 androidTarget { }
        namespace = "..."
        compileSdk = 36
        minSdk = 26
    }
}
```

**3. Android application 模块不用 apply kotlin 插件**

AGP 9 内置 Kotlin 支持,`androidApp` 的 `plugins { }` 里只有:

```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)    // Compose 仍需
}
```

**4. Plugin alias 在 TOML 里用驼峰**

Kotlin DSL 不接受连字符命名的 plugin alias:

```toml
# TOML
kotlinMultiplatform = { id = "...", version.ref = "kotlin" }

# build.gradle.kts
alias(libs.plugins.kotlinMultiplatform)
```

---

## 当前阶段

**阶段 2 已基本打通**。短期阻塞清单 A~E 全部完成,详见 `doc/技术路线图.md`。

**已落地的端到端能力**

- 认证:邮箱注册/登录/刷新/登出、两步式 handle、**多设备 Session 列表 + 撤销**、
  **首登引导(`GET/PATCH /api/v1/me/onboarding-state`,Desktop Home 一次性"给未来的自己,寄第一封信"卡片,寄出任意一封信后服务端自动 flip `firstLetterSent`)**
- 用户:多地址(真实 + 虚拟锚点)、当前位置切换、联系人 + 屏蔽
- 写信:草稿 CRUD、**多段 segment 编辑 + 划掉(strikethrough)、Stage 3「涂改模式」Switch(停顿 3 秒后再 backspace 自动把删字保留为划线段,模仿手写信)**、封存冷静期、
  **贴纸 + 图片附件(`POST /uploads/photo/sign-put` 预签 → 客户端直传 MinIO → `addPhoto(objectKey)`,读取时 server 重签 GET URL)**、重量/邮票承载检查、
  **扫描信上传(`/uploads/scan/sign-put` 接受 jpg/png/webp/pdf,30MB)+ 手写信上传(`/uploads/handwriting/sign-put` 接受 application/json + image/png,5MB),`CreateDraftRequest(contentType="scan"|"handwriting", *_objectKey=...)` 创建非文本草稿,详情页 server 重签发 GET URL,Desktop ComposeScreen 「键入文本/扫描信」模式 chip 直传扫描件**
- 寄送:距离计算、等级化送达时间、拟真事件、加速(调试)
- 收件:按地址归属、`delivered/read` 状态、**SSE 实时通知推送(含 ping 心跳 + 瞬时信号双轨,upload_done/letter_read 不入库直接广播)**、搜索、收藏、分类夹、
  **收/发件箱行尾 `📷N 🏷M` 附件统计 chip;详情页 Coil3 KMP `AsyncImage` 渲染图片附件,扫描信/手写信通过 `ScannedBody`(图片直显、PDF/JSON 走 `LocalUriHandler.openUri`)展示**
- 基础设施:Koin 模块化(服务端按域 auth/user/stamp/mail/storage)、客户端 `koin-compose-viewmodel`、
  logback 日志滚动、**testcontainers-postgres + testcontainers-minio 集成测试(auth / send / attachment / segment / sse / sessions / media-upload / sse-heartbeat-signals / search / notification-quiet-hours / scan-upload / ocr-task-runner / handwriting-upload / daily-reward-timezone / onboarding / export 共 16 条 happy path,39 个用例)**
- 归档导出:**`POST /api/v1/me/export` 同步生成 ZIP(`manifest.json` + `letters.json` 全量 sender/recipient 信件,经 `detailForExport` 绕过 delivery 时间窗) → 直传 `users/{uid}/exports/...zip` → 1 小时 presigned GET;Desktop Home 顶栏「导」按钮触发,`LocalUriHandler.openUri` 弹浏览器下载**
- 异步任务管线:**进程内 `AsyncTaskRunner` 协程 + PG `FOR UPDATE SKIP LOCKED` 原子认领 + 失败重试退避;`ocr_index` stub 处理器把扫描信内容写入索引,寄出后立刻可被全文搜索;`GET /api/v1/letters/{id}/ocr-status` 暴露任务状态;Python image-worker 接入只需替换 `OcrIndexService.process` 函数体**
- 搜索:**全文检索 tsvector + 中文 bigram(`letter_bigram` plpgsql 函数 + `letter_contents` 触发器维护 `index_tsv` GIN 索引,`/api/v1/letters/search?q=...` 走 `index_tsv @@ letter_bigram_query(?)`)**
- 通知免打扰:**`NotificationPrefsDto` 增加 `quietStart/quietEnd/timezone`,半开区间 + 跨日语义 + IANA 时区(V7 迁移);静默期通知仍落库,但跳过 SSE 推送**

**后续路线**参考《信件应用-技术设计文档.md》第六章:

- 阶段 3:手写、涂改、字体、信纸、拆信动画
- 阶段 4:扫描图像 Python worker + OCR
- 阶段 5:完整功能
- 阶段 6:打磨发布

---

## 万一构建失败的排查思路

AGP 9 和 Kotlin 2.3 都是较新版本,组合里个别库可能还没完全适配。

1. **Gradle 版本不对**: `gradle -v` 确认是 9.1.0
2. **JDK 版本不对**: `java -version` 确认是 17+(不要用 21 等,某些 Kotlin/Native 工具对 17 之外的 JDK 不稳定)
3. **Android Studio 太老**: 需要 Otter 3 Feature Drop 或更新
4. **依赖未下载**: `./gradlew --refresh-dependencies build`
5. **Compose Compiler 版本冲突**: 查看错误信息,如提示需要特定版本,在 `libs.versions.toml` 调整
6. **iOS 相关错误**: 暂时在 `composeApp/build.gradle.kts` 注释掉三个 iOS target,Android + Desktop 先跑起来再说

---

## 文件速查

| 文件 | 作用 |
|------|------|
| `gradle/libs.versions.toml` | 统一依赖版本 |
| `settings.gradle.kts` | 模块列表 |
| `docker-compose.yml` | 本地 Postgres + MinIO |
| `server/src/main/resources/application.yaml` | 服务端配置 |
| `server/src/main/resources/db/migration/` | Flyway SQL |
| `server/src/main/kotlin/com/luvtter/server/Application.kt` | 服务端入口 |
| `composeApp/src/commonMain/kotlin/com/luvtter/app/App.kt` | 客户端 UI 入口 + NavHost |
| `composeApp/src/commonMain/kotlin/com/luvtter/app/di/AppModule.kt` | 客户端 Koin 装配 |
| `androidApp/src/main/kotlin/com/luvtter/app/android/MainActivity.kt` | Android 入口 |
| `api-contract/src/commonMain/kotlin/com/luvtter/contract/` | 共享 DTO |
| `shared/src/commonMain/kotlin/com/luvtter/shared/network/` | 客户端网络层(各 *Api*,含 `NotificationApi.stream()` SSE) |
| `server/src/main/kotlin/com/luvtter/server/di/Modules.kt` | 服务端按域 Koin 模块 |
| `server/src/test/kotlin/com/luvtter/server/` | testcontainers 集成测试 |
