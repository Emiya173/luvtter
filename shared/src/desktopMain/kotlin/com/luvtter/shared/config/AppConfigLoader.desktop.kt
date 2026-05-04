package com.luvtter.shared.config

import java.io.File

/**
 * Desktop 查找顺序:
 * 1. 环境变量 `LUVTTER_CONFIG` 指向的绝对路径
 * 2. 当前工作目录下 `luvtter.toml`(便于开发期 `./gradlew :composeApp:run` 直接用仓库根的覆盖文件)
 * 3. `${XDG_CONFIG_HOME:-~/.config}/luvtter/luvtter.toml`
 *
 * 全部不存在则返回 null,由 [loadAppConfig] 走默认值。
 */
actual fun readAppConfigSource(): String? {
    val candidates = buildList {
        System.getenv("LUVTTER_CONFIG")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        add(File("luvtter.toml"))
        val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.config"
        add(File("$xdg/luvtter/luvtter.toml"))
    }
    val hit = candidates.firstOrNull { it.isFile && it.canRead() } ?: return null
    return runCatching { hit.readText(Charsets.UTF_8) }.getOrNull()
}
