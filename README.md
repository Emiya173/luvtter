# luvtter

慢节奏信件应用 · Kotlin 全栈(Ktor 服务端 + Compose Multiplatform 客户端,覆盖 Desktop / Android / iOS)。

## Brief

- 写信 → 信件按距离 / 等级化送达时间在「在途」状态停留 → 收件人到时拆封
- 段落式编辑、划掉、涂改模式、贴纸 / 图片 / 扫描信 / 手写信附件
- 多地址(真实 / 虚拟锚点)、联系人、屏蔽、好友隔离
- SSE 实时通知 + 拆信 / 寄出动画 + 纸面化 UI 风格
- 全文搜索(中文 bigram)、分类夹、收藏、归档导出 ZIP
- 服务端 Postgres + MinIO 对象存储,异步任务管线 + OCR 索引

详细功能与技术设计:

- 功能设计:[`doc/信件应用-功能设计文档.md`](doc/信件应用-功能设计文档.md)
- 技术设计:[`doc/信件应用-技术设计文档.md`](doc/信件应用-技术设计文档.md)
- 路线图:[`doc/技术路线图.md`](doc/技术路线图.md)
- UI 迁移:[`doc/UI迁移计划.md`](doc/UI迁移计划.md)

## Usage

最终用户拿 GitHub Release 的产物即可:

- **Desktop**:`luvtter-desktop-<tag>-<platform>.{deb,dmg,msi}`,装完打开,登录后即可写/收信
- **Arch Linux**:稳定版 `yay -S luvtter-desktop-bin`(CI 在每个 tag 自动同步)/ 跟主线 `yay -S luvtter-desktop-git`
- **Android**:`luvtter-android-<tag>-universal-debug.apk`,侧载安装

客户端默认连本地 `http://localhost:8080` 后端。要指向自己的服务器,在 Desktop 上写一份 `~/.config/luvtter/luvtter.toml`(模板见仓库根目录 `luvtter.example.toml`):

```toml
[server]
baseUrl = "https://luvtter.example.com"
```

完整字段说明见 [`doc/deployment.md` §4](doc/deployment.md)。

## 部署

自建服务端:用 Release 里的 `luvtter-server-<tag>-dist.tar.gz` 或 `luvtter-server-<tag>.jar`,搭配 Postgres 16 + MinIO/S3。注册账号通过随发行包附带的 `bin/luvtter-cli register-user ...`。

完整部署、ENV、CLI 命令清单见 [**`doc/deployment.md`**](doc/deployment.md)。

## 开发

本地把 server + 客户端 + 基础设施跑起来:

```bash
docker compose up -d postgres minio
./gradlew :server:run
./gradlew :server:cli --args="register-user --email=dev@local --password=12345678 --name=Dev"
./gradlew :composeApp:run
```

完整开发指南、命令参考、CI/Release 流程见 [**`doc/development.md`**](doc/development.md)。
