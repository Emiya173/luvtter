package com.luvtter.shared.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

expect fun platformHttpClient(): HttpClient

/**
 * 用于直传到对象存储的"裸"客户端：不带 JWT、不带 baseUrl、不带 ContentNegotiation。
 * 直接 PUT 字节到 MinIO 预签名 URL 时用。
 */
fun createRawHttpClient(): HttpClient = platformHttpClient().config {
    install(Logging) { level = LogLevel.INFO }
}

fun createHttpClient(
    baseUrl: String,
    tokenProvider: () -> String? = { null }
): HttpClient = platformHttpClient().config {
    install(ContentNegotiation) {
        json(Json {
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        })
    }
    install(Logging) {
        level = LogLevel.INFO
    }
    install(SSE)
    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
        tokenProvider()?.let { token ->
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
