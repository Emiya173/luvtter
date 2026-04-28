# AUR packages

仓库内维护两个 AUR 包模板:

| 目录 | AUR 包名 | 来源 | 谁维护 |
|---|---|---|---|
| `luvtter-desktop-bin/` | `luvtter-desktop-bin` | GitHub Release 的 `tar.gz`(Compose Desktop createDistributable) | release.yml 自动 push |
| `luvtter-desktop-git/` | `luvtter-desktop-git` | git HEAD,本机 Gradle 自构 | 手工 push,改动 PKGBUILD 时同步 |

两个包都用 `provides=(luvtter-desktop)` + `conflicts=(...)` 互斥,用户三选一(`-bin` / `-git` / 未来可能的源码 release 包 `luvtter-desktop`)。

## `-bin` 自动同步流程

`.github/workflows/release.yml` 里的 `aur-publish` job:
1. tag 触发 release,等 `luvtter-desktop-vX.Y.Z-linux-x86_64.tar.gz` asset 上架
2. `sed` 注入 `pkgver=X.Y.Z` 到 `aur/luvtter-desktop-bin/PKGBUILD`
3. `KSXGitHub/github-actions-deploy-aur` 跑 `updpkgsums` + 重生 `.SRCINFO` + `git push` 到 `ssh://aur@aur.archlinux.org/luvtter-desktop-bin.git`

依赖 GitHub repo secret `AUR_SSH_PRIVATE_KEY`(对应公钥已挂在 aur.archlinux.org 账号上)。

## `-git` 手动维护流程

`-git` 包不绑版本,通常只在改 PKGBUILD 本身(makedepends 调整、构建命令变化、依赖增减)时才更新一次。流程:

```bash
# 一次性 clone
git clone ssh://aur@aur.archlinux.org/luvtter-desktop-git.git ~/aur/luvtter-desktop-git

# 改完模板后同步
cd ~/aur/luvtter-desktop-git
cp /path/to/luvtter/aur/luvtter-desktop-git/{PKGBUILD,luvtter-desktop.desktop} .
makepkg --printsrcinfo > .SRCINFO
git add -A
git commit -m "Update build deps"
git push
```

不要把版本号写死到 PKGBUILD 的 `pkgver=` —— `pkgver()` 函数会在 `makepkg` 时动态生成。

## 本地试装(Arch 主机)

`-bin`(模拟一次发版后的安装):
```bash
cd aur/luvtter-desktop-bin
sed -i 's/^pkgver=.*/pkgver=0.1.0/' PKGBUILD   # 改成已发布过的 tag
updpkgsums
makepkg -si
```

`-git`(无需 release):
```bash
cd aur/luvtter-desktop-git
makepkg -si
```

## 首次启用 AUR

1. AUR 注册账号 + 上传 SSH 公钥
2. **每个包名都需要一次首次 push 来在 AUR 上"创建"**:
   ```bash
   mkdir luvtter-desktop-bin && cd luvtter-desktop-bin
   git init -b master
   git remote add origin ssh://aur@aur.archlinux.org/luvtter-desktop-bin.git
   cp /path/to/luvtter/aur/luvtter-desktop-bin/{PKGBUILD,luvtter-desktop.desktop,.SRCINFO} .
   # 这里要先把 .SRCINFO/PKGBUILD 里的占位 pkgver 改成实际 tag
   git add -A && git commit -m "Initial release v0.1.0"
   git push -u origin master
   ```
3. 第一次 push 成功后,后续的自动化 push(`-bin`)和手动 push(`-git`)就只是普通的 git push。
