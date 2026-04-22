# Letter App (v2)

慢节奏信件应用 · Kotlin 全栈

**技术栈(最新版)**

- Gradle 9.1.0, JDK 17
- Kotlin 2.3.20
- AGP 9.1.1 (Android Gradle Plugin)
- Compose Multiplatform 1.10.3
- Ktor 3.4.0
- Exposed 1.0.0, PostgreSQL 16
- MinIO 对象存储

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

IDEA 中打开项目,运行 `server/src/main/kotlin/com/letter/server/Application.kt` 的 `main`。

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
./gradlew test                     # 跑测试
```

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

**阶段 1:基础设施**(当前)- 项目骨架 + Hello API 端到端跑通。

后续路线参考《信件应用-技术设计文档.md》第六章:

- 阶段 2:认证 + 用户/地址 CRUD + 最简寄信流程
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
| `server/src/main/kotlin/com/letter/server/Application.kt` | 服务端入口 |
| `composeApp/src/commonMain/kotlin/com/letter/app/App.kt` | 客户端 UI 入口 |
| `androidApp/src/main/kotlin/com/letter/app/android/MainActivity.kt` | Android 入口 |
| `api-contract/src/commonMain/kotlin/com/letter/contract/` | 共享 DTO |
