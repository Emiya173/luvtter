package com.luvtter.server.auth

import io.ktor.server.config.ApplicationConfig

/**
 * 控制 HTTP 注册接口的开关。CLI 注册不走这里,关闭后只能通过 CLI 创建账号。
 */
data class AuthPolicy(
    val allowRegistration: Boolean,
)

fun ApplicationConfig.authPolicy(): AuthPolicy = AuthPolicy(
    allowRegistration = propertyOrNull("auth.allowRegistration")?.getString()?.toBoolean() ?: false,
)
