# luvtter-desktop-bin (AUR)

这个目录是 AUR 包 `luvtter-desktop-bin` 的源模板,**不是**实际推送到 AUR 的副本。
真正的 AUR 仓库由 `.github/workflows/release.yml` 里的 `aur-publish` job 自动同步:

1. tag 触发 release → 构建并上传 `luvtter-desktop-vX.Y.Z-linux-x86_64.deb`
2. `aur-publish` 把本目录复制出去,用 tag 注入 `pkgver`,`updpkgsums` 重算 sha256
3. push 到 `ssh://aur@aur.archlinux.org/luvtter-desktop-bin.git`

要让自动化生效,GitHub repo 需配置 secret:

- `AUR_SSH_PRIVATE_KEY` — 一个已在 https://aur.archlinux.org/account 上传公钥的 SSH 私钥(OpenSSH 格式,不带 passphrase)
- (可选) `AUR_USERNAME` / `AUR_EMAIL` — 提交 commit 时的署名,默认走 maintainer

首次 push 之前,确保 AUR 上已存在该包名(或本地先 `git clone ssh://aur@aur.archlinux.org/luvtter-desktop-bin.git` 创建空仓库占位)。

## 本地验证(Arch 主机)

```bash
cd aur/luvtter-desktop-bin
# 临时把 pkgver 改成已发布过的 tag,比如 0.1.0
sed -i 's/^pkgver=.*/pkgver=0.1.0/' PKGBUILD
updpkgsums
makepkg -si
```
