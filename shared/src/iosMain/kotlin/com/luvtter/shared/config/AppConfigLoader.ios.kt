package com.luvtter.shared.config

/**
 * iOS 暂不支持外部配置文件,先返回 null 走默认值。
 * 后续可改为读 `Bundle.main.path(forResource: "luvtter", ofType: "toml")`。
 */
actual fun readAppConfigSource(): String? = null
