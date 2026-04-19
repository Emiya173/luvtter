package com.letter.shared.network

import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.HelloResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class HelloApi(private val client: HttpClient) {
    suspend fun hello(): HelloResponse {
        return client.get("/api/v1/hello").body<ApiResponse<HelloResponse>>().data
    }
}
