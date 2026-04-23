package com.letter.shared.network

import com.letter.contract.dto.ApiError
import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val details: Map<String, String>? = null
) : RuntimeException("[$code] $message")

val ApiJson: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

suspend fun HttpResponse.toApiException(): ApiException {
    val raw = runCatching { bodyAsText() }.getOrDefault("")
    val err = runCatching {
        ApiJson.decodeFromString(ErrorResponse.serializer(), raw).error
    }.getOrElse {
        ApiError(code = "HTTP_${status.value}", message = raw.ifBlank { status.description })
    }
    return ApiException(status.value, err.code, err.message, err.details)
}

suspend inline fun <reified T> HttpResponse.unwrap(): T {
    if (status.isSuccess()) {
        return body<ApiResponse<T>>().data
    }
    throw toApiException()
}

suspend fun HttpResponse.ensureSuccess() {
    if (status.isSuccess()) return
    throw toApiException()
}
