package com.luvtter.shared.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 加载本地 `luvtter.toml` 配置。各平台 actual 提供 raw 字符串后由 commonMain 走 ktoml 解析。
 * 文件缺失或解析失败一律降级为 [AppConfig.Default],错误以 warn 级别打印,便于排查。
 */
expect fun readAppConfigSource(): String?

private val log = KotlinLogging.logger("AppConfigLoader")

private val tomlParser by lazy {
    // ignoreUnknownNames=true:旧字段保留容忍;allowEmptyValues=true:`baseUrl = ""` 等价缺省
    Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true, allowEmptyValues = true))
}

fun loadAppConfig(): AppConfig {
    val raw = runCatching { readAppConfigSource() }
        .onFailure { log.warn(it) { "AppConfig: 读取本地 toml 失败,走默认值" } }
        .getOrNull()?.takeIf { it.isNotBlank() }
    if (raw == null) {
        log.info { "AppConfig: 没有本地 toml 文件,走默认值" }
        return AppConfig.Default
    }
    return runCatching { tomlParser.decodeFromString(AppConfig.serializer(), raw) }
        .onSuccess {
            log.info {
                "AppConfig: 加载成功 server=${it.server.baseUrl.ifBlank { "<default>" }} " +
                    "devAuth.enabled=${it.devAuth.enabled} showExpedite=${it.features.showExpedite}"
            }
        }
        .onFailure { log.warn(it) { "AppConfig: 解析 toml 失败,走默认值" } }
        .getOrElse { AppConfig.Default }
}
