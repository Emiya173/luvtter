# 开发指南

本地把 server + 客户端 + 基础设施跑起来。生产部署见 `doc/deployment.md`。

## 1. 首次启动

所有 Gradle 命令走仓库自带的 `./gradlew`(Windows 用 `gradlew.bat`)。

### 1.1 启动基础设施

```bash
docker compose up -d postgres minio
```

- Postgres: `localhost:5432`(db `letter`,user/pwd `letter`)
- MinIO API: `localhost:9000`,Console: `localhost:9001`(`minioadmin`/`minioadmin`)

访问 <http://localhost:9001> 登录,新建 bucket `letter`。

### 1.2 启动服务端

```bash
./gradlew :server:run
```

首次启动 Flyway 自动建表。健康检查:

```bash
curl http://localhost:8080/api/v1/hello
```

### 1.3 创建第一个账号

默认 `AUTH_ALLOW_REGISTRATION=false`,HTTP 注册关闭。用 CLI:

```bash
./gradlew :server:cli --args="register-user --email=dev@local --password=12345678 --name=Dev"
```

或开发期临时打开 HTTP 注册:`AUTH_ALLOW_REGISTRATION=true ./gradlew :server:run`,再走客户端 register 流程。

### 1.4 启动客户端

```bash
./gradlew :composeApp:run                  # Desktop(最快反馈)
./gradlew :androidApp:installDebug         # Android(模拟器)
```

Android 模拟器访问宿主机用 `10.0.2.2:8080`,代码已配置。iOS 需先在 Xcode 链接 `ComposeApp.framework`,MVP 阶段可延后。

## 2. 常用命令

```bash
./gradlew :server:run                                      # 服务端
./gradlew :composeApp:run                                  # Desktop 客户端
./gradlew :androidApp:installDebug                         # 装 Android
./gradlew :composeApp:compileKotlinDesktop                 # 仅校验 Desktop 编译(快)
./gradlew :server:test                                     # 服务端集成测试(testcontainers)
./gradlew :server:cli --args="<cmd> [--k=v ...]"           # 维护 CLI(命令清单见 deployment.md §3)
./gradlew :server:installDist                              # 构造 server + cli 发行包
./gradlew :server:buildFatJar                              # Ktor 单文件 fat jar
./gradlew :server:distTar                                  # tar 发行包
```

服务端测试要求本机 Docker 守护进程可用(client API ≥ 1.40)。

## 3. 关键文件速查

| 文件 | 作用 |
|------|------|
| `gradle/libs.versions.toml` | 统一依赖版本 |
| `docker-compose.yml` | 本地 Postgres + MinIO |
| `server/src/main/resources/application.yaml` | 服务端配置 |
| `server/src/main/resources/db/migration/` | Flyway SQL |
| `server/src/main/kotlin/com/luvtter/server/Application.kt` | 服务端入口 |
| `server/src/main/kotlin/com/luvtter/server/tools/LuvtterCli.kt` | 维护 CLI |
| `server/src/main/kotlin/com/luvtter/server/di/Modules.kt` | 服务端按域 Koin 模块 |
| `server/src/test/kotlin/com/luvtter/server/` | testcontainers 集成测试 |
| `composeApp/src/commonMain/kotlin/com/luvtter/app/App.kt` | 客户端 UI 入口 + NavHost |
| `composeApp/src/commonMain/kotlin/com/luvtter/app/di/AppModule.kt` | 客户端 Koin 装配 |
| `androidApp/src/main/kotlin/com/luvtter/app/android/MainActivity.kt` | Android 入口 |
| `api-contract/src/commonMain/kotlin/com/luvtter/contract/` | 共享 DTO |
| `shared/src/commonMain/kotlin/com/luvtter/shared/network/` | 客户端网络层(各 *Api*,含 SSE) |
| `shared/src/commonMain/kotlin/com/luvtter/shared/config/` | TOML 配置加载(expect/actual) |
| `luvtter.example.toml` | Desktop 客户端 TOML 配置示例 |

## 4. CI

`.github/workflows/ci.yml` 在 push / PR 时跑:

- `:server:test` — testcontainers 集成测试
- `:server:installDist` + smoke check — 验证 server + CLI 打包
- `:composeApp:compileKotlinDesktop|compileAndroidMain|compileKotlinIos*` — KMP 编译
- `:androidApp:assembleDebug` — Android APK 打包

`.github/workflows/release.yml` 在打 `v*` tag 时打包 fat jar、dist tarball、APK、Desktop 三平台原生包,并同步 AUR(`luvtter-desktop-bin`)。
