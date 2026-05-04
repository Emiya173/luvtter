package com.luvtter.shared.config

/**
 * Android 暂不支持外部配置文件(需要 Context),先返回 null 走默认值。
 * 后续要接的话:把 `assets/luvtter.toml` 通过 `Context.assets.open` 读出来。
 */
actual fun readAppConfigSource(): String? = null
