# 部署与运维

服务端 + 客户端发布产物的拿取、配置、运行,以及运维 CLI 的使用方式。开发环境的本地启动见 `doc/development.md`。

## 1. 产物形态

GitHub Release 在 push `v*` tag 后由 `.github/workflows/release.yml` 自动发布:

| 文件 | 内容 | 用途 |
|------|------|------|
| `luvtter-server-<tag>.jar` | Ktor fat jar(单文件,内含全部依赖与 CLI 类) | `java -jar` 直接起服务,或 `java -cp ... LuvtterCliKt` 跑维护命令 |
| `luvtter-server-<tag>-dist.tar.gz` | 应用发行包(`bin/luvtter-server` + `bin/luvtter-cli` + `lib/`) | 解压即用,带跨平台启动脚本,推荐生产部署用这个 |
| `luvtter-android-<tag>-universal-debug.apk` | Android Debug APK(unsigned) | 侧载安装 |
| `luvtter-desktop-<tag>-<platform>.{deb,dmg,msi}` | Desktop 原生包 | 用户安装 |
| `luvtter-desktop-<tag>-linux-x86_64.tar.gz` | createDistributable tar | 供 AUR 同步 |

## 2. 服务端部署

### 2.1 依赖

- Postgres 16(Flyway 自动迁移建表)
- MinIO 或兼容 S3 的对象存储(预签名 PUT/GET)
- JDK 17+(jar / dist 必需;Docker 镜像内置不需要)

### 2.2 启动方式

**fat jar:**

```bash
java -jar luvtter-server-v0.1.0.jar
```

**distribution(推荐):**

```bash
tar -xzf luvtter-server-v0.1.0-dist.tar.gz
cd luvtter-server-0.1.0
bin/luvtter-server                       # 起服务
bin/luvtter-cli help                     # 维护命令
```

### 2.3 配置(全部可用环境变量覆盖)

| ENV | 默认 | 说明 |
|-----|------|------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/letter` | Postgres JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `letter` / `letter` | DB 凭据 |
| `S3_ENDPOINT` | `http://localhost:9000` | S3/MinIO 端点 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `minioadmin` / `minioadmin` | S3 凭据 |
| `S3_BUCKET` | `letter` | bucket 名 |
| `JWT_SECRET` | `dev-secret-change-me` | **生产必须改** |
| `AUTH_ALLOW_REGISTRATION` | `false` | HTTP `/api/v1/auth/register` 开关。生产建议保持 `false`,通过 CLI 创建账号 |
| `SSE_HEARTBEAT_SECONDS` | `25` | SSE 心跳间隔 |
| `TASKS_POLL_MILLIS` | `2000` | 异步任务轮询间隔 |
| `USE_STUB_OCR` | `true` | `false` 时让 Python image-worker 处理 OCR |

完整字段见 `server/src/main/resources/application.yaml`。

### 2.4 注册策略

- `AUTH_ALLOW_REGISTRATION=true` — 任意来源都可走 HTTP `POST /api/v1/auth/register`
- `AUTH_ALLOW_REGISTRATION=false`(默认) — HTTP 注册返回 403 `REGISTRATION_DISABLED`,只能用 CLI 创建账号

CLI 直连 DB,不受这个开关影响,所以即使 HTTP 关闭仍可补账号。

## 3. 维护 CLI

`bin/luvtter-cli`(Linux/macOS)/ `bin/luvtter-cli.bat`(Windows)与 server 共用同一份 `application.yaml` + 环境变量,直连后端配置的数据库。

### 3.1 命令一览

```
bin/luvtter-cli help
bin/luvtter-cli register-user --email=foo@bar.com --password=12345678 --name=Foo
bin/luvtter-cli list-users
bin/luvtter-cli list-users --limit=10
```

### 3.2 fat jar 用法

未解压 dist 时,fat jar 也带 CLI 类:

```bash
java -cp luvtter-server-v0.1.0.jar com.luvtter.server.tools.LuvtterCliKt register-user \
    --email=foo@bar.com --password=12345678 --name=Foo
```

### 3.3 容器中执行

```bash
docker exec -it <container> /opt/luvtter-server/bin/luvtter-cli list-users
```

### 3.4 扩展新命令

`server/src/main/kotlin/com/luvtter/server/tools/LuvtterCli.kt` 内追加一个 `CliCommand` 实现并挂进 `commands` 列表即可,无需改 Gradle/CI 配置。

## 4. 客户端配置(Desktop)

Desktop 启动时按以下顺序查找 TOML 配置(只读取**第一个存在**的):

1. `$LUVTTER_CONFIG`(绝对路径)
2. 当前工作目录 `./luvtter.toml`
3. `${XDG_CONFIG_HOME:-~/.config}/luvtter/luvtter.toml`

模板见仓库根目录 `luvtter.example.toml`。完整字段:

```toml
[server]
baseUrl = ""             # 留空 → 平台默认(Desktop=http://localhost:8080)

[devAuth]
enabled = false          # 登录页自动填充测试账号(仅本地开发)
email = ""
password = ""

[features]
showExpedite = true      # 寄件箱「加速到达」按钮(测试用)
allowRegistration = true # 登录页是否展示「申领入籍」入口
```

Android/iOS 当前不读取此文件,固定走代码默认值。

## 5. 健康检查

服务端起来后:

```bash
curl http://localhost:8080/api/v1/hello
```

返回 `data.message=Hello from letter-app server` 即正常。
