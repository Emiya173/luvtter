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
